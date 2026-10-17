package shtaros.cinema;

import net.minecraft.client.Minecraft;
import shtaros.browser.ShtarOSBrowserBridge;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;

/** Places a compact 10-block-wide cinema and eight physical speakers around the listener. */
public final class ShtarOSCinemaBuilder {
    private static final int RADIUS = 10;

    public static void buildCinema(LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        if (player == null || !mc.hasSingleplayerServer() || mc.getSingleplayerServer() == null) {
            if (player != null) player.sendSystemMessage(Component.literal("§c[ShtarOS] /shtaros setup needs a singleplayer world."));
            return;
        }
        Direction front = player.getDirection();
        Direction right = front.getClockWise();
        BlockPos center = player.blockPosition();
        ServerLevel level = mc.getSingleplayerServer().getLevel(player.level().dimension());
        if (level == null) return;
        level.getServer().execute(() -> {

        removePreviousGeneratedTheater(level, center, front, right);

        // Ten blocks wide by six high. The renderer letterboxes this to exact 16:9.
        BlockPos screenCenter = center.relative(front, 8);
        BlockPos min = null, max = null;
        for (int x = -5; x < 5; x++) {
            for (int y = 1; y <= 6; y++) {
                BlockPos pos = screenCenter.relative(right, x).above(y);
                level.setBlockAndUpdate(pos, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
                if (min == null) { min = pos; max = pos; }
                else {
                    min = new BlockPos(Math.min(min.getX(), pos.getX()), Math.min(min.getY(), pos.getY()), Math.min(min.getZ(), pos.getZ()));
                    max = new BlockPos(Math.max(max.getX(), pos.getX()), Math.max(max.getY(), pos.getY()), Math.max(max.getZ(), pos.getZ()));
                }
            }
        }
        BlockPos screenMin = min;
        BlockPos screenMax = max;

        // A visible sea-lantern rim spills real block light onto the panel and theater.
        for (int x = -6; x <= 5; x++) {
            for (int y = 0; y <= 7; y++) {
                if (x == -6 || x == 5 || y == 0 || y == 7) {
                    level.setBlockAndUpdate(screenCenter.relative(right, x).above(y), Blocks.SEA_LANTERN.defaultBlockState());
                }
            }
        }
        for (int x = -4; x <= 3; x += 3) {
            level.setBlockAndUpdate(screenCenter.relative(right, x).relative(front).above(3), Blocks.SEA_LANTERN.defaultBlockState());
        }

        placeSpeaker(level, screenCenter.relative(right, -8));
        placeSpeaker(level, screenCenter.relative(right, 7));
        placeSpeaker(level, center.relative(right, -RADIUS).relative(front, 2));
        placeSpeaker(level, center.relative(right, -RADIUS).relative(front, -4));
        placeSpeaker(level, center.relative(right, RADIUS).relative(front, 2));
        placeSpeaker(level, center.relative(right, RADIUS).relative(front, -4));
        placeSpeaker(level, center.relative(front, -9).relative(right, -4));
        placeSpeaker(level, center.relative(front, -9).relative(right, 4));

        // Two-block viewing riser with a short stair approach behind the seats.
        for (int x = -1; x <= 1; x++) {
            BlockPos seat = center.relative(right, x).relative(front, -2);
            level.setBlockAndUpdate(seat, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
            level.setBlockAndUpdate(seat.above(), Blocks.POLISHED_BLACKSTONE.defaultBlockState());
            level.setBlockAndUpdate(seat.above(2), Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, front));
        }
        level.setBlockAndUpdate(center.relative(front, -4), Blocks.POLISHED_BLACKSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, front));
        level.setBlockAndUpdate(center.relative(front, -3), Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        level.setBlockAndUpdate(center.relative(front, -3).above(), Blocks.POLISHED_BLACKSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, front));
        BlockPos oldPowerBlock = center.relative(front, 11).relative(right, 13);
        if (level.getBlockState(oldPowerBlock).is(Blocks.REDSTONE_BLOCK))
            level.setBlockAndUpdate(oldPowerBlock, Blocks.AIR.defaultBlockState());

        mc.execute(() -> {
            ShtarOSCinemaManager.setScreen(screenMin, screenMax, front.getOpposite());
            bindSpeakers(center, front);
            player.sendSystemMessage(Component.literal("§a[ShtarOS] 10×6 screen, raised seating, eight physical speakers, 10-block side radius."));
        });
        });
    }

