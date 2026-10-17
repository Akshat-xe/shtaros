package shtaros.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import shtaros.audio.ShtarOSSpatialManager;
import shtaros.cinema.ShtarOSSurroundManager;
import shtaros.browser.ShtarOSBrowserBridge;
import shtaros.browser.ShtarOSScreenRaycast;
import shtaros.cinema.ShtarOSCinemaManager;
import shtaros.cinema.ShtarOSVideoPlayer;
import shtaros.cinema.ShtarOSCinemaView;
import shtaros.cinema.ShtarOSScreenRenderer;
import shtaros.gui.ShtarOSSetupScreen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftTickMixin {

    private static boolean wasIKeyDown = false;
    private static long unfocusedSince;
    private static boolean backgroundPausedCinema;
    private static boolean backgroundPausedAudio;

    /**
     * Executes at the HEAD of every single render frame BEFORE any level or GUI render pass begins!
     * Uploads the newest source-timed video frame whether the remote is open or closed.
     */
    @Inject(method = "renderFrame", at = @At("HEAD"))
    private void onRenderFrame(boolean tick, CallbackInfo ci) {
        ShtarOSVideoPlayer.updateTextureOnRender();
        ShtarOSScreenRenderer.prepareHaloOnRenderThread();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onClientTick(CallbackInfo ci) {
        Minecraft mc = (Minecraft)(Object)this;
        ShtarOSCinemaView.tick();

        if (mc.getWindow() != null && !mc.getWindow().isFocused()) {
            if (unfocusedSince == 0) unfocusedSince = System.nanoTime();
            if (!backgroundPausedAudio && System.nanoTime() - unfocusedSince > 2_000_000_000L) {
                if (ShtarOSCinemaManager.getCurrentVideoId() != null && !ShtarOSCinemaManager.isPaused()) {
                    ShtarOSCinemaManager.togglePause();
                    backgroundPausedCinema = true;
                }
                ShtarOSSpatialManager.pauseForBackground();
                backgroundPausedAudio = true;
            }
        } else {
            unfocusedSince = 0;
            if (backgroundPausedCinema && ShtarOSCinemaManager.getCurrentVideoId() != null && ShtarOSCinemaManager.isPaused())
                ShtarOSCinemaManager.togglePause();
            backgroundPausedCinema = false;
            if (backgroundPausedAudio) ShtarOSSpatialManager.resumeFromBackground();
            backgroundPausedAudio = false;
        }

        // Check for 'I' key via registered Minecraft KeyMapping or raw input edge
        if (mc.gui != null && mc.gui.screen() == null && mc.player != null) {
            boolean triggered = false;
            if (shtaros.ShtarOSMod.REMOTE_KEY != null && shtaros.ShtarOSMod.REMOTE_KEY.consumeClick()) {
                triggered = true;
            }
            try {
                boolean isIDown = InputConstants.isKeyDown(InputConstants.KEY_I);
                if (isIDown && !wasIKeyDown) {
                    triggered = true;
                }
                wasIKeyDown = isIDown;
            } catch (Throwable ignored) {}

            if (triggered && !ShtarOSBrowserBridge.isTypingMode()) {
                mc.setScreenAndShow(new ShtarOSSetupScreen());
            }

            // Raycast crosshair to cinema screen for in-world browser hover
            if (ShtarOSBrowserBridge.isOpen()) {
                ShtarOSBrowserBridge.tuneFrameRate(mc.getFps());
                shtaros.browser.ShtarOSScreenRaycast.RaycastResult hit = shtaros.browser.ShtarOSScreenRaycast.raycast(mc.player, 30.0);
                if (hit != null) {
                    ShtarOSBrowserBridge.mouseMove(hit.px(), hit.py());
                } else if (ShtarOSBrowserBridge.isTypingMode() && !ShtarOSBrowserBridge.isManualTypingMode()) {
                    ShtarOSBrowserBridge.setTypingMode(false);
                }
                ShtarOSBrowserBridge.tickPointerIdle();
                ShtarOSBrowserBridge.tickFocusCheck();
            } else {
                shtaros.browser.ShtarOSScreenRaycast.clearHit();
            }
        }

        ShtarOSSpatialManager.tick();
        ShtarOSCinemaManager.tick();
        ShtarOSSurroundManager.tick();
    }
}
