package grandtweaks.mixin;

import grandtweaks.GrandConfig;
import grandtweaks.zoom.GrandZoom;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void move(float distance, float dy, float dx);

    @Inject(method = "update", at = @At("HEAD"))
    private void grandtweaks$updateZoomState(DeltaTracker deltaTracker, CallbackInfo ci) {
        GrandZoom.update();
    }

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void grandtweaks$applyFovZoom(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (GrandZoom.isZoomActive()) {
            float originalFov = cir.getReturnValue();
            float zoom = GrandZoom.getCurrentZoom();
            cir.setReturnValue(originalFov / zoom);
        }
    }

    @Inject(method = "alignWithEntity", at = @At("RETURN"))
    private void grandtweaks$applyGrandAnchor(float partialTick, CallbackInfo ci) {
        if ("grand".equalsIgnoreCase(GrandConfig.zoomMode) && GrandZoom.isZoomActive()) {
            float anchorDist = GrandZoom.getVirtualAnchorDistance();
            if (anchorDist > 0.001f) {
                // Shift camera forward along the sight line to project into distant chunks/LODs
                this.move(anchorDist, 0.0f, 0.0f);
            }
        }
    }
}
