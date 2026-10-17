package shtaros.cinema;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import shtaros.ShtarOSDaemonManager;
import shtaros.browser.ShtarOSBrowserBridge;
import shtaros.model.SongMetadata;
import shtaros.model.ShtarOSLibrary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * ShtarOS Cinema Central Manager
 * Coordinates Screen rendering, Surround Audio, and Power State.
 */
public class ShtarOSCinemaManager {

    private static BlockPos screenMin = null;
    private static BlockPos screenMax = null;
    private static Level screenLevel = null;
    private static long screenBoundNanos;
    private static boolean screenDamaged;
    private static Direction screenFacing = Direction.NORTH;
    private static int screenWidth = 8;
    private static int screenHeight = 5;

    private static String currentVideoTitle = "Idle (Select a video to stream)";
    private static String currentVideoId = null;
    private static boolean paused;
    private static boolean looping;
    private static int playbackOffsetSeconds;
    private static long playbackStartedNanos;
    private static final List<SongMetadata> playOrder = new ArrayList<>();
    private static final List<String> activity = new ArrayList<>();
    private static int playIndex = -1;
    private static boolean mixEnabled;
    private static boolean mixLoading;

    public static List<SongMetadata> getPlayOrder() { return List.copyOf(playOrder); }
    public static List<String> getActivity() { return List.copyOf(activity); }
    public static int getPlayIndex() { return playIndex; }
    public static int currentDuration() { return playIndex >= 0 && playIndex < playOrder.size() ? playOrder.get(playIndex).durationSeconds() : 0; }
    public static boolean isMixEnabled() { return mixEnabled; }
    public static void log(String message) {
        activity.add(message);
        if (activity.size() > 5) activity.remove(0);
    }

    public static void playSong(SongMetadata song) {
        playOrder.clear();
        playOrder.add(song);
        playIndex = 0;
        mixEnabled = false;
        playVideo(song.id(), song.title());
    }

    public static void addToQueue(SongMetadata song) {
        if (currentVideoId == null) { playSong(song); return; }
        playOrder.add(song);
        log("Queued: " + song.title());
    }

    public static void addNext(SongMetadata song) {
        if (currentVideoId == null) { playSong(song); return; }
        playOrder.add(Math.max(0, playIndex + 1), song);
        log("Playing next: " + song.title());
    }

    public static void playPlaylist(List<SongMetadata> songs) {
        if (songs.isEmpty()) return;
        playOrder.clear();
        playOrder.addAll(songs);
        playIndex = 0;
        mixEnabled = false;
        log("Playlist loaded: " + songs.size() + " videos");
        playVideo(songs.get(0).id(), songs.get(0).title());
    }

    public static void startMix(SongMetadata seed) {
        playSong(seed);
        mixEnabled = true;
        log("Guest mix started from: " + seed.title());
        requestMix();
    }

    public static void playNext() {
        if (playIndex + 1 < playOrder.size()) {
            playIndex++;
            SongMetadata next = playOrder.get(playIndex);
            playVideo(next.id(), next.title());
        } else if (mixEnabled) {
            ShtarOSVideoPlayer.stop();
            ShtarOSVideoPlayer.refreshStandbyFrame();
            ShtarOSSurroundManager.stop();
            currentVideoId = null;
            currentVideoTitle = "Finding the next mix track...";
            requestMix();
            log("Finding the next mix track...");
        } else if (looping && !playOrder.isEmpty()) {
            playIndex = 0;
            playVideo(playOrder.get(0).id(), playOrder.get(0).title());
        } else {
            stopPlayback();
        }
    }

    public static void playQueuedAt(int index) {
        if (index < 0 || index >= playOrder.size()) return;
        playIndex = index;
        SongMetadata selected = playOrder.get(index);
        playVideo(selected.id(), selected.title());
    }

    public static void playPrevious() {
        if (currentVideoId == null) return;
        if (playbackSeconds() > 3 || playIndex <= 0) { replay(); return; }
        playIndex--;
        SongMetadata previous = playOrder.get(playIndex);
        playVideo(previous.id(), previous.title());
    }

