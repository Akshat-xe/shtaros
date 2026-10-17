package shtaros.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Four setup actions. Playback stays on the theater wall or jukebox. */
public final class ShtarOSSetupScreen extends Screen {
    public ShtarOSSetupScreen() { super(Component.literal("ShtarOS setup")); }

    @Override public boolean isPauseScreen() { return false; }

    @Override protected void init() {
        int x = (width - 224) / 2;
        int y = (height - 154) / 2;
        addAction("Set up theater", x + 12, y + 36, ShtarOSSetupActions::theater);
        addAction("Set up jukebox", x + 116, y + 36, ShtarOSSetupActions::jukebox);
        addAction("Repair nearby", x + 12, y + 70, ShtarOSSetupActions::repair);
        addAction("Delete nearby", x + 116, y + 70, ShtarOSSetupActions::delete);
        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(x + 62, y + 111, 100, 20).build());
    }

    private void addAction(String label, int x, int y, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(label), button -> {
            onClose();
            action.run();
        }).bounds(x, y, 96, 24).build());
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int x = (width - 224) / 2;
        int y = (height - 154) / 2;
        graphics.fill(x, y, x + 224, y + 154, 0xED171D1B);
        graphics.fill(x, y, x + 224, y + 3, 0xFFCB956F);
        graphics.text(font, Component.literal("SHTAROS  /  SETUP"), x + 12, y + 14, 0xFFE8E3D8);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }
}
