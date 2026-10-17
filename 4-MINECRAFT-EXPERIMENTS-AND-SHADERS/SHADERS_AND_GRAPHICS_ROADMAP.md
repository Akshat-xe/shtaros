# Shaders and Graphics on Native Metal: Current State & Future Roadmap

## The Core Dilemma: Why Don't Iris / OptiFine Shaders Work?

Standard Minecraft shaderpacks (BSL, Complementary Reimagined, SEUS, AstraLex) are written in **GLSL (OpenGL Shading Language)**. They interact directly with:
- OpenGL Framebuffer Objects (`glGenFramebuffers`, `glBindFramebuffer`)
- OpenGL Texture Targets (`GL_TEXTURE_2D`, `GL_COLOR_ATTACHMENT0..15`)
- OpenGL Uniform Buffers and G-Buffers

Because `mcopt` **bypasses OpenGL entirely** and submits native Apple Metal command encoders, any mod that attempts to execute OpenGL draw calls or bind GL framebuffers will either crash or render invisible black/purple screens.

---

## 🎨 How We Can Get Next-Level Graphics Today

Even without traditional GLSL shader packs, here is how you can maximize graphics and visual fidelity right now on Apple Silicon:

### 1. Enable Native Metal Level-of-Detail (Far Terrain LOD)
- **Flag:** `-Dmcopt.lod=true -Dmcopt.lod.nativeNoise=neon`
- **What it does:** Similar to the famous *Distant Horizons* mod, but running **100% natively in Metal compute shaders**!
- Generates beautiful distant terrain up to 64–128 chunk render distance on the horizon without loading block entities or causing lag.
- Already pre-configured for you in the `Release fa-mp-ex-26.3` profile!

### 2. High-Resolution PBR Texture Packs (LabPBR / Model Packs)
- You can use high-definition resource packs (e.g., 64x, 128x, 256x textures like *Faithful 64x*, *Stay True*, *Dramatic Skys*).
- Resource packs modify textures, skyboxes, and 3D block models directly through vanilla Minecraft assets, which `mcopt`'s native texture manager (`AtlasDouble.java` and `ResidentAnim.java`) handles natively with zero FPS loss.

### 3. Custom Atmosphere & Sky Enhancements
- Mods like **FabricSkyBoxes** (if utilizing vanilla render layers) or custom color maps can alter sunset gradients, cloud depth, and fog colors without relying on OpenGL shader passes.

---

## 🔮 The Future: Native Metal Shading Language (MSL) Shaders

In the official `mcopt` source code, Noah Dunnagan has already laid the groundwork for an official **native Metal shader pipeline**:

In [`mcmetal.h`](mcopt/metal/mcmetal.h):
```c
id<MTLBlitCommandEncoder> mc_blit(Enc *enc); 
// the frame's blit encoder, ending any open render encoder (shaderpack runtime)

id<MTLCommandBuffer> mc_frame_cmd(Enc *enc); 
// the frame's command buffer with no encoder open (native shading pipeline)

void mc_render_resume(Enc *enc, id<MTLComputeCommandEncoder> compute);
```

### The Roadmap:
1. **MSL Shader Engine:** Instead of emulating OpenGL or compiling GLSL via SPIR-V/MoltenVK (which is slow and introduces translation bugs), `mcopt` plans to introduce native **Metal Shading Language (`.metal`) shaderpacks**.
2. **BSL-Level Visual Quality:** In the official README:
   > *"Plans: Our own shaders (BSL-level) as an option"*
3. **Ray-Tracing / Hardware Acceleration:** Modern Apple M3, M4, and M5 chips feature dedicated **Hardware Ray-Tracing accelerators**. A native Metal shaderpack will be able to invoke `MTLAccelerationStructure` directly to achieve real-time ray-traced shadows and water reflections on Apple Silicon with higher performance than any OpenGL shader ever achieved.
