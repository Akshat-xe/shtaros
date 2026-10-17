package grandtweaks.gui;

import grandtweaks.GrandConfig;
import grandtweaks.zoom.GrandZoom;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

public class GrandTweaksScreen extends OptionsSubScreen {

    public GrandTweaksScreen(Screen parent, Options options) {
        super(parent, options, Component.literal("Grand Tweaks: Native Metal & Performance"));
        GrandConfig.load();
    }

    @Override
    public boolean isPauseScreen() {
        // Game continues running live in background while menu is open!
        return false;
    }

    @Override
    public void onClose() {
        GrandConfig.save();
        super.onClose();
    }

    @Override
    protected void addOptions() {
        // ==========================================
        // SECTION 1: 🚀 Apple Metal Graphics Engine
        // ==========================================
        this.list.addHeader(Component.literal("§6🚀 Apple Metal Graphics Engine"));

        Button bMetal = Button.builder(
                Component.literal("Engine: " + (GrandConfig.metal ? "§aApple Metal" : "§cVanilla OpenGL")),
                btn -> {
                    GrandConfig.metal = !GrandConfig.metal;
                    btn.setMessage(Component.literal("Engine: " + (GrandConfig.metal ? "§aApple Metal" : "§cVanilla OpenGL")));
                    GrandConfig.save();
                }
        ).tooltip(Tooltip.create(Component.literal(
                "Replaces standard OpenGL with Apple's native Metal 3 API. Delivers 400-600 FPS on Apple Silicon M-series chips.\n\n" +
                "ON: Maximum frame rates and unified memory optimization.\n" +
                "OFF: Reverts to vanilla OpenGL rendering."
        ))).build();

        Button bProfile = Button.builder(
                Component.literal("Preset: §e" + getProfileDisplayName()),
                btn -> {
                    if ("indie".equals(GrandConfig.profile)) GrandConfig.profile = "recommended";
                    else if ("recommended".equals(GrandConfig.profile)) GrandConfig.profile = "alpha";
                    else if ("alpha".equals(GrandConfig.profile)) GrandConfig.profile = "none";
                    else GrandConfig.profile = "indie";
                    btn.setMessage(Component.literal("Preset: §e" + getProfileDisplayName()));
                    GrandConfig.save();
                }
        ).tooltip(Tooltip.create(Component.literal(
                "Pre-tuned performance presets for Apple Silicon.\n\n" +
                "Indie: Mcopt custom Metal terrain pipeline (Fastest, 500+ FPS).\n" +
                "Balanced: Balanced stability and high FPS.\n" +
                "Experimental: Bleeding-edge features.\n" +
                "None: Disables custom pipeline."
        ))).build();

        this.list.addSmall(bMetal, bProfile);

        Button bPace = Button.builder(
                Component.literal("Frame Smooth Pacing: " + (GrandConfig.pace ? "§aON" : "§cOFF")),
                btn -> {
                    GrandConfig.pace = !GrandConfig.pace;
                    btn.setMessage(Component.literal("Frame Smooth Pacing: " + (GrandConfig.pace ? "§aON" : "§cOFF")));
                    GrandConfig.save();
                }
        ).tooltip(Tooltip.create(Component.literal(
                "Smooths out frame submission intervals to match your MacBook display refresh.\n\n" +
                "ON: Eliminates frame pacing micro-stutter.\n" +
                "OFF: Uncapped raw frame delivery."
        ))).build();

        Button bVignette = Button.builder(
                Component.literal("Screen Vignette: " + (GrandConfig.vignette ? "§aREMOVE" : "§7KEEP")),
                btn -> {
                    GrandConfig.vignette = !GrandConfig.vignette;
                    btn.setMessage(Component.literal("Screen Vignette: " + (GrandConfig.vignette ? "§aREMOVE" : "§7KEEP")));
                    GrandConfig.save();
                }
        ).tooltip(Tooltip.create(Component.literal(
                "Controls the dark vignette shadow around the edges of the screen.\n\n" +
                "REMOVE: Clearer visibility and saves fragment shader GPU cycles.\n" +
                "KEEP: Vanilla darkened screen edges."
        ))).build();

        this.list.addSmall(bPace, bVignette);

        // ==========================================
        // SECTION 2: ⚡ Smart Culling & Optimization
        // ==========================================
        this.list.addHeader(Component.literal("§b⚡ Smart Block Culling & Optimization"));

        Button bUocc = Button.builder(
                Component.literal("Cave & Block Culling: " + (GrandConfig.uocc ? "§aON" : "§cOFF")),
                btn -> {
                    GrandConfig.uocc = !GrandConfig.uocc;
                    btn.setMessage(Component.literal("Cave & Block Culling: " + (GrandConfig.uocc ? "§aON" : "§cOFF")));
                    GrandConfig.save();
                }
        ).tooltip(Tooltip.create(Component.literal(
                "Uses Apple Silicon GPU tile hardware to hide blocks and caves behind mountains that you cannot see.\n\n" +
                "ON: Massive FPS boost by skipping hidden blocks.\n" +
                "OFF: Draws blocks even if blocked from view."
        ))).build();

        Button bWater = Button.builder(
                Component.literal("Underwater Surface Culling: " + (GrandConfig.tOcc ? "§aON" : "§cOFF")),
                btn -> {
                    GrandConfig.tOcc = !GrandConfig.tOcc;
                    btn.setMessage(Component.literal("Underwater Surface Culling: " + (GrandConfig.tOcc ? "§aON" : "§cOFF")));
                    GrandConfig.save();
                }
        ).tooltip(Tooltip.create(Component.literal(
                "Stops drawing water surfaces that are completely submerged or hidden.\n\n" +
                "ON: Saves memory bandwidth near oceans and rivers.\n" +
                "OFF: Renders all underwater surfaces."
        ))).build();

        this.list.addSmall(bUocc, bWater);

        Button bLight = Button.builder(
                Component.literal("Fast Light Memory: " + (GrandConfig.lightMap ? "§aON" : "§cOFF")),
                btn -> {
                    GrandConfig.lightMap = !GrandConfig.lightMap;
                    btn.setMessage(Component.literal("Fast Light Memory: " + (GrandConfig.lightMap ? "§aON" : "§cOFF")));
                    GrandConfig.save();
                }
        ).tooltip(Tooltip.create(Component.literal(
                "Prevents memory allocation spikes when updating torch and sun lighting.\n\n" +
                "ON: Eliminates garbage collection lag spikes from light updates.\n" +
                "OFF: Standard lighting allocations."
        ))).build();

        Button bMesh = Button.builder(
                Component.literal("Hardware Mesher: " + (GrandConfig.mesh ? "§aON" : "§cOFF")),
                btn -> {
                    GrandConfig.mesh = !GrandConfig.mesh;
                    btn.setMessage(Component.literal("Hardware Mesher: " + (GrandConfig.mesh ? "§aON" : "§cOFF")));
                    GrandConfig.save();
                }
        ).tooltip(Tooltip.create(Component.literal(
                "Builds chunk geometry using low-level compiled C code and Apple Silicon NEON instructions.\n\n" +
                "ON: Instant chunk building with zero stutter.\n" +
                "OFF: Standard Java chunk meshing."
        ))).build();

        this.list.addSmall(bLight, bMesh);

        Button bEdge = Button.builder(
                Component.literal("Border Chunk Protection: " + (GrandConfig.edgeCap ? "§aON" : "§cOFF")),
                btn -> {
                    GrandConfig.edgeCap = !GrandConfig.edgeCap;
                    btn.setMessage(Component.literal("Border Chunk Protection: " + (GrandConfig.edgeCap ? "§aON" : "§cOFF")));
                    GrandConfig.save();
                }
        ).tooltip(Tooltip.create(Component.literal(
                "Prevents frame drops when moving near the edge of your loaded render distance.\n\n" +
                "ON: Smooth flight and elytra exploration.\n" +
                "OFF: Uncapped border updates."
        ))).build();

        this.list.addBig(bEdge);

        // ==========================================
        // SECTION 3: 🌄 Distant Horizons & Far Terrain (LOD)
        // ==========================================
        this.list.addHeader(Component.literal("§a🌄 Distant Horizons & Far Terrain (LOD)"));

        Button bLod = Button.builder(
                Component.literal("Far Terrain (LOD): " + (GrandConfig.lod ? "§aON" : "§cOFF")),
                btn -> {
                    GrandConfig.lod = !GrandConfig.lod;
                    btn.setMessage(Component.literal("Far Terrain (LOD): " + (GrandConfig.lod ? "§aON" : "§cOFF")));
                    GrandConfig.save();
                }
        ).tooltip(Tooltip.create(Component.literal(
                "Renders mountains and terrain up to 2,000+ blocks into the distance using lightweight simplified meshes (like Distant Horizons).\n\n" +
                "ON: Infinite panoramic view with 400+ FPS.\n" +
                "OFF: Normal fog cuts off at render distance."
        ))).build();

        Button bNeon = Button.builder(
                Component.literal("Terrain Math: §e" + (GrandConfig.neon ? "Apple Silicon SIMD" : "Default")),
                btn -> {
                    GrandConfig.neon = !GrandConfig.neon;
                    btn.setMessage(Component.literal("Terrain Math: §e" + (GrandConfig.neon ? "Apple Silicon SIMD" : "Default")));
                    GrandConfig.save();
                }
        ).tooltip(Tooltip.create(Component.literal(
                "Hardware acceleration for world generation calculations.\n\n" +
                "Apple Silicon SIMD: Uses Apple M5 128-bit vector execution units for maximum speed.\n" +
                "Default: Standard scalar math."
        ))).build();

        this.list.addSmall(bLod, bNeon);

        // ==========================================
        // SECTION 4: 🎨 Native Shaders & 60Hz Anti-Tear
        // ==========================================
        this.list.addHeader(Component.literal("§d🎨 Native Shaders & 60Hz Anti-Tear"));

        Button bBloom = Button.builder(
                Component.literal("Grand Bloom Shader: " + (GrandConfig.bloom ? "§aENABLED" : "§7DISABLED")),
                btn -> {
                    GrandConfig.bloom = !GrandConfig.bloom;
                    btn.setMessage(Component.literal("Grand Bloom Shader: " + (GrandConfig.bloom ? "§aENABLED" : "§7DISABLED")));
                    GrandConfig.save();
                }
        ).tooltip(Tooltip.create(Component.literal(
                "Native Metal post-processing shader with HDR Gaussian bloom on torches, lanterns, and sun, plus vivid color grading.\n\n" +
                "ENABLED: Cinematic lighting and rich vibrance with zero FPS loss.\n" +
                "DISABLED: Vanilla flat lighting."
        ))).build();

        Button bBlur = Button.builder(
                Component.literal("60Hz Anti-Tear Filter: " + (GrandConfig.motionBlur == 0 ? "§7OFF" : "§aON")),
                btn -> {
                    GrandConfig.motionBlur = GrandConfig.motionBlur == 0 ? 1 : 0;
                    btn.setMessage(Component.literal("60Hz Anti-Tear Filter: " + (GrandConfig.motionBlur == 0 ? "§7OFF" : "§aON")));
                    GrandConfig.save();
                }
        ).tooltip(Tooltip.create(Component.literal(
                "Subpixel horizontal reconstruction filter in the shader blit pass.\n\n" +
                "ON: Eliminates horizontal scanline tearing when spinning quickly on a 60Hz MacBook screen.\n" +
                "OFF: Standard scanline output."
        ))).build();

        this.list.addSmall(bBloom, bBlur);

        // ==========================================
        // SECTION 5: 🎯 Zoom & Distant Chunk Focus
        // ==========================================
        this.list.addHeader(Component.literal("§e🎯 Zoom & Distant Chunk Focus (Key C)"));

        Button bZoomMode = Button.builder(
                Component.literal("Zoom Style: " + ("grand".equalsIgnoreCase(GrandConfig.zoomMode) ? "§bGrand Anchor (LOD)" : "§aStandard Spyglass")),
                btn -> {
                    GrandConfig.zoomMode = "grand".equalsIgnoreCase(GrandConfig.zoomMode) ? "normal" : "grand";
                    btn.setMessage(Component.literal("Zoom Style: " + ("grand".equalsIgnoreCase(GrandConfig.zoomMode) ? "§bGrand Anchor (LOD)" : "§aStandard Spyglass")));
                    GrandConfig.save();
                }
        ).tooltip(Tooltip.create(Component.literal(
                "Choose how zooming behaves when pressing [ C ].\n\n" +
                "Grand Anchor (LOD): Projects your focus point forward up to 1,000 blocks, actively forcing and loading chunks at your crosshair so you can see full blocks and trees!\n" +
                "Standard Spyglass: Smooth optical zoom without projecting forward."
        ))).build();

        Button bSmoothZoom = Button.builder(
                Component.literal("Smooth Zoom Easing: " + (GrandConfig.smoothZoom ? "§aON" : "§cOFF")),
                btn -> {
                    GrandConfig.smoothZoom = !GrandConfig.smoothZoom;
                    btn.setMessage(Component.literal("Smooth Zoom Easing: " + (GrandConfig.smoothZoom ? "§aON" : "§cOFF")));
                    GrandConfig.save();
                }
        ).tooltip(Tooltip.create(Component.literal(
                "Camera smoothly glides into zoom instead of snapping abruptly.\n\n" +
                "ON: Buttery cinematic zoom transition.\n" +
                "OFF: Instant snap zoom."
        ))).build();

        this.list.addSmall(bZoomMode, bSmoothZoom);

        Button bBaseZoom = Button.builder(
                Component.literal("Base Magnification: §e" + GrandConfig.zoomBaseFactor + "x"),
                btn -> {
                    if (GrandConfig.zoomBaseFactor == 2.0f) GrandConfig.zoomBaseFactor = 3.5f;
                    else if (GrandConfig.zoomBaseFactor == 3.5f) GrandConfig.zoomBaseFactor = 5.0f;
                    else if (GrandConfig.zoomBaseFactor == 5.0f) GrandConfig.zoomBaseFactor = 8.0f;
                    else if (GrandConfig.zoomBaseFactor == 8.0f) GrandConfig.zoomBaseFactor = 12.0f;
                    else GrandConfig.zoomBaseFactor = 2.0f;
                    btn.setMessage(Component.literal("Base Magnification: §e" + GrandConfig.zoomBaseFactor + "x"));
                    GrandZoom.setUserTargetZoom(GrandConfig.zoomBaseFactor);
                    GrandConfig.save();
                }
        ).tooltip(Tooltip.create(Component.literal(
                "Base magnification level when pressing [ C ].\n\n" +
                "You can always scroll up/down with mouse wheel while holding [ C ] to zoom anywhere from 1.5x to 32x!"
        ))).build();

        Button bMotionSmooth = Button.builder(
                Component.literal("Smooth Camera (Genshin Style): " + (GrandConfig.motionSmooth ? "§aON" : "§cOFF")),
                btn -> {
                    GrandConfig.motionSmooth = !GrandConfig.motionSmooth;
                    btn.setMessage(Component.literal("Smooth Camera (Genshin Style): " + (GrandConfig.motionSmooth ? "§aON" : "§cOFF")));
                    GrandConfig.save();
                }
        ).tooltip(Tooltip.create(Component.literal(
                "Applies responsive camera rotational momentum (similar to Genshin Impact).\n\n" +
                "ON: Removes jerky mouse delta jumps and screen judder on 60Hz screens.\n" +
                "OFF: Raw mouse angle updates."
        ))).build();

        this.list.addSmall(bBaseZoom, bMotionSmooth);

        Button bHelp = Button.builder(
                Component.literal("💡 Quick Key: Press [ J ] to open tweaks instantly | Hold [ C ] to Zoom"),
                btn -> {}
        ).tooltip(Tooltip.create(Component.literal(
                "Keyboard Shortcuts:\n\n" +
                "• Press [ J ] anywhere in-game to open this menu without pausing!\n" +
                "• Press [ ESC ] to save and return instantly.\n" +
                "• Hold [ C ] and scroll mouse wheel to zoom in/out (1.5x - 32x)."
        ))).build();

        this.list.addBig(bHelp);
    }

    private static String getProfileDisplayName() {
        if ("indie".equalsIgnoreCase(GrandConfig.profile)) return "Indie (Fastest)";
        if ("recommended".equalsIgnoreCase(GrandConfig.profile)) return "Balanced";
        if ("alpha".equalsIgnoreCase(GrandConfig.profile)) return "Experimental";
        return "None (Vanilla)";
    }
}
