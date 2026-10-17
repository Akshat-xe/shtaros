# ShtarOS Cinema: how the whole system works

This guide lives in the **main `ShtarOS` folder** so you can open it in Finder and explain the project without reading Java. It describes the build on 17 October 2026. [README.md](README.md) is the shorter start guide; [repo-map.txt](repo-map.txt) tells you where each part lives. The preserved `saves/checkpoint-1-2026-10-17` is an older recovery copy.

## The one-minute explanation

ShtarOS is a **Minecraft client mod** with two ways to put moving images on a block-built theater:

1. **Browser mode:** Rinku runs an embedded Chromium browser inside Minecraft. ShtarOS draws that browser's live image on a flat surface in the 3D world. Mouse clicks and typing are translated from the player's crosshair into browser input. Browser audio is copied into eight positioned Minecraft OpenAL sound sources at note blocks.
2. **ShtarOS stream mode:** A small Python service on this Mac searches YouTube using `yt-dlp`. FFmpeg decodes the chosen video into raw frames and the audio into eight PCM feeds. The mod receives those streams over `127.0.0.1:25592`, uploads the newest video frame as a texture, and plays each audio feed at its note block.

These are **two separate playback routes**. The embedded browser does not need the Python media service for ordinary browsing. The older `/shtaros play` command uses the service. Starting one of those streams closes the browser so the two video sources do not fight for the same screen.

For the latest measured crackle investigation, code changes, and the exact log checks to run after restarting, see [PLAYBACK_AUDIT.md](PLAYBACK_AUDIT.md).

```text
Browser route: website → Chromium/Rinku → Minecraft texture → world screen
                                └→ decoded PCM → 8 OpenAL sources → note blocks

Stream route:  YouTube → yt-dlp → FFmpeg → local HTTP → Minecraft texture → screen
                                         └→ 8 PCM feeds → OpenAL → note blocks
```

## What “Rinku” is

The name is **Rinku** (the mod, not a Mac app). It embeds the Chromium browser engine in Minecraft and supplies the rendered image, mouse/keyboard methods, and Chromium's native runtime. ShtarOS is the part that decides **where** to draw that image in your world and **how** a player interacts with it. Rinku is supported on macOS 11+ on both Apple Silicon and Intel according to [its official project README](https://github.com/Keksuccino/Rinku). The ShtarOS 2.0.0 release installs Rinku as a required, separately licensed dependency; the older command stream still has a distinct media pipeline.