    public static void seekTo(int seconds) {
        if (currentVideoId == null) return;
        playbackOffsetSeconds = Math.max(0, seconds);
        if (!paused) startStreams(playbackOffsetSeconds);
    }

    private static void requestMix() {
        if (mixLoading || playIndex < 0 || playOrder.isEmpty()) return;
        mixLoading = true;
        SongMetadata seed = playOrder.get(playIndex);
        CompletableFuture.runAsync(() -> {
            try {
                ShtarOSDaemonManager.ensureRunning();
                String query = URLEncoder.encode(seed.title() + " " + seed.author() + " music", StandardCharsets.UTF_8);
                HttpURLConnection conn = (HttpURLConnection) URI.create("http://127.0.0.1:25592/search?q=" + query).toURL().openConnection();
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(15000);
                List<SongMetadata> found = new ArrayList<>();
                if (conn.getResponseCode() == 200) {
                    try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                        for (JsonElement element : JsonParser.parseReader(reader).getAsJsonArray()) {
                            JsonObject item = element.getAsJsonObject();
                            String id = item.get("id").getAsString();
                            if (!id.matches("[A-Za-z0-9_-]{11}")) continue;
                            String title = item.get("title").getAsString();
                            String author = item.has("author") ? item.get("author").getAsString() : "YouTube";
                            int duration = item.has("duration") && !item.get("duration").isJsonNull() ? item.get("duration").getAsInt() : 0;
                            String label = item.has("duration_str") ? item.get("duration_str").getAsString() : "";
                            found.add(new SongMetadata(id, title, author, label, duration));
                        }
                    }
                }
                Minecraft.getInstance().execute(() -> {
                    mixLoading = false;
                    if (!mixEnabled) return;
                    int before = playOrder.size();
                    for (SongMetadata item : found) {
                        if (playOrder.stream().noneMatch(existing -> existing.id().equals(item.id()))) playOrder.add(item);
                    }
                    if (currentVideoId == null && playIndex + 1 < playOrder.size()) playNext();
                    else if (playOrder.size() == before) {
                        mixEnabled = false;
                        if (currentVideoId == null) stopPlayback();
                        log("Mix found no new songs. Search for another seed.");
                    }
                });
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> {
                    mixLoading = false;
                    mixEnabled = false;
                    if (currentVideoId == null) stopPlayback();
                    log("Mix search failed: " + e.getMessage());
                });
            }
        });
    }

    public static boolean hasScreen() {
        return screenMin != null && screenMax != null;
    }

    public static BlockPos getScreenMin() {
        return screenMin;
    }

    public static BlockPos getScreenMax() {
        return screenMax;
    }

    public static Direction getScreenFacing() {
        return screenFacing;
    }

    public static int getScreenWidth() {
        return screenWidth;
    }

    public static int getScreenHeight() {
        return screenHeight;
    }

    public static double getScreenDiagonal() {
        return Math.sqrt(screenWidth * screenWidth + screenHeight * screenHeight);
    }

    public static String getCurrentVideoTitle() {
        return currentVideoTitle;
    }

    public static String getCurrentVideoId() {
        return currentVideoId;
    }

    public static boolean isPaused() { return paused; }
    public static boolean isLooping() { return looping; }
    public static boolean toggleLoop() { looping = !looping; return looping; }
    public static int playbackSeconds() {
        return playbackOffsetSeconds + (paused || currentVideoId == null ? 0 :
                (int) ((System.nanoTime() - playbackStartedNanos) / 1_000_000_000L));
    }

    public static void setScreen(BlockPos min, BlockPos max, Direction facing) {
        screenMin = min;
        screenMax = max;
        screenLevel = Minecraft.getInstance().level;
        screenBoundNanos = System.nanoTime();
        screenDamaged = false;
        screenFacing = facing;

        screenWidth = Math.abs(max.getX() - min.getX()) + Math.abs(max.getZ() - min.getZ()) + 1;
        screenHeight = Math.abs(max.getY() - min.getY()) + 1;

        System.out.println("[ShtarOS Cinema] Screen configured: " + screenWidth + "x" + screenHeight + " blocks, facing " + facing + " (Diagonal: " + String.format("%.1f", getScreenDiagonal()) + " blocks)");

        if (ShtarOSBrowserBridge.isAvailable() && !ShtarOSBrowserBridge.isOpen() && currentVideoId == null) {
            ShtarOSBrowserBridge.open();
        }
    }

    public static void clearScreen() {
        ShtarOSBrowserBridge.close();
        stopPlayback();
        ShtarOSSurroundManager.clearSpeakers();
        screenMin = null;
        screenMax = null;
        screenLevel = null;
        screenDamaged = false;
        screenFacing = Direction.NORTH;
        screenWidth = 0;
        screenHeight = 0;
    }

    public static void playVideo(String videoId, String title) {
        ShtarOSBrowserBridge.close();
        Minecraft mc = Minecraft.getInstance();
        if ((!hasScreen() || !isScreenIntact()) && mc.player != null) {
            scanAndBindCinema(mc.player);
        }
        if (!isScreenIntact() || ShtarOSSurroundManager.activeSpeakerCount() == 0) {
            log("Cannot play: repair the screen or add a speaker");
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§c[ShtarOS] Build or scan a complete screen and speakers first."));
            return;
        }
        if (playIndex < 0 || playIndex >= playOrder.size() || !playOrder.get(playIndex).id().equals(videoId)) {
            playOrder.clear();
            playOrder.add(new SongMetadata(videoId, title, "YouTube", "", 0));
            playIndex = 0;
            mixEnabled = false;
        }
        currentVideoId = videoId;
        currentVideoTitle = title;
        paused = false;
        ShtarOSLibrary.remember(playOrder.get(playIndex));
        log("Playing: " + title);
        startStreams(0);
    }

    private static void startStreams(int offsetSeconds) {
        playbackOffsetSeconds = Math.max(0, offsetSeconds);
        playbackStartedNanos = System.nanoTime();
        String videoId = currentVideoId;
        if (videoId == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendOverlayMessage(
                    Component.literal("§d[ShtarOS Cinema] §eLoading: §f" + currentVideoTitle + "...")
            );
        }

        // 1. Start live video streaming
        ShtarOSVideoPlayer.play(videoId, playbackOffsetSeconds);

        // 2. Start multi-speaker surround audio
        ShtarOSSurroundManager.play(videoId, playbackOffsetSeconds);

        if (mc.player != null) {
            mc.player.sendSystemMessage(
                    Component.literal("§d[ShtarOS Cinema] §eStarting video and audio: §f" + currentVideoTitle)
            );
        }
    }

    public static void stopPlayback() {
        ShtarOSVideoPlayer.stop();
        ShtarOSVideoPlayer.refreshStandbyFrame();
        ShtarOSSurroundManager.stop();
        currentVideoId = null;
        currentVideoTitle = "Idle (Stopped)";
        paused = false;
        playbackOffsetSeconds = 0;
        mixEnabled = false;
        log("Playback stopped");
    }

    public static boolean isScreenIntact() {
        Minecraft mc = Minecraft.getInstance();
        if (!hasScreen() || mc.level == null || mc.level != screenLevel) return false;
        for (int x = screenMin.getX(); x <= screenMax.getX(); x++) {
            for (int y = screenMin.getY(); y <= screenMax.getY(); y++) {
                for (int z = screenMin.getZ(); z <= screenMax.getZ(); z++) {
                    BlockPos panelBlock = new BlockPos(x, y, z);
                    if (mc.level.hasChunkAt(panelBlock) && mc.level.getBlockState(panelBlock).isAir()) return false;
                }
            }
        }
        return true;
    }

    /** Restore only missing blocks in the bound theater; never overwrite a player's replacement blocks. */
    public static boolean repairCinema(LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        if (player == null || !mc.hasSingleplayerServer() || mc.getSingleplayerServer() == null) return false;
        if (!hasScreen() || player.level() != screenLevel) return scanAndBindCinema(player);
        ServerLevel level = mc.getSingleplayerServer().getLevel(player.level().dimension());
        if (level == null) return false;
        BlockPos min = screenMin;
        BlockPos max = screenMax;
        List<BlockPos> speakerPositions = ShtarOSSurroundManager.getSpeakers().stream().map(s -> s.pos).toList();
        level.getServer().execute(() -> {
            int fixed = 0;
            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int y = min.getY(); y <= max.getY(); y++) {
                    for (int z = min.getZ(); z <= max.getZ(); z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (level.getBlockState(pos).isAir()) {
                            level.setBlockAndUpdate(pos, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
                            fixed++;
                        }
                    }
                }
            }
            for (BlockPos pos : speakerPositions) {
                if (level.getBlockState(pos).isAir()) {
                    level.setBlockAndUpdate(pos, Blocks.NOTE_BLOCK.defaultBlockState());
                    fixed++;
                }
            }
            int repaired = fixed;
            mc.execute(() -> {
                screenDamaged = false;
                screenBoundNanos = System.nanoTime();
                log("Theater repaired: " + repaired + " missing blocks");
                player.sendSystemMessage(Component.literal("§a[ShtarOS] Repaired " + repaired + " missing theater blocks."));
            });
        });
        return true;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (hasScreen() && (mc.level == null || mc.level != screenLevel)) {
            ShtarOSBrowserBridge.close();
            stopPlayback();
            screenMin = null;
            screenMax = null;
            screenLevel = null;
            screenDamaged = false;
            ShtarOSSurroundManager.clearSpeakers();
            return;
        }
        if (hasScreen() && !screenDamaged && System.nanoTime() - screenBoundNanos > 2_000_000_000L && !isScreenIntact()) {
            ShtarOSBrowserBridge.close();
            stopPlayback();
            screenDamaged = true;
            log("Screen damaged. Use Auto Detect & Fix.");
        }
        if (currentVideoId != null && !paused && ShtarOSVideoPlayer.endedNaturally()) {
            if (looping) startStreams(0);
            else playNext();
        }
    }

    public static void togglePause() {
        if (currentVideoId == null) return;
        if (paused) {
            paused = false;
            startStreams(playbackOffsetSeconds);
        } else {
            playbackOffsetSeconds = playbackSeconds();
            paused = true;
            ShtarOSVideoPlayer.stop();
            ShtarOSSurroundManager.stop();
        }
    }

    public static void seek(int deltaSeconds) {
        if (currentVideoId == null) return;
        int target = Math.max(0, playbackSeconds() + deltaSeconds);
        playbackOffsetSeconds = target;
        if (!paused) startStreams(target);
    }

    public static void replay() {
        if (currentVideoId == null) return;
        paused = false;
        startStreams(0);
    }

    public static void togglePower() {
        ShtarOSVideoPlayer.togglePower();
        if (!ShtarOSVideoPlayer.isPowered()) {
            stopPlayback();
        }
    }

    /**
     * Auto-detects an existing wall (e.g. the user's dirt or concrete screen) and note block speakers nearby.
     */
    public static boolean scanAndBindCinema(LocalPlayer player) {
        if (player == null || player.level() == null) return false;
        Level level = player.level();
        BlockPos playerPos = player.blockPosition();

        // 1. Look for nearby Note Blocks / Jukeboxes
        int speakerCount = 0;
        ShtarOSSurroundManager.clearSpeakers();

        int radius = 16;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -4; dy <= 10; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos p = playerPos.offset(dx, dy, dz);
                    BlockState bs = level.getBlockState(p);
                    if (bs.is(Blocks.NOTE_BLOCK) || bs.is(Blocks.JUKEBOX)) {
                        // Angle in the listener's local forward/right frame.
                        Direction front = player.getDirection();
                        Direction right = front.getClockWise();
                        double forward = dx * front.getStepX() + dz * front.getStepZ();
                        double sideways = dx * right.getStepX() + dz * right.getStepZ();
                        double angle = Math.toDegrees(Math.atan2(sideways, forward));
                        ShtarOSSurroundManager.SpeakerRole role = getRoleForAngle(angle);
                        ShtarOSSurroundManager.registerSpeaker(p, role);
                    }
                }
            }
        }

        // 2. Look for the nearest large vertical wall in front of the player
        Direction facing = player.getDirection();

        // Scan in front for non-air blocks forming a wall
        BlockPos foundWallBlock = null;
        for (int dist = 3; dist <= 18; dist++) {
            BlockPos check = playerPos.relative(facing, dist).above(2);
            if (!level.getBlockState(check).isAir()) {
                foundWallBlock = check;
                break;
            }
        }

        if (foundWallBlock != null) {
            BlockState panelMaterial = level.getBlockState(foundWallBlock);
            // Find wall bounds
            int minX = foundWallBlock.getX(), maxX = foundWallBlock.getX();
            int minY = foundWallBlock.getY(), maxY = foundWallBlock.getY();
            int minZ = foundWallBlock.getZ(), maxZ = foundWallBlock.getZ();

            // Expand bounds
            for (int i = 1; i <= 8; i++) {
                if (facing.getAxis() == Direction.Axis.Z) {
                    if (level.getBlockState(foundWallBlock.offset(i, 0, 0)).is(panelMaterial.getBlock())) maxX = foundWallBlock.getX() + i;
                    if (level.getBlockState(foundWallBlock.offset(-i, 0, 0)).is(panelMaterial.getBlock())) minX = foundWallBlock.getX() - i;
                } else {
                    if (level.getBlockState(foundWallBlock.offset(0, 0, i)).is(panelMaterial.getBlock())) maxZ = foundWallBlock.getZ() + i;
                    if (level.getBlockState(foundWallBlock.offset(0, 0, -i)).is(panelMaterial.getBlock())) minZ = foundWallBlock.getZ() - i;
                }
            }
            for (int y = 1; y <= 7; y++) {
                if (level.getBlockState(foundWallBlock.above(y)).is(panelMaterial.getBlock())) maxY = foundWallBlock.getY() + y;
                if (level.getBlockState(foundWallBlock.below(y)).is(panelMaterial.getBlock())) minY = foundWallBlock.getY() - y;
            }

            setScreen(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), facing.getOpposite());
        } else {
            log("No physical screen found in front");
            player.sendSystemMessage(Component.literal("§c[ShtarOS] No physical screen found. Use /shtaros setup."));
            return false;
        }

        speakerCount = ShtarOSSurroundManager.getSpeakers().size();
        ShtarOSBrowserBridge.speakersChanged();
        log("Screen bound: " + screenWidth + "x" + screenHeight + ", " + speakerCount + " speakers");
        player.sendSystemMessage(
                Component.literal("§d[ShtarOS Cinema] §a✔ Cinema detected: Screen " + screenWidth + "x" + screenHeight + " blocks (" + String.format("%.1f", getScreenDiagonal()) + " diag), " + speakerCount + " surround speakers connected!")
        );
        return true;
    }

    private static ShtarOSSurroundManager.SpeakerRole getRoleForAngle(double deg) {
        if (deg >= 0 && deg < 60) return ShtarOSSurroundManager.SpeakerRole.FRONT_RIGHT;
        if (deg >= 60 && deg < 95) return ShtarOSSurroundManager.SpeakerRole.SIDE_RIGHT_FRONT;
        if (deg >= 95 && deg < 145) return ShtarOSSurroundManager.SpeakerRole.SIDE_RIGHT_REAR;
        if (deg >= 145) return ShtarOSSurroundManager.SpeakerRole.REAR_RIGHT;
        if (deg > -60) return ShtarOSSurroundManager.SpeakerRole.FRONT_LEFT;
        if (deg > -95) return ShtarOSSurroundManager.SpeakerRole.SIDE_LEFT_FRONT;
        if (deg > -145) return ShtarOSSurroundManager.SpeakerRole.SIDE_LEFT_REAR;
        return ShtarOSSurroundManager.SpeakerRole.REAR_LEFT;
    }
}
