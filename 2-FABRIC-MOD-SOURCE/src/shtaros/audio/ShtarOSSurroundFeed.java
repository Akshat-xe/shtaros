package shtaros.audio;

import net.minecraft.client.sounds.AudioStream;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Splits one synchronized eight-channel PCM stream into eight OpenAL mono streams. */
public final class ShtarOSSurroundFeed implements ShtarOSSpeakerFeed {
    private static final int SPEAKERS = 8;
    private static final int SAMPLES = 1024;
    private final ArrayBlockingQueue<byte[]>[] queues;
    private final SpeakerStream[] streams = new SpeakerStream[SPEAKERS];
    private final AtomicInteger activeMask;
    private volatile boolean running = true;
    private volatile boolean finished;
    private volatile String error;
    private volatile HttpURLConnection connection;
    private volatile InputStream input;
    private final Thread worker;

    @SuppressWarnings("unchecked")
    public ShtarOSSurroundFeed(String videoId, int startSeconds, int speakerMask) {
        activeMask = new AtomicInteger(speakerMask);
        queues = new ArrayBlockingQueue[SPEAKERS];
        for (int i = 0; i < SPEAKERS; i++) {
            queues[i] = new ArrayBlockingQueue<>(128);
            streams[i] = new SpeakerStream(i);
        }
        worker = new Thread(() -> pump(videoId, startSeconds), "ShtarOS-Surround-Feed");
        worker.setDaemon(true);
        worker.start();
    }

    public AudioStream speaker(int index) {
        return streams[index];
    }

    public boolean waitForInitialData(int timeoutMs) {
        long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (running && !finished && System.nanoTime() < end) {
            if (queuedAudio() >= 36) return true;
            try { Thread.sleep(20); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return queuedAudio() > 0;
    }

    public boolean isFinished() {
        return finished && queuedAudio() == 0;
    }

    private int queuedAudio() {
        int mask = activeMask.get();
        int max = 0;
        for (int i = 0; i < SPEAKERS; i++) {
            if ((mask & (1 << i)) != 0) max = Math.max(max, queues[i].size());
        }
        return max;
    }

    public void disable(int index) {
        activeMask.getAndUpdate(mask -> mask & ~(1 << index));
        queues[index].clear();
    }

    public String error() { return error; }

    private void pump(String videoId, int startSeconds) {
        try {
            String encoded = java.net.URLEncoder.encode(videoId, java.nio.charset.StandardCharsets.UTF_8);
            connection = (HttpURLConnection) URI.create("http://127.0.0.1:25592/surround/stream/" + encoded + "?start=" + Math.max(0, startSeconds)).toURL().openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            if (connection.getResponseCode() != 200) {
                error = "Media server returned HTTP " + connection.getResponseCode();
                return;
            }
            input = connection.getInputStream();
            byte[] interleaved = new byte[SAMPLES * SPEAKERS * 2];
            boolean receivedAudio = false;
            while (running) {
                int n = 0;
                while (n < interleaved.length && running) {
                    int read = input.read(interleaved, n, interleaved.length - n);
                    if (read < 0) break;
                    n += read;
                }
                if (n < interleaved.length) break;
                receivedAudio = true;
                byte[][] mono = new byte[SPEAKERS][SAMPLES * 2];
                for (int sample = 0; sample < SAMPLES; sample++) {
                    for (int speaker = 0; speaker < SPEAKERS; speaker++) {
                        int src = (sample * SPEAKERS + speaker) * 2;
                        int dst = sample * 2;
                        mono[speaker][dst] = interleaved[src];
                        mono[speaker][dst + 1] = interleaved[src + 1];
                    }
                }
                for (int speaker = 0; speaker < SPEAKERS; speaker++) {
                    if ((activeMask.get() & (1 << speaker)) == 0) continue;
                    ArrayBlockingQueue<byte[]> queue = queues[speaker];
                    while (running && (activeMask.get() & (1 << speaker)) != 0) {
                        if (queue.offer(mono[speaker], 100, TimeUnit.MILLISECONDS)) break;
                    }
                }
            }
            if (running && !receivedAudio) error = "Media server sent no audio";
        } catch (Exception e) {
            error = "Audio connection failed: " + e.getMessage();
            if (running) System.err.println("[ShtarOS Surround] Feed error: " + e.getMessage());
        } finally {
            finished = true;
            disconnect();
        }
    }

    private void disconnect() {
        try { if (input != null) input.close(); } catch (Exception ignored) {}
        if (connection != null) connection.disconnect();
    }

    @Override
    public void close() {
        running = false;
        activeMask.set(0);
        worker.interrupt();
        disconnect();
        for (ArrayBlockingQueue<byte[]> queue : queues) queue.clear();
    }

    private final class SpeakerStream implements AudioStream {
        private final int index;
        private final AudioFormat format = new AudioFormat(44100, 16, 1, true, false);
        private byte[] pending;
        private int offset;

        SpeakerStream(int index) { this.index = index; }

        @Override public AudioFormat getFormat() { return format; }

        @Override public ByteBuffer read(int size) throws IOException {
            if (!running) return null;
            int target = Math.max(2048, Math.min(size & ~1, 16384));
            ByteBuffer buffer = ByteBuffer.allocateDirect(target).order(ByteOrder.LITTLE_ENDIAN);
            while (buffer.hasRemaining() && running) {
                if (pending == null || offset == pending.length) {
                    try {
                        pending = queues[index].poll(buffer.position() > 0 ? 0 : 35, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        break;
                    }
                    offset = 0;
                    if (pending == null) break;
                }
                int n = Math.min(buffer.remaining(), pending.length - offset);
                buffer.put(pending, offset, n);
                offset += n;
            }
            if (buffer.position() == 0) {
                if (finished) return null;
                ByteBuffer silence = ByteBuffer.allocateDirect(4096).order(ByteOrder.LITTLE_ENDIAN);
                for (int i = 0; i < 2048; i++) silence.putShort((short) 0);
                silence.flip();
                return silence;
            }
            buffer.flip();
            return buffer;
        }

        @Override public void close() { queues[index].clear(); pending = null; }
    }
}
