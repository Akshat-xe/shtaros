package shtaros;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import shtaros.cinema.ShtarOSVideoPlayer;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Starts the companion localhost service when the Minecraft client needs it. */
public final class ShtarOSDaemonManager {
    private static final String HEALTH_URL = "http://127.0.0.1:25592/";
    private static final Path DAEMON_DIR = FabricLoader.getInstance().getGameDir().resolve("shtaros");
    private static Process ownedProcess;

    private ShtarOSDaemonManager() {}

    public static synchronized void ensureRunning() throws IOException {
        if (healthy()) {
            ShtarOSVideoPlayer.refreshStandbyFrame();
            return;
        }
        restartBundledOldVersion();

        Path script = DAEMON_DIR.resolve("daemon.py");
        Path python = DAEMON_DIR.resolve("venv/bin/python3");
        if (!Files.isRegularFile(script) || !Files.isExecutable(python)) {
            throw new IOException("ShtarOS media service is not installed. Run install-shtaros.command.");
        }

        Path log = DAEMON_DIR.resolve("daemon-25592.log");
        Process process = new ProcessBuilder(python.toString(), script.toString())
                .directory(DAEMON_DIR.toFile())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()))
                .start();

        long deadline = System.nanoTime() + 8_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (healthy()) {
                ownedProcess = process;
                Files.writeString(DAEMON_DIR.resolve("daemon-25592.pid"), Long.toString(process.pid()));
                ShtarOSVideoPlayer.refreshStandbyFrame();
                return;
            }
            if (!process.isAlive()) break;
            try { Thread.sleep(150); }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("ShtarOS server startup interrupted", e);
            }
        }
        if (process.isAlive()) process.destroy();
        String detail = "server did not start; see " + log;
        if (Files.isRegularFile(log)) {
            String contents = Files.readString(log);
            if (!contents.isBlank()) detail = contents.substring(Math.max(0, contents.length() - 250)).trim();
        }
        System.err.println("[ShtarOS] Server startup failed: " + detail);
        throw new IOException("Local media server could not start; check daemon-25592.log");
    }

    private static boolean healthy() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(HEALTH_URL).toURL().openConnection();
            connection.setConnectTimeout(400);
            connection.setReadTimeout(400);
            if (connection.getResponseCode() != 200) return false;
            try (InputStreamReader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
                JsonObject status = JsonParser.parseReader(reader).getAsJsonObject();
                return status.has("service") && "shtaros-cinema-daemon".equals(status.get("service").getAsString())
                        && status.has("version") && "2.3.0".equals(status.get("version").getAsString());
            }
        } catch (Exception ignored) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static void restartBundledOldVersion() throws IOException {
        Path pidFile = DAEMON_DIR.resolve("daemon-25592.pid");
        if (!Files.isRegularFile(pidFile)) return;
        try {
            long pid = Long.parseLong(Files.readString(pidFile).trim());
            ProcessHandle handle = ProcessHandle.of(pid).orElse(null);
            if (handle == null || !handle.isAlive()) return;
            String command = handle.info().commandLine().orElse("");
            if (!command.contains(DAEMON_DIR.resolve("daemon.py").toString())) {
                throw new IOException("Old server uses port 25592; stop it and reopen Minecraft.");
            }
            handle.destroy();
            for (int i = 0; i < 20 && handle.isAlive(); i++) Thread.sleep(100);
            if (handle.isAlive()) throw new IOException("Old media server did not exit; stop it and reopen Minecraft.");
        } catch (NumberFormatException e) {
            throw new IOException("Invalid ShtarOS daemon PID file", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while replacing old media server", e);
        }
    }

    public static synchronized void stopOwned() {
        Process process = ownedProcess;
        ownedProcess = null;
        if (process == null || !process.isAlive()) return;
        process.toHandle().descendants().forEach(ProcessHandle::destroy);
        process.destroy();
        try {
            Path pid = DAEMON_DIR.resolve("daemon-25592.pid");
            if (Files.isRegularFile(pid) && Files.readString(pid).trim().equals(Long.toString(process.pid()))) Files.delete(pid);
        } catch (IOException ignored) {}
    }
}
