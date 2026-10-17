package shtaros.gui;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

/** A compact shortcut beside the vanilla inventory, with a narrow-screen fallback. */
public final class ShtarOSInventoryButtons {
    private ShtarOSInventoryButtons() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof InventoryScreen)) return;
            int x = (width + 176) / 2 + 8;
            int y = (height - 166) / 2 + 18;
            if (width - x < 96) {
                Screens.getWidgets(screen).add(Button.builder(Component.literal("ShtarOS setup"), button ->
                        client.setScreenAndShow(new ShtarOSSetupScreen())).bounds(width - 98, 4, 94, 20).build());
                return;
            }
            shortcut(screen, x, y, "Theater", ShtarOSSetupActions::theater);
            shortcut(screen, x, y + 24, "Jukebox", ShtarOSSetupActions::jukebox);
            shortcut(screen, x, y + 48, "Repair", ShtarOSSetupActions::repair);
            shortcut(screen, x, y + 72, "Delete", ShtarOSSetupActions::delete);
        });
    }

    private static void shortcut(net.minecraft.client.gui.screens.Screen screen, int x, int y,
                                 String label, Runnable action) {
        Screens.getWidgets(screen).add(Button.builder(Component.literal(label), button -> {
            screen.onClose();
            action.run();
        }).bounds(x, y, 88, 20).build());
    }
}
