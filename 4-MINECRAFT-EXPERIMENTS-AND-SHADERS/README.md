# Minecraft Experiments & Native Metal Optimization Suite

Welcome to the **Minecraft Experiments** research repository. This workspace contains technical analyses, architecture breakdowns, compatibility guides, and benchmark tuning scripts for running **Minecraft: Java Edition** on Apple Silicon using native Metal rendering and optimized Fabric subsystems.

---

## 🚀 Active Launcher Profiles

In your launcher (TLauncher), you now have the following clean profiles:

| Profile in Launcher | Mod Folder Path | Included Optimizations |
| :--- | :--- | :--- |
| **`Release fa-mp-ex-26.3`** | `~/Library/Application Support/minecraft/mods-mcopt-ex` | **The Ultimate Stack**: Native Metal (`mcopt`) + `Grand Tweaks` (Zoom & Motion UI) + `Lithium` (physics) + `FerriteCore` (RAM) + `Krypton` (networking) + Experimental LOD Far Terrain + Native Metal Bloom Shader. |
| **`Release fa-mp-26.3`** | `~/Library/Application Support/minecraft/mods-mcopt` | **Pure mcopt**: Native Metal renderer + `Grand Tweaks` with default vanilla settings. |
| **`Release fa-26.3`** | `~/Library/Application Support/minecraft/mods` | **General Modded**: Standard Fabric where you can drop in any custom mods manually. |
| **`Release 26.3`** | *N/A* | **Vanilla**: 100% stock Minecraft with zero mods. |

---

## 📦 Installed Optimization & Graphics Stack

1. **`grand-tweaks-1.0.0.jar` (Custom Built Native Mod)**
   - **Key `C` Zoom System**: Smooth exponential zoom with dynamic mouse scroll adjustment (1.5x up to 32x) and sensitivity dampening.
   - **Dual Zoom Modes**:
     - **Grand Zoom (Virtual Anchor)**: Projects a virtual camera anchor forward along your look vector (up to 1,000 blocks), forcing `mcopt` and the engine to stream and render distant chunks/LOD in full detail without moving the player.
     - **Normal Zoom**: Optical FOV reduction.
   - **60Hz Anti-Tear / Anti-Judder Smoothing (Genshin Impact Style)**: Eliminates 60Hz display tearing and frame-pacing micro-stutter when running at 400–600 FPS using responsive camera rotational momentum.
   - **In-Game Settings UI**: Access anytime via **Video Settings ➔ [⚙ Grand Tweaks]**.

2. **`Grand-Bloom-Shaders.zip` (Native Metal Core Shaderpack)**
   - 9-tap Gaussian HDR bloom on highlights (sun, lanterns, torches, glowstone, lava).
   - Rich vibrant color grading and contrast enhancement.
   - Horizontal subpixel anti-tear reconstruction for 60Hz displays.
   - 100% compatible with `mcopt`'s Metal backend with zero FPS drop.

3. **`mcopt-0.3.0-alpha.2.jar`**  
   - Apple Metal 3 native rendering backend using Java 25 FFM downcalls and tile-based deferred rendering.

4. **`lithium-fabric-0.26.2+mc26.3.jar`**  
   - World physics, chunk ticking, collision detection, and AI optimization.

5. **`ferritecore-9.0.0-fabric.jar`**  
   - Memory deduplication cutting RAM usage by up to 50%.

6. **`krypton-0.3.2.jar`**  
   - High-performance network stack speeding up chunk transmission.

---

## 📑 Research & Documentation Guides

- [`ZOOM_AND_MOTION_GUIDE.md`](./ZOOM_AND_MOTION_GUIDE.md) — Comprehensive guide to the Key `C` Zoom, Grand vs Normal mode, 60Hz anti-tear fix, and RAM settings.
- [`CHIP_COMPATIBILITY_INTEL_AMD.md`](./CHIP_COMPATIBILITY_INTEL_AMD.md) — Can `mcopt` run on Intel or AMD PC chips? Why is it exclusive to Apple Silicon? What are the PC alternatives?
- [`MCOPT_ARCHITECTURE_AND_EXTENSIONS.md`](./MCOPT_ARCHITECTURE_AND_EXTENSIONS.md) — Deep architectural teardown of `mcopt` (Java 25 FFM, RenderPearl, TBDR tile architecture, and Metal compute).
- [`SHADERS_AND_GRAPHICS_ROADMAP.md`](./SHADERS_AND_GRAPHICS_ROADMAP.md) — Why GLSL shaders (Iris/OptiFine) don't work, how native MSL shaders are being built, and how to get maximum graphics today.
- [`CONTRIBUTORS_AND_COMMUNITY_INTEL.md`](./CONTRIBUTORS_AND_COMMUNITY_INTEL.md) — Who is behind `mcopt`, analysis of pull requests in flight, and active developers.
- [`benchmark_and_tuning.sh`](./benchmark_and_tuning.sh) — Quick terminal diagnostic tool for checking your M5 GPU core configuration and logs.
