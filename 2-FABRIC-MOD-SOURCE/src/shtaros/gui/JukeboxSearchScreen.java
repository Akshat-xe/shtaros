package shtaros.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import shtaros.audio.ShtarOSSpatialManager;
import shtaros.ShtarOSDaemonManager;
import shtaros.model.SongMetadata;
import shtaros.model.ShtarOSLibrary;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Audio-only remote for a placed jukebox.
 * Features:
 *  - Real-time search against local python daemon via yt-dlp
 *  - Dynamic song results with title, artist, and duration
 *  - 3D spatial stream trigger bound to Jukebox block position
 *  - Pause, Stop, Volume (25%-100%), and Quick Close controls
 *  - Non-pausing background operation (game continues running live)
 */
public class JukeboxSearchScreen extends Screen {

    private final BlockPos jukeboxPos;
    private final List<SongMetadata> searchResults = new ArrayList<>();
    private EditBox searchBox;
    private String currentQuery = "";
    private String statusMessage = "Search for a track or paste a playlist link.";
    private boolean isSearching = false;
    private String bufferingSongId = null;
    private int scroll = 0;
    private int queueScroll = 0;
    private final List<Control> controls = new ArrayList<>();
    private record Control(String label, int x, int y, int width, int height) {}

    private static final int PANEL_WIDTH = 520;
    private static final int PANEL_HEIGHT = 280;

    public JukeboxSearchScreen(BlockPos jukeboxPos) {
        super(Component.literal("ShtarOS Spatial 3D Jukebox"));
        this.jukeboxPos = jukeboxPos.immutable();
        this.searchResults.addAll(ShtarOSLibrary.suggestions());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        clearWidgets();
        controls.clear();
        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;

        String previousText = this.searchBox != null ? this.searchBox.getValue() : this.currentQuery;
        this.searchBox = new EditBox(this.font, panelX + 16, panelY + 38, 248, 20, Component.literal("Search"));
        this.searchBox.setHint(Component.literal("Search or paste YouTube playlist URL"));
        this.searchBox.setMaxLength(500);
        this.searchBox.setValue(previousText);
        this.addRenderableWidget(this.searchBox);
        this.setInitialFocus(this.searchBox);

        addButton("Fetch", panelX + 268, panelY + 38, 74, 20, this::performSearch);

        int listStartY = panelY + 84;
        ShtarOSSpatialManager.JukeboxPlayback activePb = ShtarOSSpatialManager.getPlayback(this.jukeboxPos);
        boolean isBuffering = ShtarOSSpatialManager.isBuffering(this.jukeboxPos);

        for (int i = 0; i < 4 && scroll + i < searchResults.size(); i++) {
            final SongMetadata song = searchResults.get(scroll + i);
            int rowY = listStartY + i * 31;

            boolean isThisSongActive = (activePb != null && activePb.song.id().equals(song.id()));
            boolean isThisSongBuffering = song.id().equals(this.bufferingSongId) && isBuffering;

            String playLabel = isThisSongBuffering ? "..." : isThisSongActive ? "▶" : "Play";
            addButton(playLabel, panelX + 246, rowY + 3, 46, 20, () -> {
                if (isThisSongActive) {
                    ShtarOSSpatialManager.togglePause(this.jukeboxPos);
                } else {
                    this.bufferingSongId = song.id();
                    this.statusMessage = "Buffering: " + song.title();
                    ShtarOSSpatialManager.play(this.jukeboxPos, song);
                }
                this.minecraft.execute(this::rebuildWidgets);
            });
            addButton("+ Queue", panelX + 296, rowY + 3, 48, 20, () -> {
                ShtarOSSpatialManager.addToQueue(jukeboxPos, song);
                statusMessage = "Queued: " + song.title();
                queueScroll = Math.max(0, ShtarOSSpatialManager.getPlayOrder(jukeboxPos).size() - 6);
                rebuildWidgets();
            });
        }

        List<SongMetadata> queue = ShtarOSSpatialManager.getPlayOrder(jukeboxPos);
        for (int row = 0; row < 6 && queueScroll + row < queue.size(); row++) {
            int index = queueScroll + row;
            SongMetadata song = queue.get(index);
            addButton(fit((index + 1) + ". " + song.title(), 126), panelX + 366,
                    panelY + 77 + row * 24, 130, 20, () -> {
                        ShtarOSSpatialManager.playQueuedAt(jukeboxPos, index);
                        bufferingSongId = song.id();
                        statusMessage = "Buffering: " + song.title();
                        rebuildWidgets();
                    });
        }

        int bottomY = panelY + PANEL_HEIGHT - 36;
        boolean isPlaying = activePb != null && !activePb.paused;

        addButton("|<", panelX + 16, bottomY, 44, 20, () -> {
            ShtarOSSpatialManager.previous(jukeboxPos);
            rebuildWidgets();
        });
        addButton(isPlaying ? "Ⅱ" : "▶", panelX + 64, bottomY, 68, 20, () -> {
                    ShtarOSSpatialManager.togglePause(this.jukeboxPos);
                    this.minecraft.execute(this::rebuildWidgets);
        });
        addButton(">|", panelX + 136, bottomY, 44, 20, () -> {
            ShtarOSSpatialManager.next(jukeboxPos);
            this.rebuildWidgets();
        });
        addButton("■", panelX + 184, bottomY, 44, 20, () -> {
                    this.bufferingSongId = null;
                    ShtarOSSpatialManager.stop(this.jukeboxPos);
                    this.minecraft.execute(this::rebuildWidgets);
        });
        addButton("Vol -", panelX + 236, bottomY, 52, 20, () -> adjustVolume(-1));
        addButton("Vol +", panelX + 338, bottomY, 52, 20, () -> adjustVolume(1));
        addButton("Close", panelX + 440, bottomY, 64, 20, this::onClose);
    }

