#!/usr/bin/env bash
# ==============================================================================
# ShtarOS All-in-One Installer for Minecraft
# 3D In-World Chromium Browser • 8-Channel Surround Sound • Cinema & Jukebox
# ==============================================================================

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" 2>/dev/null && pwd || pwd)"

# Bootstrap repository files if executed standalone via curl | bash
CLEANUP_TEMP=false
if [[ ! -d "$ROOT/mods" || ! -f "$ROOT/version/ShtarOS/ShtarOS.json.template" ]]; then
    echo "📦 Repository files not found locally. Downloading ShtarOS from GitHub..."
    TEMP_DIR="$(mktemp -d /tmp/shtaros-install-XXXXXX 2>/dev/null || mktemp -d -t shtaros-install)"
    if command -v git >/dev/null 2>&1; then
        git clone --depth=1 https://github.com/Akshat-xe/shtaros.git "$TEMP_DIR"
    else
        echo "   📥 Git not found, downloading archive via curl..."
        curl -fsSL https://github.com/Akshat-xe/shtaros/archive/refs/heads/main.tar.gz | tar -xz -C "$TEMP_DIR" --strip-components=1
    fi
    ROOT="$TEMP_DIR"
    CLEANUP_TEMP=true
    trap 'if [[ "$CLEANUP_TEMP" == "true" ]]; then rm -rf "$TEMP_DIR"; fi' EXIT
fi


echo "============================================================"
echo "    🚀 SHTAROS ALL-IN-ONE MINECRAFT INSTALLER"
echo "    Spatial Jukebox • 8-Channel Surround • 3D Browser"
echo "============================================================"

# 1. Determine Target Minecraft Directory
OS="$(uname -s)"
if [[ -f "./launcher_profiles.json" || -d "./versions" ]]; then
    DEFAULT_GAME_DIR="$(pwd)"
elif [[ "$OS" == "Darwin" ]]; then
    DEFAULT_GAME_DIR="$HOME/Library/Application Support/minecraft"
elif [[ "$OS" == "Linux" ]]; then
    DEFAULT_GAME_DIR="$HOME/.minecraft"
else
    echo "⚠️ Unsupported operating system: $OS. Expected macOS or Linux."
    exit 1
fi

GAME_DIR="${1:-$DEFAULT_GAME_DIR}"
echo "📍 Target Minecraft directory: $GAME_DIR"
mkdir -p "$GAME_DIR"

VERSIONS_DIR="$GAME_DIR/versions"
SHTAROS_VERSION_DIR="$VERSIONS_DIR/ShtarOS"
SHTAROS_MODS_DIR="$GAME_DIR/shtaros-mods"
MEDIA_DIR="$GAME_DIR/shtaros"
LIBS_DIR="$GAME_DIR/libraries"
mkdir -p "$SHTAROS_VERSION_DIR" "$SHTAROS_MODS_DIR" "$MEDIA_DIR" "$LIBS_DIR"

# 2. Check Java
echo "🔍 Checking Java runtime..."
JAVA_BIN="$(command -v java || true)"
if [[ -n "$JAVA_BIN" ]]; then
    JAVA_VER="$("$JAVA_BIN" -version 2>&1 | head -n 1 || true)"
    echo "   Found Java: $JAVA_VER"
else
    echo "   ⚠️ Java not found in PATH. Make sure Java 21 or Java 25 is installed for Minecraft 26.3."
fi

# 3. Check FFmpeg
echo "🔍 Checking FFmpeg for media streaming..."
FFMPEG="$(command -v ffmpeg || true)"
for candidate in "$HOME/.local/bin/ffmpeg" /opt/homebrew/bin/ffmpeg /usr/local/bin/ffmpeg /usr/bin/ffmpeg; do
    if [[ -z "$FFMPEG" && -x "$candidate" ]]; then FFMPEG="$candidate"; fi
done

if [[ -n "$FFMPEG" ]]; then
    echo "   Found FFmpeg: $FFMPEG"
else
    echo "   ⚠️ FFmpeg is not found. Jukebox music and /shtaros play require FFmpeg."
    if [[ "$OS" == "Darwin" ]]; then
        echo "   👉 Install it using Homebrew: brew install ffmpeg"
    else
        echo "   👉 Install it using apt/pacman: sudo apt install ffmpeg"
    fi
fi

