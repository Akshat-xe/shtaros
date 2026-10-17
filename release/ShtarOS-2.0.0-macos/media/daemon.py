#!/usr/bin/env python3
"""
ShtarOS Spatial 3D Cinema & Surround Audio Daemon
Port: localhost:25592
Provides:
  - GET /               : Health check
  - GET /search?q=...   : Metadata search via yt-dlp
  - GET /stream/<id>    : Transcoded 44.1kHz 16-bit Mono/Stereo PCM stream for OpenAL
  - GET /video/stream/<id> : Pacing-controlled raw RGBA video for Minecraft Cinema Screen
  - GET /boot_frame     : "AKSHAT CREATION" boot frame animation JPEG
  - GET /standby_frame  : Standby / TV Off frame JPEG
"""

import sys
import os
import io
import shutil
import subprocess
import logging
import time
from flask import Flask, request, jsonify, Response
import re
from urllib.parse import urlsplit
import yt_dlp
from PIL import Image, ImageDraw, ImageFont, ImageFilter

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("ShtarOSDaemon")

app = Flask(__name__)
MEDIA_ERROR_LOG = os.path.join(os.path.dirname(os.path.abspath(__file__)), "media-errors.log")

VIDEO_ID = re.compile(r"^[A-Za-z0-9_-]{11}$")


def video_target(video_id):
    if not VIDEO_ID.fullmatch(video_id):
        return None
    return f"https://www.youtube.com/watch?v={video_id}"


def start_media_processes(yt_cmd, ff_cmd, buffer_size):
    """Launch one download and one decoder, retaining useful media errors."""
    with open(MEDIA_ERROR_LOG, "ab", buffering=0) as errors:
        p_yt = subprocess.Popen(yt_cmd, stdout=subprocess.PIPE, stderr=errors)
        try:
            p_ff = subprocess.Popen(ff_cmd, stdin=p_yt.stdout, stdout=subprocess.PIPE,
                                    stderr=errors, bufsize=buffer_size)
        except Exception:
            p_yt.terminate()
            raise
        finally:
            p_yt.stdout.close()
    return p_yt, p_ff

# Resolve ffmpeg executable
FFMPEG_PATHS = [
    os.path.join(os.path.expanduser("~"), ".local", "bin", "ffmpeg"),
    "/opt/homebrew/bin/ffmpeg",
    "/usr/local/bin/ffmpeg",
    shutil.which("ffmpeg")
]
FFMPEG_BIN = next((p for p in FFMPEG_PATHS if p and os.path.isfile(p) and os.access(p, os.X_OK)), "ffmpeg")
logger.info(f"Using ffmpeg at: {FFMPEG_BIN}")

# Use the imported module through this interpreter. Virtualenv entry-point
# scripts contain absolute shebangs that break when the project is moved.
YT_DLP_CMD = [sys.executable, "-m", "yt_dlp"]
logger.info(f"Using yt-dlp through: {sys.executable}")


def format_duration(seconds):
    if not seconds:
        return "0:00"
    try:
        sec = int(seconds)
        m, s = divmod(sec, 60)
        h, m = divmod(m, 60)
        if h > 0:
            return f"{h}:{m:02d}:{s:02d}"
        return f"{m}:{s:02d}"
    except Exception:
        return "0:00"


@app.route("/", methods=["GET"])
def health():
    return jsonify({
        "status": "online",
        "service": "shtaros-cinema-daemon",
        "version": "2.3.0",
        "ffmpeg": FFMPEG_BIN
    })


@app.route("/search", methods=["GET"])
def search():
    query = request.args.get("q", "").strip()
    if not query:
        return jsonify([])
    if len(query) > 300:
        return jsonify({"error": "query too long"}), 400
    if query.startswith(("http://", "https://")):
        host = urlsplit(query).hostname
        if host not in ("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be"):
            return jsonify({"error": "only YouTube links are supported"}), 400

    logger.info(f"Searching for: {query}")
    ydl_opts = {
        "extract_flat": True,
        "quiet": True,
        "skip_download": True,
        "default_search": "ytsearch8"
    }

    try:
        search_query = f"ytsearch8:{query}" if not (query.startswith("http://") or query.startswith("https://")) else query
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            res = ydl.extract_info(search_query, download=False)
            entries = res.get("entries", [res]) if "entries" in res else [res]
            
            results = []
            for entry in entries:
                if not entry:
                    continue
                video_id = entry.get("id") or entry.get("url")
                if not video_id:
                    continue
                
                title = entry.get("title", "Unknown Title")
                duration = entry.get("duration", 0)
                uploader = entry.get("uploader") or entry.get("channel") or entry.get("artist") or "YouTube"
                thumbnail = entry.get("thumbnail") or ""
                
                results.append({
                    "id": str(video_id),
                    "title": str(title),
                    "author": str(uploader),
                    "duration": duration,
                    "duration_str": format_duration(duration),
                    "thumbnail": str(thumbnail)
                })

            logger.info(f"Found {len(results)} results for query: {query}")
            return jsonify(results)
    except Exception as e:
        logger.error(f"Search failed for '{query}': {e}", exc_info=True)
        return jsonify({"error": str(e)}), 500


