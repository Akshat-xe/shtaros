package grandtweaks.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import grandtweaks.gui.GrandTweaksScreen;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "keyPress", at = @At("HEAD"))
    private void grandtweaks$onKeyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
        // action 1 = GLFW_PRESS; KEY_J = 13
        if (action == 1 && event.key() == InputConstants.KEY_J) {
            if (this.minecraft != null && this.minecraft.gui != null && this.minecraft.gui.screen() == null) {
                this.minecraft.setScreenAndShow(new GrandTweaksScreen(null, this.minecraft.options));
            }
        }
    }
}
