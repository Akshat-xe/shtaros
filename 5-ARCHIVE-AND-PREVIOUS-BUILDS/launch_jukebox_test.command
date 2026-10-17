#!/bin/bash
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "=========================================================="
echo "🎵 Launching MineOS Spatial 3D Jukebox Environment"
echo "=========================================================="

# 1. Start daemon if not running
"$DIR/daemon/start_daemon.sh"

echo ""
echo "Launching TLauncher..."
java -jar /Applications/TLauncher.jar &

echo ""
echo "=========================================================="
echo "🎮 Ready! In TLauncher:"
echo " 1. Select profile: Release fa-mp-ex-26.3 or Release fa-26.3"
echo " 2. Click 'Enter the game'"
echo " 3. Enter any world (Creative or Survival)"
echo " 4. Place a Jukebox block and Right-Click it!"
echo " 5. Type any song (e.g. 'c418 sweden') and click 'Stream'!"
echo "=========================================================="