@app.route("/boot_frame", methods=["GET"])
def boot_frame():
    """Generates the Akshat Creation boot screen frame."""
    progress = int(request.args.get("progress", "100"))
    progress = max(0, min(100, progress))
    w = min(2560, max(320, int(request.args.get("w", "1280"))))
    h = min(1440, max(180, int(request.args.get("h", "720"))))

    img = Image.new("RGB", (w, h), color=(248, 249, 252))
    draw = ImageDraw.Draw(img)

    # Stylish outer borders
    draw.rectangle([(10, 10), (w - 11, h - 11)], outline=(210, 215, 225), width=3)
    draw.rectangle([(18, 18), (w - 19, h - 19)], outline=(230, 235, 245), width=2)

    # Accent top bar
    draw.rectangle([(22, 22), (w - 23, 30)], fill=(220, 38, 38))

    # Main Brand Text
    cx = w // 2
    draw.text((cx, 140), "A K S H A T   C R E A T I O N", fill=(20, 25, 40), anchor="mm")
    draw.text((cx, 190), "ShtarOS Cinema Display • Spatial Surround", fill=(80, 90, 110), anchor="mm")

    # Separator line
    draw.line([(w // 4, 230), (3 * w // 4, 230)], fill=(200, 205, 220), width=2)

    # Status / Info
    status_text = "System Initialized • Ready" if progress >= 100 else f"Booting High-Definition Core... {progress}%"
    draw.text((cx, 275), status_text, fill=(50, 60, 80), anchor="mm")

    # Progress bar outline & fill
    bar_x1, bar_y1, bar_x2, bar_y2 = cx - 240, 320, cx + 240, 345
    draw.rectangle([(bar_x1, bar_y1), (bar_x2, bar_y2)], outline=(180, 185, 200), width=2, fill=(235, 238, 245))
    fill_width = int((bar_x2 - bar_x1 - 6) * (progress / 100.0))
    if fill_width > 0:
        draw.rectangle([(bar_x1 + 3, bar_y1 + 3), (bar_x1 + 3 + fill_width, bar_y2 - 3)], fill=(37, 99, 235))

    # Footer
    draw.text((cx, 420), "Press 'I' or type /shtaros to open Remote", fill=(130, 140, 155), anchor="mm")
    draw.text((cx, 460), "Spatial OpenAL Audio • 44.1kHz • Source FPS Video", fill=(160, 170, 185), anchor="mm")

    return Response(img.convert("RGBA").tobytes(), mimetype="application/octet-stream")


@app.route("/standby_frame", methods=["GET"])
def standby_frame():
    return cinema_card(False)


@app.route("/loading_frame", methods=["GET"])
def loading_frame():
    return cinema_card(True)


def cinema_card(loading):
    """A readable 16:9 standby/loading card for both world panel and remote."""
    w = min(2560, max(320, int(request.args.get("w", "1280"))))
    h = min(1440, max(180, int(request.args.get("h", "720"))))
    img = Image.new("RGBA", (w, h), (8, 15, 28, 255))
    glow = Image.new("RGBA", (w, h))
    ImageDraw.Draw(glow).ellipse((-w // 5, -h // 3, w + w // 5, h + h // 3), fill=(12, 35, 58, 120))
    img = Image.alpha_composite(img, glow.filter(ImageFilter.GaussianBlur(max(18, w // 12)))).convert("RGB")
    draw = ImageDraw.Draw(img)
    cx = w // 2
    try:
        font = ImageFont.truetype("/System/Library/Fonts/Supplemental/Arial Bold.ttf", max(24, w // 21))
        small = ImageFont.truetype("/System/Library/Fonts/Supplemental/Arial.ttf", max(14, w // 42))
    except OSError:
        font = small = ImageFont.load_default()
    margin = max(14, w // 40)
    draw.rounded_rectangle((margin, margin, w - margin, h - margin), radius=18,
                           outline=(42, 93, 128), width=max(2, w // 360))
    draw.rectangle((margin * 2, h // 5, w - margin * 2, h // 5 + 3), fill=(44, 188, 214))
    draw.text((cx, h * .46), "SHTAROS CINEMA", font=font, fill=(235, 249, 255), anchor="mm")
    if loading:
        draw.text((cx, h * .58), "Loading stream...", font=small, fill=(92, 220, 232), anchor="mm")
        draw.rounded_rectangle((w * .36, h * .77, w * .64, h * .79), radius=5, fill=(34, 57, 76))
        draw.rounded_rectangle((w * .36, h * .77, w * .52, h * .79), radius=5, fill=(44, 188, 214))

    return Response(img.convert("RGBA").tobytes(), mimetype="application/octet-stream")


@app.route("/stream/<video_id>", methods=["GET"])
def stream_audio(video_id):
    target = video_target(video_id)
    if target is None:
        return jsonify({"error": "invalid video id"}), 400
    channels = int(request.args.get("channels", "1"))
    if channels not in [1, 2]:
        channels = 1

    logger.info(f"Piped Audio stream requested for video_id: {video_id} (channels={channels})")

    yt_cmd = [
        *YT_DLP_CMD,
        "-f", "bestaudio/best",
        "-o", "-",
        "--quiet",
        "--no-warnings",
        target
    ]

    ff_cmd = [
        FFMPEG_BIN,
        "-loglevel", "error",
        "-nostdin",
        "-i", "pipe:0",
        "-vn",
        "-f", "s16le",
        "-acodec", "pcm_s16le",
        "-ar", "44100",
        "-ac", str(channels),
        "pipe:1"
    ]

    def generate_pcm():
        p_yt, p_ff = start_media_processes(yt_cmd, ff_cmd, 65536)

        try:
            while True:
                chunk = p_ff.stdout.read(16384)
                if not chunk:
                    break
                yield chunk
        except GeneratorExit:
            logger.info(f"Client disconnected from audio stream {video_id}")
        except Exception as e:
            logger.warning(f"Error during audio streaming {video_id}: {e}")
        finally:
            try:
                p_ff.terminate()
                p_ff.wait(timeout=0.5)
            except Exception:
                p_ff.kill()
            try:
                p_yt.terminate()
                p_yt.wait(timeout=0.5)
            except Exception:
                p_yt.kill()
            logger.info(f"Audio stream closed for {video_id}")

    return Response(
        generate_pcm(),
        mimetype="audio/x-raw",
        headers={
            "Cache-Control": "no-cache, no-store",
            "X-Audio-Format": f"PCM_16LE_44100_{'STEREO' if channels == 2 else 'MONO'}",
            "X-Sample-Rate": "44100",
            "X-Channels": str(channels)
        }
    )


@app.route("/surround/stream/<video_id>", methods=["GET"])
def stream_surround(video_id):
    """One synchronized source, eight interleaved mono speaker feeds (16-bit, 44.1 kHz)."""
    target = video_target(video_id)
    if target is None:
        return jsonify({"error": "invalid video id"}), 400
    start = min(21600, max(0, int(request.args.get("start", "0"))))

    yt_cmd = [*YT_DLP_CMD, "-f", "bestaudio/best", "-o", "-", "--quiet", "--no-warnings", target]
    # Front speakers retain stereo. Side and rear feeds emphasize stereo difference,
    # keeping centered vocals at the screen; each group gets a restrained EQ band.
    pan = ("aformat=channel_layouts=stereo,"
           "pan=8c|c0=c0|c1=c1|c2=0.45*c0-0.30*c1|c3=0.30*c0-0.20*c1|"
           "c4=0.45*c1-0.30*c0|c5=0.30*c1-0.20*c0|"
           "c6=0.25*c0-0.18*c1|c7=0.25*c1-0.18*c0,"
           "anequalizer=c2 f=160 w=160 g=-6|c3 f=160 w=160 g=-6|"
           "c4 f=160 w=160 g=-6|c5 f=160 w=160 g=-6|"
           "c6 f=9000 w=5000 g=-8|c7 f=9000 w=5000 g=-8")
    ff_cmd = [FFMPEG_BIN, "-loglevel", "error", "-nostdin", "-i", "pipe:0",
              "-ss", str(start), "-vn", "-af", pan,
              "-ar", "44100", "-c:a", "pcm_s16le", "-f", "s16le", "pipe:1"]

    def generate():
        p_yt, p_ff = start_media_processes(yt_cmd, ff_cmd, 131072)
        stream_start = time.monotonic()
        sent_bytes = 0
        try:
            while True:
                chunk = p_ff.stdout.read(16384)
                if not chunk:
                    break
                sent_bytes += len(chunk)
                target_time = stream_start + sent_bytes / (44100 * 8 * 2)
                delay = target_time - time.monotonic()
                if delay > 0:
                    time.sleep(delay)
                yield chunk
        finally:
            logger.info("Surround stream %s delivered %.1f seconds of PCM", video_id,
                        sent_bytes / (44100 * 8 * 2))
            for process in (p_ff, p_yt):
                try:
                    process.terminate()
                    process.wait(timeout=1)
                except Exception:
                    process.kill()

    return Response(generate(), mimetype="application/octet-stream",
                    headers={"Cache-Control": "no-store", "X-Sample-Rate": "44100",
                             "X-Channels": "8", "X-Format": "signed-16le-interleaved"})


@app.route("/video/stream/<video_id>", methods=["GET"])
def stream_video(video_id):
    target = video_target(video_id)
    if target is None:
        return jsonify({"error": "invalid video id"}), 400
    width = min(2560, max(320, int(request.args.get("w", "1280"))))
    height = min(1440, max(180, int(request.args.get("h", "720"))))
    start = min(21600, max(0, int(request.args.get("start", "0"))))

    logger.info(f"Piped video stream requested for {video_id} ({width}x{height}, 30 fps, start={start}s)")

    yt_cmd = [
        *YT_DLP_CMD,
        "-f", f"bestvideo[height<={height}][vcodec^=avc1]/bestvideo[height<={height}]/best[height<={height}]/best",
        "-o", "-",
        "--quiet",
        "--no-warnings",
        target
    ]

    frame_size = width * height * 4

    ff_cmd = [
        FFMPEG_BIN,
        "-loglevel", "error",
        "-nostdin",
        "-i", "pipe:0",
        "-ss", str(start),
        "-vf", f"fps=30,scale={width}:{height}:force_original_aspect_ratio=decrease:flags=lanczos,pad={width}:{height}:(ow-iw)/2:(oh-ih)/2:black,eq=brightness=0.035",
        "-fps_mode", "cfr",
        "-pix_fmt", "rgba",
        "-f", "rawvideo",
        "pipe:1"
    ]

    def generate_rawvideo():
        p_yt, p_ff = start_media_processes(yt_cmd, ff_cmd, frame_size * 2)
        next_frame = time.monotonic()

        try:
            while True:
                chunk = p_ff.stdout.read(frame_size)
                if len(chunk) < frame_size:
                    break
                now = time.monotonic()
                if next_frame > now:
                    time.sleep(next_frame - now)
                elif now - next_frame > 0.5:
                    next_frame = now
                next_frame += 1.0 / 30.0
                yield chunk
        except GeneratorExit:
            logger.info(f"Client disconnected from video stream {video_id}")
        except Exception as e:
            logger.warning(f"Error during video streaming {video_id}: {e}")
        finally:
            try:
                p_ff.terminate()
                p_ff.wait(timeout=0.5)
            except Exception:
                p_ff.kill()
            try:
                p_yt.terminate()
                p_yt.wait(timeout=0.5)
            except Exception:
                p_yt.kill()
            logger.info(f"Video stream closed for {video_id}")

    return Response(
        generate_rawvideo(),
        mimetype="application/octet-stream",
        headers={
            "Cache-Control": "no-cache, no-store",
            "X-Video-Width": str(width),
            "X-Video-Height": str(height),
            "X-Video-FPS": "30",
            "X-Frame-Size": str(frame_size)
        }
    )


if __name__ == "__main__":
    port = 25592
    logger.info(f"Starting ShtarOS Cinema Daemon on http://127.0.0.1:{port}")
    app.run(host="127.0.0.1", port=port, threaded=True)