    private void addButton(String label, int x, int y, int w, int h, Runnable action) {
        controls.add(new Control(label, x, y, w, h));
        addRenderableWidget(Button.builder(Component.literal(label), button -> action.run()).bounds(x, y, w, h).build());
    }

    private void adjustVolume(int step) {
        int tenths = Math.round(ShtarOSSpatialManager.getVolume() * 10);
        ShtarOSSpatialManager.setVolume(Math.max(0, Math.min(10, tenths + step)) / 10.0f);
        rebuildWidgets();
    }

    private String fit(String value, int maxWidth) {
        if (value == null) return "";
        if (font.width(value) <= maxWidth) return value;
        while (!value.isEmpty() && font.width(value + "...") > maxWidth) value = value.substring(0, value.length() - 1);
        return value + "...";
    }

    private void performSearch() {
        if (this.searchBox == null) return;
        String query = this.searchBox.getValue().trim();
        if (query.isEmpty() || isSearching) return;

        this.currentQuery = query;
        this.isSearching = true;
        this.statusMessage = "Fetching from YouTube...";
        this.scroll = 0;
        this.searchResults.clear();
        this.minecraft.execute(this::rebuildWidgets);

        CompletableFuture.runAsync(() -> {
            try {
                ShtarOSDaemonManager.ensureRunning();
                String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(4))
                        .build();

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:25592/search?q=" + encoded))
                        .timeout(Duration.ofSeconds(120))
                        .GET()
                        .build();

                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    JsonArray array = JsonParser.parseString(resp.body()).getAsJsonArray();
                    List<SongMetadata> fetched = new ArrayList<>();
                    for (JsonElement el : array) {
                        if (!el.isJsonObject()) continue;
                        JsonObject obj = el.getAsJsonObject();
                        String id = obj.has("id") ? obj.get("id").getAsString() : "";
                        String title = obj.has("title") ? sanitize(obj.get("title").getAsString()) : "Unknown";
                        String author = obj.has("author") ? sanitize(obj.get("author").getAsString()) : "";
                        String durationStr = obj.has("duration_str") ? obj.get("duration_str").getAsString() : "0:00";
                        int durationSec = obj.has("duration") && !obj.get("duration").isJsonNull() ? obj.get("duration").getAsInt() : 0;
                        if (id.matches("[A-Za-z0-9_-]{11}")) {
                            fetched.add(new SongMetadata(id, title, author, durationStr, durationSec));
                        }
                    }

                    this.minecraft.execute(() -> {
                        this.searchResults.clear();
                        this.searchResults.addAll(fetched);
                        this.isSearching = false;
                        if (query.startsWith("http") && query.contains("list=") && !fetched.isEmpty()) {
                            ShtarOSSpatialManager.playPlaylist(jukeboxPos, fetched);
                            queueScroll = 0;
                            this.statusMessage = "Playlist loaded: " + fetched.size() + " songs.";
                        } else if (fetched.isEmpty()) {
                            this.statusMessage = "§cNo results found for '" + query + "'.";
                        } else {
                            this.statusMessage = "§aFound " + fetched.size() + " songs.";
                        }
                        this.rebuildWidgets();
                    });
                } else {
                    this.minecraft.execute(() -> {
                        this.isSearching = false;
                        this.statusMessage = "§cDaemon returned HTTP " + resp.statusCode();
                        this.rebuildWidgets();
                    });
                }
            } catch (Exception e) {
                this.minecraft.execute(() -> {
                    this.isSearching = false;
                    this.statusMessage = "§cDaemon offline? Run start_daemon.sh";
                    this.rebuildWidgets();
                });
            }
        });
    }

    private static String sanitize(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c >= 32 && c <= 126) {
                b.append(c);
            } else if (Character.isLetterOrDigit(c) || Character.isWhitespace(c) || c == '-' || c == '(' || c == ')' || c == '[' || c == ']') {
                b.append(c);
            } else {
                b.append(' ');
            }
        }
        String res = b.toString().replaceAll("\\s+", " ").trim();
        return res.isEmpty() ? s : res;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            this.onClose();
            return true;
        }
        if (event.isConfirmation() && this.searchBox != null && this.searchBox.isFocused()) {
            performSearch();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = (height - PANEL_HEIGHT) / 2;
        if (x >= panelX + 358 && x <= panelX + 504 && y >= panelY + 70 && y <= panelY + 224) {
            queueScroll = Math.max(0, Math.min(Math.max(0, ShtarOSSpatialManager.getPlayOrder(jukeboxPos).size() - 6),
                    queueScroll - (int) Math.signum(vertical)));
            rebuildWidgets();
            return true;
        }
        if (x >= panelX + 16 && x <= panelX + 346 && y >= panelY + 82 && y <= panelY + 210) {
            scroll = Math.max(0, Math.min(Math.max(0, searchResults.size() - 4), scroll - (int) Math.signum(vertical)));
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        extractor.fill(0, 0, this.width, this.height, 0x90000000);
        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;
        extractor.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xF2161918);
        extractor.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 28, 0xFF222724);
        extractor.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 2, 0xFFD6976E);
        extractor.text(this.font, Component.literal("SHTAROS  /  JUKEBOX"), panelX + 14, panelY + 8, 0xFFE8E5DA);
        extractor.text(this.font, Component.literal("96-BLOCK AUDIO"), panelX + PANEL_WIDTH - 112, panelY + 8, 0xFFAFC4A0);
        extractor.fill(panelX + 358, panelY + 38, panelX + 504, panelY + 227, 0xFF222724);
        extractor.text(this.font, Component.literal("UP NEXT"), panelX + 366, panelY + 51, 0xFFAFC4A0);
        extractor.text(this.font, Component.literal("RESULTS"), panelX + 16, panelY + 70, 0xFFAFB5A9);

        if (!ShtarOSSpatialManager.isBuffering(this.jukeboxPos)) {
            this.bufferingSongId = null;
        }

        ShtarOSSpatialManager.JukeboxPlayback activePb = ShtarOSSpatialManager.getPlayback(this.jukeboxPos);
        int listStartY = panelY + 84;
        if (isSearching) {
            extractor.text(this.font, Component.literal("Fetching from YouTube..."), panelX + 22, listStartY + 18, 0xFFD6976E);
        } else if (searchResults.isEmpty()) {
            extractor.text(this.font, Component.literal(fit(statusMessage, 312)), panelX + 22, listStartY + 18, 0xFFAFB5A9);
        } else {
            for (int i = 0; i < 4 && scroll + i < searchResults.size(); i++) {
                SongMetadata song = searchResults.get(scroll + i);
                int rowY = listStartY + i * 31;
                boolean isThisSongActive = (activePb != null && activePb.song.id().equals(song.id()));
                int rowBg = isThisSongActive ? 0xFF37443B : 0xFF222724;
                extractor.fill(panelX + 16, rowY, panelX + 346, rowY + 27, rowBg);
                extractor.text(this.font, Component.literal(fit((scroll + i + 1) + ". " + song.title(), 218)),
                        panelX + 20, rowY + 3, isThisSongActive ? 0xFFAFC4A0 : 0xFFE8E5DA);
                extractor.text(this.font, Component.literal(fit(song.author() + "  " + song.durationStr(), 210)),
                        panelX + 20, rowY + 15, 0xFFAFB5A9);
            }
        }
        List<SongMetadata> queue = ShtarOSSpatialManager.getPlayOrder(jukeboxPos);
        if (queue.isEmpty()) extractor.text(font, Component.literal("Queue is empty"), panelX + 366, panelY + 80, 0xFFAFB5A9);
        if (queue.size() > 6) extractor.text(font, Component.literal((queueScroll + 1) + "-" + Math.min(queue.size(), queueScroll + 6) + " / " + queue.size()),
                panelX + 366, panelY + 218, 0xFFAFB5A9);

        int statusY = panelY + 214;
        extractor.fill(panelX + 16, statusY, panelX + 346, statusY + 1, 0xFF414941);
        String now = activePb == null ? ShtarOSSpatialManager.isBuffering(jukeboxPos) ? "BUFFERING" : "READY"
                : activePb.paused ? "PAUSED" : "PLAYING";
        extractor.text(font, Component.literal(now + "  " + fit(activePb == null ? "Select a song" : activePb.song.title(), 270)),
                panelX + 20, statusY + 5, 0xFFAFC4A0);
        extractor.text(font, Component.literal(fit(statusMessage.replaceAll("§.", ""), 320)), panelX + 20, statusY + 17, 0xFFAFB5A9);

        super.extractRenderState(extractor, mouseX, mouseY, delta);
        for (Control control : controls) {
            boolean hover = mouseX >= control.x && mouseX < control.x + control.width
                    && mouseY >= control.y && mouseY < control.y + control.height;
            boolean primary = control.label.equals("Fetch") || control.label.equals("Play")
                    || control.label.equals("▶") || control.label.equals("Ⅱ") || control.label.equals("Playing");
            int color = primary ? (hover ? 0xFFE6AA81 : 0xFFD6976E) : (hover ? 0xFF455348 : 0xFF303831);
            extractor.fill(control.x, control.y, control.x + control.width, control.y + control.height, color);
            extractor.fill(control.x, control.y + control.height - 1, control.x + control.width, control.y + control.height, 0xFF414941);
            String label = fit(control.label, control.width - 8);
            extractor.text(font, Component.literal(label), control.x + (control.width - font.width(label)) / 2,
                    control.y + (control.height - 8) / 2, primary ? 0xFF101412 : 0xFFE8E5DA);
        }
        int volume = Math.round(ShtarOSSpatialManager.getVolume() * 100);
        extractor.text(font, Component.literal(volume + "%"), panelX + 292, panelY + PANEL_HEIGHT - 30, 0xFFAFC4A0);
    }
}
