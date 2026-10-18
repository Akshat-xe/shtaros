package shtaros.browser;

import com.mojang.blaze3d.platform.InputConstants;
import shtaros.cinema.ShtarOSCinemaManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Modern, multi-tab Chromium browser GUI for the ShtarOS cinema system.
 * Supports multiple tabs, direct URL input, fast Brave/YouTube searching,
 * and seamless in-world cinema screen mirroring.
 */
public final class ShtarOSBrowserScreen extends Screen {
    private static final int MARGIN = 8;
    private static final int TAB_BAR_Y = 6;
    private static final int TAB_HEIGHT = 18;
    private static final int NAV_BAR_Y = 28;
    private static final int NAV_HEIGHT = 22;
    private static final int HEADER_HEIGHT = NAV_BAR_Y + NAV_HEIGHT + 6;

    private EditBox address;
    private Button back;
    private Button forward;
    private boolean browserReady;
    private int worldWidth;
    private int worldHeight;

    public ShtarOSBrowserScreen() {
        super(Component.literal("ShtarOS Multi-Tab Browser"));
    }

    @Override
    protected void init() {
        super.init();
        if (worldWidth == 0) {
            worldWidth = ShtarOSBrowserBridge.getWidth();
            worldHeight = ShtarOSBrowserBridge.getHeight();
        }
        browserReady = ShtarOSBrowserBridge.open();
        if (!browserReady) {
            addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                    .bounds(width / 2 - 40, height / 2 + 24, 80, NAV_HEIGHT).build());
            return;
        }
        if (ShtarOSCinemaManager.getCurrentVideoId() != null) ShtarOSCinemaManager.stopPlayback();

        // 1. Top Tab Strip
        List<ShtarOSBrowserBridge.BrowserTab> tabList = ShtarOSBrowserBridge.getTabs();
        int activeIdx = ShtarOSBrowserBridge.getActiveTabIndex();
        int tabX = MARGIN;
        int maxTabW = Math.min(130, Math.max(60, (width - MARGIN * 2 - 40) / Math.max(1, tabList.size())));

        for (int i = 0; i < tabList.size(); i++) {
            final int tabIdx = i;
            ShtarOSBrowserBridge.BrowserTab tab = tabList.get(i);
            boolean isActive = (i == activeIdx);
            String rawTitle = tab.getTitle();
            String tabText = font.plainSubstrByWidth(rawTitle, maxTabW - 20);
            if (isActive) tabText = "● " + tabText;

            int tabW = maxTabW;
            addRenderableWidget(Button.builder(Component.literal(tabText), b -> {
                ShtarOSBrowserBridge.switchTab(tabIdx);
                rebuildWidgets();
            }).bounds(tabX, TAB_BAR_Y, tabW - 16, TAB_HEIGHT).build());

            // Tab close '×'
            addRenderableWidget(Button.builder(Component.literal("×"), b -> {
                ShtarOSBrowserBridge.closeTab(tabIdx);
                rebuildWidgets();
            }).bounds(tabX + tabW - 16, TAB_BAR_Y, 14, TAB_HEIGHT).build());

            tabX += tabW + 2;
        }

