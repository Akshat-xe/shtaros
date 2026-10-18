package shtaros.mixin;

import shtaros.cinema.ShtarOSCinemaManager;
import shtaros.browser.ShtarOSBrowserBridge;
import shtaros.browser.ShtarOSBrowserScreen;
import shtaros.gui.ShtarOSSetupScreen;
import shtaros.gui.JukeboxSearchScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void onRightClickBlock(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        if (player == null || player.level() == null || hand != InteractionHand.MAIN_HAND) {
            return;
        }

        // When player is sneaking (Shift), NEVER intercept: allows placing blocks, redstone, and components on top
        if (player.isShiftKeyDown() && !isWithinScreen(hitResult.getBlockPos())) {
            return;
        }

        BlockPos pos = hitResult.getBlockPos().immutable();
        BlockState state = player.level().getBlockState(pos);

        // Note Block -> Open Cinema Remote
        if (state.is(Blocks.NOTE_BLOCK)) {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> mc.setScreenAndShow(new ShtarOSSetupScreen()));
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }

        // A standalone jukebox is the audio-only player. Sneak-click keeps vanilla behavior.
        if (state.is(Blocks.JUKEBOX)) {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> mc.setScreenAndShow(new JukeboxSearchScreen(pos)));
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }

        // Screen Panels (Polished Blackstone or bounded screen wall)
        if (isWithinScreen(pos)) {
            Minecraft mc = Minecraft.getInstance();
            if (ShtarOSBrowserBridge.isAvailable()) {
                if (!ShtarOSBrowserBridge.isOpen()) {
                    ShtarOSBrowserBridge.open();
                    ShtarOSCinemaManager.stopPlayback();
                    if (mc.player != null) {
                        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§d[ShtarOS] §aBrowser active on cinema screen! Look and click directly in-world."));
                    }
                    cir.setReturnValue(InteractionResult.SUCCESS);
                    return;
                }
                if (player.isShiftKeyDown()) {
                    mc.execute(() -> mc.setScreenAndShow(new ShtarOSBrowserScreen()));
                    cir.setReturnValue(InteractionResult.SUCCESS);
                    return;
                }
            } else {
                mc.execute(() -> mc.setScreenAndShow(new ShtarOSSetupScreen()));
                cir.setReturnValue(InteractionResult.SUCCESS);
                return;
            }
            cir.setReturnValue(InteractionResult.SUCCESS);
        }
    }

    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void onStartDestroyBlock(BlockPos pos, net.minecraft.core.Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (ShtarOSCinemaManager.isWithinScreen(pos)) {
            // Protect screen from being broken when clicking on it
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void onContinueDestroyBlock(BlockPos pos, net.minecraft.core.Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (ShtarOSCinemaManager.isWithinScreen(pos)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void onDestroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (ShtarOSCinemaManager.isWithinScreen(pos)) {
            cir.setReturnValue(false);
        }
    }

    private boolean isWithinScreen(BlockPos pos) {
        return ShtarOSCinemaManager.isWithinScreen(pos);
    }
}
