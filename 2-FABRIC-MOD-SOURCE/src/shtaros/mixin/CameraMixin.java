package shtaros.mixin;

import shtaros.cinema.ShtarOSCinemaView;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setPosition(Vec3 position);
    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"))
    private void shtarosCinemaView(DeltaTracker delta, CallbackInfo ci) {
        if (!ShtarOSCinemaView.isActive()) return;
        Vec3 position = ShtarOSCinemaView.cameraPosition();
        if (position == null) return;
        setPosition(position);
        setRotation(ShtarOSCinemaView.yaw(), ShtarOSCinemaView.pitch());
    }
}