Rinku/CEF uses **off-screen rendering**: Chromium renders a page without opening a normal macOS browser window. ShtarOS takes the resulting texture and draws it in Minecraft. CEF documents this mode in its [off-screen rendering guide](https://chromiumembedded.github.io/cef/general_usage). Rinku stores browser data such as cookies in its own macOS cache at `~/Library/Application Support/Rinku/cef-cache`, as documented by [Rinku](https://github.com/Keksuccino/Rinku). A site's login may work and persist there, but compatibility depends on that site; login, camera/microphone, protected streaming, and every web app have **not** been verified in this ShtarOS build.

### Browser versus Mac applications

The theater now opens a **general web browser**, starting at DuckDuckGo. It can navigate to a web URL, search the web, or go to YouTube. It currently has **one in-world page** with basic navigation, rather than a full desktop Chrome interface with tabs, download management and extensions. It is **not showing every MacBook app**. Finder, Terminal, TLauncher and native Mac windows are drawn by macOS, outside Chromium; merely embedding Chromium cannot make them appear in the game.

For a pasted link, stand near the intact theater and type `/shtaros https://www.youtube.com/watch?v=...` in chat. `/shtaros url <link>` works the same way. ShtarOS accepts HTTP and HTTPS links, starts the embedded browser if needed, stops any separate daemon video, and loads the page on the physical screen. The website controls whether its player autoplays; if it blocks autoplay, click Play on the screen. Link navigation stays local to your browser session.

**F6 cinema view** places the *camera* directly in front of the panel and aims it at the center. It does not teleport the player or change the surround sound listener. The HUD and hand disappear; mouse clicks and movement keys are held so the view stays steady. Press F6 again, F5, or Esc to return to the original perspective and HUD. Leaving the world or damaging the screen also ends this view.

The display has a soft, full-bright halo around its edges. Browser hover coordinates are sent when they change, and the browser pointer is moved off the page after five seconds without movement. This lets YouTube fade its controls and dark hover vignette; moving the mouse or clicking brings the controls back. The video surface itself remains full bright, and the halo does not alter video pixels.

| You want to show | Current route | Status |
|---|---|---|
| YouTube or another website | Rinku browser on the screen | Implemented; site behavior varies |
| A web class or web app | Enter its URL in the browser | Browser navigation implemented; sign-in and media permissions need site-by-site testing |
| YouTube search/playlist | Website on the theater wall | Site controls remain in Chromium |
| Finder, Terminal or another native Mac app | A future macOS window capture bridge | Not implemented |
| Another player's own synchronized screen | Future server/client networking | Not implemented |

For native Mac windows, a possible future implementation is: select a window with **ScreenCaptureKit**, capture its video/audio, copy frames into a Minecraft texture, and translate deliberate clicks/typing back to that app. Apple's [ScreenCaptureKit sample](https://developer.apple.com/documentation/screencapturekit/capturing-screen-content-in-macos) shows selecting displays, apps and windows and receiving video/audio samples; macOS prompts for Screen Recording permission. Sending input to another app is another part of the design and would need an appropriate macOS input/Accessibility path and explicit permission. This would be a separate native bridge and security model, not a tweak to Rinku.

The Linux project [Waylandcraft](https://github.com/EVV1E/waylandcraft) can run Linux Wayland apps inside Minecraft because it includes a Wayland compositor. Its own requirements say Linux, and its FAQ says multiplayer would require video streaming and a network/input rewrite. macOS native apps use the macOS window system, so that Linux compositor cannot directly host Finder or other Mac apps. The transferable idea is a **window capture → texture → input return** pipeline; the capture implementation must be built for macOS. An Intel Mac could run the same architecture with the matching Intel native binaries; Rinku already lists Intel macOS support.

## Why video can appear on blocks

The polished-blackstone wall is the **physical anchor**, not one tiny television inside each block. ShtarOS records its bounds and draws one large, camera-facing, textured **quad** just in front of the wall. A quad is two triangles, like a flat poster. Its four corners get texture coordinates from `(0,0)` to `(1,1)`, so Minecraft's graphics pipeline fills the rectangle with the browser image or decoded video frame. The texture is made of pixels, but it may contain thousands of pixels across ten Minecraft blocks. Minecraft blocks do not limit it to ten horizontal pixels.

The new automatic setup builds a **10-block-wide × 6-block-high** panel, eight blocks in front of the setup position. Exact 16:9 content occupies about **10 × 5.625 blocks** within it, leaving narrow bars above and below rather than stretching people or text. A sea-lantern border lights the physical area; the quad itself is rendered full-bright. From the setup center, the two front note blocks sit beside the screen at left 8/right 7, four side speakers sit about 10 blocks to either side, and two rear speakers sit nine blocks behind. The sofa remains on a two-block riser. The positions rotate with the direction you face when running `/shtaros setup`.

`/shtaros scan` can bind a nearby existing wall and nearby note blocks instead of generating one. **Repair nearby** on the compact setup panel or inventory fills missing air blocks in a recognized generated theater and rebinds its speakers; it leaves non-air replacement blocks alone. **Delete nearby** removes only matching generated theater blocks, or a jukebox within two blocks. A broken screen closes playback. A broken speaker stops that speaker's sound while intact speakers continue. The eight note blocks are **location markers for OpenAL sound emitters**; they are not producing vanilla note-block tones. The standalone jukebox is a different, music-only route.

## How images travel: the two routes

### Browser route

The browser asks websites for their HTML, JavaScript, images and media using Chromium's own networking. Chromium decodes and lays out the page. Rinku exposes an off-screen texture; ShtarOS draws that texture on the world quad. The website's video player controls playback. The ShtarOS Python service does not decode browser pages. The world browser paints at 960×540 and requests up to 60 FPS; CEF may deliver fewer frames when decoding or rendering cannot keep up. The full browser view caps its texture at 1280×720 even on a high-density display. In that view, a toolbar supplies **Back, Forward, Reload, Home, URL/search and Done**. Home opens DuckDuckGo; the address bar searches the web if the text is not a URL. You can navigate to YouTube, web classes, or other sites, subject to what this Chromium build and each site support.

### Command stream route

The `/shtaros play` command sends a search request to the local service at `127.0.0.1:25592`. This address means **the same Mac**, not an external server. `yt-dlp` finds the media URL; FFmpeg decodes it. The video HTTP response contains raw **RGBA** frames. The mod reads one complete frame, replaces a waiting stale frame if necessary, and uploads the newest frame into a Minecraft `DynamicTexture` on the render thread. It uses a fixed 1280×720 display frame at up to 30 FPS and asks for a source no taller than that output where available. It keeps aspect ratio and pads when the source is a different shape. The older build used 2560×1440 raw frames; that required about **442 MB/s** of local video transfer at 30 FPS. The new 1280×720 frame is about **3.69 MB**, or **111 MB/s** at 30 FPS. These are raw transfer rates *inside the Mac*; internet video remains compressed. Reduced frame size should lower lag, but smoothness still depends on decoding, the game frame rate and the website.

The service's video and audio are separate requests. It tries to hold the first video frame until speaker audio is primed, but it does not share a single master playback clock with YouTube. Long-term exact lip sync is therefore not guaranteed on that route. Browser mode uses Chromium's own media timeline for the webpage, but ShtarOS's speaker buffer can add some sound delay.

The Python service binds only to **127.0.0.1**, so its HTTP port is for this Mac's game process. Browser logins happen inside Rinku/Chromium and use its separate cookie cache; the ShtarOS YouTube search service does not receive browser passwords. The automatic typing probe reports only whether the focused page element is editable, not what you type.

## How sound travels, and why it could crackle

Web audio is delivered to ShtarOS by CEF's **audio callback** as separate floating-point sample arrays for each source channel. The [CEF audio-handler contract](https://github.com/chromiumembedded/cef/blob/master/include/cef_audio_handler.h) also supplies sample rate, channel count, frame count and presentation timestamp. ShtarOS copies the callback's left/right samples immediately, combines them into eight mono 16-bit PCM feeds, then hands each feed to a Minecraft OpenAL source placed at its assigned note block. Your position and orientation change how OpenAL attenuates and pans those sources. The sound is sent to the Mac audio output device through Minecraft/OpenAL; the note blocks are virtual speaker coordinates.

Minecraft's stream channel asks for four buffers at startup. A live test of the previous 20 ms refill build still logged more than 1,100 source recoveries and about 400 empty reads per ten seconds, despite Minecraft running around 100 FPS. This showed that repeated restarts consumed packets as they arrived and never rebuilt an audio reserve. The current code waits for about **260 ms of captured PCM**, uses **4096-byte OpenAL buffers** (about 46 ms at 44.1 kHz), and waits for about **220 ms of buffered PCM** before restarting a drained source. Its incoming queue is capped near 400 ms so stale packets can be discarded if capture outruns playback. Ten-second log counters now show source recoveries, empty reads, dropped packets, and PCM input/output frames. This latest change compiles and passes a focused PCM test, but needs a fresh in-game listen and log after restarting Minecraft. Exact lip sync remains unmeasured because Rinku does not expose a video presentation timestamp here.

**Speaker recovery:** Minecraft's `Channel.updateStream()` refills only buffers that have finished. If a speaker drains completely, calling `play()` again cannot fill an empty source. ShtarOS now reattaches its live stream after a drain. Channel attach, updates, volume changes, and release run on Minecraft's sound executor, matching the game's own sound scheduling. The game log reports how many browser speakers actually attach and whether a drained speaker was refilled. The latest native playback still requires a restart to load this build.

The command stream route differs: FFmpeg produces **one synchronized, interleaved eight-channel, 44.1 kHz PCM stream** from the source stereo audio. It shapes the front, side and rear feeds, and the Java mod separates the interleaved samples into eight OpenAL streams. The browser route currently uses left/right mixing coefficients but no separate frequency filters. Neither route is a Dolby renderer. Both are **stereo-derived eight-position sound**, not discrete 8.1: there is no ninth LFE/subwoofer emitter or independently authored surround channels. A real 8.1 design would need a supported multichannel source, a ninth physical position and a new channel mapping.

## How clicking and typing work

ShtarOS shoots a ray from the player's eyes along the crosshair. When it intersects the screen quad, the hit's position is converted into horizontal/vertical fractions and then browser pixel coordinates. A left click, mouse movement or scroll over the screen is forwarded to Chromium. The screen blocks are protected from accidental left-click mining while the browser is active. Shift + right-click opens the full browser view.

**In-world typing:** Aim at a website search box and left-click it. ShtarOS reads Chromium's text cursor and immediately routes keystrokes to the browser while stopping player movement. It also starts Minecraft's SDL text input explicitly: Rinku starts it automatically only when a normal GUI is open and the mouse is free, while the in-world screen has neither condition. A delayed page focus check runs after every click to handle ordinary input fields when the cursor signal is late. Click outside the text field or look away to walk again. If a site uses a custom editor, press **F8** after clicking; this manual typing mode stays on until **F8** or **Esc**, even if the screen raycast briefly misses. The focus check reports only whether an editable field has focus. In the full browser view, Minecraft's GUI already captures typing.

## Controls you can use now

| Action | Control |
|---|---|
| Build the compact theater | `/shtaros setup` while facing the screen direction in singleplayer |
| Bind an existing wall/speakers | `/shtaros scan` |
| Open compact setup | `I` or `/shtaros gui`; the inventory has the same four actions |
| Open the general browser | `/shtaros browser` or Shift + right-click the screen |
| Browse on the world screen | Look at it; left-click and scroll with the crosshair |
| Type in the world screen | Click a text field; F8 is the manual fallback, Esc exits |
| Go straight to a site | Browser address bar or `/shtaros url https://example.com` |
| Paste a link in chat | Stand within the theater radius and run `/shtaros https://example.com/page` |
| Centered cinema view | **F6** enters or leaves; **F5** or **Esc** returns to normal view |
| Search YouTube on the world screen | `/shtaros search <words>` |
| Play a local decoded stream | `/shtaros play <query>` |
| Repair or remove the generated layout | `I` → **Repair nearby** or **Delete nearby** |
| Check speakers | `/shtaros speakers` |

The `I` panel contains only theater, jukebox, repair, and delete actions. The same four shortcuts appear beside the inventory when there is room. When the browser is active, use the website's player controls or the full browser view.

## Building, installing and recovery

The source lives in `2-FABRIC-MOD-SOURCE`. Run `./build_shtaros.sh` there. It compiles the Java source for **Java 25**, packages `shtaros-2.0.0.jar`, and copies it to `1-READY-TO-PLAY` and `~/Library/Application Support/minecraft/mods`. Restart the Fabric **fa-26.3** game after replacing a JAR; an already running game retains the older loaded classes. Run the release installer to place the matching Rinku and Fabric API JARs. Its Python service is installed in the active game directory's `shtaros` folder and starts automatically when needed. The service needs its Python environment, `yt-dlp` and FFmpeg.

The main logic is grouped like this:

| Piece | Responsibility |
|---|---|
| `ShtarOSCinemaBuilder`, `ShtarOSCinemaManager` | Create/bind/repair theater and manage route state |
| `ShtarOSScreenRenderer` | Draw a full-bright texture quad over the wall |
| `ShtarOSBrowserBridge`, `ShtarOSBrowserScreen`, `ShtarOSScreenRaycast` | Rinku browser, GUI, and 3D interaction |
| `ShtarOSBrowserAudioCapture`, `ShtarOSBrowserSurroundFeed` | Capture browser PCM and make eight feeds |
| `ShtarOSVideoPlayer`, `ShtarOSSurroundFeed` | Receive video/audio from the local service |
| `ShtarOSSurroundManager` | Position eight OpenAL channels and stop broken speakers |
| `ShtarOSSetupScreen`, `ShtarOSInventoryButtons` | Four setup actions in the I panel and inventory |
| `daemon.py` | YouTube search and FFmpeg HTTP endpoints |

`saves/checkpoint-1-2026-10-17` stays untouched. It predates several browser and compact-layout changes. The current JAR is the active build in `1-READY-TO-PLAY` and the Minecraft mods directory.

## Multiplayer: what it would take

The present cinema and browser are **client-side**. Another player joining a server would not automatically see your Chromium pixels or hear your private browser session. Simply placing the same blocks on a multiplayer server is insufficient. To give each player an independent browser, ShtarOS needs server-owned screen IDs, ownership/permissions, a way to assign browsers to screens on each client, and a protocol for input and screen state. To let *other players view the same screen*, the owner would need to send compressed frames and audio over the network; raw 1280×720 frames at 30 FPS are far too large for ordinary Minecraft packets. A shared screen also needs click control rules so visitors cannot accidentally navigate or use someone else's signed-in account. This is a separate project, and it has **not been implemented or tested**.

## What to check next in the game

1. Restart fa-26.3 so the new JAR is loaded. Run `/shtaros setup` at a clear location, facing the intended wall. Check that the panel is 10×6 and the eight note blocks are in their new positions. Existing bound theater walls can still be used with `/shtaros scan`; the new size applies to new Auto setup builds.
2. Right-click the wall, or use `/shtaros browser`. Enter a normal website and a YouTube URL. Check Back/Forward/Home, click a search field on the world screen, and type a phrase including **g**. Walk away or press Esc and confirm movement resumes.
3. Play a browser video. Listen from the sofa, then break **one** note block and confirm that side quiets while the other sources continue. Press `I` → **Repair nearby**, reload the page if needed, and repeat. Compare against `/shtaros play` if desired.
4. If sound still crackles, record which route was active, whether the game FPS dropped, and the time in `~/Library/Application Support/minecraft/logs/latest.log`. If video still lags, distinguish browser mode from the local raw-video route. Those details identify the next specific bottleneck.

The build and focused PCM checks can pass without proving that macOS Chromium, OpenAL and the display are smooth together. That requires the in-game test above.
