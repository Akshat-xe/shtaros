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

/** Places a 14-block-wide luxury cinema, upgraded VIP sofa lounge, and eight physical surround speakers. */
public final class ShtarOSCinemaBuilder {
    private static final int RADIUS = 12;

    public static void buildCinema(LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        if (player == null) return;
        if (!mc.hasSingleplayerServer() || mc.getSingleplayerServer() == null) {
            player.sendSystemMessage(Component.literal("§d[ShtarOS Multiplayer] §aPrivate Cinema & Jukebox ready!"));
            player.sendSystemMessage(Component.literal("§e• Place a 14×8 Blackstone wall with Note Blocks, then run §a/shtaros scan§e."));
            player.sendSystemMessage(Component.literal("§e• Or right-click any placed Jukebox to play 3D spatial music."));
            player.sendSystemMessage(Component.literal("§e• To share with 20 players: type §a/shtaros share§e so friends can click to sync!"));
            return;
        }
        Direction front = player.getDirection();
        Direction right = front.getClockWise();
        BlockPos center = player.blockPosition();
        ServerLevel level = mc.getSingleplayerServer().getLevel(player.level().dimension());
        if (level == null) return;
        level.getServer().execute(() -> {

        removePreviousGeneratedTheater(level, center, front, right);

        // 14 blocks wide by 8 blocks high (1.75 aspect ratio, nearly identical to 16:9).
        BlockPos screenCenter = center.relative(front, 10);
        BlockPos min = null, max = null;
        for (int x = -7; x <= 6; x++) {
            for (int y = 1; y <= 8; y++) {
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

        // Sea-lantern illuminated perimeter rim (16x10 outer frame)
        for (int x = -8; x <= 7; x++) {
            for (int y = 0; y <= 9; y++) {
                if (x == -8 || x == 7 || y == 0 || y == 9) {
                    level.setBlockAndUpdate(screenCenter.relative(right, x).above(y), Blocks.SEA_LANTERN.defaultBlockState());
                }
            }
        }
        for (int x = -5; x <= 4; x += 3) {
            level.setBlockAndUpdate(screenCenter.relative(right, x).relative(front).above(4), Blocks.SEA_LANTERN.defaultBlockState());
        }

        // 8 Physical Surround Sound Speakers
        placeSpeaker(level, screenCenter.relative(right, -9));
        placeSpeaker(level, screenCenter.relative(right, 8));
        placeSpeaker(level, center.relative(right, -RADIUS).relative(front, 2));
        placeSpeaker(level, center.relative(right, -RADIUS).relative(front, -4));
        placeSpeaker(level, center.relative(right, RADIUS).relative(front, 2));
        placeSpeaker(level, center.relative(right, RADIUS).relative(front, -4));
        placeSpeaker(level, center.relative(front, -10).relative(right, -5));
        placeSpeaker(level, center.relative(front, -10).relative(right, 5));

        // --- Upgraded Cozy Luxury Cinema Sofa Lounge & Decor ---
        // Floor foundation under Row 1
        for (int x = -4; x <= 4; x++) {
            BlockPos floor = center.relative(right, x).relative(front, -2);
            level.setBlockAndUpdate(floor, Blocks.SPRUCE_PLANKS.defaultBlockState());
        }

        // Row 1: Front lounge sofa (5 seats wide with curved cozy spruce armrests)
        for (int x = -2; x <= 2; x++) {
            BlockPos seat = center.relative(right, x).relative(front, -2).above();
            level.setBlockAndUpdate(seat, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, front));
        }
        // Inward-facing armrests Row 1
        level.setBlockAndUpdate(center.relative(right, -3).relative(front, -2).above(),
                Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, right));
        level.setBlockAndUpdate(center.relative(right, 3).relative(front, -2).above(),
                Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, right.getOpposite()));

        // Side end-tables with flowers and warm lantern for Row 1
        BlockPos leftTable = center.relative(right, -4).relative(front, -2).above();
        BlockPos rightTable = center.relative(right, 4).relative(front, -2).above();
        level.setBlockAndUpdate(leftTable, Blocks.BOOKSHELF.defaultBlockState());
        level.setBlockAndUpdate(leftTable.above(), Blocks.POTTED_RED_TULIP.defaultBlockState());
        level.setBlockAndUpdate(rightTable, Blocks.BOOKSHELF.defaultBlockState());
        level.setBlockAndUpdate(rightTable.above(), Blocks.LANTERN.defaultBlockState());

        // Center Coffee Table & potted flowers in front of Row 1
        BlockPos coffeeLeft = center.relative(right, -1).relative(front, -1);
        BlockPos coffeeMid = center.relative(front, -1);
        BlockPos coffeeRight = center.relative(right, 1).relative(front, -1);
        level.setBlockAndUpdate(coffeeLeft, Blocks.SPRUCE_PLANKS.defaultBlockState());
        level.setBlockAndUpdate(coffeeMid, Blocks.SPRUCE_PLANKS.defaultBlockState());
        level.setBlockAndUpdate(coffeeRight, Blocks.SPRUCE_PLANKS.defaultBlockState());
        level.setBlockAndUpdate(coffeeLeft.above(), Blocks.POTTED_FERN.defaultBlockState());
        level.setBlockAndUpdate(coffeeMid.above(), Blocks.LANTERN.defaultBlockState());
        level.setBlockAndUpdate(coffeeRight.above(), Blocks.POTTED_CORNFLOWER.defaultBlockState());

        // Plush Red Carpet runner in front aisle
        for (int x = -3; x <= 3; x++) {
            BlockPos carpetPos = center.relative(right, x).relative(front, 0);
            level.setBlockAndUpdate(carpetPos, Blocks.CARPET.red().defaultBlockState());
            BlockPos carpetPos2 = center.relative(right, x).relative(front, 1);
            level.setBlockAndUpdate(carpetPos2, Blocks.CARPET.red().defaultBlockState());
        }

        // Row 2: Raised VIP Riser Sofa (elevated 1 block for unobstructed view)
        for (int x = -4; x <= 4; x++) {
            for (int z = -6; z <= -4; z++) {
                BlockPos floor = center.relative(right, x).relative(front, z);
                level.setBlockAndUpdate(floor, Blocks.SPRUCE_PLANKS.defaultBlockState());
                level.setBlockAndUpdate(floor.above(), Blocks.SPRUCE_PLANKS.defaultBlockState());
            }
        }
        for (int x = -2; x <= 2; x++) {
            BlockPos seat = center.relative(right, x).relative(front, -5);
            level.setBlockAndUpdate(seat.above(2), Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, front));
        }
        // VIP Armrests
        level.setBlockAndUpdate(center.relative(right, -3).relative(front, -5).above(2),
                Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, right));
        level.setBlockAndUpdate(center.relative(right, 3).relative(front, -5).above(2),
                Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, right.getOpposite()));

        // VIP Side Tables with warm decor
        BlockPos vipLeftTable = center.relative(right, -4).relative(front, -5).above(2);
        BlockPos vipRightTable = center.relative(right, 4).relative(front, -5).above(2);
        level.setBlockAndUpdate(vipLeftTable, Blocks.BOOKSHELF.defaultBlockState());
        level.setBlockAndUpdate(vipLeftTable.above(), Blocks.LANTERN.defaultBlockState());
        level.setBlockAndUpdate(vipRightTable, Blocks.BOOKSHELF.defaultBlockState());
        level.setBlockAndUpdate(vipRightTable.above(), Blocks.POTTED_RED_TULIP.defaultBlockState());

        // Smooth Side Access Stairs to VIP Riser
        level.setBlockAndUpdate(center.relative(right, -4).relative(front, -3).above(),
                Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, front.getOpposite()));
        level.setBlockAndUpdate(center.relative(right, 4).relative(front, -3).above(),
                Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, front.getOpposite()));

        mc.execute(() -> {
            ShtarOSCinemaManager.setScreen(screenMin, screenMax, front.getOpposite());
            bindSpeakers(center, front);
            player.sendSystemMessage(Component.literal("§a[ShtarOS] 14×8 widescreen cinema, luxury VIP lounge sofa, eight physical surround speakers built!"));
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                for (int i = 0; i < 20; i++) {
                    if (ShtarOSBrowserBridge.isAvailable()) {
                        mc.execute(() -> {
                            if (!ShtarOSBrowserBridge.isOpen()) {
                                ShtarOSBrowserBridge.open();
                            }
                        });
                        if (ShtarOSBrowserBridge.isOpen()) {
                            mc.execute(() -> {
                                if (player != null) player.sendSystemMessage(Component.literal("§d[ShtarOS] §aIn-world browser active! Left-click screen or press [U] to control."));
                            });
                            break;
                        }
                    }
                    try { Thread.sleep(250); } catch (InterruptedException ignored) {}
                }
            });
        });
        });
    }

    private record TheaterSite(BlockPos center, Direction front) {
        BlockPos screenCenter() { return center.relative(front, 10); }
        Direction right() { return front.getClockWise(); }
        BlockPos screenMin() {
            BlockPos a = screenCenter().relative(right(), -7).above();
            BlockPos b = screenCenter().relative(right(), 6).above(8);
            return new BlockPos(Math.min(a.getX(), b.getX()), a.getY(), Math.min(a.getZ(), b.getZ()));
        }
        BlockPos screenMax() {
            BlockPos a = screenCenter().relative(right(), -7).above();
            BlockPos b = screenCenter().relative(right(), 6).above(8);
            return new BlockPos(Math.max(a.getX(), b.getX()), b.getY(), Math.max(a.getZ(), b.getZ()));
        }
    }

    private static boolean matchesSite(LocalPlayer player, TheaterSite site) {
        BlockPos screen = site.screenCenter();
        Direction right = site.right();
        int rim = 0;
        for (int x : new int[]{-8, 7}) for (int y : new int[]{0, 9})
            if (player.level().getBlockState(screen.relative(right, x).above(y)).is(Blocks.SEA_LANTERN)) rim++;
        if (rim < 2) return false;
        int panel = 0;
        for (int x = -7; x <= 6; x++) for (int y = 1; y <= 8; y++)
            if (player.level().getBlockState(screen.relative(right, x).above(y)).is(Blocks.POLISHED_BLACKSTONE)) panel++;
        return panel >= 16;
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
            TheaterSite bound = new TheaterSite(screen.relative(front.getOpposite(), 10), front);
            if (standing.distSqr(bound.center()) <= 18 * 18 && matchesSite(player, bound)) return bound;
        }
        TheaterSite nearest = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -14; dx <= 14; dx++) for (int dz = -14; dz <= 14; dz++) {
            for (int dy = -4; dy <= 4; dy++) {
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
        for (int x = -7; x <= 6; x++) for (int y = 1; y <= 8; y++)
            blocks.put(screen.relative(right, x).above(y), Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        for (int x = -8; x <= 7; x++) for (int y = 0; y <= 9; y++)
            if (x == -8 || x == 7 || y == 0 || y == 9)
                blocks.put(screen.relative(right, x).above(y), Blocks.SEA_LANTERN.defaultBlockState());
        for (int x = -5; x <= 4; x += 3)
            blocks.put(screen.relative(right, x).relative(front).above(4), Blocks.SEA_LANTERN.defaultBlockState());

        BlockPos[] floors = {
                screen.relative(right, -9), screen.relative(right, 8),
                center.relative(right, -RADIUS).relative(front, 2),
                center.relative(right, -RADIUS).relative(front, -4),
                center.relative(right, RADIUS).relative(front, 2),
                center.relative(right, RADIUS).relative(front, -4),
                center.relative(front, -10).relative(right, -5),
                center.relative(front, -10).relative(right, 5)};
        for (BlockPos floor : floors) {
            blocks.put(floor, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
            blocks.put(floor.above(), Blocks.NOTE_BLOCK.defaultBlockState());
        }

        // Floor foundation under Row 1
        for (int x = -4; x <= 4; x++) {
            blocks.put(center.relative(right, x).relative(front, -2), Blocks.SPRUCE_PLANKS.defaultBlockState());
        }

        // Row 1: Front lounge sofa (5 seats wide with curved cozy spruce armrests)
        for (int x = -2; x <= 2; x++) {
            blocks.put(center.relative(right, x).relative(front, -2).above(),
                    Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, front));
        }
        // Inward-facing armrests Row 1
        blocks.put(center.relative(right, -3).relative(front, -2).above(),
                Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, right));
        blocks.put(center.relative(right, 3).relative(front, -2).above(),
                Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, right.getOpposite()));

        // Side end-tables with flowers and warm lantern for Row 1
        BlockPos leftTable = center.relative(right, -4).relative(front, -2).above();
        BlockPos rightTable = center.relative(right, 4).relative(front, -2).above();
        blocks.put(leftTable, Blocks.BOOKSHELF.defaultBlockState());
        blocks.put(leftTable.above(), Blocks.POTTED_RED_TULIP.defaultBlockState());
        blocks.put(rightTable, Blocks.BOOKSHELF.defaultBlockState());
        blocks.put(rightTable.above(), Blocks.LANTERN.defaultBlockState());

        // Center Coffee Table & potted flowers in front of Row 1
        BlockPos coffeeLeft = center.relative(right, -1).relative(front, -1);
        BlockPos coffeeMid = center.relative(front, -1);
        BlockPos coffeeRight = center.relative(right, 1).relative(front, -1);
        blocks.put(coffeeLeft, Blocks.SPRUCE_PLANKS.defaultBlockState());
        blocks.put(coffeeMid, Blocks.SPRUCE_PLANKS.defaultBlockState());
        blocks.put(coffeeRight, Blocks.SPRUCE_PLANKS.defaultBlockState());
        blocks.put(coffeeLeft.above(), Blocks.POTTED_FERN.defaultBlockState());
        blocks.put(coffeeMid.above(), Blocks.LANTERN.defaultBlockState());
        blocks.put(coffeeRight.above(), Blocks.POTTED_CORNFLOWER.defaultBlockState());

        // Plush Red Carpet runner in front aisle
        for (int x = -3; x <= 3; x++) {
            blocks.put(center.relative(right, x).relative(front, 0), Blocks.CARPET.red().defaultBlockState());
            blocks.put(center.relative(right, x).relative(front, 1), Blocks.CARPET.red().defaultBlockState());
        }

        // Row 2: Raised VIP Riser Sofa (elevated 1 block for unobstructed view)
        for (int x = -4; x <= 4; x++) {
            for (int z = -6; z <= -4; z++) {
                BlockPos floor = center.relative(right, x).relative(front, z);
                blocks.put(floor, Blocks.SPRUCE_PLANKS.defaultBlockState());
                blocks.put(floor.above(), Blocks.SPRUCE_PLANKS.defaultBlockState());
            }
        }
        for (int x = -2; x <= 2; x++) {
            blocks.put(center.relative(right, x).relative(front, -5).above(2),
                    Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, front));
        }
        // VIP Armrests
        blocks.put(center.relative(right, -3).relative(front, -5).above(2),
                Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, right));
        blocks.put(center.relative(right, 3).relative(front, -5).above(2),
                Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, right.getOpposite()));

        // VIP Side Tables with warm decor
        BlockPos vipLeftTable = center.relative(right, -4).relative(front, -5).above(2);
        BlockPos vipRightTable = center.relative(right, 4).relative(front, -5).above(2);
        blocks.put(vipLeftTable, Blocks.BOOKSHELF.defaultBlockState());
        blocks.put(vipLeftTable.above(), Blocks.LANTERN.defaultBlockState());
        blocks.put(vipRightTable, Blocks.BOOKSHELF.defaultBlockState());
        blocks.put(vipRightTable.above(), Blocks.POTTED_RED_TULIP.defaultBlockState());

        // Smooth Side Access Stairs to VIP Riser
        blocks.put(center.relative(right, -4).relative(front, -3).above(),
                Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, front.getOpposite()));
        blocks.put(center.relative(right, 4).relative(front, -3).above(),
                Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, front.getOpposite()));

        return blocks;
    }

    private static void bindSpeakers(BlockPos center, Direction front) {
        Direction right = front.getClockWise();
        BlockPos screen = center.relative(front, 10);
        ShtarOSSurroundManager.clearSpeakers();
        ShtarOSSurroundManager.registerSpeaker(screen.relative(right, -9).above(), ShtarOSSurroundManager.SpeakerRole.FRONT_LEFT);
        ShtarOSSurroundManager.registerSpeaker(screen.relative(right, 8).above(), ShtarOSSurroundManager.SpeakerRole.FRONT_RIGHT);
        ShtarOSSurroundManager.registerSpeaker(center.relative(right, -RADIUS).relative(front, 2).above(), ShtarOSSurroundManager.SpeakerRole.SIDE_LEFT_FRONT);
        ShtarOSSurroundManager.registerSpeaker(center.relative(right, -RADIUS).relative(front, -4).above(), ShtarOSSurroundManager.SpeakerRole.SIDE_LEFT_REAR);
        ShtarOSSurroundManager.registerSpeaker(center.relative(right, RADIUS).relative(front, 2).above(), ShtarOSSurroundManager.SpeakerRole.SIDE_RIGHT_FRONT);
        ShtarOSSurroundManager.registerSpeaker(center.relative(right, RADIUS).relative(front, -4).above(), ShtarOSSurroundManager.SpeakerRole.SIDE_RIGHT_REAR);
        ShtarOSSurroundManager.registerSpeaker(center.relative(front, -10).relative(right, -5).above(), ShtarOSSurroundManager.SpeakerRole.REAR_LEFT);
        ShtarOSSurroundManager.registerSpeaker(center.relative(front, -10).relative(right, 5).above(), ShtarOSSurroundManager.SpeakerRole.REAR_RIGHT);
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
        BlockPos old10 = center.relative(front, 8);
        for (int x = -6; x <= 5; x++) for (int y = 0; y <= 7; y++)
            clearIf(level, old10.relative(right, x).above(y), Blocks.SEA_LANTERN);
        for (int x = -5; x < 5; x++) for (int y = 1; y <= 6; y++)
            clearIf(level, old10.relative(right, x).above(y), Blocks.POLISHED_BLACKSTONE);

        BlockPos old14 = center.relative(front, 10);
        for (int x = -8; x <= 7; x++) for (int y = 0; y <= 9; y++)
            clearIf(level, old14.relative(right, x).above(y), Blocks.SEA_LANTERN);
        for (int x = -7; x <= 6; x++) for (int y = 1; y <= 8; y++)
            clearIf(level, old14.relative(right, x).above(y), Blocks.POLISHED_BLACKSTONE);

        // Clear previous lounge furniture if present
        for (int x = -6; x <= 6; x++) {
            for (int z = -8; z <= 2; z++) {
                for (int y = 0; y <= 4; y++) {
                    BlockPos p = center.relative(right, x).relative(front, z).above(y);
                    clearIf(level, p, Blocks.POLISHED_BLACKSTONE);
                    clearIf(level, p, Blocks.POLISHED_BLACKSTONE_STAIRS);
                    clearIf(level, p, Blocks.DARK_OAK_STAIRS);
                    clearIf(level, p, Blocks.SPRUCE_STAIRS);
                    clearIf(level, p, Blocks.SPRUCE_PLANKS);
                    clearIf(level, p, Blocks.BOOKSHELF);
                    clearIf(level, p, Blocks.LANTERN);
                    clearIf(level, p, Blocks.CARPET.red());
                    clearIf(level, p, Blocks.POTTED_RED_TULIP);
                    clearIf(level, p, Blocks.POTTED_CORNFLOWER);
                    clearIf(level, p, Blocks.POTTED_FERN);
                }
            }
        }
    }

    private static void clearIf(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.Block block) {
        if (level.getBlockState(pos).is(block)) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }
}