        // [+] New Tab button
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            ShtarOSBrowserBridge.newTab(ShtarOSBrowserBridge.getHomeUrl());
            rebuildWidgets();
        }).bounds(tabX, TAB_BAR_Y, 20, TAB_HEIGHT).build());

        // 2. Navigation / Search Bar
        int x = MARGIN;
        back = addRenderableWidget(Button.builder(Component.literal("←"), b -> ShtarOSBrowserBridge.back())
                .bounds(x, NAV_BAR_Y, 22, NAV_HEIGHT).build());
        x += 24;
        forward = addRenderableWidget(Button.builder(Component.literal("→"), b -> ShtarOSBrowserBridge.forward())
                .bounds(x, NAV_BAR_Y, 22, NAV_HEIGHT).build());
        x += 24;
        addRenderableWidget(Button.builder(Component.literal("↻"), b -> ShtarOSBrowserBridge.reload())
                .bounds(x, NAV_BAR_Y, 22, NAV_HEIGHT).build());
        x += 24;
        addRenderableWidget(Button.builder(Component.literal("🏠"), b -> ShtarOSBrowserBridge.home())
                .bounds(x, NAV_BAR_Y, 24, NAV_HEIGHT).build());
        x += 28;

        int rightWidth = 190;
        int addrWidth = Math.max(60, width - x - rightWidth - MARGIN);
        address = addRenderableWidget(new EditBox(font, x, NAV_BAR_Y, addrWidth, NAV_HEIGHT,
                Component.literal("Search Brave or enter web URL...")));
        address.setMaxLength(2048);
        address.setValue(ShtarOSBrowserBridge.url());

        int rx = width - MARGIN;
        rx -= 38;
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(rx, NAV_BAR_Y, 38, NAV_HEIGHT).build());

        rx -= 48;
        addRenderableWidget(Button.builder(Component.literal("Inspect"), b -> ShtarOSBrowserBridge.openDevTools())
                .bounds(rx, NAV_BAR_Y, 44, NAV_HEIGHT).build());

        rx -= 17;
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            ShtarOSBrowserBridge.zoomIn();
            rebuildWidgets();
        }).bounds(rx, NAV_BAR_Y, 15, NAV_HEIGHT).build());

        rx -= 34;
        String zoomStr = (int) Math.round(ShtarOSBrowserBridge.getZoom() * 100) + "%";
        addRenderableWidget(Button.builder(Component.literal(zoomStr), b -> {
            ShtarOSBrowserBridge.resetZoom();
            rebuildWidgets();
        }).bounds(rx, NAV_BAR_Y, 32, NAV_HEIGHT).build());

        rx -= 17;
        addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            ShtarOSBrowserBridge.zoomOut();
            rebuildWidgets();
        }).bounds(rx, NAV_BAR_Y, 15, NAV_HEIGHT).build());

        rx -= 30;
        addRenderableWidget(Button.builder(Component.literal("Go"), b -> navigate())
                .bounds(rx, NAV_BAR_Y, 26, NAV_HEIGHT).build());

        resizeBrowser();
    }

    private int browserWidth() {
        int availableWidth = Math.max(1, width - 2 * MARGIN);
        int availableHeight = Math.max(1, height - HEADER_HEIGHT - MARGIN);
        return Math.max(1, Math.min(availableWidth, availableHeight * 16 / 9));
    }
    private int browserHeight() {
        return Math.max(1, Math.min(height - HEADER_HEIGHT - MARGIN, browserWidth() * 9 / 16));
    }
    private int browserX() { return (width - browserWidth()) / 2; }
    private int browserY() { return HEADER_HEIGHT + Math.max(0, height - HEADER_HEIGHT - MARGIN - browserHeight()) / 2; }
    private boolean inside(double x, double y) {
        return x >= browserX() && x < browserX() + browserWidth()
                && y >= browserY() && y < browserY() + browserHeight();
    }
    private int px(double x) { return (int) ((x - browserX()) * ShtarOSBrowserBridge.getWidth() / browserWidth()); }
    private int py(double y) { return (int) ((y - browserY()) * ShtarOSBrowserBridge.getHeight() / browserHeight()); }

    private void resizeBrowser() {
        if (browserReady) ShtarOSBrowserBridge.resize(
                Math.min(1280, (int) (browserWidth() * minecraft.getWindow().getGuiScale())),
                Math.min(720, (int) (browserHeight() * minecraft.getWindow().getGuiScale())));
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        resizeBrowser();
    }

    private void navigate() {
        if (address == null) return;
        String text = address.getValue().trim();
        if (text.isEmpty()) return;
        String url;
        if (text.startsWith("https://") || text.startsWith("http://")) {
            url = text;
        } else if (!text.contains(" ") && text.contains(".")) {
            url = "https://" + text;
        } else {
            // Search using Brave privacy search engine
            url = "https://search.brave.com/search?q=" + URLEncoder.encode(text, StandardCharsets.UTF_8);
        }
        address.setValue(url);
        address.setFocused(false);
        ShtarOSBrowserBridge.navigate(url);
        ShtarOSBrowserBridge.focus();
    }

    @Override
    public void tick() {
        super.tick();
        if (back != null) back.active = ShtarOSBrowserBridge.canGoBack();
        if (forward != null) forward.active = ShtarOSBrowserBridge.canGoForward();
        if (address != null && !address.isFocused()) {
            String url = ShtarOSBrowserBridge.url();
            if (url != null && !url.isBlank() && !url.equals(address.getValue())) address.setValue(url);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF141918);
        // Header background (tabs + navigation bar)
        graphics.fill(0, 0, width, browserY() - 4, 0xFF1E2623);
        graphics.fill(0, TAB_BAR_Y + TAB_HEIGHT + 2, width, TAB_BAR_Y + TAB_HEIGHT + 3, 0xFF2F3B36);

        if (browserReady && ShtarOSBrowserBridge.isTextureReady()) {
            Identifier texture = ShtarOSBrowserBridge.texture();
            if (texture != null) {
                graphics.blit(texture, browserX(), browserY(), browserX() + browserWidth(),
                        browserY() + browserHeight(), 0.0F, 0.0F, 1.0F, 1.0F);
            }
        } else {
            String message = browserReady ? "Loading page in Chromium…" : ShtarOSBrowserBridge.error();
            graphics.centeredText(font, Component.literal(message), width / 2, height / 2, 0xFFE5E5DE);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (!browserReady || !inside(event.x(), event.y())) return false;
        if (event.button() < InputConstants.MOUSE_BUTTON_LEFT || event.button() > InputConstants.MOUSE_BUTTON_RIGHT) return false;
        setFocused(null);
        if (address != null) address.setFocused(false);
        ShtarOSBrowserBridge.focus();
        ShtarOSBrowserBridge.mouseMove(px(event.x()), py(event.y()));
        ShtarOSBrowserBridge.mousePress(px(event.x()), py(event.y()), event.button());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (super.mouseReleased(event)) return true;
        if (!browserReady || !inside(event.x(), event.y())) return false;
        if (event.button() < InputConstants.MOUSE_BUTTON_LEFT || event.button() > InputConstants.MOUSE_BUTTON_RIGHT) return false;
        ShtarOSBrowserBridge.mouseRelease(px(event.x()), py(event.y()), event.button());
        return true;
    }

    @Override
    public void mouseMoved(double x, double y) {
        if (browserReady && inside(x, y)) ShtarOSBrowserBridge.mouseMove(px(x), py(y));
        super.mouseMoved(x, y);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (browserReady && inside(event.x(), event.y())) {
            ShtarOSBrowserBridge.mouseMove(px(event.x()), py(event.y()));
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (super.mouseScrolled(x, y, scrollX, scrollY)) return true;
        if (!browserReady || !inside(x, y)) return false;
        ShtarOSBrowserBridge.mouseWheel(px(x), py(y), scrollY);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_F12) {
            ShtarOSBrowserBridge.toggleDevTools();
            return true;
        }

        // Fast keyboard shortcuts
        if (event.hasControlDown()) {
            if (event.key() == InputConstants.KEY_I && event.hasShiftDown()) {
                ShtarOSBrowserBridge.toggleDevTools();
                return true;
            }
            if (event.key() == InputConstants.KEY_MINUS) {
                ShtarOSBrowserBridge.zoomOut();
                rebuildWidgets();
                return true;
            }
            if (event.key() == InputConstants.KEY_EQUALS || event.key() == InputConstants.KEY_NUMPADEQUALS) {
                ShtarOSBrowserBridge.zoomIn();
                rebuildWidgets();
                return true;
            }
            if (event.key() == InputConstants.KEY_0 || event.key() == InputConstants.KEY_NUMPAD0) {
                ShtarOSBrowserBridge.resetZoom();
                rebuildWidgets();
                return true;
            }
            if (event.key() == InputConstants.KEY_T) {
                ShtarOSBrowserBridge.newTab(ShtarOSBrowserBridge.getHomeUrl());
                rebuildWidgets();
                return true;
            }
            if (event.key() == InputConstants.KEY_W) {
                ShtarOSBrowserBridge.closeTab(ShtarOSBrowserBridge.getActiveTabIndex());
                rebuildWidgets();
                return true;
            }
            if (event.key() == InputConstants.KEY_TAB) {
                int next = (ShtarOSBrowserBridge.getActiveTabIndex() + 1) % Math.max(1, ShtarOSBrowserBridge.getTabs().size());
                ShtarOSBrowserBridge.switchTab(next);
                rebuildWidgets();
                return true;
            }
            if (event.key() == InputConstants.KEY_L && address != null) {
                setFocused(address);
                address.setFocused(true);
                return true;
            }
            if (event.key() == InputConstants.KEY_R) {
                ShtarOSBrowserBridge.reload();
                return true;
            }
        }

        if (address != null && address.isFocused()
                && (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER)) {
            navigate();
            return true;
        }
        if (super.keyPressed(event)) return true;
        if (!browserReady || (address != null && address.isFocused())) return false;
        ShtarOSBrowserBridge.keyPress(event);
        return true;
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (super.keyReleased(event)) return true;
        if (!browserReady || (address != null && address.isFocused())) return false;
        ShtarOSBrowserBridge.keyRelease(event);
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (super.charTyped(event)) return true;
        if (!browserReady || (address != null && address.isFocused())) return false;
        if (event.codepoint() == 0) return false;
        ShtarOSBrowserBridge.typed((char) event.codepoint());
        return true;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        if (browserReady) ShtarOSBrowserBridge.resize(worldWidth, worldHeight);
        super.onClose();
    }
}
