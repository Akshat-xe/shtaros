#!/bin/bash
# ==============================================================================
# Minecraft Experiments: Apple Silicon Diagnostics & Tuning Helper
# ==============================================================================

echo "=========================================================="
echo "🍎 Minecraft Apple Silicon Metal Diagnostics"
echo "=========================================================="

echo -n "Checking OS version: "
sw_vers -productVersion

echo -n "Checking Apple Chip & Architecture: "
uname -m
ioreg -rd1 -c IOPlatformExpertDevice | grep "model" | awk '{print $3}'

echo -n "Checking GPU Core Count: "
ioreg -rd1 -c AGXAccelerator | grep "gpu-core-count" | awk -F'=' '{print $2}' | tr -d ' '

echo -n "Checking Active Java Runtime: "
"$HOME/Library/Application Support/minecraft/runtime/java-runtime-epsilon/osx-arm64/java-runtime-epsilon/jre.bundle/Contents/Home/bin/java" -version 2>&1 | head -n 1

echo ""
echo "=========================================================="
echo "📁 Active Experimental Mod Stack (mods-mcopt-ex):"
echo "=========================================================="
ls -lh "$HOME/Library/Application Support/minecraft/mods-mcopt-ex"

echo ""
echo "=========================================================="
echo "🎯 Experimental Launcher Flags:"
echo "=========================================================="
echo "• -Dfabric.modsFolder=.../mods-mcopt-ex"
echo "• -Dmcopt.lod=true                  (Native Metal Far Terrain LOD)"
echo "• -Dmcopt.lod.nativeNoise=neon      (ARM NEON SIMD 3D Noise)"
echo "• -Dmcopt.own.frag.uocc=true        (GPU Depth Pyramid Occlusion Culling)"
echo "• -Dmcopt.metal.paceAdapt=true      (Adaptive Frame Pacing for 120Hz)"
echo "• -Dmcopt.alloc.lightMap=true       (Zero-Allocation Lighting Engine)"
echo "• -Dmcopt.own.frag.tOcc=true        (Translucent Unit Culling)"
echo "=========================================================="
echo "Ready to launch! Open TLauncher and select: Release fa-mp-ex-26.3"
