package shtaros.audio;

import net.minecraft.client.sounds.AudioStream;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded eight-speaker PCM feed from Chromium's planar float audio callback. */
public final class ShtarOSBrowserSurroundFeed implements ShtarOSSpeakerFeed {
    private static final int SPEAKERS = 8;
    // Bounded hardware buffers provide a solid continuous audio reserve in OpenAL
    // so game tick jitters and CEF frame rendering never starve the speakers.
    private static final int MAX_STREAM_BUFFER_BYTES = 16384;
    private static final int MAX_QUEUED_AUDIO_MS = 2000;
    private static final int PRIME_AUDIO_MS = 350;
    private final ArrayBlockingQueue<byte[]>[] queues;
    private final SpeakerStream[] streams = new SpeakerStream[SPEAKERS];
    private final AtomicInteger activeMask;
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private final int sampleRate;
    private volatile boolean closed;
    private volatile boolean finished;
    private volatile long framesReceived;
    private volatile long framesRead;
    private volatile long droppedPackets;
    private volatile long emptyReads;

    @SuppressWarnings("unchecked")
    public ShtarOSBrowserSurroundFeed(int sampleRate, int speakerMask) {
        if (sampleRate < 8000 || sampleRate > 192000) throw new IllegalArgumentException("Invalid Chromium sample rate");
        this.sampleRate = sampleRate;
        activeMask = new AtomicInteger(speakerMask);
        queues = new ArrayBlockingQueue[SPEAKERS];
        for (int i = 0; i < SPEAKERS; i++) {
            queues[i] = new ArrayBlockingQueue<>(128);
            streams[i] = new SpeakerStream(i, sampleRate);
        }
    }

    @Override public AudioStream speaker(int index) { return streams[index]; }
    public boolean isPrimed() { return bufferedMillis(0) >= PRIME_AUDIO_MS && !closed; }
    public boolean markScheduled() { return scheduled.compareAndSet(false, true); }
    public long droppedPackets() { return droppedPackets; }
    public int sampleRate() { return sampleRate; }
    public long emptyReads() { return emptyReads; }
    public long framesReceived() { return framesReceived; }
    public long framesRead() { return framesRead; }
    public int bufferedMillis(int index) {
        int bytes = 0;
        for (byte[] packet : queues[index]) bytes += packet.length;
        if (streams[index].hasPending()) bytes += streams[index].pendingBytes();
        return bytes * 500 / sampleRate;
    }
    public boolean hasBufferedAudio(int index) {
        return !queues[index].isEmpty() || streams[index].hasPending();
    }
    @Override public boolean isFinished() { return finished && queuedAudio() == 0; }

    /** Copies native CEF data before its callback returns. Never blocks the capture thread. */
    public void accept(FloatBuffer left, FloatBuffer right, int frames) {
        if (closed || finished || frames <= 0 || frames > 65536) return;
        byte[][] pcm = new byte[SPEAKERS][frames * 2];
        for (int i = 0; i < frames; i++) {
            float l = clean(left.get(i));
            float r = clean(right.get(i));
            float mid = .5f * (l + r);
            float side = .5f * (l - r);
            // Shared center content (often speech) stays at the front. Sending
            // the same voice loudly to eight positions smears its localization.
            write(pcm[0], i, .85f * l);
            write(pcm[1], i, .85f * r);
            write(pcm[2], i, .05f * mid + .32f * side);
            write(pcm[3], i, .04f * mid + .22f * side);
            write(pcm[4], i, .05f * mid - .32f * side);
            write(pcm[5], i, .04f * mid - .22f * side);
            write(pcm[6], i, .03f * mid + .15f * side);
            write(pcm[7], i, .03f * mid - .15f * side);
        }
        int mask = activeMask.get();
        int maxPackets = Math.max(8, Math.min(128,
                (sampleRate * MAX_QUEUED_AUDIO_MS / 1000 + frames - 1) / frames));
        for (int s = 0; s < SPEAKERS; s++) {
            if ((mask & (1 << s)) == 0) continue;
            ArrayBlockingQueue<byte[]> queue = queues[s];
            while (queue.size() >= maxPackets) {
                if (queue.poll() != null && s == 0) droppedPackets++;
            }
            queue.offer(pcm[s]);
        }
        framesReceived += frames;
    }

