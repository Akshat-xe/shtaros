# 🚀 ShtarOS: 3D Cinema, In-World Browser & Spatial Surround Sound for Minecraft

[![Minecraft](https://img.shields.io/badge/Minecraft-26.3-brightgreen.svg)](https://minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-0.19.5-blue.svg)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-21%2F25-orange.svg)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-LGPL%2FApache-informational.svg)](LICENSE)

**ShtarOS** transforms Minecraft into an interactive multimedia auditorium with a **16:9 physical cinema screen**, a **live 3D Chromium browser**, an **8-channel positional surround sound system**, and a **spatial music jukebox** with up to 96-block range.

---

## ⚡ 1-Command Universal Installation

Anyone can install the complete ShtarOS system with a single command. It creates a dedicated, isolated version profile called **`ShtarOS`** in your launcher so **nothing conflicts** with your existing Minecraft versions, mods, or profiles.

### Option A: Direct Command-Line Download & Install (Instant 1-Liner)

Open Terminal and paste this single command to download and install automatically:

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Akshat-xe/shtaros/main/install.sh)"
```

### Option B: Clone & Install via Terminal

```bash
git clone https://github.com/Akshat-xe/shtaros.git && cd shtaros && ./install.sh
```

### Option C: Via Double-Click (macOS Finder)

1. Download or clone this repository.
2. Open the folder in Finder.
3. Double-click **`install.command`**.

---

## 🎮 How to Play

1. Open **TLauncher** or the official **Minecraft Launcher**.
2. In the version dropdown list, select **`ShtarOS`**.
3. Click **Enter the game** (or **Play**).
4. Enter any singleplayer or multiplayer world.

---

## ✨ Features & Controls

| Action | Control / Command | Description |
|---|---|---|
| **Quick Remote** | Press **`I`** | Opens the ShtarOS 4-Action Remote (Theater, Jukebox, Repair, Delete). |
| **Auto-Build Cinema** | `/shtaros setup` | Automatically constructs the 16:9 cinema wall, seating riser, and 8 speaker blocks. |
| **Stream Any Video / Webpage** | `/shtaros link <URL>` | Opens and streams any YouTube video, Twitch stream, or website directly on the screen. |
| **Direct 3D Interaction** | Look & **Left-Click** | Click buttons, links, search bars, and video controls in 3D right on the cinema wall. |
| **In-World Keyboard Typing** | Click any input box | Automatically hooks Minecraft text input to type inside the browser. Press **`Esc`** or **`F8`** to toggle. |
| **Full Browser Mode** | `/shtaros browser` | Opens the full-screen Chromium desktop browser interface. |
| **Reconnect Cinema** | `/shtaros scan` | Scans and binds an existing nearby cinema wall and speaker layout. |
| **Inspect Speakers** | `/shtaros speakers` | Displays the physical block coordinates and channel status of all 8 speakers. |
| **Stop Playback** | `/shtaros stop` | Safely stops video streaming, browser audio, and releases OpenAL channels. |

---

## 🔊 8-Channel Physical Surround Sound Architecture

ShtarOS doesn't just play sound through your headset; it streams synchronized audio to **eight physical note blocks** placed around your seating area:

```
                  [ 16:9 CINEMA SCREEN ]
                     (Sea Lantern Rim)
         [FRONT_LEFT]                [FRONT_RIGHT]

  [SIDE_LEFT]                                [SIDE_RIGHT]
                     🛋️ Center Sofa
  [REAR_LEFT]                                [REAR_RIGHT]
                 [BACK_LEFT]  [BACK_RIGHT]
```

- Each speaker operates as an independent **OpenAL 3D sound source**.
- Audio feeds are dynamically adjusted for distance, stereo separation, and directional acoustics.
- Breaking a speaker block immediately silences its channel; replacing it restores the channel.
- Compatible with **Sound Physics Remastered** for true block reverb and wall attenuation.

---

## 🛡️ Zero-Conflict Architecture

- **Dedicated Version**: ShtarOS registers as a unique version profile (`versions/ShtarOS`) in TLauncher and Minecraft Launcher.
- **Isolated Mod Directory**: Uses `-Dfabric.modsFolder` pointing to `shtaros-mods/`. Your existing mods in `.minecraft/mods` will **never** be overwritten, deleted, or conflicted.
- **Pre-Bundled Performance Stack**: Includes optimized versions of **Sodium**, **Lithium**, **FerriteCore**, **Krypton**, and **ModMenu** to maintain 120–140+ FPS while streaming video.
- **Self-Healing Backend**: The Python media daemon (`shtaros/venv`) starts automatically in the background when the world opens and safely shuts down when Minecraft exits.

---

## 📋 System Prerequisites

- **Java**: Java 21 or Java 25 (Java 25 recommended for Minecraft 26.3 bytecode).
- **FFmpeg**: Required for media decoding (`brew install ffmpeg` on macOS, `sudo apt install ffmpeg` on Linux).
- **Python**: Python 3.10+ (used by the local media streaming daemon).

---

## 📁 Repository Structure

- **`install.sh` / `install.command`**: Universal one-click installer.
- **`version/ShtarOS/`**: Custom Fabric 26.3 launcher version definition and TLauncher manifest.
- **`mods/`**: ShtarOS Fabric mod, Rinku Chromium engine, Fabric API, and optimization suite.
- **`libraries/`**: Standalone Fabric Loader and Mixin libraries.
- **`2-FABRIC-MOD-SOURCE/`**: Java 25 source code for the ShtarOS Fabric mod.
- **`3-STREAMING-DAEMON-BACKEND/`**: Python/Flask media daemon using yt-dlp and FFmpeg.
- **`4-MINECRAFT-EXPERIMENTS-AND-SHADERS/`**: Custom shaders and visual enhancements.
- **`release/`**: Standalone offline zip distribution package.

---

## 📜 Credits & Third-Party Notices

- **ShtarOS**: Custom Cinema, Surround Audio, and Jukebox mod by Slayer / Akshat-xe.
- **Rinku**: Chromium Embedded Framework (CEF) wrapper for Fabric by Keksuccino (LGPL licensed).
- **FabricMC**: Fabric Loader and Fabric API (Apache-2.0).
- **Sodium**: Performance rendering engine by JellySquid / CaffeineMC (LGPL-3.0).

---

Developed with passion by **Akshat-xe**. Enjoy the ultimate Minecraft cinema experience!