# 4. Set up Python Streaming Daemon Backend
echo "🐍 Setting up Python media streaming service in $MEDIA_DIR..."
DAEMON_SRC="$ROOT/3-STREAMING-DAEMON-BACKEND"
if [[ ! -f "$DAEMON_SRC/daemon.py" ]]; then
    # Fallback to release/media
    DAEMON_SRC="$ROOT/release/ShtarOS-2.0.0-macos/media"
fi

cp "$DAEMON_SRC/daemon.py" "$MEDIA_DIR/"
cp "$DAEMON_SRC/requirements.txt" "$MEDIA_DIR/"

VENV="$MEDIA_DIR/venv"
if [[ ! -x "$VENV/bin/python3" ]] || ! "$VENV/bin/python3" -c 'import flask, yt_dlp, PIL, flask_cors' >/dev/null 2>&1; then
    echo "   Configuring Python virtual environment..."
    PYTHON=""
    for candidate in /opt/homebrew/bin/python3 /usr/local/bin/python3 /usr/bin/python3 "$(command -v python3 || true)"; do
        if [[ -x "$candidate" ]] && "$candidate" -c 'import sys; assert sys.version_info >= (3, 10)' 2>/dev/null; then
            PYTHON="$candidate"
            break
        fi
    done
    if [[ -z "$PYTHON" ]]; then
        echo "   ⚠️ Python 3.10+ required for media daemon. Please install Python 3.10 or newer."
    else
        echo "   Using Python: $PYTHON"
        "$PYTHON" -m venv "$VENV"
        "$VENV/bin/python3" -m pip install --disable-pip-version-check --quiet -r "$MEDIA_DIR/requirements.txt"
    fi
fi

if [[ -x "$VENV/bin/python3" ]]; then
    "$VENV/bin/python3" -m py_compile "$MEDIA_DIR/daemon.py"
    echo "   ✅ Python media service ready."
fi