    private static float clean(float value) { return Float.isFinite(value) ? value : 0f; }

    private static void write(byte[] pcm, int frame, float sample) {
        int value = (int) (Math.max(-1f, Math.min(1f, sample)) * 32767f);
        pcm[frame * 2] = (byte) value;
        pcm[frame * 2 + 1] = (byte) (value >>> 8);
    }

    private int queuedAudio() {
        int max = 0;
        int mask = activeMask.get();
        for (int i = 0; i < SPEAKERS; i++) {
            if ((mask & (1 << i)) != 0) max = Math.max(max, queues[i].size());
        }
        return max;
    }

    @Override public void disable(int index) {
        activeMask.getAndUpdate(mask -> mask & ~(1 << index));
        queues[index].clear();
    }

    public void finish() { finished = true; }

    @Override public void close() {
        closed = true;
        finished = true;
        activeMask.set(0);
        for (ArrayBlockingQueue<byte[]> queue : queues) queue.clear();
    }

    private final class SpeakerStream implements AudioStream {
        private final int index;
        private final AudioFormat format;
        private byte[] pending;
        private int offset;
        private boolean fadingIn;

        SpeakerStream(int index, int sampleRate) {
            this.index = index;
            format = new AudioFormat(sampleRate, 16, 1, true, false);
        }

        boolean hasPending() { return pending != null && offset < pending.length; }
        int pendingBytes() { return hasPending() ? pending.length - offset : 0; }

        @Override public AudioFormat getFormat() { return format; }

        @Override public ByteBuffer read(int size) throws IOException {
            if (closed) return null;
            // Minecraft requests up to a second. 6144 bytes is about 70 ms
            // at 44.1 kHz mono, enough to cross a game tick without a large lag.
            int target = Math.max(2, Math.min(size & ~1, MAX_STREAM_BUFFER_BYTES));
            ByteBuffer buffer = ByteBuffer.allocateDirect(target).order(ByteOrder.LITTLE_ENDIAN);
            while (buffer.hasRemaining()) {
                if (pending == null || offset == pending.length) {
                    pending = queues[index].poll();
                    offset = 0;
                    if (pending == null) break;
                }
                int n = Math.min(buffer.remaining(), pending.length - offset);
                buffer.put(pending, offset, n);
                offset += n;
            }
            int audioBytes = buffer.position();
            if (index == 0) framesRead += audioBytes / 2;
            // OpenAL asks for four buffers at startup. Returning null when
            // capture has no more samples leaves that slot unqueued; a padded
            // silent buffer would play *after* fresh packets have arrived.
            if (audioBytes == 0) {
                if (index == 0) emptyReads++;
                if (!finished && !closed) {
                    // Conceal underrun with a tiny silence buffer (256 frames = 512 bytes = ~5.8ms)
                    // This keeps the OpenAL hardware source in AL_PLAYING state without clicks or pops,
                    // allowing fresh incoming audio packets to continue seamlessly without channel tear-down!
                    ByteBuffer silence = ByteBuffer.allocateDirect(512).order(ByteOrder.LITTLE_ENDIAN);
                    for (int i = 0; i < 256; i++) silence.putShort((short) 0);
                    silence.flip();
                    return silence;
                }
                return null;
            }
            // A network/render stall may leave part of a buffer empty. Fade at
            // that boundary rather than cutting a nonzero waveform to silence.
            if (audioBytes > 0 && fadingIn) fade(buffer, 0, Math.min(128, audioBytes / 2), true);
            if (audioBytes < target) {
                if (audioBytes > 0) fade(buffer, audioBytes / 2 - Math.min(128, audioBytes / 2),
                        Math.min(128, audioBytes / 2), false);
                fadingIn = true;
            } else {
                fadingIn = false;
            }
            buffer.flip();
            return buffer;
        }

        private void fade(ByteBuffer buffer, int firstFrame, int frames, boolean fadeIn) {
            for (int i = 0; i < frames; i++) {
                int byteIndex = (firstFrame + i) * 2;
                float gain = fadeIn ? (i + 1f) / frames : (frames - i - 1f) / frames;
                buffer.putShort(byteIndex, (short) (buffer.getShort(byteIndex) * gain));
            }
        }

        @Override public void close() { queues[index].clear(); pending = null; }
    }
}
