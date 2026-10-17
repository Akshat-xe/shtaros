package shtaros.audio;

import net.minecraft.client.sounds.AudioStream;

import javax.sound.sampled.AudioFormat;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance streaming AudioStream for Minecraft's OpenAL engine.
 * Connects to ShtarOS Audio Daemon (localhost:25592) to stream 44.1kHz 16-bit Mono PCM.
 */
public class ShtarOSAudioStream implements AudioStream {

    private final AudioFormat format;
    private final String streamUrl;
    // 2048 chunks * 16KB = 32MB pre-buffered in memory for seamless continuous playback
    private final BlockingQueue<byte[]> chunkQueue = new LinkedBlockingQueue<>(2048);
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean finished = new AtomicBoolean(false);
    private final AtomicLong totalBytesBuffered = new AtomicLong(0);

    private Thread workerThread;
    private HttpURLConnection connection;
    private InputStream inputStream;

    private byte[] pendingChunk = null;
    private int pendingOffset = 0;

    public ShtarOSAudioStream(String streamUrl) {
        this.streamUrl = streamUrl;
        // 44100 Hz, 16-bit, 1 channel (Mono), signed PCM, Little Endian
        // AL_FORMAT_MONO16 in OpenAL is required for 3D spatial attenuation & panning
        this.format = new AudioFormat(44100.0f, 16, 1, true, false);

        startWorker();
    }

    private void startWorker() {
        this.workerThread = new Thread(() -> {
            try {
                URL url = URI.create(this.streamUrl).toURL();
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("User-Agent", "ShtarOS-Spatial-Jukebox/1.0");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(30000);

                int responseCode = connection.getResponseCode();
                if (responseCode != 200) {
                    System.err.println("[ShtarOS Jukebox] HTTP stream returned error: " + responseCode);
                    finished.set(true);
                    return;
                }

                inputStream = new BufferedInputStream(connection.getInputStream(), 65536);
                byte[] tempBuffer = new byte[16384];

                while (running.get()) {
                    int bytesRead = inputStream.read(tempBuffer);
                    if (bytesRead == -1) {
                        break;
                    }
                    if (bytesRead > 0) {
                        byte[] chunk = new byte[bytesRead];
                        System.arraycopy(tempBuffer, 0, chunk, 0, bytesRead);
                        totalBytesBuffered.addAndGet(bytesRead);

                        while (running.get()) {
                            if (chunkQueue.offer(chunk, 100, TimeUnit.MILLISECONDS)) {
                                break;
                            }
                        }
                    }
                }
            } catch (InterruptedException ignored) {
            } catch (Exception e) {
                if (running.get()) {
                    System.err.println("[ShtarOS Jukebox] Stream worker error: " + e.getMessage());
                }
            } finally {
                finished.set(true);
            }
        }, "ShtarOS-Audio-Downloader");

        this.workerThread.setDaemon(true);
        this.workerThread.start();
    }

    /**
     * Waits for initial chunks to arrive from the daemon before attaching to OpenAL.
     * Ensures at least ~88KB (~1 second of audio) or 6 chunks before OpenAL begins playback.
     */
    public boolean waitForInitialData(int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (totalBytesBuffered.get() >= 88200 || chunkQueue.size() >= 6 || (finished.get() && !chunkQueue.isEmpty())) {
                return true;
            }
            if (finished.get() && chunkQueue.isEmpty()) {
                return false;
            }
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                return false;
            }
        }
        return totalBytesBuffered.get() > 16384 || !chunkQueue.isEmpty();
    }

    @Override
    public AudioFormat getFormat() {
        return this.format;
    }

    @Override
    public ByteBuffer read(int size) throws IOException {
        if (!running.get()) {
            return null;
        }

        byte[] output = new byte[size];
        int totalGathered = 0;

        while (totalGathered < size && running.get()) {
            if (pendingChunk != null && pendingOffset < pendingChunk.length) {
                int toCopy = Math.min(size - totalGathered, pendingChunk.length - pendingOffset);
                System.arraycopy(pendingChunk, pendingOffset, output, totalGathered, toCopy);
                totalGathered += toCopy;
                pendingOffset += toCopy;

                if (pendingOffset >= pendingChunk.length) {
                    pendingChunk = null;
                    pendingOffset = 0;
                }
                continue;
            }

            int pollTimeoutMs = (totalGathered > 0) ? 100 : 1500;
            byte[] nextChunk = null;
            try {
                nextChunk = chunkQueue.poll(pollTimeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {}

            if (nextChunk != null) {
                pendingChunk = nextChunk;
                pendingOffset = 0;
            } else {
                if (finished.get() && chunkQueue.isEmpty()) {
                    break;
                }
                if (totalGathered > 0) {
                    break;
                }
                if (!finished.get()) {
                    try {
                        nextChunk = chunkQueue.poll(500, TimeUnit.MILLISECONDS);
                        if (nextChunk != null) {
                            pendingChunk = nextChunk;
                            pendingOffset = 0;
                            continue;
                        }
                    } catch (InterruptedException ignored) {}
                }
                break;
            }
        }

        // Align totalGathered to 2 bytes (16-bit mono sample boundary)
        if (totalGathered % 2 != 0) {
            totalGathered--;
        }

        if (totalGathered == 0) {
            if (finished.get() && chunkQueue.isEmpty()) {
                return null; // Signals clean EOF
            }
            // Return small silence buffer (4096 bytes = 46ms of audio) to prevent AL_INVALID_VALUE
            ByteBuffer silence = ByteBuffer.allocateDirect(4096);
            silence.order(ByteOrder.LITTLE_ENDIAN);
            silence.position(0);
            silence.limit(4096);
            return silence;
        }

        ByteBuffer directBuffer = ByteBuffer.allocateDirect(totalGathered);
        directBuffer.order(ByteOrder.LITTLE_ENDIAN);
        directBuffer.put(output, 0, totalGathered);
        directBuffer.flip();
        return directBuffer;
    }

    public boolean isFinished() {
        return finished.get() && chunkQueue.isEmpty() && pendingChunk == null;
    }

    @Override
    public void close() throws IOException {
        running.set(false);
        if (workerThread != null) {
            workerThread.interrupt();
        }
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (Exception ignored) {}
        }
        if (connection != null) {
            try {
                connection.disconnect();
            } catch (Exception ignored) {}
        }
        chunkQueue.clear();
        pendingChunk = null;
    }
}
