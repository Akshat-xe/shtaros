package grandtweaks.zoom;

import com.mojang.blaze3d.platform.InputConstants;
import grandtweaks.GrandConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

public class GrandZoom {
    private static float userTargetZoom = 3.5f;
    private static float currentZoom = 1.0f;
    private static float currentAnchorDist = 0.0f;
    private static boolean active = false;
    private static long lastUpdateTime = 0L;

    // Chunk Streaming Cache
    private static int lastLoadedCX = Integer.MIN_VALUE;
    private static int lastLoadedCZ = Integer.MIN_VALUE;
    private static long lastChunkLoadTime = 0L;
    private static final Set<ChunkPos> streamedChunks = new HashSet<>();

    public static void update() {
        Minecraft client = Minecraft.getInstance();
        boolean isKeyDown = false;

        // Only trigger zoom when player is actively playing and no screen/chat is open
        if (client != null && client.gui != null && client.gui.screen() == null) {
            try {
                isKeyDown = InputConstants.isKeyDown(InputConstants.KEY_C);
            } catch (Exception ignored) {
            }
        }

        long now = System.nanoTime();
        float dt = (lastUpdateTime == 0L) ? 0.016f : Math.min(0.05f, (float) ((now - lastUpdateTime) / 1_000_000_000.0));
        lastUpdateTime = now;

        float targetZoom = isKeyDown ? userTargetZoom : 1.0f;
        // Project focal anchor forward along sightline (up to 1,000 blocks into distant terrain)
        float targetAnchor = (isKeyDown && "grand".equalsIgnoreCase(GrandConfig.zoomMode))
                ? (float) Math.min(1000.0, (userTargetZoom - 1.0f) * 40.0f)
                : 0.0f;

        if (GrandConfig.smoothZoom) {
            float zoomBlend = 1.0f - (float) Math.exp(-dt * 18.0f);
            currentZoom += (targetZoom - currentZoom) * zoomBlend;

            float anchorBlend = 1.0f - (float) Math.exp(-dt * 14.0f);
            currentAnchorDist += (targetAnchor - currentAnchorDist) * anchorBlend;
        } else {
            currentZoom = targetZoom;
            currentAnchorDist = targetAnchor;
        }

        if (currentZoom < 1.002f && !isKeyDown) {
            currentZoom = 1.0f;
            currentAnchorDist = 0.0f;
            active = false;
            cleanUpStreamedChunks(client);
        } else {
            active = true;
            if (isKeyDown && "grand".equalsIgnoreCase(GrandConfig.zoomMode) && client.player != null) {
                streamTargetChunks(client, now);
            }
        }
    }

    private static void streamTargetChunks(Minecraft client, long now) {
        if (client.getSingleplayerServer() == null || client.player == null) return;
        // Rate-limit chunk tickets to every 250ms to keep integrated server lightweight
        if (now - lastChunkLoadTime < 250_000_000L) return;
        lastChunkLoadTime = now;

        Vec3 eye = client.player.getEyePosition();
        Vec3 look = client.player.getViewVector(1.0f);
        Vec3 focal = eye.add(look.scale(Math.max(32.0, currentAnchorDist)));

        int cx = ((int) Math.floor(focal.x)) >> 4;
        int cz = ((int) Math.floor(focal.z)) >> 4;

        if (cx == lastLoadedCX && cz == lastLoadedCZ) return;
        lastLoadedCX = cx;
        lastLoadedCZ = cz;

        try {
            ServerLevel serverLevel = client.getSingleplayerServer().getLevel(client.player.level().dimension());
            if (serverLevel == null) return;
            ServerPlayer serverPlayer = client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID());
            if (serverPlayer == null) return;

            // Load 3x3 chunks at the distant crosshair target and send them to the client
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int tx = cx + dx;
                    int tz = cz + dz;
                    ChunkPos cp = new ChunkPos(tx, tz);
                    streamedChunks.add(cp);
                    serverLevel.getChunkSource().addTicketWithRadius(TicketType.FORCED, cp, 1);
                    ChunkAccess ca = serverLevel.getChunkSource().getChunk(tx, tz, ChunkStatus.FULL, true);
                    if (ca instanceof LevelChunk levelChunk) {
                        serverPlayer.connection.send(new ClientboundLevelChunkWithLightPacket(
                                levelChunk, serverLevel.getLightEngine(), null, null
                        ));
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void cleanUpStreamedChunks(Minecraft client) {
        if (streamedChunks.isEmpty()) return;
        lastLoadedCX = Integer.MIN_VALUE;
        lastLoadedCZ = Integer.MIN_VALUE;
        if (client != null && client.getSingleplayerServer() != null && client.player != null) {
            try {
                ServerLevel serverLevel = client.getSingleplayerServer().getLevel(client.player.level().dimension());
                if (serverLevel != null) {
                    for (ChunkPos cp : streamedChunks) {
                        serverLevel.getChunkSource().removeTicketWithRadius(TicketType.FORCED, cp, 1);
                        serverLevel.setChunkForced(cp.x(), cp.z(), false);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        streamedChunks.clear();
    }

    public static void onScroll(double yOffset) {
        if (yOffset > 0) {
            userTargetZoom = Math.min(32.0f, userTargetZoom + 1.25f);
        } else if (yOffset < 0) {
            userTargetZoom = Math.max(1.5f, userTargetZoom - 1.25f);
        }
    }

    public static boolean isZoomActive() {
        return active && currentZoom > 1.002f;
    }

    public static float getCurrentZoom() {
        return currentZoom;
    }

    public static float getVirtualAnchorDistance() {
        return "grand".equalsIgnoreCase(GrandConfig.zoomMode) ? currentAnchorDist : 0.0f;
    }

    public static double getSensitivityFactor() {
        if (!isZoomActive() || currentZoom <= 1.05f) {
            return 1.0;
        }
        return Math.max(0.12, 1.0 / Math.pow(currentZoom, 0.75));
    }

    public static float getUserTargetZoom() {
        return userTargetZoom;
    }

    public static void setUserTargetZoom(float zoom) {
        userTargetZoom = zoom;
    }
}
