# Contributor Intelligence & Community Ecosystem Analysis

This document provides intelligence on the key individuals developing and contributing to `mcopt`, their ongoing work, and pull requests currently shaping the future of native Metal rendering in Minecraft.

---

## 🧑‍💻 The Core Developers & Contributors

### 1. Noah Dunnagan (`noahdunnagan`)
- **Role:** Creator & Principal Architect of `mcopt`.
- **Specialization:** Low-level graphics programming (Metal, Objective-C, C), Java Foreign Function & Memory (FFM / Project Panama), and Minecraft engine reverse engineering.
- **Philosophy:** Focuses on extracting every bit of performance from Apple Silicon unified memory and Tile-Based Deferred Rendering without relying on intermediate translation layers like MoltenVK or legacy OpenGL.

### 2. Jose Manuel Silva (`jose-manuel-silva`)
- **Role:** High-impact optimization engineer & contributor.
- **Key Contributions:**
  - **PR #18 ("Run on macOS 15"):** Reduced platform restrictions by detecting Metal residency sets down to macOS 15, broadening device compatibility.
  - **PR #19 ("Opt-in render scale with MetalFX upscaling"):** Implemented Apple's **MetalFX** spatial scaler, allowing the game to render at 75–85% resolution and upscale dynamically with near-zero visual degradation and +20–35% FPS.
  - **PR #20 ("Translucent Occlusion Culling"):** Implemented hierarchical Z depth culling for translucent passes, culling ~2,100 hidden water/glass units before vertex shading.
  - **PR #21 ("RGSS Sampling Optimization"):** Tuned Rotated Grid Super-Sampling (RGSS) for higher texture fidelity with lower GPU fill costs.

### 3. ssquadteam (`ssquadteam`)
- **Role:** Compatibility and Client Integration.
- **Key Contributions:**
  - **PR #10 ("Lunar Client support, Sodium 0.9.2, Metal settings page, and 26.2 support"):**
    - Patched Lunar Client's hook on `MappableRingBuffer.rotate()` so players on custom third-party clients don't crash.
    - Added an in-game UI page into Video Settings to toggle MetalFX Render Scale and Frame Generation on the fly without editing `.properties` files.
    - Added multi-version build support for Minecraft 26.2.

### 4. ByteV0rtex (`ByteV0rtex`)
- **Role:** Display Latency, VSync & ProMotion Engineer.
- **Key Contributions:**
  - Analyzed and resolved frame presentation stalls (#2, #7, #12).
  - Designed the adaptive queue pacer that aligns frame presentation buffers directly with the display's VSync interval, ensuring that high internal FPS translates into tear-free, low-latency 120Hz ProMotion output.

### 5. Caleb (`caleb@wedflow.com`)
- **Role:** Co-author of `mcopt 0.3.0-alpha.2`.
- Focuses on build systems, Fabric bundle packaging, and regression testing.

---

## 🔍 Key Insights & What to Watch

1. **Watch PR #19 (MetalFX Upscaling):**  
   Once merged or compiled into main releases, MetalFX will bring native Apple super-resolution into Minecraft for the first time.
2. **Watch the Shaderpack Branch:**  
   Keep an eye on commits referencing `mc_blit` and `shaderpack runtime` in `mcmetal.m` — this will be the debut of native `.metal` shaderpacks.
3. **Multi-Mod Ecosystem Alignment:**  
   Pure gameplay and logic mods (like Lithium, FerriteCore, and Krypton) operate completely independently of the graphics pipeline and represent the safest, most performant companion mods to pair with `mcopt`.
