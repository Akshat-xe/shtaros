package grandtweaks.mixin;

import grandtweaks.GrandConfig;
import grandtweaks.motion.GrandMotion;
import grandtweaks.zoom.GrandZoom;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void grandtweaks$handleScrollZoom(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (GrandZoom.isZoomActive()) {
            GrandZoom.onScroll(yOffset);
            ci.cancel(); // Prevent switching active hotbar slots or scrolling inventories
        }
    }

    @Inject(method = "turnPlayer", at = @At("HEAD"))
    private void grandtweaks$handleMotionAndSensitivity(double partialTick, CallbackInfo ci) {
        if (GrandZoom.isZoomActive()) {
            double factor = GrandZoom.getSensitivityFactor();
            this.accumulatedDX *= factor;
            this.accumulatedDY *= factor;
        }

        if (GrandConfig.motionSmooth) {
            GrandMotion.applySmoothing(this.accumulatedDX, this.accumulatedDY);
            this.accumulatedDX = GrandMotion.getSmoothedDX();
            this.accumulatedDY = GrandMotion.getSmoothedDY();
        }
    }
}