    private record TheaterSite(BlockPos center, Direction front) {
        BlockPos screenCenter() { return center.relative(front, 8); }
        Direction right() { return front.getClockWise(); }
        BlockPos screenMin() {
            BlockPos a = screenCenter().relative(right(), -5).above();
            BlockPos b = screenCenter().relative(right(), 4).above(6);
            return new BlockPos(Math.min(a.getX(), b.getX()), a.getY(), Math.min(a.getZ(), b.getZ()));
        }
        BlockPos screenMax() {
            BlockPos a = screenCenter().relative(right(), -5).above();
            BlockPos b = screenCenter().relative(right(), 4).above(6);
            return new BlockPos(Math.max(a.getX(), b.getX()), b.getY(), Math.max(a.getZ(), b.getZ()));
        }
    }

    /** Only recognizes our distinctive ten-by-six panel and sea-lantern rim. */
    private static boolean matchesSite(LocalPlayer player, TheaterSite site) {
        BlockPos screen = site.screenCenter();
        Direction right = site.right();
        int rim = 0;
        for (int x : new int[]{-6, 5}) for (int y : new int[]{0, 7})
            if (player.level().getBlockState(screen.relative(right, x).above(y)).is(Blocks.SEA_LANTERN)) rim++;
        if (rim < 3) return false;
        int panel = 0;
        for (int x = -5; x < 5; x++) for (int y = 1; y <= 6; y++)
            if (player.level().getBlockState(screen.relative(right, x).above(y)).is(Blocks.POLISHED_BLACKSTONE)) panel++;
        return panel >= 12;
    }

