# Browser Research & Reverse Engineering for ShtarOS on macOS

## Reverse Engineering Summary

### 1. EVV1E / Waylandcraft Architecture & Limitations on macOS
- **Repository**: [EVV1E/waylandcraft](https://github.com/EVV1E/waylandcraft)
- **Concept**: Embeds a desktop environment directly inside the Minecraft 3D world on physical monitors.
- **Under the Hood**:
  - Employs **Smithay** (a Rust library) to instantiate a native Linux Wayland display server (`wl_display`).
  - Linux apps (Firefox, Chromium, mpv, terminals) run natively as Wayland clients connecting via Unix sockets.
  - Rust captures the surface buffers (`dmabuf` / `wl_shm`) and uploads them to OpenGL textures inside Minecraft.
  - Interacts with surfaces in-world via 3D raycasting from player eye vectors into 2D plane coordinates, plus a detached cursor mode.
- **Why Waylandcraft Cannot Run on macOS**:
  - macOS does **NOT** use Wayland; it uses Apple's proprietary **Quartz / WindowServer / Metal** display compositor.
  - Native macOS applications (Safari, Chrome, Spotify) are Cocoa/AppKit applications rendering to WindowServer and cannot connect to a Wayland socket.
  - EVV1E explicitly notes in the release documentation that the macOS fallback is disabled. Porting the Wayland compositor to macOS would require a complete rewrite using macOS private APIs or `ScreenCaptureKit`, which still does not provide native in-world window input redirection.

### 2. The Native macOS Translation: Rinku + JCEF (Chromium Embedded Framework)
- **Engine**: [Rinku 3.0.5 for Fabric 26.3](https://github.com/Keksuccino/Rinku)
- **Architecture**:
  - Embeds **JCEF (Java Chromium Embedded Framework)** compiled natively for macOS (supporting both Apple Silicon `MACOS_ARM64` and Intel `MACOS_AMD64`).
  - Runs a headless, off-screen rendered (OSR) Chromium instance in-process.
  - Fully supports modern web standards: HTML5 video, YouTube, YouTube Music, hardware-accelerated video decoding, cookies, and JavaScript.
  - ShtarOS registers a JCEF audio handler for its browser, copies decoded PCM, and routes a stereo-derived mix to eight physical OpenAL speaker sources. This browser audio path still needs an in-game listening check after restart.
  - `RinkuBrowser` provides direct off-screen OpenGL textures, plus methods for mouse motion, press, release, scroll wheel, and keyboard events.

### 3. In-World Direct 3D Interaction ("No Remote Needed — Just Click As You Play")
To bring the magic of Waylandcraft's in-world interaction to macOS without a remote or modal GUI dialog, ShtarOS now features a full 3D Screen Raycasting and Interaction Pipeline:

1. **Precision 3D Raycasting Engine (`ShtarOSScreenRaycast.java`)**:
   - Computes ray-plane intersections from the player's camera position along `player.getViewVector(1.0f)`.
   - Supports all screen orientations (North, South, East, West) and matches the 16:9 cinematic letterboxing of `ShtarOSScreenRenderer.java`.
   - Translates 3D hit point `(X, Y, Z)` into normalized `(u, v)` coordinates, and scales into the active Chromium pixel space `(px, py)`.

2. **In-World Crosshair Hover**:
   - `MinecraftTickMixin` evaluates screen intersection on every client tick.
   - When aiming at the cinema screen, sends `sendMouseMove(px, py)` to Chromium in real-time.
   - Video thumbnails, play/pause controls, scrubber bars, and links react and light up to your crosshair hover in 3D.

3. **In-World Direct Clicking & Scrolling (`MouseHandlerMixin.java` & `MultiPlayerGameModeMixin.java`)**:
   - **Left-Click (Button 1)**: Directly clicks the YouTube video, button, or link you are aiming at and cancels weapon swings / block punching.
   - **Scroll Wheel**: Scrolling the mouse wheel while looking at the cinema screen scrolls the YouTube page up and down, canceling hotbar cycling.
   - **Right-Click (Button 3)**:
     - Normal Right-Click: Activates/starts the browser on the cinema screen if closed.
     - **Shift + Right-Click**: Opens the full-screen `ShtarOSBrowserScreen` GUI with address bar, search box, and Back/Forward/Reload buttons for typing long queries.
   - **Screen Block Protection**: Left-clicking to interact will never break or destroy screen blocks.

4. **Typing status (`ShtarOSMod.java`)**:
   - The old crosshair instruction banner is gone. A small hint appears only while in-world typing is active.

5. **Chat / Console Quick Navigation**:
   - `/shtaros url <link>`: Instantly loads any URL on the cinema wall.
   - `/shtaros search <query>`: Instantly searches YouTube on the cinema wall.
   - `/shtaros reload`, `/shtaros back`, `/shtaros forward`: Quick browser controls.

### 4. Why in-world typing failed and how it is handled now

Inspection of the **installed** Rinku 3.0.5 JAR (`RinkuBrowser.setFocus`) showed that it starts Minecraft's SDL text input only when a normal `Screen` is open and the mouse is not grabbed. The in-world cinema has no open `Screen` and Minecraft grabs the mouse, so Rinku stops text input there. ShtarOS was forwarding key down events to Chromium, but Minecraft did not receive the SDL text events that make printable characters. That explains why player movement could freeze while letters never appeared on the webpage.

`ShtarOSBrowserBridge.setTypingMode` now calls Minecraft's `TextInputManager.startTextInput(browser)` for in-world editable focus and `stopTextInput(browser)` when leaving it. `focus()` re-arms text input after Rinku's own focus logic. `KeyboardHandlerMixin` forwards both physical key events and typed Unicode characters to Chromium. After every left click, ShtarOS also asks the focused page element whether it is editable; F8 remains a manual fallback for custom web editors. This is based on the local installed binary's behavior, not an assumption from another Rinku version. The [Rinku project](https://github.com/Keksuccino/Rinku) and [CEF off-screen input model](https://github.com/chromiumembedded/cef/blob/master/docs/general_usage.md) explain the browser side.

The `I` key now opens only four world setup actions. Fabric's screen API adds the same actions beside the vanilla inventory when space allows; narrow windows show a single setup button. Theater video and website controls remain on the wall, and jukebox playback remains in its separate music screen. Runtime verification in a restarted Minecraft client is still required.

---

## Verification & Deployment Checklist
1. Both `shtaros-2.0.0.jar` and `rinku_fabric_3.0.5_MC_26.3.jar` are deployed to `~/Library/Application Support/minecraft/mods/`.
2. Mod compiles cleanly with `javac --release 25`.
3. Stand in front of the screen and right-click to open the general browser, or run `/shtaros search lofi hip hop` for YouTube.
4. Aim your crosshair at any video thumbnail on the wall and left-click to play!
