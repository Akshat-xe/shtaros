package shtaros.cinema;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import shtaros.browser.ShtarOSBrowserBridge;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.Identifier;

import java.nio.ByteOrder;
import java.nio.IntBuffer;

/**
 * ShtarOS Cinema Screen Renderer
 * Renders the textured video display quad onto the physical panel blocks in the world.
 * Uses a full-bright surface on the auditorium-facing side
 * and 16:9 cinematic letterboxing.
 */
public class ShtarOSScreenRenderer {
    private static final Identifier HALO_TEXTURE_ID = Identifier.fromNamespaceAndPath("shtaros", "textures/cinema_halo");
    private static boolean haloInitialized;
    private static boolean haloAttempted;

    public static void init() {
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(ShtarOSScreenRenderer::render);
    }

    private static void render(LevelRenderContext context) {
        if (!ShtarOSCinemaManager.hasScreen() || (!ShtarOSVideoPlayer.isPowered() && !ShtarOSBrowserBridge.isOpen())) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null || mc.gameRenderer.mainCamera() == null) return;
        Vec3 camPos = mc.gameRenderer.mainCamera().position();

        BlockPos min = ShtarOSCinemaManager.getScreenMin();
        BlockPos max = ShtarOSCinemaManager.getScreenMax();
        Direction facing = ShtarOSCinemaManager.getScreenFacing();
        if (min == null || max == null) return;

        // Calculate physical screen bounding box
        double pMinX = Math.min(min.getX(), max.getX());
        double pMaxX = Math.max(min.getX(), max.getX()) + 1.0;
        double pMinY = Math.min(min.getY(), max.getY());
        double pMaxY = Math.max(min.getY(), max.getY()) + 1.0;
        double pMinZ = Math.min(min.getZ(), max.getZ());
        double pMaxZ = Math.max(min.getZ(), max.getZ()) + 1.0;

        double spanX = pMaxX - pMinX;
        double spanZ = pMaxZ - pMinZ;
        double height = pMaxY - pMinY;
        if (height <= 0.1) return;

        boolean wallAlongX = spanX >= spanZ;
        double width = wallAlongX ? spanX : spanZ;
        if (width <= 0.1) return;

        // Apply 16:9 Aspect Ratio Letterboxing
        double targetAspect = 16.0 / 9.0;
        double actualAspect = width / height;

        double renderW = width;
        double renderH = height;
        double offsetW = 0.0;
        double offsetH = 0.0;

        if (actualAspect > targetAspect) {
            // Screen is wider than 16:9: add side pillarbox bars
            renderW = height * targetAspect;
            offsetW = (width - renderW) / 2.0;
        } else if (actualAspect < targetAspect) {
            // Screen is taller than 16:9: add top/bottom letterbox bars
            renderH = width / targetAspect;
            offsetH = (height - renderH) / 2.0;
        }

        double faceOffset = 0.18; // Keep the display safely in front of block faces at oblique angles.
        PoseStack poseStack = context.poseStack();
        poseStack.pushPose();

        final double fMinY = pMinY + offsetH - camPos.y;
        final double fMaxY = pMaxY - offsetH - camPos.y;
        final double finalOffsetW = offsetW;

