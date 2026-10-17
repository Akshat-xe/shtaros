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

/** Direct, clickable Chromium view of the in-world cinema display. */
public final class ShtarOSBrowserScreen extends Screen {
    private static final int MARGIN = 12;
    private static final int BAR_HEIGHT = 24;
    private static final int GAP = 8;
    private EditBox address;
    private Button back;
    private Button forward;
    private boolean browserReady;
    private int worldWidth;
    private int worldHeight;

    public ShtarOSBrowserScreen() {
        super(Component.literal("ShtarOS Browser"));
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
                    .bounds(width / 2 - 40, height / 2 + 24, 80, BAR_HEIGHT).build());
            return;
        }
        if (ShtarOSCinemaManager.getCurrentVideoId() != null) ShtarOSCinemaManager.stopPlayback();

        int x = MARGIN;
        int y = MARGIN;
        back = addRenderableWidget(Button.builder(Component.literal("←"), b -> ShtarOSBrowserBridge.back())
                .bounds(x, y, 28, BAR_HEIGHT).build());
        x += 32;
        forward = addRenderableWidget(Button.builder(Component.literal("→"), b -> ShtarOSBrowserBridge.forward())
                .bounds(x, y, 28, BAR_HEIGHT).build());
        x += 32;
        addRenderableWidget(Button.builder(Component.literal("↻"), b -> ShtarOSBrowserBridge.reload())
                .bounds(x, y, 28, BAR_HEIGHT).build());
        x += 32;
        addRenderableWidget(Button.builder(Component.literal("Home"), b -> ShtarOSBrowserBridge.home())
                .bounds(x, y, 38, BAR_HEIGHT).build());
        x += 42;
        int rightWidth = 80;
        address = addRenderableWidget(new EditBox(font, x, y, Math.max(80, width - x - rightWidth - MARGIN), BAR_HEIGHT,
                Component.literal("Address or YouTube search")));
        address.setMaxLength(2048);
        address.setValue(ShtarOSBrowserBridge.url());
        addRenderableWidget(Button.builder(Component.literal("Go"), b -> navigate())
                .bounds(width - MARGIN - 74, y, 32, BAR_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(width - MARGIN - 38, y, 38, BAR_HEIGHT).build());
        resizeBrowser();
    }

    private int browserWidth() {
        int availableWidth = Math.max(1, width - 2 * MARGIN);
        int availableHeight = Math.max(1, height - MARGIN * 2 - BAR_HEIGHT - GAP);
        return Math.max(1, Math.min(availableWidth, availableHeight * 16 / 9));
    }
    private int browserHeight() {
        return Math.max(1, Math.min(height - MARGIN * 2 - BAR_HEIGHT - GAP, browserWidth() * 9 / 16));
    }
    private int browserX() { return (width - browserWidth()) / 2; }
    private int browserY() { return MARGIN + BAR_HEIGHT + GAP +
            Math.max(0, height - MARGIN * 2 - BAR_HEIGHT - GAP - browserHeight()) / 2; }
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
        if (text.startsWith("https://") || text.startsWith("http://")) url = text;
        else if (!text.contains(" ") && text.contains(".")) url = "https://" + text;
        else url = "https://duckduckgo.com/?q=" + URLEncoder.encode(text, StandardCharsets.UTF_8);
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
        graphics.fill(0, 0, width, browserY() - 4, 0xFF27312C);
        if (browserReady && ShtarOSBrowserBridge.isTextureReady()) {
            Identifier texture = ShtarOSBrowserBridge.texture();
            if (texture != null) {
                graphics.blit(texture, browserX(), browserY(), browserX() + browserWidth(),
                        browserY() + browserHeight(), 0.0F, 0.0F, 1.0F, 1.0F);
            }
        } else {
            String message = browserReady ? "Starting Chromium…" : ShtarOSBrowserBridge.error();
            graphics.centeredText(font, Component.literal(message), width / 2, height / 2, 0xFFE5E5DE);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (!browserReady || !inside(event.x(), event.y())) return false;
        if (event.button() < InputConstants.MOUSE_BUTTON_LEFT || event.button() > InputConstants.MOUSE_BUTTON_RIGHT) return false;
        // The address widget keeps keyboard focus until explicitly cleared.
        // Otherwise typed characters go into the toolbar instead of the page.
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
