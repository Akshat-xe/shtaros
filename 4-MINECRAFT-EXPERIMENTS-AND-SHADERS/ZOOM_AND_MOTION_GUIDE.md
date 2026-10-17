# Grand Tweaks & Performance Guide

## 1. Quick Access to Grand Tweaks

You no longer have to navigate deep menus. You can open Grand Tweaks in two instant ways:
1. **Direct Hotkey [ J ]**: Press **`J`** anytime while walking or flying in the world. The tweaks menu opens instantly without pausing your game!
2. **Main Pause Screen**: Press **`ESC`** to bring up the game pause menu. You will see a dedicated **[ ⚙ Grand Tweaks & Performance ]** button right below **Save and Quit to Title**!
3. **Video Settings**: Still available via **Options ➔ Video Settings ➔ [⚙ Grand Tweaks]** (top right).
4. **Auto-Save & Close**: Press **`ESC`** from the tweaks screen to immediately auto-save all changes and return to the game.

---

## 2. Live Background & Single Scrollable Page (No Tabs!)

- **No Confusing Tabs**: All settings are placed on a **single unified scrollable page**. You can freely scroll up and down using the mouse wheel without switching tabs or losing your spot.
- **Live Background**: Opening Grand Tweaks **never freezes the game**. The sky, clouds, and world continue running live in the background while you tweak settings.
- **Real-Time Updates**: Every button you click updates in real-time and saves to `mcopt.properties` immediately.

---

## 3. Simple, Non-Technical Setting Names & Tooltips

Hover your mouse cursor over **ANY** button to read a simple, beginner-friendly explanation box:

### 🚀 Section 1: Apple Metal Graphics Engine
- **Engine: Apple Metal [ON / OFF]**
  - *What it does*: Replaces standard OpenGL with Apple's native Metal 3 API, pushing 400–600 FPS on Apple Silicon chips.
  - *ON*: Maximum frame rates and unified memory optimization.
  - *OFF*: Reverts to vanilla OpenGL rendering.
- **Preset: [Indie (Fastest) / Balanced / Experimental]**
  - *What it does*: Pre-tuned performance presets. "Indie" enables mcopt's custom terrain pipeline for maximum FPS.
- **Frame Smooth Pacing: [ON / OFF]**
  - *What it does*: Smooths out frame delivery intervals to match your MacBook display refresh. Eliminates micro-stutter.
- **Screen Vignette: [REMOVE / KEEP]**
  - *What it does*: Removes the dark border shadow around the screen for clearer visibility and extra FPS.

### ⚡ Section 2: Smart Block Culling & Optimization
- **Cave & Block Culling: [ON / OFF]**
  - *What it does*: Uses Apple Silicon GPU hardware to hide blocks and caves behind mountains that you cannot see. Huge FPS boost.
- **Underwater Surface Culling: [ON / OFF]**
  - *What it does*: Stops drawing water surfaces that are completely submerged or covered.
- **Fast Light Memory: [ON / OFF]**
  - *What it does*: Eliminates RAM allocation spikes when updating torch and sun lighting, stopping GC lag spikes.
- **Hardware Mesher: [ON / OFF]**
  - *What it does*: Builds chunk geometry using compiled C code and Apple Silicon NEON SIMD instructions for instant chunk loading.
- **Border Chunk Protection: [ON / OFF]**
  - *What it does*: Prevents frame drops when moving fast near the border of your render distance.

### 🌄 Section 3: Distant Horizons & Far Terrain (LOD)
- **Far Terrain (LOD): [ON / OFF]**
  - *What it does*: Renders mountains and terrain up to 2,000+ blocks into the distance using lightweight simplified meshes.
- **Terrain Math: [Apple Silicon SIMD / Default]**
  - *What it does*: Uses Apple M5 128-bit vector execution hardware for world generation math.

### 🎨 Section 4: Native Shaders & 60Hz Anti-Tear
- **Grand Bloom Shader: [ENABLED / DISABLED]**
  - *What it does*: Native Metal post-processing shader with HDR Gaussian bloom on torches, lanterns, and sun, plus vivid color grading.
- **60Hz Anti-Tear Filter: [ON / OFF]**
  - *What it does*: Softens horizontal scanline tearing when spinning quickly on a 60Hz MacBook screen without blurring text.

### 🎯 Section 5: Zoom & Distant Chunk Focus (Key C)
- **Zoom Style: [Grand Anchor (LOD) / Standard Spyglass]**
  - *Grand Anchor*: Projects your focus point forward up to 1,000 blocks and **actively loads and streams distant chunks** directly at your crosshair so you can see full blocks, trees, and terrain!
  - *Standard Spyglass*: Smooth optical zoom without projecting forward.
- **Smooth Zoom Easing: [ON / OFF]**
  - *What it does*: Camera smoothly glides into zoom instead of snapping abruptly.
- **Base Magnification: [3.5x]**
  - *What it does*: Starting zoom level when pressing [ C ]. Scroll mouse wheel up/down while holding [ C ] to scale anywhere between 1.5x and 32x!
- **Smooth Camera (Genshin Style): [ON / OFF]**
  - *What it does*: Applies responsive camera rotational momentum to remove jerky mouse delta jumps and screen judder.

---

## 4. Zoom Chunk Loading: How It Works

When you aim at a distant mountain (even 300 to 1,000 blocks away) and hold **`C`**:
1. Grand Zoom calculates your crosshair focal point in 3D space.
2. The integrated server creates active chunk tickets and loads the 3x3 chunks at that distant location.
3. The server streams full chunk packets (`ClientboundLevelChunkWithLightPacket`) directly to your client connection.
4. Your client builds full 3D meshes for that mountain so you see the real trees, blocks, and lighting.
5. When you release **`C`**, the camera glides back home, and temporary chunk tickets are cleanly released.
