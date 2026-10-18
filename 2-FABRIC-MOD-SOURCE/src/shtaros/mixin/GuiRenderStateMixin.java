package shtaros.mixin;

import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fixes: java.lang.IllegalStateException: Can only blur once per frame
 * In Minecraft 26.3+, Screen.extractRenderStateWithTooltipAndSubtitles() automatically invokes
 * extractBackground(), which blurs before the stratum. Certain screens (such as Iris's
 * FeatureMissingErrorScreen or nested mod screens) also manually call extractBackground(),
 * causing blurBeforeThisStratum() to be called more than once per frame.
 *
 * This mixin safely cancels duplicate blur requests within the same frame so the screen
 * renders smoothly instead of crashing Minecraft to the launcher.
 */
@Mixin(GuiRenderState.class)
public class GuiRenderStateMixin {
    @Shadow
    private int firstStratumAfterBlur;

    @Inject(method = "blurBeforeThisStratum", at = @At("HEAD"), cancellable = true)
    private void shtaros$preventDuplicateBlur(CallbackInfo ci) {
        if (this.firstStratumAfterBlur != Integer.MAX_VALUE) {
            ci.cancel();
        }
    }
}
