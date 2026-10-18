# 🚀 ShtarOS: 3D Cinema, In-World Browser & Spatial Surround Sound for Minecraft

[![Minecraft](https://img.shields.io/badge/Minecraft-26.3-brightgreen.svg)](https://minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-0.19.5-blue.svg)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-21%2F25-orange.svg)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-LGPL%2FApache-informational.svg)](LICENSE)

**ShtarOS** transforms Minecraft into an interactive multimedia auditorium with a **16:9 physical cinema screen**, a **live 3D Chromium browser** (powered by Brave Search), an **8-channel positional surround sound system**, and a **spatial music jukebox** with up to 96-block range.

---

## ⚡ 1-Command Universal Installation (5 Steps)

Install the entire ShtarOS cinema, browser, 8-channel audio, and complete multiplayer mod bundle with a single terminal command:

1. **Locate Your Minecraft Directory**: Open Minecraft Launcher or TLauncher (or in Minecraft, go to **Options** ➔ **Resource Packs** ➔ **Open Pack Folder**, then go up one folder to the main Minecraft folder).
2. **Open Terminal**: Press `Command + Space` (macOS) or `Ctrl + Alt + T` (Linux), type `Terminal`, and press Enter.
3. **Paste the 1-Line Universal Command**:
   ```bash
   /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Akshat-xe/shtaros/main/install.sh)"
   ```
4. **Automatic Setup**: The installer automatically detects your game directory, registers the **`ShtarOS`** version profile, installs all Fabric runtime libraries, deploys the 34+ performance & multiplayer mods, unpacks shaderpacks, and prepares the background media service without touching your survival worlds or edits.
5. **Launch & Play**: Open your launcher, select the **`ShtarOS`** profile from the versions dropdown, click **Play**, join your world, and press **`U`** or type `/shtaros setup`!

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
| **Quick Remote** | Press **`U`** | Opens the ShtarOS 4-Action Remote (Theater, Jukebox, Repair, Delete). |
| **Auto-Build Cinema** | `/shtaros setup` | Automatically constructs the 16:9 cinema wall, seating riser, 8 speaker blocks, and boots the in-world browser. |
| **Stream Any Video / Webpage** | `/shtaros link <URL>` | Opens and streams any YouTube video, Twitch stream, or website directly on the screen. |
| **Direct 3D Interaction** | Look & **Left-Click** | Click buttons, links, search bars, and video controls in 3D right on the cinema wall. |
| **In-World Keyboard Typing** | Click any input box | Automatically hooks Minecraft text input to type inside the browser. Press **`Esc`** or **`F8`** to toggle. |
| **Full Browser Mode** | `/shtaros browser` | Opens the full-screen Chromium desktop browser interface. |
| **Reconnect Cinema** | `/shtaros scan` | Scans and binds an existing nearby cinema wall and speaker layout. |
| **Inspect Speakers** | `/shtaros speakers` | Displays the physical block coordinates and channel status of all 8 speakers. |
| **Stop Playback** | `/shtaros stop` | Safely stops video streaming, browser audio, and releases speaker channels. |

---

## 🔊 8-Channel Physical Surround Sound

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

- Each speaker operates as an independent directional channel around the room.
- Dynamic audio panning creates true 3D auditorium acoustics for movies, music, and in-game browsing.
- Breaking a speaker block immediately silences its channel; replacing it restores sound instantly.
- Fully compatible with **Sound Physics Remastered** for true block reverberation and wall absorption.

---

## 📋 System Prerequisites

- **Java**: Java 21 or Java 25 (Java 25 recommended for Minecraft 26.3).
- **FFmpeg**: Required for media decoding (`brew install ffmpeg` on macOS, `sudo apt install ffmpeg` on Linux).
- **Python**: Python 3.10+ (used by the local media streaming daemon).

---

## 📁 Repository Structure

- **`install.sh` / `install.command`**: Universal one-command installer.
- **`version/ShtarOS/`**: Custom Fabric 26.3 launcher version definition and TLauncher manifest.
- **`mods/`**: ShtarOS Fabric mod, Rinku Chromium engine, Fabric API, and the complete multiplayer optimization suite.
- **`shaderpacks/`**: Pre-bundled high-performance shaders (Complementary Reimagined, MakeUp UltraFast, Mellow, RenderPearl).
- **`libraries/`**: Standalone Fabric Loader and Mixin runtime libraries.
- **`2-FABRIC-MOD-SOURCE/`**: Java 25 source code for the ShtarOS Fabric mod.
- **`3-STREAMING-DAEMON-BACKEND/`**: Python/Flask media daemon using yt-dlp and FFmpeg.

---

## 👥 Contributors & Credits

- **Akshat-xe**: Creator, Lead Architect & Mod Developer.
- **AGI (Google Antigravity / Gemini)**: AI Co-Developer, Spatial Audio Engineering, System Optimization & Closed-Loop Architecture.
- **ChatGPT (OpenAI)**: Architecture review & code optimization.
- **Rinku**: Chromium Embedded Framework (CEF) wrapper for Fabric by Keksuccino (LGPL-3.0).
- **FabricMC**: Fabric Loader and Fabric API (Apache-2.0).
- **Sodium & Iris**: Next-gen shaders & rendering engine by CaffeineMC & IMS.

---

## 📦 What We Are Giving To You (Extra Mods & Shaders)

When you run the 1-line installer, ShtarOS automatically bundles and installs the complete suite of performance, visual, and multiplayer mods so you and your friends can play together smoothly without manual setup:

### 🎬 Internal Core Systems
- **ShtarOS 2.0 (`shtaros-2.0.0.jar`)**: In-world 16:9 Cinema screen, in-game 3D Chromium browser (Brave Search powered), 8-channel physical note block surround sound, and 96-block spatial Jukebox.
- **Rinku Chromium Engine (`rinku_fabric_3.0.5_MC_26.3.jar`)**: Hardware-accelerated embedded browser for live video, YouTube streaming, and web navigation inside Minecraft.
- **Python Media Daemon**: Local background service delivering high-bitrate synchronized audio and video streams.

### ⚡ Optimization & High-FPS Suite
- **Sodium (`0.9.3-beta.1`)** & **Sodium Extra**: Modern GPU rendering engine delivering 120–140+ FPS.
- **Lithium**: Server and tick physics optimization.
- **FerriteCore**: Heavy memory and RAM usage reducer.
- **ImmediatelyFast**: Immediate-mode HUD, GUI, and font rendering acceleration.
- **Entity Culling**: Skips rendering mobs and entities hidden behind walls or blocks.
- **C2ME (Concurrent Chunk Management)**: Multi-threaded world generation and chunk loading.
- **ScalableLux**: High-performance multi-threaded lighting engine.
- **Krypton**: Network stack optimization for smooth server ping.

### 🌟 Shaders, Visuals & Immersion
- **Iris Shaders (`1.11.7`)**: Full shaderpack support with pre-bundled shaderpacks:
  - *Complementary Reimagined r5.9.3*
  - *MakeUp UltraFast 9.5g*
  - *Mellow v3.4.1a*
  - *RenderPearl v2.8.0-beta.4*
- **LambDynamicLights**: Real-time holding torch and sea lantern dynamic lighting.
- **Enhanced Block Entities**: Smooth rendering for chests, signs, and bells.
- **Full Brightness Toggle**: Clean ambient brightness toggle.

### 🎮 Multiplayer, QoL & World Exploration
- **Dungeons and Taverns**: Spectacular custom world dungeons, taverns, and structures.
- **World We Once Owned (WWOO)**: Vast biome overhauls and natural landscapes.
- **Streams Reflowing**: Flowing river physics and realistic water streams.
- **Sound Physics Remastered**: Block reverberation, sound occlusion, and wall absorption.
- **BetterAdvancements**: Full-screen zoomable advancement tree.
- **MouseTweaks**: Fast inventory sorting and dragging controls.
- **Zoomify**: Smooth customizable cinematic camera zoom.
- **ModMenu**: In-game mod configuration and status dashboard.

---

Developed with passion by **Akshat-xe**. Enjoy the ultimate Minecraft cinema experience!