# 5. Pre-install Fabric Loader & Mixin Libraries
echo "📦 Installing Fabric Loader and Mixin runtime libraries..."
if [[ -d "$ROOT/libraries/net/fabricmc" ]]; then
    mkdir -p "$LIBS_DIR/net/fabricmc"
    cp -R "$ROOT/libraries/net/fabricmc"/* "$LIBS_DIR/net/fabricmc/"
    echo "   ✅ Fabric runtime libraries installed."
fi

# 6. Install Dedicated ShtarOS Version (Zero-Conflict Profile)
echo "🎮 Installing ShtarOS version profile in $SHTAROS_VERSION_DIR..."

python3 - <<PY
import json
import os

template_file = "$ROOT/version/ShtarOS/ShtarOS.json.template"
target_file = "$SHTAROS_VERSION_DIR/ShtarOS.json"
mods_folder = "$SHTAROS_MODS_DIR"

with open(template_file, "r", encoding="utf-8") as f:
    content = f.read()

# Replace __MODS_FOLDER__ with the actual absolute path of isolated shtaros-mods
content = content.replace("__MODS_FOLDER__", mods_folder)

with open(target_file, "w", encoding="utf-8") as f:
    f.write(content)

print("   Generated ShtarOS.json with isolated modsFolder.")
PY

cp "$ROOT/version/ShtarOS/TLauncherAdditional.json" "$SHTAROS_VERSION_DIR/TLauncherAdditional.json"

# 7. Check or Download Minecraft 26.3 Client JAR
TARGET_JAR="$SHTAROS_VERSION_DIR/ShtarOS.jar"
EXPECTED_SHA1="e877b6a07acd633fb3bb475002175cec036e7b87"

check_sha1() {
    local file="$1"
    python3 -c "import hashlib, sys; f=open(sys.argv[1],'rb'); h=hashlib.sha1(f.read()).hexdigest(); sys.exit(0 if h == '$EXPECTED_SHA1' else 1)" "$file" 2>/dev/null
}

JAR_FOUND=false
if [[ -f "$TARGET_JAR" ]] && check_sha1 "$TARGET_JAR"; then
    echo "   ✅ ShtarOS.jar already verified."
    JAR_FOUND=true
else
    # Look for existing 26.3 jars on the system
    for candidate in \
        "$VERSIONS_DIR/fa-26.3/fa-26.3.jar" \
        "$VERSIONS_DIR/26.3/26.3.jar" \
        "$VERSIONS_DIR/.backup_fabric-loader-0.19.5-26.3/fabric-loader-0.19.5-26.3.jar"; do
        if [[ -f "$candidate" ]] && check_sha1 "$candidate"; then
            echo "   Found matching client jar at $candidate. Copying to ShtarOS.jar..."
            cp "$candidate" "$TARGET_JAR"
            JAR_FOUND=true
            break
        fi
    done
fi

if [[ "$JAR_FOUND" != "true" ]]; then
    echo "   📥 Downloading Minecraft 26.3 client jar from official Mojang CDN..."
    CLIENT_URL="https://piston-data.mojang.com/v1/objects/e877b6a07acd633fb3bb475002175cec036e7b87/client.jar"
    curl -L --progress-bar "$CLIENT_URL" -o "$TARGET_JAR"
    if check_sha1 "$TARGET_JAR"; then
        echo "   ✅ Client jar verified successfully."
    else
        echo "   ⚠️ Downloaded client jar hash mismatch! Launcher will retry download on startup."
    fi
fi

# 8. Safely Register "ShtarOS" in launcher_profiles.json
echo "📝 Registering 'ShtarOS' profile in launcher_profiles.json..."
PROFILES_FILE="$GAME_DIR/launcher_profiles.json"
python3 - <<PY
import json
import os
import shutil
from datetime import datetime, timezone

profiles_file = "$PROFILES_FILE"

data = {}
if os.path.exists(profiles_file):
    try:
        shutil.copy2(profiles_file, profiles_file + ".bak")
        with open(profiles_file, "r", encoding="utf-8") as f:
            data = json.load(f)
    except Exception as e:
        print("   Notice: Creating clean launcher_profiles structure:", e)
        data = {}

if "profiles" not in data or not isinstance(data["profiles"], dict):
    data["profiles"] = {}

now_iso = datetime.now(timezone.utc).astimezone().isoformat()

# Add or update shtaros profile without touching any other profile
data["profiles"]["shtaros"] = {
    "name": "ShtarOS",
    "type": "custom",
    "created": data["profiles"].get("shtaros", {}).get("created", now_iso),
    "lastUsed": now_iso,
    "lastVersionId": "ShtarOS",
    "icon": "Crafting_Table"
}

with open(profiles_file, "w", encoding="utf-8") as f:
    json.dump(data, f, indent=2)

print("   ✅ Registered 'ShtarOS' in launcher_profiles.json safely.")
PY

# 9. Install ShtarOS & Optimization Mods into Isolated & Main Folders
echo "⚡ Installing ShtarOS & complete mod bundle into $SHTAROS_MODS_DIR and $GAME_DIR/mods..."
MODS_DIR="$GAME_DIR/mods"
mkdir -p "$SHTAROS_MODS_DIR" "$MODS_DIR"

# Clean legacy or conflicting duplicate jars
rm -f "$MODS_DIR/mineos-1.0.0.jar" "$SHTAROS_MODS_DIR/mineos-1.0.0.jar"
rm -f "$MODS_DIR/sodium-fabric-0.9.2+mc26.3.jar" "$SHTAROS_MODS_DIR/sodium-fabric-0.9.2+mc26.3.jar"

cp "$ROOT/mods"/*.jar "$SHTAROS_MODS_DIR/"
cp "$ROOT/mods"/*.jar "$MODS_DIR/"
echo "   ✅ Installed $(ls -1 "$SHTAROS_MODS_DIR"/*.jar | wc -l | tr -d ' ') mods successfully."

# 10. Install Shaderpacks
if [[ -d "$ROOT/shaderpacks" ]]; then
    mkdir -p "$GAME_DIR/shaderpacks"
    cp "$ROOT/shaderpacks"/*.zip "$GAME_DIR/shaderpacks/" 2>/dev/null || true
    echo "   ✅ Installed shaderpacks into $GAME_DIR/shaderpacks"
fi

echo ""
echo "============================================================"
echo "🎉 SHTAROS INSTALLATION COMPLETED SUCCESSFULLY!"
echo "============================================================"
echo ""
echo "🎮 HOW TO PLAY:"
echo "1. Open TLauncher or official Minecraft Launcher."
echo "2. Look at your versions dropdown list:"
echo "   ➡️ Select version: 'ShtarOS'"
echo "3. Click 'Enter the game' or 'Play'."
echo ""
echo "✨ IN-GAME CONTROLS:"
echo "• Press 'I' on your keyboard to open the ShtarOS Quick Setup Remote."
echo "• Use '/shtaros setup' to auto-build the 16:9 cinema & 8-speaker theater."
echo "• Use '/shtaros link <url>' to open any webpage or YouTube video."
echo "• Use '/shtaros scan' to reconnect an existing cinema setup."
echo "============================================================"
