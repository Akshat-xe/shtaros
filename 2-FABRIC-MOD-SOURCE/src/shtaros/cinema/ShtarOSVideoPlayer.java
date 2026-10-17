package shtaros.cinema;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ShtarOS Cinema Video Player (1280x720, 30 FPS ceiling)
 * Streams uncompressed raw RGBA frames from the local daemon into a DynamicTexture.
 * Direct memory upload with reusable buffers, zero client-side decode overhead,
 * and zero PNG/format validation errors.
 */
public class ShtarOSVideoPlayer {

    public static final Identifier TEXTURE_ID = Identifier.fromNamespaceAndPath("shtaros", "textures/cinema_screen.png");
    public static final int FRAME_WIDTH = 1280;
    public static final int FRAME_HEIGHT = 720;
    public static final int FRAME_SIZE = FRAME_WIDTH * FRAME_HEIGHT * 4;

    private static DynamicTexture dynamicTexture;

    // The producer never writes the frame currently being uploaded by the render thread.
    private static final ArrayBlockingQueue<byte[]> freeBuffers = new ArrayBlockingQueue<>(3);
    private static final AtomicReference<byte[]> readyBuffer = new AtomicReference<>(null);
    static {
        for (int i = 0; i < 3; i++) freeBuffers.add(new byte[FRAME_SIZE]);
    }

    private static final AtomicBoolean isPlaying = new AtomicBoolean(false);
    private static final AtomicBoolean isPaused = new AtomicBoolean(false);
    private static final AtomicBoolean isPowered = new AtomicBoolean(true);
    private static final AtomicBoolean hasFrame = new AtomicBoolean(false);
    private static final AtomicBoolean streamFrameReady = new AtomicBoolean(false);
    private static final AtomicBoolean endedNaturally = new AtomicBoolean(false);
    private static final AtomicInteger session = new AtomicInteger();

    private static Thread streamThread;
    private static volatile HttpURLConnection activeConnection;
    private static volatile InputStream activeStream;
    private static String currentVideoId = null;
    private static boolean initialized = false;

    public static synchronized void ensureInitialized() {
        if (initialized && dynamicTexture != null) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getTextureManager() == null) return;
            dynamicTexture = new DynamicTexture("cinema_screen", FRAME_WIDTH, FRAME_HEIGHT, false);

            // Fill with dark blue-black initial chassis pattern directly into pixel buffer
            NativeImage pixels = dynamicTexture.getPixels();
            if (pixels != null) {
                IntBuffer colors = pixels.getPixelBytes().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
                for (int y = 0; y < FRAME_HEIGHT; y++) {
                    for (int x = 0; x < FRAME_WIDTH; x++) {
                        int r = 18 + (y * 12 / FRAME_HEIGHT);
                        int g = 22 + (y * 16 / FRAME_HEIGHT);
                        int b = 32 + (y * 24 / FRAME_HEIGHT);
                        if (x < 6 || x >= FRAME_WIDTH - 6 || y < 6 || y >= FRAME_HEIGHT - 6) {
                            r = 37; g = 99; b = 235;
                        }
                        colors.put(0xFF000000 | (b << 16) | (g << 8) | r);
                    }
                }
                try {
                    dynamicTexture.upload();
                } catch (Throwable ignored) {}
            }

            mc.getTextureManager().register(TEXTURE_ID, dynamicTexture);
            initialized = true;
            System.out.println("[ShtarOS Cinema] DynamicTexture (1280x720) initialized and registered successfully!");

