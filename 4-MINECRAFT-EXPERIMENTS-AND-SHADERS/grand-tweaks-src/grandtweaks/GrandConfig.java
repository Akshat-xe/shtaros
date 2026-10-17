package grandtweaks;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class GrandConfig {
    private static final Path CONFIG_PATH = Paths.get(System.getProperty("user.home"),
            "Library/Application Support/minecraft/config/mcopt.properties");

    public static String profile = "indie";
    public static boolean metal = true;
    public static boolean lod = true;
    public static boolean neon = true;
    public static boolean uocc = true;
    public static boolean tOcc = true;
    public static boolean pace = true;
    public static boolean lightMap = true;
    public static boolean edgeCap = true;
    public static boolean vignette = true;
    public static boolean mesh = true;
    public static boolean bloom = true;

    // Zoom & Motion Enhancements
    public static String zoomMode = "grand"; // "grand" (Virtual Anchor & Chunk Projection) or "normal" (Optical FOV)
    public static boolean smoothZoom = true;
    public static float zoomBaseFactor = 3.5f;
    public static boolean motionSmooth = true; // 60Hz Anti-Tear Camera Smoothing (Genshin Style)
    public static int motionBlur = 1; // 0 = off, 1 = mild (60Hz Anti-Tear), 2 = cinematic

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            save();
            return;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
            props.load(in);
            profile = props.getProperty("profile", "indie");
            metal = Boolean.parseBoolean(props.getProperty("mcopt.metal", "true"));
            lod = Boolean.parseBoolean(props.getProperty("mcopt.lod", "true"));
            neon = "neon".equalsIgnoreCase(props.getProperty("mcopt.lod.nativeNoise", "neon"));
            uocc = Boolean.parseBoolean(props.getProperty("mcopt.own.frag.uocc", "true"));
            tOcc = Boolean.parseBoolean(props.getProperty("mcopt.own.frag.tOcc", "true"));
            pace = Boolean.parseBoolean(props.getProperty("mcopt.metal.paceAdapt", "true"));
            lightMap = Boolean.parseBoolean(props.getProperty("mcopt.alloc.lightMap", "true"));
            edgeCap = Boolean.parseBoolean(props.getProperty("mcopt.edgeCap", "true"));
            vignette = Boolean.parseBoolean(props.getProperty("mcopt.own.vignetteNoop", "true"));
            mesh = Boolean.parseBoolean(props.getProperty("mcopt.own.mesh", "true"));
            bloom = Boolean.parseBoolean(props.getProperty("grand.bloom", "true"));

            zoomMode = props.getProperty("grand.zoom.mode", "grand");
            smoothZoom = Boolean.parseBoolean(props.getProperty("grand.zoom.smooth", "true"));
            zoomBaseFactor = Float.parseFloat(props.getProperty("grand.zoom.base", "3.5"));
            motionSmooth = Boolean.parseBoolean(props.getProperty("grand.motion.smooth", "true"));
            motionBlur = Integer.parseInt(props.getProperty("grand.motion.blur", "1"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            if (!Files.exists(CONFIG_PATH.getParent())) {
                Files.createDirectories(CONFIG_PATH.getParent());
            }
            StringBuilder sb = new StringBuilder();
            sb.append("# Grand Tweaks & mcopt configuration\n");
            sb.append("profile=").append(profile).append("\n");
            sb.append("mcopt.metal=").append(metal).append("\n");
            sb.append("mcopt.lod=").append(lod).append("\n");
            sb.append("mcopt.lod.nativeNoise=").append(neon ? "neon" : "none").append("\n");
            sb.append("mcopt.own.frag.uocc=").append(uocc).append("\n");
            sb.append("mcopt.own.frag.tOcc=").append(tOcc).append("\n");
            sb.append("mcopt.metal.paceAdapt=").append(pace).append("\n");
            sb.append("mcopt.alloc.lightMap=").append(lightMap).append("\n");
            sb.append("mcopt.edgeCap=").append(edgeCap).append("\n");
            sb.append("mcopt.own.vignetteNoop=").append(vignette).append("\n");
            sb.append("mcopt.own.mesh=").append(mesh).append("\n");
            sb.append("grand.bloom=").append(bloom).append("\n");

            sb.append("grand.zoom.mode=").append(zoomMode).append("\n");
            sb.append("grand.zoom.smooth=").append(smoothZoom).append("\n");
            sb.append("grand.zoom.base=").append(zoomBaseFactor).append("\n");
            sb.append("grand.motion.smooth=").append(motionSmooth).append("\n");
            sb.append("grand.motion.blur=").append(motionBlur).append("\n");

            Files.writeString(CONFIG_PATH, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
