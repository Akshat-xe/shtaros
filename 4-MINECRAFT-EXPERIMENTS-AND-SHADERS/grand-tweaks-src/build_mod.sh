#!/bin/bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="$DIR/build_output"
MINECRAFT_DIR="$HOME/Library/Application Support/minecraft"
LIBS_DIR="$MINECRAFT_DIR/libraries"
CLIENT_JAR="$MINECRAFT_DIR/versions/fa-26.3/fa-26.3.jar"

echo "=========================================================="
echo "🎵 Building MineOS Spatial Jukebox & Grand Tweaks Mod"
echo "=========================================================="

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

# Assemble full classpath
CP=$(find "$LIBS_DIR" -name "*.jar" | tr '\n' ':')$CLIENT_JAR

# Find all java sources
SOURCES=$(find "$DIR" -name "*.java")

echo "Compiling $(echo "$SOURCES" | wc -w | tr -d ' ') Java source files with javac..."
javac -cp "$CP" -sourcepath "$DIR" -d "$BUILD_DIR" $SOURCES

# Copy metadata assets into build output
cp "$DIR/fabric.mod.json" "$BUILD_DIR/"
cp "$DIR/grandtweaks.mixins.json" "$BUILD_DIR/"

# Package into JAR
JAR_TARGET="$DIR/../grand-tweaks-1.0.0.jar"
echo "Packaging JAR to $JAR_TARGET..."
cd "$BUILD_DIR"
jar -cf "$JAR_TARGET" fabric.mod.json grandtweaks.mixins.json grandtweaks

# Copy to all active mod profile folders
echo "Deploying to active launcher mod folders..."
mkdir -p "$MINECRAFT_DIR/mods"
mkdir -p "$MINECRAFT_DIR/mods-mcopt"
mkdir -p "$MINECRAFT_DIR/mods-mcopt-ex"

cp -v "$JAR_TARGET" "$MINECRAFT_DIR/mods/"
cp -v "$JAR_TARGET" "$MINECRAFT_DIR/mods-mcopt/"
cp -v "$JAR_TARGET" "$MINECRAFT_DIR/mods-mcopt-ex/"

# Also create standalone MineOS Jukebox project folder and copy jar there
STANDALONE_DIR="$HOME/Desktop/MineOS/mineos-jukebox"
mkdir -p "$STANDALONE_DIR"
cp -v "$JAR_TARGET" "$STANDALONE_DIR/mineos-jukebox-1.0.0.jar"

echo ""
echo "=========================================================="
echo "✅ Build & Deployment Complete!"
echo "JAR Size: $(du -h "$JAR_TARGET" | cut -f1)"
echo "Target Locations:"
echo " • $JAR_TARGET"
echo " • $MINECRAFT_DIR/mods/grand-tweaks-1.0.0.jar"
echo " • $MINECRAFT_DIR/mods-mcopt/grand-tweaks-1.0.0.jar"
echo " • $MINECRAFT_DIR/mods-mcopt-ex/grand-tweaks-1.0.0.jar"
echo " • $STANDALONE_DIR/mineos-jukebox-1.0.0.jar"
echo "=========================================================="
