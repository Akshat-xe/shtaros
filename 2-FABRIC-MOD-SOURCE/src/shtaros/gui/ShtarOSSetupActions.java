package shtaros.gui;

import shtaros.audio.ShtarOSSpatialManager;
import shtaros.cinema.ShtarOSCinemaBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/** World edits used by the compact setup panel and the inventory shortcuts. */
public final class ShtarOSSetupActions {
    private ShtarOSSetupActions() {}

    public static void theater() {
        ShtarOSCinemaBuilder.buildCinema(Minecraft.getInstance().player);
    }

    public static void jukebox() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !mc.hasSingleplayerServer() || mc.getSingleplayerServer() == null) {
            message("§cJukebox setup needs a singleplayer world.");
            return;
        }
        BlockPos pos = player.blockPosition().relative(player.getDirection(), 2);
        if (!player.level().getBlockState(pos).isAir() || player.level().getBlockState(pos.below()).isAir()) {
            message("§eStand two blocks from an empty spot on solid ground.");
            return;
        }
        ServerLevel level = mc.getSingleplayerServer().getLevel(player.level().dimension());
        if (level == null) return;
        level.getServer().execute(() -> {
            if (level.getBlockState(pos).isAir() && !level.getBlockState(pos.below()).isAir())
                level.setBlockAndUpdate(pos, Blocks.JUKEBOX.defaultBlockState());
            mc.execute(() -> message("§aJukebox ready. Right-click it to play music."));
        });
    }

    public static void repair() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && !ShtarOSCinemaBuilder.repairNearby(player))
            message("§eNo generated theater found nearby. Use Theater setup first.");
    }

    public static void delete() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !mc.hasSingleplayerServer() || mc.getSingleplayerServer() == null) {
            message("§cDelete needs a singleplayer world.");
            return;
        }
        BlockPos underfoot = player.blockPosition().below();
        if (player.level().getBlockState(underfoot).is(Blocks.JUKEBOX)) {
            deleteJukebox(underfoot);
            return;
        }
        if (ShtarOSCinemaBuilder.deleteNearby(player)) return;
        BlockPos nearest = null;
        double best = Double.MAX_VALUE;
        for (BlockPos candidate : BlockPos.betweenClosed(player.blockPosition().offset(-2, -1, -2),
                player.blockPosition().offset(2, 1, 2))) {
            if (!player.level().getBlockState(candidate).is(Blocks.JUKEBOX)) continue;
            double distance = candidate.distSqr(player.blockPosition());
            if (distance < best) { best = distance; nearest = candidate.immutable(); }
        }
        if (nearest != null) deleteJukebox(nearest);
        else message("§eNo generated theater or nearby jukebox found.");
    }

    private static void deleteJukebox(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.getSingleplayerServer() == null) return;
        ShtarOSSpatialManager.stop(pos);
        ServerLevel level = mc.getSingleplayerServer().getLevel(player.level().dimension());
        if (level == null) return;
        level.getServer().execute(() -> {
            if (level.getBlockState(pos).is(Blocks.JUKEBOX))
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            mc.execute(() -> message("§eRemoved jukebox at " + pos.toShortString() + "."));
        });
    }

    private static void message(String text) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) player.sendSystemMessage(Component.literal("§d[ShtarOS] §r" + text));
    }
}
