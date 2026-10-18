package shtaros.mixin;

import shtaros.browser.ShtarOSBrowserBridge;
import shtaros.browser.ShtarOSScreenRaycast;
import shtaros.cinema.ShtarOSCinemaView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    private int pressedButton;
    private ShtarOSScreenRaycast.RaycastResult pressHit;
    private boolean pressedTextField;

    @Inject(method = "onMove", at = @At("HEAD"))
    private void onPhysicalMouseMove(long window, double x, double y, double dx, double dy, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if ((dx != 0.0 || dy != 0.0) && mc.gui != null && mc.gui.screen() == null
                && ShtarOSBrowserBridge.isOpen()) ShtarOSBrowserBridge.notePhysicalPointerMove();
    }

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void onMouseButton(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null || mc.player == null) {
            return;
        }
        if (mc.gui.screen() != null) {
            try {
                if (!org.lwjgl.sdl.SDLMouse.SDL_CursorVisible()) {
                    org.lwjgl.sdl.SDLMouse.SDL_ShowCursor();
                }
            } catch (Throwable ignored) {}
            return;
        }
        if (ShtarOSCinemaView.isActive()) {
            ci.cancel();
            return;
        }

        if (!ShtarOSBrowserBridge.isOpen()) {
            return;
        }

        ShtarOSScreenRaycast.RaycastResult hit = ShtarOSScreenRaycast.raycast(mc.player, 30.0);
        if (action == 0 && info.button() == pressedButton && pressHit != null) {
            ShtarOSBrowserBridge.mouseRelease(hit == null ? pressHit.px() : hit.px(),
                    hit == null ? pressHit.py() : hit.py(), info.button());
            if (info.button() == 1 && !ShtarOSBrowserBridge.isManualTypingMode())
                ShtarOSBrowserBridge.requestFocusCheck();
            pressedButton = 0;
            pressHit = null;
            pressedTextField = false;
            ci.cancel();
            return;
        }
        if (hit == null) {
            if (action == 1 && ShtarOSBrowserBridge.isTypingMode() && !ShtarOSBrowserBridge.isManualTypingMode())
                ShtarOSBrowserBridge.setTypingMode(false);
            return;
        }

        // Shift + Right-Click opens full browser GUI with tabs and devtools
        if (info.button() == 1 && mc.player.isShiftKeyDown()) {
            if (action == 1) {
                mc.setScreenAndShow(new shtaros.browser.ShtarOSBrowserScreen());
            }
            ci.cancel();
            return;
        }

        if (info.button() >= 0 && info.button() <= 2) {
            if (action == 1) { // Pressed
                ShtarOSBrowserBridge.focus();
                ShtarOSBrowserBridge.mouseMove(hit.px(), hit.py());
                ShtarOSBrowserBridge.mousePress(hit.px(), hit.py(), info.button());
                pressedButton = info.button();
                pressHit = hit;
                pressedTextField = info.button() == 0 && ShtarOSBrowserBridge.isTextCursor();
                if (pressedTextField) ShtarOSBrowserBridge.setTypingMode(true);
                else if (info.button() == 0 && ShtarOSBrowserBridge.isTypingMode()
                        && !ShtarOSBrowserBridge.isManualTypingMode())
                    ShtarOSBrowserBridge.setTypingMode(false);
            } else if (action == 0) { // Released
                ShtarOSBrowserBridge.mouseRelease(hit.px(), hit.py(), info.button());
            }
            ci.cancel();
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null || mc.gui.screen() != null || mc.player == null) {
            return;
        }
        if (ShtarOSCinemaView.isActive()) {
            ci.cancel();
            return;
        }

        if (!ShtarOSBrowserBridge.isOpen()) {
            return;
        }

        ShtarOSScreenRaycast.RaycastResult hit = ShtarOSScreenRaycast.getLatestHit();
        if (hit == null) {
            hit = ShtarOSScreenRaycast.raycast(mc.player, 30.0);
        }

        if (hit != null) {
            ShtarOSBrowserBridge.mouseWheel(hit.px(), hit.py(), vertical);
            ci.cancel();
        }
    }
}
