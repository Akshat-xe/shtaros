#!/bin/bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_DIR="$DIR/src"
BUILD_DIR="$DIR/build_output"
MINECRAFT_DIR="$HOME/Library/Application Support/minecraft"
LIBS_DIR="$MINECRAFT_DIR/libraries"
CLIENT_JAR="$MINECRAFT_DIR/versions/fa-26.3/fa-26.3.jar"

echo "=========================================================="
echo "🎵 Building ShtarOS for Java 25 (version 69.0)"
echo "=========================================================="

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"
PROCESSED_MODS="$MINECRAFT_DIR/.fabric/processedMods"
CP=$(find "$LIBS_DIR" -name "*.jar" | tr '\n' ':')$(find "$PROCESSED_MODS" -name "*.jar" 2>/dev/null | tr '\n' ':')$CLIENT_JAR


# Find all java sources and compile with --release 25 (class major version 69)
echo "Compiling Java sources with javac --release 25..."
find "$SRC_DIR" -name "*.java" -print0 | xargs -0 javac --release 25 -cp "$CP" -sourcepath "$SRC_DIR" -d "$BUILD_DIR"

cp "$SRC_DIR/fabric.mod.json" "$BUILD_DIR/"
cp "$SRC_DIR/shtaros.mixins.json" "$BUILD_DIR/"

JAR_TARGET="$DIR/shtaros-2.0.0.jar"
echo "Packaging JAR to $JAR_TARGET..."
cd "$BUILD_DIR"
jar -cf "$JAR_TARGET" fabric.mod.json shtaros.mixins.json shtaros

echo "Deploying to Minecraft mods directory..."
mkdir -p "$MINECRAFT_DIR/mods"
cp -v "$JAR_TARGET" "$MINECRAFT_DIR/mods/shtaros-2.0.0.jar"
cp -v "$JAR_TARGET" "$DIR/../1-READY-TO-PLAY/shtaros-2.0.0.jar"

echo ""
echo "=========================================================="
echo "✅ ShtarOS Mod Built & Deployed Successfully!"
echo "JAR Size: $(du -h "$JAR_TARGET" | cut -f1)"
echo "Location: $MINECRAFT_DIR/mods/shtaros-2.0.0.jar"
echo "=========================================================="
