package grandtweaks.mixin;

import grandtweaks.gui.GrandTweaksScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VideoSettingsScreen.class)
public abstract class VideoSettingsMixin extends Screen {
    protected VideoSettingsMixin(Component title) {
        super(title);
    }

    @Inject(method = "addTitle", at = @At("RETURN"))
    private void grandtweaks$addTopButton(CallbackInfo ci) {
        this.addRenderableWidget(
            Button.builder(Component.literal("⚙ Grand Tweaks"), btn -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreenAndShow(new GrandTweaksScreen(this, this.minecraft.options));
                }
            }).bounds(this.width - 130, 8, 120, 20).build()
        );
    }
}
