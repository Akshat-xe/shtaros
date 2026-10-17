package shtaros.cinema;

import shtaros.browser.ShtarOSBrowserBridge;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/** A local viewing camera. The player and the audio listener never move. */
public final class ShtarOSCinemaView {
    private static boolean active;
    private static boolean hudHiddenBefore;
    private static CameraType cameraTypeBefore;
    private static ClientLevel level;
    private static Vec3 cameraPosition;
    private static float yaw;
    private static float pitch;

    private ShtarOSCinemaView() {}

    public static boolean isActive() { return active; }
    public static Vec3 cameraPosition() { return cameraPosition; }
    public static float yaw() { return yaw; }
    public static float pitch() { return pitch; }

    public static boolean isInsideTheater(LocalPlayer player) {
        if (player == null || !ShtarOSCinemaManager.isScreenIntact()) return false;
        BlockPos min = ShtarOSCinemaManager.getScreenMin();
        BlockPos max = ShtarOSCinemaManager.getScreenMax();
        if (min == null || max == null) return false;
        double x = (min.getX() + max.getX() + 1.0) * 0.5;
        double y = (min.getY() + max.getY() + 1.0) * 0.5;
        double z = (min.getZ() + max.getZ() + 1.0) * 0.5;
        double radius = Math.max(18.0, ShtarOSCinemaManager.getScreenDiagonal() + 4.0);
        double dx = player.getX() - x;
        double dz = player.getZ() - z;
        return dx * dx + dz * dz <= radius * radius && Math.abs(player.getY() - y) <= 12.0;
    }

    public static void toggle() {
        if (active) exit();
        else enter();
    }

    public static void enter() {
        Minecraft mc = Minecraft.getInstance();
        if (active || mc.player == null || mc.gui.screen() != null) return;
        if (!isInsideTheater(mc.player)) {
            mc.player.sendSystemMessage(Component.literal("§c[ShtarOS] Stand near an intact theater screen to use F6."));
            return;
        }
        BlockPos min = ShtarOSCinemaManager.getScreenMin();
        BlockPos max = ShtarOSCinemaManager.getScreenMax();
        Direction facing = ShtarOSCinemaManager.getScreenFacing();
        if (facing == null) return;

        double x = (min.getX() + max.getX() + 1.0) * 0.5;
        double y = (min.getY() + max.getY() + 1.0) * 0.5;
        double z = (min.getZ() + max.getZ() + 1.0) * 0.5;
        double width = Math.max(max.getX() - min.getX() + 1, max.getZ() - min.getZ() + 1);
        double distance = Math.max(7.0, width / 1.2);
        // Place the view on the audience side of the display, aimed at its center.
        cameraPosition = new Vec3(x + facing.getStepX() * distance, y,
                z + facing.getStepZ() * distance);
        double dx = x - cameraPosition.x;
        double dz = z - cameraPosition.z;
        yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        pitch = 0.0f;

        hudHiddenBefore = mc.gui.hud.isHidden();
        cameraTypeBefore = mc.options.getCameraType();
        level = mc.level;
        ShtarOSBrowserBridge.setTypingMode(false);
        KeyMapping.releaseAll();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (!hudHiddenBefore) mc.gui.hud.toggle();
        active = true;
    }

    public static void exit() {
        if (!active) return;
        active = false;
        Minecraft mc = Minecraft.getInstance();
        if (cameraTypeBefore != null) mc.options.setCameraType(cameraTypeBefore);
        if (mc.gui.hud.isHidden() != hudHiddenBefore) mc.gui.hud.toggle();
        KeyMapping.setAll();
        level = null;
        cameraPosition = null;
    }

    public static void tick() {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level || mc.player == null || mc.gui.screen() != null
                || !ShtarOSCinemaManager.isScreenIntact()) exit();
    }
}