        Identifier browserTexture = ShtarOSBrowserBridge.texture();
        Identifier screenTexture = browserTexture != null ? browserTexture : ShtarOSVideoPlayer.getTextureId();
        if (haloInitialized) {
            double haloMarginX = renderW * 0.095;
            double haloMarginY = renderH * 0.095;
            submitHalo(context, poseStack, facing, wallAlongX, camPos,
                    pMinX + offsetW - haloMarginX, pMaxX - offsetW + haloMarginX,
                    pMinY + offsetH - haloMarginY, pMaxY - offsetH + haloMarginY,
                    pMinZ + offsetW - haloMarginX, pMaxZ - offsetW + haloMarginX,
                    pMinX - faceOffset - 0.012, pMaxX + faceOffset + 0.012,
                    pMinZ - faceOffset - 0.012, pMaxZ + faceOffset + 0.012);
        }
        context.submitNodeCollector().submitCustomGeometry(
                poseStack,
                RenderTypes.entityTranslucentEmissive(screenTexture),
                (pose, vc) -> {
                    if (wallAlongX) {
                        double fMinX = pMinX + finalOffsetW - camPos.x;
                        double fMaxX = pMaxX - finalOffsetW - camPos.x;

                        // North Face (facing -Z, observer looking South +Z: Left is +X, Right is -X)
                        double zNorth = pMinZ - faceOffset - camPos.z;
                        if (facing == Direction.NORTH) drawQuad(pose, vc,
                                (float)fMaxX, (float)fMinY, (float)zNorth, 0.0f, 1.0f,
                                (float)fMaxX, (float)fMaxY, (float)zNorth, 0.0f, 0.0f,
                                (float)fMinX, (float)fMaxY, (float)zNorth, 1.0f, 0.0f,
                                (float)fMinX, (float)fMinY, (float)zNorth, 1.0f, 1.0f,
                                0.0f, 0.0f, -1.0f);

                        // South Face (facing +Z, observer looking North -Z: Left is -X, Right is +X)
                        double zSouth = pMaxZ + faceOffset - camPos.z;
                        if (facing == Direction.SOUTH) drawQuad(pose, vc,
                                (float)fMinX, (float)fMinY, (float)zSouth, 0.0f, 1.0f,
                                (float)fMinX, (float)fMaxY, (float)zSouth, 0.0f, 0.0f,
                                (float)fMaxX, (float)fMaxY, (float)zSouth, 1.0f, 0.0f,
                                (float)fMaxX, (float)fMinY, (float)zSouth, 1.0f, 1.0f,
                                0.0f, 0.0f, 1.0f);

                    } else {
                        // Wall along Z
                        double fMinZ = pMinZ + finalOffsetW - camPos.z;
                        double fMaxZ = pMaxZ - finalOffsetW - camPos.z;

                        // West Face (facing -X, observer looking East +X: Left is -Z, Right is +Z)
                        double xWest = pMinX - faceOffset - camPos.x;
                        if (facing == Direction.WEST) drawQuad(pose, vc,
                                (float)xWest, (float)fMinY, (float)fMinZ, 0.0f, 1.0f,
                                (float)xWest, (float)fMaxY, (float)fMinZ, 0.0f, 0.0f,
                                (float)xWest, (float)fMaxY, (float)fMaxZ, 1.0f, 0.0f,
                                (float)xWest, (float)fMinY, (float)fMaxZ, 1.0f, 1.0f,
                                -1.0f, 0.0f, 0.0f);

                        // East Face (facing +X, observer looking West -X: Left is +Z, Right is -Z)
                        double xEast = pMaxX + faceOffset - camPos.x;
                        if (facing == Direction.EAST) drawQuad(pose, vc,
                                (float)xEast, (float)fMinY, (float)fMaxZ, 0.0f, 1.0f,
                                (float)xEast, (float)fMaxY, (float)fMaxZ, 0.0f, 0.0f,
                                (float)xEast, (float)fMaxY, (float)fMinZ, 1.0f, 0.0f,
                                (float)xEast, (float)fMinY, (float)fMinZ, 1.0f, 1.0f,
                                1.0f, 0.0f, 0.0f);
                    }
                }
        );

