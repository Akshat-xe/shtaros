package shtaros.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import shtaros.browser.ShtarOSScreenRaycast;
import shtaros.cinema.ShtarOSCinemaManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Inject(method = "submitBlockOutline", at = @At("HEAD"), cancellable = true)
    private void onRenderBlockOutline(PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                                      LevelRenderState levelRenderState, CallbackInfo ci) {
        if (!ShtarOSCinemaManager.hasScreen()) {
            return;
        }

        // 1. If looking directly at the cinema screen via raycast, never render block outline
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && ShtarOSScreenRaycast.raycast(mc.player, 30.0) != null) {
            ci.cancel();
            return;
        }

        // 2. If the targeted block outline position is within the cinema screen bounds, cancel outline
        if (levelRenderState != null && levelRenderState.blockOutlineRenderState != null) {
            BlockPos targetPos = levelRenderState.blockOutlineRenderState.pos();
            if (targetPos != null && ShtarOSCinemaManager.isWithinScreen(targetPos)) {
                ci.cancel();
            }
        }
    }
}