            // Asynchronously fetch standby frame from daemon
            loadStaticFrame("http://127.0.0.1:25592/standby_frame?w=" + FRAME_WIDTH + "&h=" + FRAME_HEIGHT);
        } catch (Throwable t) {
            System.err.println("[ShtarOS Cinema] Initialization warning: " + t.getMessage());
        }
    }

    public static Identifier getTextureId() {
        ensureInitialized();
        return TEXTURE_ID;
    }

    public static void refreshStandbyFrame() {
        if (!isPlaying.get() && isPowered.get()) {
            int standbySession = session.get();
            Minecraft.getInstance().execute(() -> {
                if (session.get() != standbySession || isPlaying.get() || dynamicTexture == null) return;
                NativeImage pixels = dynamicTexture.getPixels();
                if (pixels == null) return;
                IntBuffer colors = pixels.getPixelBytes().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
                for (int y = 0; y < FRAME_HEIGHT; y++) {
                    for (int x = 0; x < FRAME_WIDTH; x++) {
                        int blue = 26 + 22 * (FRAME_HEIGHT - y) / FRAME_HEIGHT;
                        int red = 9 + 6 * (FRAME_HEIGHT - y) / FRAME_HEIGHT;
                        int green = 20 + 12 * (FRAME_HEIGHT - y) / FRAME_HEIGHT;
                        if (x < 7 || x >= FRAME_WIDTH - 7 || y < 7 || y >= FRAME_HEIGHT - 7) {
                            red = 42; green = 188; blue = 214;
                        }
                        colors.put(0xFF000000 | (blue << 16) | (green << 8) | red);
                    }
                }
                dynamicTexture.upload();
            });
            loadStaticFrame("http://127.0.0.1:25592/standby_frame?w=" + FRAME_WIDTH + "&h=" + FRAME_HEIGHT);
        }
    }

    public static boolean isPlaying() {
        return isPlaying.get();
    }

    public static boolean hasFrame() { return hasFrame.get(); }
    public static boolean endedNaturally() { return endedNaturally.get(); }

    public static boolean isPaused() {
        return isPaused.get();
    }

    public static boolean isPowered() {
        return isPowered.get();
    }

    public static void setPower(boolean power) {
        isPowered.set(power);
        if (!power) {
            stop();
            loadStaticFrame("http://127.0.0.1:25592/standby_frame?w=" + FRAME_WIDTH + "&h=" + FRAME_HEIGHT);
        } else {
            loadStaticFrame("http://127.0.0.1:25592/boot_frame?progress=100&w=" + FRAME_WIDTH + "&h=" + FRAME_HEIGHT);
        }
    }

    public static void togglePower() {
        setPower(!isPowered.get());
    }

    public static synchronized void play(String videoId, int startSeconds) {
        ensureInitialized();
        stop();

        isPowered.set(true);
        isPlaying.set(true);
        isPaused.set(false);
        hasFrame.set(false);
        streamFrameReady.set(false);
        endedNaturally.set(false);
        currentVideoId = videoId;
        int playbackSession = session.incrementAndGet();
        loadStaticFrame("http://127.0.0.1:25592/loading_frame?w=" + FRAME_WIDTH + "&h=" + FRAME_HEIGHT);

        streamThread = new Thread(() -> {
            try {
                // The server paces a 30 FPS stream; render displays the newest complete frame.
                String streamUrl = "http://127.0.0.1:25592/video/stream/" + java.net.URLEncoder.encode(videoId, java.nio.charset.StandardCharsets.UTF_8) + "?w=" + FRAME_WIDTH + "&h=" + FRAME_HEIGHT + "&start=" + Math.max(0, startSeconds);
                HttpURLConnection conn = (HttpURLConnection) URI.create(streamUrl).toURL().openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(15000);
                conn.connect();

                if (conn.getResponseCode() != 200) {
                    System.err.println("[ShtarOS Cinema] HD Video stream failed with HTTP " + conn.getResponseCode());
                    reportPlaybackError("Video server returned HTTP " + conn.getResponseCode(), playbackSession);
                    conn.disconnect();
                    return;
                }

                InputStream in = conn.getInputStream();
                if (!isPlaying.get() || session.get() != playbackSession) {
                    in.close();
                    conn.disconnect();
                    return;
                }
                activeConnection = conn;
                activeStream = in;

                // Hold frame zero until the physical speaker channels are primed.
                // Backpressure keeps the video decoder from racing ahead during audio startup.
                long audioDeadline = System.nanoTime() + 12_000_000_000L;
                while (isPlaying.get() && session.get() == playbackSession
                        && !ShtarOSSurroundManager.isReady() && System.nanoTime() < audioDeadline) {
                    Thread.sleep(10);
                }
                if (!isPlaying.get() || session.get() != playbackSession) {
                    try { in.close(); } catch (Exception ignored) {}
                    conn.disconnect();
                    if (activeConnection == conn) { activeConnection = null; activeStream = null; }
                    return;
                }

                int receivedFrames = 0;
                while (isPlaying.get() && session.get() == playbackSession) {
                    if (isPaused.get()) {
                        Thread.sleep(100);
                        continue;
                    }

                    byte[] targetBuffer = freeBuffers.poll();
                    if (targetBuffer == null) {
                        byte[] stale = readyBuffer.getAndSet(null);
                        targetBuffer = stale != null ? stale : freeBuffers.take();
                    }

                    int totalRead = 0;
                    while (totalRead < FRAME_SIZE && isPlaying.get() && session.get() == playbackSession) {
                        int r = in.read(targetBuffer, totalRead, FRAME_SIZE - totalRead);
                        if (r == -1) break;
                        totalRead += r;
                    }

                    if (totalRead < FRAME_SIZE) {
                        freeBuffers.offer(targetBuffer);
                        break;
                    }

                    if (session.get() != playbackSession) {
                        freeBuffers.offer(targetBuffer);
                        break;
                    }
                    streamFrameReady.set(true);
                    byte[] stale = readyBuffer.getAndSet(targetBuffer);
                    if (stale != null) freeBuffers.offer(stale);
                    receivedFrames++;
                }

                if (receivedFrames == 0 && isPlaying.get() && session.get() == playbackSession) {
                    reportPlaybackError("Video server sent no frames", playbackSession);
                } else if (receivedFrames > 0 && session.get() == playbackSession) {
                    endedNaturally.set(true);
                }

                try { in.close(); } catch (Exception ignored) {}
                try { conn.disconnect(); } catch (Exception ignored) {}
                if (activeConnection == conn) {
                    activeConnection = null;
                    activeStream = null;
                }
            } catch (Exception e) {
                if (isPlaying.get()) {
                    System.err.println("[ShtarOS Cinema] HD Video stream error: " + e.getMessage());
                    reportPlaybackError("Video stream failed: " + e.getMessage(), playbackSession);
                }
            } finally {
                if (session.get() == playbackSession) isPlaying.set(false);
            }
        }, "ShtarOS-HDVideoStream");
        streamThread.setDaemon(true);
        streamThread.start();
    }

    private static void reportPlaybackError(String message, int playbackSession) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (session.get() == playbackSession && mc.player != null) {
                ShtarOSCinemaManager.log("Video error: " + message);
                mc.player.sendSystemMessage(Component.literal("§c[ShtarOS] " + message));
            }
        });
    }

    public static synchronized void stop() {
        session.incrementAndGet();
        isPlaying.set(false);
        isPaused.set(false);
        endedNaturally.set(false);
        currentVideoId = null;
        InputStream in = activeStream;
        HttpURLConnection conn = activeConnection;
        activeStream = null;
        activeConnection = null;
        if (in != null) try { in.close(); } catch (Exception ignored) {}
        if (conn != null) conn.disconnect();
        if (streamThread != null) {
            streamThread.interrupt();
            streamThread = null;
        }
        byte[] stale = readyBuffer.getAndSet(null);
        if (stale != null) freeBuffers.offer(stale);
    }

    public static void togglePause() {
        if (isPlaying.get()) {
            isPaused.set(!isPaused.get());
        }
    }

    /**
     * Called at the HEAD of every render frame (before any render pass starts)
     * to safely upload the latest video frame to GPU texture memory.
     */
    public static void updateTextureOnRender() {
        ensureInitialized();
        if (!initialized || dynamicTexture == null || dynamicTexture.getPixels() == null) return;

        byte[] raw = readyBuffer.getAndSet(null);
        if (raw != null && raw.length == FRAME_SIZE) {
            try {
                ByteBuffer buf = dynamicTexture.getPixels().getPixelBytes();
                buf.clear();
                buf.put(raw);
                buf.position(0);
                dynamicTexture.upload();
                hasFrame.set(true);
            } catch (Exception ignored) {
                // Ignore transient frame upload drops
            } finally {
                freeBuffers.offer(raw);
            }
        }
    }

    private static void loadStaticFrame(String urlStr) {
        int frameSession = session.get();
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);
                if (conn.getResponseCode() == 200) {
                    InputStream in = conn.getInputStream();
                    byte[] targetBuffer = freeBuffers.take();
                    int totalRead = 0;
                    while (totalRead < FRAME_SIZE) {
                        int r = in.read(targetBuffer, totalRead, FRAME_SIZE - totalRead);
                        if (r == -1) break;
                        totalRead += r;
                    }
                    in.close();
                    if (totalRead == FRAME_SIZE && session.get() == frameSession && !streamFrameReady.get()) {
                        byte[] stale = readyBuffer.getAndSet(targetBuffer);
                        if (stale != null) freeBuffers.offer(stale);
                    } else freeBuffers.offer(targetBuffer);
                }
                conn.disconnect();
            } catch (Exception ignored) {}
        }).start();
    }
}
