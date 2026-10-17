package shtaros.browser;

import shtaros.cinema.ShtarOSCinemaManager;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * 3D In-World Raycast Engine.
 * Calculates exact intersection of player crosshairs with the cinema screen,
 * mapping 3D eye vectors into 2D Chromium browser pixel coordinates.
 */
public final class ShtarOSScreenRaycast {

    public record RaycastResult(
            Vec3 hitPos,
            double u,
            double v,
            int px,
            int py,
            double distance,
            Direction facing
    ) {}

    private static volatile RaycastResult latestHit = null;

    public static RaycastResult getLatestHit() {
        return latestHit;
    }

    public static void clearHit() {
        latestHit = null;
    }

    /**
     * Performs a 3D raycast from the player's eye along their view direction
     * to the cinema screen quad in the world.
     */
    public static RaycastResult raycast(LocalPlayer player, double maxDistance) {
        if (player == null || !ShtarOSCinemaManager.hasScreen()) {
            latestHit = null;
            return null;
        }

        BlockPos min = ShtarOSCinemaManager.getScreenMin();
        BlockPos max = ShtarOSCinemaManager.getScreenMax();
        Direction facing = ShtarOSCinemaManager.getScreenFacing();
        if (min == null || max == null || facing == null) {
            latestHit = null;
            return null;
        }

        double pMinX = Math.min(min.getX(), max.getX());
        double pMaxX = Math.max(min.getX(), max.getX()) + 1.0;
        double pMinY = Math.min(min.getY(), max.getY());
        double pMaxY = Math.max(min.getY(), max.getY()) + 1.0;
        double pMinZ = Math.min(min.getZ(), max.getZ());
        double pMaxZ = Math.max(min.getZ(), max.getZ()) + 1.0;

        double spanX = pMaxX - pMinX;
        double spanZ = pMaxZ - pMinZ;
        double height = pMaxY - pMinY;
        if (height <= 0.1) { latestHit = null; return null; }

        boolean wallAlongX = spanX >= spanZ;
        double width = wallAlongX ? spanX : spanZ;
        if (width <= 0.1) { latestHit = null; return null; }

        double targetAspect = 16.0 / 9.0;
        double actualAspect = width / height;

        double renderW = width;
        double renderH = height;
        double offsetW = 0.0;
        double offsetH = 0.0;

        if (actualAspect > targetAspect) {
            renderW = height * targetAspect;
            offsetW = (width - renderW) / 2.0;
        } else if (actualAspect < targetAspect) {
            renderH = width / targetAspect;
            offsetH = (height - renderH) / 2.0;
        }

        double faceOffset = 0.18;
        double fMinY = pMinY + offsetH;
        double fMaxY = pMaxY - offsetH;

        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 dir = player.getViewVector(1.0f);

        double t = -1.0;
        double hitX = 0, hitY = 0, hitZ = 0;
        double u = 0, v = 0;

        if (wallAlongX) {
            double fMinX = pMinX + offsetW;
            double fMaxX = pMaxX - offsetW;

            if (facing == Direction.NORTH) {
                // North face: z = pMinZ - faceOffset, normal (0, 0, -1), observer looking South (+Z)
                double zPlane = pMinZ - faceOffset;
                if (dir.z > 1e-4) {
                    t = (zPlane - eye.z) / dir.z;
                    if (t > 0 && t <= maxDistance) {
                        hitX = eye.x + t * dir.x;
                        hitY = eye.y + t * dir.y;
                        hitZ = zPlane;
                        if (hitY >= fMinY && hitY <= fMaxY && hitX >= fMinX && hitX <= fMaxX) {
                            u = (fMaxX - hitX) / (fMaxX - fMinX);
                            v = (fMaxY - hitY) / (fMaxY - fMinY);
                        } else { t = -1; }
                    }
                }
            } else if (facing == Direction.SOUTH) {
                // South face: z = pMaxZ + faceOffset, normal (0, 0, 1), observer looking North (-Z)
                double zPlane = pMaxZ + faceOffset;
                if (dir.z < -1e-4) {
                    t = (zPlane - eye.z) / dir.z;
                    if (t > 0 && t <= maxDistance) {
                        hitX = eye.x + t * dir.x;
                        hitY = eye.y + t * dir.y;
                        hitZ = zPlane;
                        if (hitY >= fMinY && hitY <= fMaxY && hitX >= fMinX && hitX <= fMaxX) {
                            u = (hitX - fMinX) / (fMaxX - fMinX);
                            v = (fMaxY - hitY) / (fMaxY - fMinY);
                        } else { t = -1; }
                    }
                }
            }
        } else {
            // Wall along Z
            double fMinZ = pMinZ + offsetW;
            double fMaxZ = pMaxZ - offsetW;

            if (facing == Direction.WEST) {
                // West face: x = pMinX - faceOffset, normal (-1, 0, 0), observer looking East (+X)
                double xPlane = pMinX - faceOffset;
                if (dir.x > 1e-4) {
                    t = (xPlane - eye.x) / dir.x;
                    if (t > 0 && t <= maxDistance) {
                        hitX = xPlane;
                        hitY = eye.y + t * dir.y;
                        hitZ = eye.z + t * dir.z;
                        if (hitY >= fMinY && hitY <= fMaxY && hitZ >= fMinZ && hitZ <= fMaxZ) {
                            u = (hitZ - fMinZ) / (fMaxZ - fMinZ);
                            v = (fMaxY - hitY) / (fMaxY - fMinY);
                        } else { t = -1; }
                    }
                }
            } else if (facing == Direction.EAST) {
                // East face: x = pMaxX + faceOffset, normal (1, 0, 0), observer looking West (-X)
                double xPlane = pMaxX + faceOffset;
                if (dir.x < -1e-4) {
                    t = (xPlane - eye.x) / dir.x;
                    if (t > 0 && t <= maxDistance) {
                        hitX = xPlane;
                        hitY = eye.y + t * dir.y;
                        hitZ = eye.z + t * dir.z;
                        if (hitY >= fMinY && hitY <= fMaxY && hitZ >= fMinZ && hitZ <= fMaxZ) {
                            u = (fMaxZ - hitZ) / (fMaxZ - fMinZ);
                            v = (fMaxY - hitY) / (fMaxY - fMinY);
                        } else { t = -1; }
                    }
                }
            }
        }

        if (t > 0) {
            u = Math.clamp(u, 0.0, 1.0);
            v = Math.clamp(v, 0.0, 1.0);
            int bw = ShtarOSBrowserBridge.getWidth();
            int bh = ShtarOSBrowserBridge.getHeight();
            int px = (int) Math.clamp(u * bw, 0, bw - 1);
            int py = (int) Math.clamp(v * bh, 0, bh - 1);
            RaycastResult res = new RaycastResult(new Vec3(hitX, hitY, hitZ), u, v, px, py, t, facing);
            latestHit = res;
            return res;
        }

        latestHit = null;
        return null;
    }
}
