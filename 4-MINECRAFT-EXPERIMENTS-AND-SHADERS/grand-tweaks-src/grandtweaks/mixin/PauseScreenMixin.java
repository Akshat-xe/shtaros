package grandtweaks.mixin;

import grandtweaks.gui.GrandTweaksScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    @Shadow private Button disconnectButton;

    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "createPauseMenu", at = @At("RETURN"))
    private void grandtweaks$addPauseMenuButton(CallbackInfo ci) {
        int btnW = 204;
        int btnX = this.width / 2 - 102;
        int btnY = (this.disconnectButton != null) ? this.disconnectButton.getY() + 24 : this.height / 4 + 148;

        this.addRenderableWidget(
            Button.builder(Component.literal("⚙ Grand Tweaks & Performance"), btn -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreenAndShow(new GrandTweaksScreen(this, this.minecraft.options));
                }
            }).bounds(btnX, btnY, btnW, 20).build()
        );
    }
}
