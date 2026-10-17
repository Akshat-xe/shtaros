package shtaros.browser;

import shtaros.audio.ShtarOSBrowserSurroundFeed;
import shtaros.cinema.ShtarOSSurroundManager;
import net.minecraft.client.Minecraft;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Optional JCEF audio handler. Copies native PCM inside the callback's lifetime. */
final class ShtarOSBrowserAudioCapture {
    private static volatile Object target;
    private static volatile ShtarOSBrowserSurroundFeed feed;
    private static volatile int channelCount;
    private static volatile boolean captureDecisionLogged;
    private static volatile boolean packetLogged;
    private static volatile long lastTimingLogMillis;
    private static Object handler;
    private static Method getData;
    private static Method getAddress;

    private ShtarOSBrowserAudioCapture() {}

    static void install(Class<?> api) throws ReflectiveOperationException {
        if (handler != null) return;
        Class<?> type = Class.forName("org.cef.handler.CefAudioHandler");
        Class<?> pointer = Class.forName("org.cef.misc.DataPointer");
        getData = pointer.getMethod("getData", int.class);
        getAddress = pointer.getMethod("getAddress");
        Object next = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "ShtarOS browser surround capture";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> null;
                        };
                    }
                    if (method.getName().equals("getAudioParameters")) {
                        boolean selected = args != null && args.length > 0 && matches(args[0]);
                        if (selected && !captureDecisionLogged) {
                            captureDecisionLogged = true;
                            System.out.println("[ShtarOS Browser Audio] Chromium capture selected for theater browser");
                        }
                        return selected;
                    }
                    if (args == null || args.length == 0 || !matches(args[0])) {
                        return method.getReturnType() == boolean.class ? false : null;
                    }
                    try {
                        switch (method.getName()) {
                            case "onAudioStreamStarted" -> {
                                int rate = (int) args[1].getClass().getMethod("getSampleRate").invoke(args[1]);
                                int channels = (int) args[2];
                                if (channels < 1) return null;
                                channelCount = channels;
                                packetLogged = false;
                                System.out.println("[ShtarOS Browser Audio] Stream started: " + rate + " Hz, " + channels + " channels");
                                ShtarOSBrowserSurroundFeed previous = feed;
                                ShtarOSBrowserSurroundFeed nextFeed = new ShtarOSBrowserSurroundFeed(rate, 0xFF);
                                feed = nextFeed;
                                if (previous != null) Minecraft.getInstance().execute(() -> ShtarOSSurroundManager.stopBrowser(previous));
                            }
                            case "onAudioStreamPacket" -> packet(args);
                            case "onAudioStreamStopped" -> stopFeed();
                            case "onAudioStreamError" -> {
                                System.err.println("[ShtarOS Browser Audio] Chromium stream error: " + args[1]);
                                stopFeed();
                            }
                        }
                    } catch (Throwable failure) {
                        System.err.println("[ShtarOS Browser Audio] " + failure);
                        stopFeed();
                    }
                    return null;
                });
        Object client = api.getMethod("getClient").invoke(null);
        client.getClass().getMethod("addAudioHandler", type).invoke(client, next);
        handler = next;
    }

    static void setTarget(Object browser) {
        target = browser;
        captureDecisionLogged = false;
    }

    private static boolean matches(Object browser) {
        Object current = target;
        if (browser == current && current != null) return true;
        if (browser == null || current == null) return false;
        try {
            int expected = (int) current.getClass().getMethod("getIdentifier").invoke(current);
            int actual = (int) browser.getClass().getMethod("getIdentifier").invoke(browser);
            return expected >= 0 && expected == actual;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    static void closeTarget() {
        target = null;
        stopFeed();
    }

    static void rebindSpeakers() {
        ShtarOSBrowserSurroundFeed old = feed;
        if (target == null || old == null) return;
        feed = new ShtarOSBrowserSurroundFeed(old.sampleRate(), 0xFF);
        old.close();
    }

    private static void packet(Object[] args) throws ReflectiveOperationException {
        ShtarOSBrowserSurroundFeed current = feed;
        if (current == null) return;
        int frames = (int) args[2];
        if (frames <= 0 || frames > 65536) return;
        if (args.length > 3 && args[3] instanceof Number timestamp) {
            long now = System.currentTimeMillis();
            long age = now - timestamp.longValue();
            if (Math.abs(age) < 60_000 && now - lastTimingLogMillis > 10_000) {
                lastTimingLogMillis = now;
                System.out.println("[ShtarOS Browser Audio] Packet age " + age + " ms, " + frames + " frames");
            }
        }
        Object pointer = args[1];
        long leftAddress = (long) getAddress.invoke(getData.invoke(pointer, 0));
        long rightAddress = channelCount > 1
                ? (long) getAddress.invoke(getData.invoke(pointer, 1)) : leftAddress;
        if (leftAddress == 0 || rightAddress == 0) return;
        FloatBuffer left = MemoryUtil.memByteBuffer(leftAddress, frames * Float.BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        FloatBuffer right = MemoryUtil.memByteBuffer(rightAddress, frames * Float.BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        current.accept(left, right, frames);
        if (!packetLogged) {
            packetLogged = true;
            System.out.println("[ShtarOS Browser Audio] First PCM packet received");
        }
        if (current.isPrimed() && current.markScheduled()) {
            Minecraft.getInstance().execute(() -> {
                if (feed == current && target != null) ShtarOSSurroundManager.playBrowser(current);
                else current.close();
            });
        }
    }

    private static void stopFeed() {
        ShtarOSBrowserSurroundFeed old = feed;
        feed = null;
        if (old != null) {
            old.finish();
            Minecraft.getInstance().execute(() -> ShtarOSSurroundManager.stopBrowser(old));
        }
    }
}
