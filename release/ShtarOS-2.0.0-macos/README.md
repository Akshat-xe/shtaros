# ShtarOS 2.0.0 — macOS installer

This package installs the ShtarOS Fabric mod, its browser and Fabric API dependencies, and the local media service for jukebox music and theater streaming. It supports Apple Silicon and Intel Macs running **Minecraft Java 26.3, Fabric Loader 0.19.5+, and Java 25**.

## Install

1. Install/select a **Fabric 26.3** Minecraft profile. Quit Minecraft.
2. Install **Python 3.10+** and **FFmpeg** if needed. With Homebrew: `brew install python ffmpeg`.
3. Unzip the package. Double-click `install-shtaros.command`, or run `bash install-shtaros.command` in Terminal from this folder. The installer needs internet access once to install the media service's Python packages. If your launcher uses a custom game directory, run `bash install-shtaros.command '/absolute/path/to/game-directory'`.
4. Launch the Fabric 26.3 profile. Press **I** for setup, or type `/shtaros setup`. Right-click a jukebox for music-only controls. Aim and click on the theater screen to browse. Paste a URL with `/shtaros <url>`.

Existing theater blocks remain in the world after upgrading. To bind an existing screen and note blocks, stand near the seats facing the screen and use `/shtaros scan`; use **I → Repair nearby** if generated blocks are missing.

The installer puts three JARs in the game directory's `mods` folder and the media service in its `shtaros` folder. The service starts automatically when a world opens; no separate server command is needed. On first browser use, the browser dependency may download its native Chromium runtime. This package is for macOS; it has not been tested on Windows or Linux.

If Minecraft reports duplicate mods, remove any older `mineos-*.jar` from `mods`. The installer removes the known `mineos-1.0.0.jar`. Existing worlds and blocks are not deleted.

## What is included

- `mods/shtaros-2.0.0.jar`: our cinema, note-block surround, jukebox, controls, and setup.
- `mods/rinku_fabric_3.0.5_MC_26.3.jar`: the separate Chromium browser library.
- `mods/fabric-api-0.163.0+26.3.jar`: Fabric API.
- `media/daemon.py` and `requirements.txt`: local YouTube search and FFmpeg media service.

The installer does not install Minecraft, Fabric Loader, Java, Python, or FFmpeg. Browser-only playback still needs the browser dependency. Jukebox music and `/shtaros play` need Python and FFmpeg. The sound is eight positional feeds derived from stereo; it is not discrete 8.1 or Dolby Atmos.

**Playback status:** Compilation, the media-service smoke check, and a focused PCM-buffer test passed. A previous browser-audio build still showed frequent source recoveries in the game log. This release changes its startup and recovery buffer reserve, but that change has not yet had a fresh in-game listening test. Restart Minecraft and check `latest.log` for `[ShtarOS Surround] 10s diagnostics` during a 30-second video.

See `THIRD_PARTY_NOTICES.md` and `licenses/` for the third-party licenses. ShtarOS branding applies to the ShtarOS mod; the dependency's own name and attribution remain with its original JAR.
