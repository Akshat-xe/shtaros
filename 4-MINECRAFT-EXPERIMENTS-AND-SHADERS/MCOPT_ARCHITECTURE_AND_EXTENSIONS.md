# Deep Dive: mcopt Architecture & Optimization Pipeline

`mcopt` is one of the most technically ambitious Minecraft performance mods written in recent years. Instead of patching OpenGL calls like OptiFine or Sodium, it completely replaces the rendering pipeline with a native Apple Metal 3 engine.

Here is how the entire pipeline functions from Java down to Apple Silicon silicon.

---

## 1. The Rendering Abstraction: Mojang `RenderPearl`
In modern Minecraft versions (26.x), Mojang began decoupling Minecraft from legacy Blaze3D OpenGL by introducing `com.mojang.renderpearl.api.device.GpuBackend` and `PreferredGraphicsApi`.

`mcopt` uses a Fabric Mixin (`PreferredGraphicsApiMixin`) to inject `MetalBackend` at Index 0:
```java
@Inject(method = "getBackendsToTry", at = @At("RETURN"), cancellable = true)
private void mcopt$metalFirst(CallbackInfoReturnable<GpuBackend[]> cir) {
    GpuBackend[] fallbacks = cir.getReturnValue();
    GpuBackend[] backends = new GpuBackend[fallbacks.length + 1];
    backends[0] = new MetalBackend(); // Injects Metal as preferred backend
    System.arraycopy(fallbacks, 0, backends, 1, fallbacks.length);
    cir.setReturnValue(backends);
}
```
Minecraft now attempts to initialize Metal first. If Metal fails or is turned off (`-Dmcopt.metal=false`), it seamlessly falls back to OpenGL or Vulkan.

---

## 2. Zero-Overhead Downcalls: Java 25 Foreign Function & Memory (FFM)
Historically, Java talked to C/C++ via **JNI (Java Native Interface)**, which suffers from:
- Context switching overhead
- Garbage collector pinning overhead
- Boxing/unboxing penalties

`mcopt` leverages Java 22+ / Java 25's **Foreign Function & Memory API (Project Panama)**:
```java
private static final Linker LINKER = Linker.nativeLinker();
private static final SymbolLookup LIB = SymbolLookup.libraryLookup(extract(), Arena.global());

// Critical downcall: skips JVM thread-state transition!
LINKER.downcallHandle(symbol, descriptor, Linker.Option.critical(false));
```
With `Linker.Option.critical(false)`, the JVM does not transition the thread state or interact with the safepoint mechanism. The invocation compiles directly into ARM64 register moves (`x0`, `x1`, `x2`) and a `bl` branch instruction. A native draw call costs **less than 3 nanoseconds**.

---

## 3. Tile-Based Deferred Rendering (TBDR) Optimization
Apple Silicon GPUs divide the screen into 16x16 or 32x32 pixel tiles. Each tile is processed in ultra-fast on-chip SRAM before being committed to main RAM.

- **Standard OpenGL on Mac:** Forces frequent tile flushes and memory reloads between passes, bottlenecking unified memory bandwidth.
- **mcopt's Enc Structure:**
  ```c
  typedef struct {
      Ctx *ctx;
      id<MTLCommandBuffer> cmd;
      id<MTLRenderCommandEncoder> render;
      id<MTLTexture> colors[MAX_COLORS];
      id<MTLTexture> depth;
      ...
  } Enc;
  ```
  `mcopt` keeps `render` open across multiple passes if attachments don't change. It uses **LoadActionLoad** and **StoreActionDontCare** for intermediate attachments, saving gigabytes of memory traffic per second.

---

## 4. Native Chunk Meshing & Occlusion Culling
`mcopt` contains a custom chunk mesher written in C and Metal compute shaders:
- **`mcown.m`**: Custom mesher decoding quad records in 40-byte compact structures.
- **`lodnoise.c`**: SIMD-accelerated 3D noise generation using ARM NEON vector instructions (`vld1q_f32`, `vaddq_f32`).
- **GPU Depth Pyramid Hierarchical Z (Hi-Z)**:
  Builds a downscaled depth pyramid from earlier opaque passes, testing later translucent or entity bounds against the pyramid before executing fragment shaders (`mcopt.own.frag.uocc=true`).

---

## 5. Experimental Enhancements in Flight (PRs)
- **MetalFX Spatial & Temporal Upscaling (PR #19)**:
  Renders at 75% or 85% resolution and uses Apple's Neural Engine / MetalFX hardware scaler to reconstruct 4K native output with +20% to +35% FPS gains.
- **Translucent Occlusion Culling (PR #20)**:
  Eliminates ~2100 hidden water/glass units per frame on land before vertex processing, delivering up to +18% FPS.
- **Adaptive Frame Pacing (`mcopt.metal.paceAdapt=true`)**:
  Matches frame present timing precisely to Apple ProMotion 120Hz display refresh intervals, eliminating micro-stutters.
