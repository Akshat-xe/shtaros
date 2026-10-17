# ShtarOS playback audit — 17 October 2026

## Which path is active

The latest game log shows Rinku 3.0.5 initializing its macOS ARM64 Chromium runtime, then Chromium starting a **44,100 Hz, two-channel** audio stream. ShtarOS attached that stream to **eight physical note-block sources**. This playback is the embedded browser route:

`website → Rinku/CEF browser → off-screen texture → ShtarOS world quad`

`website audio → CEF planar float callback → ShtarOS stereo-to-eight PCM split → Minecraft sound executor → eight OpenAL sources at note blocks`

The separate `/shtaros play` route uses the local Python/FFmpeg service for video and audio. It closes the browser. A browser-played YouTube video does **not** use that service, so changing its FFmpeg resolution or daemon queue cannot cure this browser stream's crackle. The screen renderer selects the Rinku texture when it is ready; otherwise it displays the daemon texture. It draws one 16:9 rectangle over the ten-by-six block wall. The I panel contains setup, jukebox, repair, and delete actions; it does not decode media.

## What the last live log proves

In `~/Library/Application Support/minecraft/logs/latest.log`, the last test showed:

- 22:11:02: Chromium capture selected; 44.1 kHz stereo PCM packets began; all eight speakers attached.
- 22:11:02, 22:11:12, 22:11:22: the sound executor reported a drained browser speaker and recovery. The recovery log was limited to once every ten seconds, so this is **at least three recoveries**, not a complete count.
- CEF packet age samples were 25, 48, and 71 ms. These are timestamps of received audio packets, not measured speaker output or video presentation time.
- No OpenAL channel exception was logged in that session. Thus the problem observed in this log is queue starvation/recovery, not failure to find the note blocks or initialize Rinku.

The previous implementation refilled OpenAL only from Minecraft's client tick, nominally every 50 ms and later under game stalls. Each per-speaker OpenAL buffer holds about 70 ms at 44.1 kHz. A late tick can drain the queue. On recovery, `Channel.updateStream()` can already queue fresh audio, but the old code also called `attachBufferStream()`, which requests four more buffers. This can unnecessarily grow latency. The repair command also cleared and closed all speaker feeds while Chromium itself stayed open, leaving no automatic way for that running stream to attach again.

### Follow-up live evidence

A later run at **22:20 and 22:21** loaded the first sound-executor change. Minecraft reported roughly **96–128 FPS**, while browser audio reported **1,165 and 1,107 source recoveries per ten seconds**, **419 and 408 empty front-speaker reads**, and **zero dropped packets**. The faster pump alone therefore did **not** solve the crackle. The source was restarting repeatedly without retaining captured audio in reserve. The latest change waits for a 260 ms captured reserve at startup and 220 ms before a drained source restarts, uses 4096-byte buffers, and records PCM input/output frame counts. Those new counters will show whether capture and playback rates match in the next run.

These are code and log findings. The log alone does **not** prove a single cause for browser video frame drops or an exact audio/video offset. CEF's [official off-screen rendering guide](https://github.com/chromiumembedded/cef/blob/master/docs/general_usage.md) says the host receives painted pixel buffers and that off-screen rendering may perform worse than a normal window. Rinku's installed `RinkuBrowser` bytecode shows an asynchronous paint buffer and texture upload path. The browser currently paints a 960×540 view and requests up to 60 FPS, adapting to 30 when Minecraft FPS is low. A requested paint rate is not a guarantee of delivered video frames. The current code has no video presentation timestamp from Rinku, so exact lip-sync correction cannot be justified from the available data.

## What changed

1. Browser speaker refills run every **20 ms** through Minecraft's sound executor, independently of world/render ticks. At most one refill is queued, to prevent task buildup when the sound thread is busy. Daemon-fed playback keeps its existing path. The live follow-up showed this cadence alone was insufficient.
2. Recovery checks OpenAL's actual queued-buffer count after `updateStream()`. It restarts a stopped source with queued PCM, and attaches the stream again only if the OpenAL queue is empty. This follows [OpenAL's queued and processed buffer contract](https://github.com/kcat/openal-soft/blob/master/include/AL/al.h).
3. A speaker repair/rebind gives an already running Chromium stream a fresh feed after the note blocks are registered. Its website stays open.
4. The log now reports `recoveries`, `droppedPackets`, `emptyReads`, `inputFrames`, and `outputFrames` per ten seconds during browser playback. It also logs Minecraft FPS, requested browser paint limit, and browser view size. The frame counters distinguish capture-rate from consumption-rate problems; they do not measure delivered Chromium video frames.
5. Restart now requires about 220 ms of fresh PCM rather than immediately consuming each incoming packet; initial playback requires about 260 ms. Four 4096-byte OpenAL buffers hold roughly 186 ms at 44.1 kHz, leaving a reserve for normal callback variation. A 400 ms incoming bound prevents unbounded latency.

This is **eight spatial sources derived from stereo**, not eight discrete source channels, an LFE channel, or Dolby Atmos. CEF's [audio handler contract](https://github.com/chromiumembedded/cef/blob/master/include/cef_audio_handler.h) supplies decoded PCM packets; ShtarOS copies them within the callback and spatializes them at the registered block coordinates.

## Verification and next live check

Java 25 compilation passed, the `ChannelAccessor` is in the packaged JAR, and the PCM check verified the new 260 ms reserve, bounded recent data, and no synthetic startup silence. The latest game log predates the **reserve change**, so **perceived crackle, frame cadence, and lip sync remain unverified for this final build**.

Restart Fabric `fa-26.3`, play one YouTube video on the theater wall for at least 30 seconds, and check `latest.log` for `[ShtarOS Surround] 10s diagnostics`. If recoveries remain high with `emptyReads` near zero, inspect sound-executor scheduling/OpenAL device load. If `emptyReads` rises, CEF or the website is not supplying PCM steadily. If `droppedPackets` rises, capture is running ahead of output. A browser video FPS trace is still needed before changing screen resolution or claiming exact A/V sync. The selected audio device in the last log was `ROSE Ceramics MK2`; actual headphone/speaker output depends on that device and macOS routing.
