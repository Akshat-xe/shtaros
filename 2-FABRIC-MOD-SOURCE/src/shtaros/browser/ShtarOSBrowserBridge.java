package shtaros.browser;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Optional bridge to Rinku with full multi-tab support and hardware video capture. */
public final class ShtarOSBrowserBridge {
    public static final String HOME = "https://search.brave.com/";
    private static final String FOCUS_PREFIX = "shtaros-focus-" + UUID.randomUUID() + ":";

    public static class BrowserTab {
        private final String id;
        private String title;
        private String url;
        private final Object browser;

        public BrowserTab(String id, String title, String url, Object browser) {
            this.id = id;
            this.title = title;
            this.url = url;
            this.browser = browser;
        }

        public String getId() { return id; }
        public String getTitle() {
            if (title != null && !title.isBlank()) return title;
            if (url != null && !url.isBlank()) {
                String clean = url.replace("https://", "").replace("http://", "").replace("www.", "");
                int slash = clean.indexOf('/');
                return slash > 0 ? clean.substring(0, slash) : clean;
            }
            return "New Tab";
        }
        public void setTitle(String title) { this.title = title; }
        public String getUrl() { return url != null ? url : ""; }
        public void setUrl(String url) { this.url = url; }
        public Object getBrowser() { return browser; }
    }

    private static final List<BrowserTab> tabs = new CopyOnWriteArrayList<>();
    private static volatile int activeTabIndex = 0;

    private static Object browser;
    private static Object displayHandler;
    private static Class<?> browserClass;
    private static String error = "";

    // Off-screen Chromium paints and uploads every changed pixel through Minecraft.
    private static int currentWidth = 960;
    private static int currentHeight = 540;
    private static int requestedFrameRate = 30;
    private static long nextFrameRateCheck;
    private static long lastVideoStatsNanos;
    private static long lastSentMouseMoveNanos;
    private static final long POINTER_IDLE_NANOS = 5_000_000_000L;
    private static int lastPointerX = Integer.MIN_VALUE;
    private static int lastPointerY = Integer.MIN_VALUE;
    private static long lastPointerMoveNanos;
    private static boolean pointerParked;
    private static boolean physicalPointerMoved;
    private static volatile boolean typingMode = false;
    private static volatile boolean manualTypingMode;
    private static volatile boolean textCursor;
    private static int textCursorId = -1;
    private static int verticalTextCursorId = -1;
    private static int focusCheckTicks = -1;
    private static volatile int focusCheckSerial;

    private ShtarOSBrowserBridge() {}

    public static int getWidth() { return currentWidth; }
    public static int getHeight() { return currentHeight; }

    public static List<BrowserTab> getTabs() { return List.copyOf(tabs); }
    public static int getActiveTabIndex() { return activeTabIndex; }
    public static BrowserTab getActiveTab() {
        if (activeTabIndex >= 0 && activeTabIndex < tabs.size()) return tabs.get(activeTabIndex);
        return null;
    }

    public static boolean newTab(String targetUrl) {
        if (!isOpen() && !open()) return false;
        String dest = (targetUrl == null || targetUrl.isBlank()) ? HOME : targetUrl;
        try {
            Class<?> api = Class.forName("de.keksuccino.rinku.Rinku");
            Object created = api.getMethod("createBrowser", String.class, boolean.class)
                    .invoke(null, dest, false);
            if (created == null) return false;
            BrowserTab tab = new BrowserTab(UUID.randomUUID().toString(), "New Tab", dest, created);
            tabs.add(tab);
            switchTab(tabs.size() - 1);
            return true;
        } catch (Throwable e) {
            System.err.println("[ShtarOS Browser] Failed to create new tab: " + rootMessage(e));
            return false;
        }
    }

