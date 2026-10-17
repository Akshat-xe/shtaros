package shtaros.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import shtaros.browser.ShtarOSBrowserBridge;
import shtaros.browser.ShtarOSScreenRaycast;
import shtaros.cinema.ShtarOSCinemaView;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void onKeyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null && mc.gui.screen() != null) {
            // A regular UI screen is already open; let vanilla handle
            return;
        }

        if (action == 1 && event.key() == InputConstants.KEY_F6) {
            ShtarOSCinemaView.toggle();
            ci.cancel();
            return;
        }
        if (ShtarOSCinemaView.isActive()) {
            if (action == 1 && (event.key() == InputConstants.KEY_F5
                    || event.key() == InputConstants.KEY_ESCAPE)) ShtarOSCinemaView.exit();
            ci.cancel();
            return;
        }

        // F8 is a fallback; editable fields enter typing mode when clicked.
        if (event.key() == InputConstants.KEY_F8 && action == 1 && ShtarOSBrowserBridge.isOpen()) {
            boolean active = ShtarOSBrowserBridge.toggleTypingMode();
            if (mc.player != null) {
                if (active) {
                    mc.player.sendOverlayMessage(Component.literal("§a⌨ Typing into browser §7(F8 or Esc to exit)"));
                    try { mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.4f, 1.4f); } catch (Throwable ignored) {}
                } else {
                    mc.player.sendOverlayMessage(Component.literal("§e[TYPING MODE OFF] §fWalking and player movement restored."));
                    try { mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.35f, 0.9f); } catch (Throwable ignored) {}
                }
            }
            ci.cancel();
            return;
        }

        // When Typing Mode is active, redirect all keys to Chromium and cancel player movement
        if (ShtarOSBrowserBridge.isTypingMode()) {
            if (event.key() == InputConstants.KEY_ESCAPE) {
                ShtarOSBrowserBridge.setTypingMode(false);
                if (mc.player != null) {
                    mc.player.sendOverlayMessage(Component.literal("§e[TYPING MODE OFF] §fWalking and player movement restored."));
                }
                ci.cancel();
                return;
            }

            if (action == 1 || action == 2) { // Pressed or Repeat
                ShtarOSBrowserBridge.keyPress(event);
            } else if (action == 0) { // Released
                ShtarOSBrowserBridge.keyRelease(event);
            }

            // Cancel Minecraft's key handling completely so W, A, S, D, Space, E, Q don't move Steve or open inventory
            ci.cancel();
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void onCharTyped(long window, CharacterEvent event, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null && mc.gui.screen() != null) {
            return;
        }

        if (ShtarOSCinemaView.isActive()) {
            ci.cancel();
            return;
        }

        if (ShtarOSBrowserBridge.isTypingMode()) {
            if (event.codepoint() != 0) {
                ShtarOSBrowserBridge.typed((char) event.codepoint());
            }
            ci.cancel();
        }
    }
}