        poseStack.popPose();
    }

    /** Upload textures before Minecraft opens the level render pass. */
    public static void prepareHaloOnRenderThread() {
        if (!ShtarOSCinemaManager.hasScreen()) return;
        if (haloAttempted) return;
        haloAttempted = true;
        try {
            Minecraft mc = Minecraft.getInstance();
            DynamicTexture texture = new DynamicTexture("cinema_halo", 64, 64, false);
            NativeImage pixels = texture.getPixels();
            if (pixels == null) { texture.close(); return; }
            IntBuffer colors = pixels.getPixelBytes().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    double u = (x + 0.5) / 64.0;
                    double v = (y + 0.5) / 64.0;
                    double dx = Math.max(0.0, Math.max(0.08 - u, u - 0.92));
                    double dy = Math.max(0.0, Math.max(0.08 - v, v - 0.92));
                    double fade = dx == 0.0 && dy == 0.0 ? 0.0
                            : Math.max(0.0, 1.0 - Math.hypot(dx, dy) / 0.08);
                    int alpha = (int) (92 * fade * fade);
                    colors.put((alpha << 24) | (255 << 16) | (225 << 8) | 190);
                }
            }
            texture.upload();
            mc.getTextureManager().register(HALO_TEXTURE_ID, texture);
            haloInitialized = true;
        } catch (RuntimeException e) {
            System.err.println("[ShtarOS Cinema] Halo texture unavailable: " + e.getMessage());
        }
    }

    private static void submitHalo(LevelRenderContext context, PoseStack stack, Direction facing,
                                   boolean wallAlongX, Vec3 camera,
                                   double minX, double maxX, double minY, double maxY,
                                   double minZ, double maxZ,
                                   double west, double east, double north, double south) {
        context.submitNodeCollector().submitCustomGeometry(stack,
                RenderTypes.entityTranslucentEmissive(HALO_TEXTURE_ID), (pose, vc) -> {
                    float y0 = (float) (minY - camera.y);
                    float y1 = (float) (maxY - camera.y);
                    if (wallAlongX) {
                        float x0 = (float) (minX - camera.x);
                        float x1 = (float) (maxX - camera.x);
                        if (facing == Direction.NORTH) {
                            float z = (float) (north - camera.z);
                            drawQuad(pose, vc, x1,y0,z,0,1, x1,y1,z,0,0,
                                    x0,y1,z,1,0, x0,y0,z,1,1, 0,0,-1);
                        } else if (facing == Direction.SOUTH) {
                            float z = (float) (south - camera.z);
                            drawQuad(pose, vc, x0,y0,z,0,1, x0,y1,z,0,0,
                                    x1,y1,z,1,0, x1,y0,z,1,1, 0,0,1);
                        }
                    } else {
                        float z0 = (float) (minZ - camera.z);
                        float z1 = (float) (maxZ - camera.z);
                        if (facing == Direction.WEST) {
                            float x = (float) (west - camera.x);
                            drawQuad(pose, vc, x,y0,z0,0,1, x,y1,z0,0,0,
                                    x,y1,z1,1,0, x,y0,z1,1,1, -1,0,0);
                        } else if (facing == Direction.EAST) {
                            float x = (float) (east - camera.x);
                            drawQuad(pose, vc, x,y0,z1,0,1, x,y1,z1,0,0,
                                    x,y1,z0,1,0, x,y0,z0,1,1, 1,0,0);
                        }
                    }
                });
    }

    private static void drawQuad(PoseStack.Pose pose, VertexConsumer vc,
                                 float x0, float y0, float z0, float u0, float v0,
                                 float x1, float y1, float z1, float u1, float v1,
                                 float x2, float y2, float z2, float u2, float v2,
                                 float x3, float y3, float z3, float u3, float v3,
                                 float nx, float ny, float nz) {
        int light = 0x00F000F0; // Full bright (illuminated Cinema TV)
        int overlay = OverlayTexture.NO_OVERLAY;

        // The supplied corners run inward; reverse winding so face culling agrees with the outward normal.
        vc.addVertex(pose, x0, y0, z0).setColor(255, 255, 255, 255).setUv(u0, v0).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, x3, y3, z3).setColor(255, 255, 255, 255).setUv(u3, v3).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, x2, y2, z2).setColor(255, 255, 255, 255).setUv(u2, v2).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, x1, y1, z1).setColor(255, 255, 255, 255).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
    }
}