    public static void switchTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        activeTabIndex = index;
        BrowserTab tab = tabs.get(index);
        browser = tab.getBrowser();
        browserClass = browser.getClass();
        resetPointer();
        call("setWindowlessFrameRate", new Class<?>[]{int.class}, requestedFrameRate);
        resize(currentWidth, currentHeight);
        focus();
        ShtarOSBrowserAudioCapture.setTarget(browser);
    }

    public static void closeTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        if (tabs.size() <= 1) {
            navigate(HOME);
            return;
        }
        BrowserTab removed = tabs.remove(index);
        try {
            removed.getBrowser().getClass().getMethod("close").invoke(removed.getBrowser());
        } catch (Throwable ignored) {}

        if (activeTabIndex >= tabs.size()) {
            activeTabIndex = tabs.size() - 1;
        } else if (activeTabIndex > index) {
            activeTabIndex--;
        }
        switchTab(activeTabIndex);
    }

    public static boolean isTypingMode() { return typingMode && isOpen(); }
    public static void setTypingMode(boolean active) {
        boolean next = active && isOpen();
        if (!next) manualTypingMode = false;
        if (typingMode == next) return;
        typingMode = next;
        if (next) {
            KeyMapping.releaseAll();
            Minecraft.getInstance().textInputManager().startTextInput(browser);
        } else {
            Minecraft.getInstance().textInputManager().stopTextInput(browser);
            KeyMapping.setAll();
        }
        System.out.println("[ShtarOS Browser] In-world typing " + (next ? "enabled" : "disabled"));
    }
    public static boolean toggleTypingMode() {
        focusCheckSerial++;
        focusCheckTicks = -1;
        if (typingMode) setTypingMode(false);
        else {
            setTypingMode(true);
            manualTypingMode = typingMode;
        }
        return typingMode;
    }
    public static boolean isManualTypingMode() { return manualTypingMode; }

    public static boolean isAvailable() {
        return FabricLoader.getInstance().isModLoaded("rinku");
    }

    public static String error() { return error; }
    public static boolean isOpen() { return browser != null; }

    public static boolean open() {
        if (browser != null) return true;
        if (!isAvailable()) {
            error = "Browser component is missing. Run the ShtarOS installer.";
            return false;
        }
        try {
            Class<?> api = Class.forName("de.keksuccino.rinku.Rinku");
            for (int wait = 0; wait < 15 && !(boolean) api.getMethod("isInitialized").invoke(null); wait++) {
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            }
            if (!(boolean) api.getMethod("isInitialized").invoke(null)) {
                error = "The browser is still starting. Try again shortly.";
                return false;
            }
            try {
                ShtarOSBrowserAudioCapture.install(api);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException audioError) {
                System.err.println("[ShtarOS Browser Audio] Capture unavailable: " + rootMessage(audioError));
            }
            try { installFocusHandler(api); }
            catch (ReflectiveOperationException | LinkageError | RuntimeException focusError) {
                System.err.println("[ShtarOS Browser] Automatic text focus unavailable: " + rootMessage(focusError));
            }
            Object created = api.getMethod("createBrowser", String.class, boolean.class)
                    .invoke(null, HOME, false);
            if (created == null) throw new IllegalStateException("The browser did not start");
            tabs.clear();
            BrowserTab initialTab = new BrowserTab(UUID.randomUUID().toString(), "Brave Search", HOME, created);
            tabs.add(initialTab);
            activeTabIndex = 0;
            browser = created;
            resetPointer();
            ShtarOSBrowserAudioCapture.setTarget(created);
            browserClass = created.getClass();
            error = "";
            requestedFrameRate = 30;
            nextFrameRateCheck = 0;
            call("setWindowlessFrameRate", new Class<?>[]{int.class}, requestedFrameRate);
            resize(currentWidth, currentHeight);
            focus();
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            error = "Browser startup failed: " + rootMessage(e);
            browser = null;
            browserClass = null;
            System.err.println("[ShtarOS Browser] " + error);
            return false;
        }
    }

    public static void close() {
        if (browser == null && tabs.isEmpty()) return;
        setTypingMode(false);
        ShtarOSBrowserAudioCapture.closeTarget();
        for (BrowserTab tab : tabs) {
            try {
                tab.getBrowser().getClass().getMethod("close").invoke(tab.getBrowser());
            } catch (Throwable ignored) {}
        }
        tabs.clear();
        browser = null;
        browserClass = null;
        activeTabIndex = 0;
        focusCheckTicks = -1;
        textCursor = false;
        resetPointer();
    }

    /** Rebind the current Chromium audio stream after theater speakers change. */
    public static void speakersChanged() {
        if (browser != null) ShtarOSBrowserAudioCapture.rebindSpeakers();
    }

    public static boolean isTextureReady() {
        return browser != null && bool("isTextureReady");
    }

    public static Identifier texture() {
        if (!isTextureReady()) return null;
        try { return (Identifier) invoke("getTextureIdentifier"); }
        catch (ReflectiveOperationException | RuntimeException e) { return null; }
    }

    public static String url() {
        if (browser == null) return HOME;
        try { return (String) invoke("getURL"); }
        catch (ReflectiveOperationException | RuntimeException e) { return HOME; }
    }

    public static boolean canGoBack() { return bool("canGoBack"); }
    public static boolean canGoForward() { return bool("canGoForward"); }
    public static void back() { call("goBack"); }
    public static void forward() { call("goForward"); }
    public static void reload() { call("reload"); }
    public static void home() { navigate(HOME); }
    public static void tuneFrameRate(int gameFps) {
        if (browser == null || gameFps <= 0) return;
        long now = System.nanoTime();
        if (now < nextFrameRateCheck) return;
        nextFrameRateCheck = now + 5_000_000_000L;
        if (now - lastVideoStatsNanos > 10_000_000_000L) {
            lastVideoStatsNanos = now;
            System.out.println("[ShtarOS Browser] Video diagnostics: gameFps=" + gameFps
                    + ", paintLimit=" + requestedFrameRate
                    + ", view=" + currentWidth + "x" + currentHeight);
        }
        int target = 30;
        if (target != requestedFrameRate) {
            requestedFrameRate = target;
            call("setWindowlessFrameRate", new Class<?>[]{int.class}, target);
            System.out.println("[ShtarOS Browser] Paint limit locked to " + target + " FPS");
        }
    }
    public static void focus() {
        call("setFocus", new Class<?>[]{boolean.class}, true);
        if (typingMode && browser != null) Minecraft.getInstance().textInputManager().startTextInput(browser);
    }
    public static void resize(int width, int height) {
        currentWidth = Math.max(320, width);
        currentHeight = Math.max(180, height);
        resetPointer();
        call("resize", new Class<?>[]{int.class, int.class}, currentWidth, currentHeight);
    }
    public static void navigate(String url) {
        resetPointer();
        BrowserTab active = getActiveTab();
        if (active != null) active.setUrl(url);
        call("loadURL", new Class<?>[]{String.class}, url);
    }
    public static void mousePress(int x, int y, int button) {
        if (pointerParked) restorePointer(x, y);
        call("sendMousePress", new Class<?>[]{int.class, int.class, int.class}, x, y, button);
    }
    public static void mouseRelease(int x, int y, int button) {
        call("sendMouseRelease", new Class<?>[]{int.class, int.class, int.class}, x, y, button);
    }
    public static void click(int x, int y, int button) {
        mousePress(x, y, button);
        mouseRelease(x, y, button);
    }
    public static void mouseMove(int x, int y) {
        if (x == lastPointerX && y == lastPointerY && !physicalPointerMoved) return;
        long now = System.nanoTime();
        if (!physicalPointerMoved && now - lastSentMouseMoveNanos < 50_000_000L
                && Math.abs(x - lastPointerX) < 4 && Math.abs(y - lastPointerY) < 4) {
            return;
        }
        lastSentMouseMoveNanos = now;
        restorePointer(x, y);
    }
    public static void notePhysicalPointerMove() {
        physicalPointerMoved = true;
        lastPointerMoveNanos = System.nanoTime();
    }
    private static void restorePointer(int x, int y) {
        lastPointerX = x;
        lastPointerY = y;
        lastPointerMoveNanos = System.nanoTime();
        pointerParked = false;
        physicalPointerMoved = false;
        call("sendMouseMove", new Class<?>[]{int.class, int.class}, x, y);
    }
    public static void tickPointerIdle() {
        if (browser == null || pointerParked || typingMode || lastPointerMoveNanos == 0
                || System.nanoTime() - lastPointerMoveNanos < POINTER_IDLE_NANOS) return;
        pointerParked = true;
        call("sendMouseMove", new Class<?>[]{int.class, int.class}, -100, -100);
    }
    private static void resetPointer() {
        lastPointerX = Integer.MIN_VALUE;
        lastPointerY = Integer.MIN_VALUE;
        lastPointerMoveNanos = 0;
        pointerParked = false;
        physicalPointerMoved = false;
    }
    public static void mouseWheel(int x, int y, double delta) {
        if (pointerParked) restorePointer(x, y);
        call("sendMouseWheel", new Class<?>[]{int.class, int.class, double.class, int.class}, x, y, delta, 0);
    }
    public static boolean isTextCursor() { return textCursor; }
    public static void requestFocusCheck() {
        if (!isOpen()) return;
        focusCheckSerial++;
        focusCheckTicks = 4;
    }

    public static void tickFocusCheck() {
        if (focusCheckTicks < 0 || --focusCheckTicks > 0 || browser == null) return;
        focusCheckTicks = -1;
        int serial = focusCheckSerial;
        String script = "(()=>{let e=document.activeElement;while(e&&e.shadowRoot&&e.shadowRoot.activeElement)e=e.shadowRoot.activeElement;"
                + "const edit=!!e&&(e.isContentEditable||"
                + "e.matches('input:not([type=button]):not([type=checkbox]):not([type=radio]),textarea,[role=textbox]'));"
                + "console.log('" + FOCUS_PREFIX + serial + ":'+(edit?'1':'0'))})()";
        call("executeJavaScript", new Class<?>[]{String.class, String.class, int.class}, script, url(), 0);
    }

    private static void installFocusHandler(Class<?> api) throws ReflectiveOperationException {
        if (displayHandler != null) return;
        Class<?> type = Class.forName("org.cef.handler.CefDisplayHandler");
        Class<?> cursorType = Class.forName("org.cef.misc.CefCursorType");
        Method cursorId = cursorType.getMethod("getId");
        textCursorId = (int) cursorId.invoke(cursorType.getField("IBEAM").get(null));
        verticalTextCursorId = (int) cursorId.invoke(cursorType.getField("VERTICAL_IBEAM").get(null));
        Object handler = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return switch (method.getName()) {
                case "toString" -> "ShtarOS editable focus listener";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> null;
            };
            if (method.getName().equals("onTitleChange") && args != null && args.length >= 2
                    && args[1] instanceof String title) {
                for (BrowserTab tab : tabs) {
                    if (matchesCandidate(tab.getBrowser(), args[0])) {
                        tab.setTitle(title);
                        break;
                    }
                }
                return null;
            }
            if (method.getName().equals("onAddressChange") && args != null && args.length >= 3
                    && args[2] instanceof String newUrl) {
                for (BrowserTab tab : tabs) {
                    if (matchesCandidate(tab.getBrowser(), args[0])) {
                        tab.setUrl(newUrl);
                        break;
                    }
                }
                return null;
            }
            if (method.getName().equals("onCursorChange") && args != null && args.length >= 2
                    && matchesBrowser(args[0]) && args[1] instanceof Integer cursor) {
                textCursor = cursor == textCursorId || cursor == verticalTextCursorId;
                return false;
            }
            if (method.getName().equals("onConsoleMessage") && args != null && args.length >= 3
                    && matchesBrowser(args[0]) && args[2] instanceof String message
                    && message.startsWith(FOCUS_PREFIX)) {
                String value = message.substring(FOCUS_PREFIX.length());
                int colon = value.indexOf(':');
                if (colon > 0) {
                    try {
                        int serial = Integer.parseInt(value.substring(0, colon));
                        boolean editable = value.substring(colon + 1).equals("1");
                        Minecraft.getInstance().execute(() -> {
                            if (serial == focusCheckSerial && !manualTypingMode && Minecraft.getInstance().gui.screen() == null
                                    && ShtarOSScreenRaycast.getLatestHit() != null) setTypingMode(editable);
                        });
                    } catch (NumberFormatException ignored) {}
                }
                return true;
            }
            return method.getReturnType() == boolean.class ? false : null;
        });
        Object client = api.getMethod("getClient").invoke(null);
        client.getClass().getMethod("addDisplayHandler", type).invoke(client, handler);
        displayHandler = handler;
    }

    private static boolean matchesCandidate(Object current, Object candidate) {
        if (candidate == current && current != null) return true;
        if (candidate == null || current == null) return false;
        try {
            int expected = (int) current.getClass().getMethod("getIdentifier").invoke(current);
            int actual = (int) candidate.getClass().getMethod("getIdentifier").invoke(candidate);
            return expected >= 0 && expected == actual;
        } catch (ReflectiveOperationException | RuntimeException ignored) { return false; }
    }

    private static boolean matchesBrowser(Object candidate) {
        return matchesCandidate(browser, candidate);
    }

    public static void keyPress(KeyEvent event) {
        call("sendKeyPress", new Class<?>[]{KeyEvent.class}, event);
    }
    public static void keyRelease(KeyEvent event) {
        call("sendKeyRelease", new Class<?>[]{KeyEvent.class}, event);
    }
    public static void typed(char character) {
        call("sendKeyTyped", new Class<?>[]{char.class, int.class}, character, 0);
    }

    private static boolean bool(String method) {
        if (browser == null) return false;
        try { return (boolean) invoke(method); }
        catch (ReflectiveOperationException | RuntimeException e) { return false; }
    }

    private static Object invoke(String name) throws ReflectiveOperationException {
        return browserClass.getMethod(name).invoke(browser);
    }

    private static void call(String name) { call(name, new Class<?>[0]); }

    private static void call(String name, Class<?>[] signature, Object... args) {
        if (browser == null) return;
        try {
            Method method = browserClass.getMethod(name, signature);
            method.invoke(browser, args);
        } catch (ReflectiveOperationException | RuntimeException e) {
            error = "Browser input failed: " + rootMessage(e);
            System.err.println("[ShtarOS Browser] " + error);
        }
    }

    private static String rootMessage(Throwable error) {
        while (error.getCause() != null) error = error.getCause();
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