    private static TheaterSite nearbySite(LocalPlayer player) {
        if (player == null || player.level() == null) return null;
        BlockPos standing = player.blockPosition();
        if (ShtarOSCinemaManager.hasScreen() && ShtarOSCinemaManager.getScreenFacing() != null) {
            Direction front = ShtarOSCinemaManager.getScreenFacing().getOpposite();
            Direction right = front.getClockWise();
            BlockPos min = ShtarOSCinemaManager.getScreenMin();
            BlockPos max = ShtarOSCinemaManager.getScreenMax();
            BlockPos screen = new BlockPos((min.getX() + max.getX() + right.getStepX()) / 2,
                    min.getY() - 1, (min.getZ() + max.getZ() + right.getStepZ()) / 2);
            TheaterSite bound = new TheaterSite(screen.relative(front.getOpposite(), 8), front);
            if (standing.distSqr(bound.center()) <= 14 * 14 && matchesSite(player, bound)) return bound;
        }
        TheaterSite nearest = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -10; dx <= 10; dx++) for (int dz = -10; dz <= 10; dz++) {
            for (int dy = -3; dy <= 3; dy++) {
                BlockPos center = standing.offset(dx, dy, dz);
                for (Direction front : Direction.Plane.HORIZONTAL) {
                    TheaterSite candidate = new TheaterSite(center, front);
                    if (!matchesSite(player, candidate)) continue;
                    double distance = standing.distSqr(center);
                    if (distance < bestDistance) { nearest = candidate; bestDistance = distance; }
                }
            }
        }
        return nearest;
    }

    private static Map<BlockPos, BlockState> layout(TheaterSite site) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        BlockPos center = site.center();
        BlockPos screen = site.screenCenter();
        Direction front = site.front();
        Direction right = site.right();
        for (int x = -5; x < 5; x++) for (int y = 1; y <= 6; y++)
            blocks.put(screen.relative(right, x).above(y), Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        for (int x = -6; x <= 5; x++) for (int y = 0; y <= 7; y++)
            if (x == -6 || x == 5 || y == 0 || y == 7)
                blocks.put(screen.relative(right, x).above(y), Blocks.SEA_LANTERN.defaultBlockState());
        for (int x = -4; x <= 3; x += 3)
            blocks.put(screen.relative(right, x).relative(front).above(3), Blocks.SEA_LANTERN.defaultBlockState());
        BlockPos[] floors = {
                screen.relative(right, -8), screen.relative(right, 7),
                center.relative(right, -RADIUS).relative(front, 2),
                center.relative(right, -RADIUS).relative(front, -4),
                center.relative(right, RADIUS).relative(front, 2),
                center.relative(right, RADIUS).relative(front, -4),
                center.relative(front, -9).relative(right, -4),
                center.relative(front, -9).relative(right, 4)};
        for (BlockPos floor : floors) {
            blocks.put(floor, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
            blocks.put(floor.above(), Blocks.NOTE_BLOCK.defaultBlockState());
        }
        for (int x = -1; x <= 1; x++) {
            BlockPos seat = center.relative(right, x).relative(front, -2);
            blocks.put(seat, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
            blocks.put(seat.above(), Blocks.POLISHED_BLACKSTONE.defaultBlockState());
            blocks.put(seat.above(2), Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, front));
        }
        blocks.put(center.relative(front, -4), Blocks.POLISHED_BLACKSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, front));
        blocks.put(center.relative(front, -3), Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        blocks.put(center.relative(front, -3).above(), Blocks.POLISHED_BLACKSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, front));
        return blocks;
    }

    private static void bindSpeakers(BlockPos center, Direction front) {
        Direction right = front.getClockWise();
        BlockPos screen = center.relative(front, 8);
        ShtarOSSurroundManager.clearSpeakers();
        ShtarOSSurroundManager.registerSpeaker(screen.relative(right, -8).above(), ShtarOSSurroundManager.SpeakerRole.FRONT_LEFT);
        ShtarOSSurroundManager.registerSpeaker(screen.relative(right, 7).above(), ShtarOSSurroundManager.SpeakerRole.FRONT_RIGHT);
        ShtarOSSurroundManager.registerSpeaker(center.relative(right, -RADIUS).relative(front, 2).above(), ShtarOSSurroundManager.SpeakerRole.SIDE_LEFT_FRONT);
        ShtarOSSurroundManager.registerSpeaker(center.relative(right, -RADIUS).relative(front, -4).above(), ShtarOSSurroundManager.SpeakerRole.SIDE_LEFT_REAR);
        ShtarOSSurroundManager.registerSpeaker(center.relative(right, RADIUS).relative(front, 2).above(), ShtarOSSurroundManager.SpeakerRole.SIDE_RIGHT_FRONT);
        ShtarOSSurroundManager.registerSpeaker(center.relative(right, RADIUS).relative(front, -4).above(), ShtarOSSurroundManager.SpeakerRole.SIDE_RIGHT_REAR);
        ShtarOSSurroundManager.registerSpeaker(center.relative(front, -9).relative(right, -4).above(), ShtarOSSurroundManager.SpeakerRole.REAR_LEFT);
        ShtarOSSurroundManager.registerSpeaker(center.relative(front, -9).relative(right, 4).above(), ShtarOSSurroundManager.SpeakerRole.REAR_RIGHT);
        ShtarOSBrowserBridge.speakersChanged();
    }

    public static boolean repairNearby(LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        TheaterSite site = nearbySite(player);
        if (site == null || !mc.hasSingleplayerServer() || mc.getSingleplayerServer() == null) return false;
        ServerLevel level = mc.getSingleplayerServer().getLevel(player.level().dimension());
        if (level == null) return false;
        level.getServer().execute(() -> {
            int fixed = 0;
            for (Map.Entry<BlockPos, BlockState> block : layout(site).entrySet()) {
                if (level.getBlockState(block.getKey()).isAir()) {
                    level.setBlockAndUpdate(block.getKey(), block.getValue());
                    fixed++;
                }
            }
            int count = fixed;
            mc.execute(() -> {
                ShtarOSCinemaManager.setScreen(site.screenMin(), site.screenMax(), site.front().getOpposite());
                bindSpeakers(site.center(), site.front());
                player.sendSystemMessage(Component.literal("§a[ShtarOS] Repaired " + count + " missing theater blocks."));
            });
        });
        return true;
    }

    public static boolean deleteNearby(LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        TheaterSite site = nearbySite(player);
        if (site == null || !mc.hasSingleplayerServer() || mc.getSingleplayerServer() == null) return false;
        ServerLevel level = mc.getSingleplayerServer().getLevel(player.level().dimension());
        if (level == null) return false;
        boolean bound = ShtarOSCinemaManager.hasScreen() && site.screenMin().equals(ShtarOSCinemaManager.getScreenMin())
                && site.screenMax().equals(ShtarOSCinemaManager.getScreenMax());
        if (bound) {
            ShtarOSCinemaManager.clearScreen();
        }
        level.getServer().execute(() -> {
            int removed = 0;
            for (Map.Entry<BlockPos, BlockState> block : layout(site).entrySet()) {
                if (level.getBlockState(block.getKey()).is(block.getValue().getBlock())) {
                    level.setBlockAndUpdate(block.getKey(), Blocks.AIR.defaultBlockState());
                    removed++;
                }
            }
            int count = removed;
            mc.execute(() -> player.sendSystemMessage(Component.literal("§e[ShtarOS] Removed " + count + " generated theater blocks.")));
        });
        return true;
    }

    private static void placeSpeaker(ServerLevel level, BlockPos floor) {
        BlockPos speaker = floor.above();
        level.setBlockAndUpdate(floor, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        level.setBlockAndUpdate(speaker, Blocks.NOTE_BLOCK.defaultBlockState());
    }

    private static void clearOldSpeaker(ServerLevel level, BlockPos floor) {
        BlockPos speaker = floor.above();
        if (level.getBlockState(speaker).is(Blocks.JUKEBOX) || level.getBlockState(speaker).is(Blocks.NOTE_BLOCK)) {
            level.setBlockAndUpdate(speaker, Blocks.AIR.defaultBlockState());
        }
    }

    private static void removePreviousGeneratedTheater(ServerLevel level, BlockPos center, Direction front, Direction right) {
        BlockPos old = center.relative(front, 11);
        // Require the distinctive old panel and rim before removing generated materials.
        if (!level.getBlockState(old.relative(right, -8).above()).is(Blocks.POLISHED_BLACKSTONE)
                || !level.getBlockState(old.relative(right, 7).above(9)).is(Blocks.POLISHED_BLACKSTONE)
                || !level.getBlockState(old.relative(right, -9)).is(Blocks.SEA_LANTERN)) return;
        for (int x = -8; x < 8; x++) for (int y = 1; y <= 9; y++)
            clearIf(level, old.relative(right, x).above(y), Blocks.POLISHED_BLACKSTONE);
        for (int x = -9; x <= 8; x++) for (int y = 0; y <= 10; y++)
            if (x == -9 || x == 8 || y == 0 || y == 10)
                clearIf(level, old.relative(right, x).above(y), Blocks.SEA_LANTERN);
        for (int x = -6; x <= 5; x += 4)
            clearIf(level, old.relative(right, x).relative(front).above(5), Blocks.SEA_LANTERN);
        clearOldSpeaker(level, old.relative(right, -11));
        clearOldSpeaker(level, old.relative(right, 10));
        for (int side : new int[]{-14, 14}) {
            clearOldSpeaker(level, center.relative(right, side).relative(front, 3));
            clearOldSpeaker(level, center.relative(right, side).relative(front, -5));
        }
        clearOldSpeaker(level, center.relative(front, -12).relative(right, -6));
        clearOldSpeaker(level, center.relative(front, -12).relative(right, 6));
    }

    private static void clearIf(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.Block block) {
        if (level.getBlockState(pos).is(block)) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }
}
