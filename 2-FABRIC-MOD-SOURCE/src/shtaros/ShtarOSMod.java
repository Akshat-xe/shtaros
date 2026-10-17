package shtaros;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.context.CommandContext;
import shtaros.cinema.ShtarOSCinemaBuilder;
import shtaros.cinema.ShtarOSCinemaManager;
import shtaros.cinema.ShtarOSScreenRenderer;
import shtaros.cinema.ShtarOSSurroundManager;
import shtaros.cinema.ShtarOSVideoPlayer;
import shtaros.cinema.ShtarOSCinemaView;
import shtaros.browser.ShtarOSBrowserBridge;
import shtaros.browser.ShtarOSBrowserScreen;
import shtaros.gui.ShtarOSSetupScreen;
import shtaros.gui.ShtarOSInventoryButtons;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.net.URI;

public class ShtarOSMod implements ClientModInitializer {

    public static KeyMapping REMOTE_KEY;

    @Override
    public void onInitializeClient() {
        System.out.println("[ShtarOS] Cinema display and physical surround initializing...");

        // Register official 'I' KeyMapping
        REMOTE_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.shtaros.remote",
                InputConstants.KEY_I,
                KeyMapping.Category.MISC
        ));
        ShtarOSInventoryButtons.register();

        // 1. Initialize Screen In-World Renderer
        ShtarOSScreenRenderer.init();
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("shtaros", "fps"), (graphics, delta) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.level == null || client.gui.screen() != null || ShtarOSCinemaView.isActive()) return;
            graphics.fill(7, 7, 66, 23, 0xC2161918);
            graphics.text(client.font, net.minecraft.network.chat.Component.literal(client.getFps() + " FPS"), 13, 11, 0xFFE8E5DA);
        });

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("shtaros", "browser_hud"), (graphics, delta) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.level == null || client.gui.screen() != null || ShtarOSCinemaView.isActive()
                    || !ShtarOSBrowserBridge.isOpen()) return;

            // Clean, non-intrusive indicator only when typing mode is active
            if (ShtarOSBrowserBridge.isTypingMode()) {
                int w = client.getWindow().getGuiScaledWidth();
                int h = client.getWindow().getGuiScaledHeight();
                String text = "§a⌨ Typing Mode §7(F8 or Esc to walk)";
                int textW = client.font.width(text);
                int bgX = (w - textW) / 2 - 6;
                int bgY = h - 30;
                graphics.fill(bgX, bgY - 2, bgX + textW + 12, bgY + 11, 0xC0141918);
                graphics.text(client.font, net.minecraft.network.chat.Component.literal(text), (w - textW) / 2, bgY, 0xFFFFFFFF);
            }
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            ShtarOSCinemaView.exit();
            shtaros.cinema.ShtarOSSurroundManager.stop();
            shtaros.audio.ShtarOSSpatialManager.stopAll();
            ShtarOSVideoPlayer.stop();
            ShtarOSBrowserBridge.close();
            ShtarOSDaemonManager.stopOwned();
        });

        // Start the companion server in the background so the standby preview is ready.
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try { ShtarOSDaemonManager.ensureRunning(); }
            catch (Exception e) { System.err.println("[ShtarOS] " + e.getMessage()); }
        });

        // 2. Register Client Commands (/shtaros, /shtaros play <query>, /shtaros setup, /shtaros scan, etc.)
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommands.literal("shtaros")
                .executes((CommandContext<FabricClientCommandSource> ctx) -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> mc.setScreenAndShow(new ShtarOSSetupScreen()));
                    return 1;
                })
                .then(ClientCommands.literal("gui").executes((CommandContext<FabricClientCommandSource> ctx) -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> mc.setScreenAndShow(new ShtarOSSetupScreen()));
                    return 1;
                }))
                .then(ClientCommands.literal("browser").executes((CommandContext<FabricClientCommandSource> ctx) -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> mc.setScreenAndShow(new ShtarOSBrowserScreen()));
                    return 1;
                }))
                .then(ClientCommands.literal("url")
                    .then(ClientCommands.argument("link", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                        .executes((CommandContext<FabricClientCommandSource> ctx) -> {
                            String link = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "link").trim();
                            Minecraft mc = Minecraft.getInstance();
                            mc.execute(() -> openTheaterLink(link));
                            return 1;
                        })
                    )
                )
                .then(ClientCommands.literal("search")
                    .then(ClientCommands.argument("query", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                        .executes((CommandContext<FabricClientCommandSource> ctx) -> {
                            String query = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "query").trim();
                            String encoded = java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
                            String target = "https://www.youtube.com/results?search_query=" + encoded;
                            Minecraft mc = Minecraft.getInstance();
                            mc.execute(() -> {
                                if (!ShtarOSBrowserBridge.isOpen()) {
                                    ShtarOSBrowserBridge.open();
                                    ShtarOSCinemaManager.stopPlayback();
                                }
                                ShtarOSBrowserBridge.navigate(target);
                                if (mc.player != null) {
                                    mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§d[ShtarOS Browser] §aSearching YouTube: §f" + query));
                                }
                            });
                            return 1;
                        })
                    )
                )
                .then(ClientCommands.literal("reload").executes((CommandContext<FabricClientCommandSource> ctx) -> {
                    ShtarOSBrowserBridge.reload();
                    return 1;
                }))
                .then(ClientCommands.literal("back").executes((CommandContext<FabricClientCommandSource> ctx) -> {
                    ShtarOSBrowserBridge.back();
                    return 1;
                }))
                .then(ClientCommands.literal("forward").executes((CommandContext<FabricClientCommandSource> ctx) -> {
                    ShtarOSBrowserBridge.forward();
                    return 1;
                }))
                .then(ClientCommands.literal("type").executes((CommandContext<FabricClientCommandSource> ctx) -> {
                    Minecraft mc = Minecraft.getInstance();
                    boolean active = ShtarOSBrowserBridge.toggleTypingMode();
                    if (mc.player != null) {
                        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            active ? "§a[ShtarOS] ⌨ Typing Mode ON. Keys sent to the browser. Press [F8] or [ESC] to walk."
                                   : "§e[ShtarOS] Typing Mode OFF. Walking and player movement restored."));
                    }
                    return 1;
                }))
                .then(ClientCommands.literal("play")
                    .then(ClientCommands.argument("query", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                        .executes((CommandContext<FabricClientCommandSource> ctx) -> {
                            String query = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "query").trim();
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.player != null) {
                                mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§d[ShtarOS] §eSearching & streaming: §f" + query + "..."));
                            }
                            java.util.concurrent.CompletableFuture.runAsync(() -> {
                                try {
                                    ShtarOSDaemonManager.ensureRunning();
                                    String cleanQuery = query.trim();
                                    if (cleanQuery.contains("127.0.0.1:25592") || cleanQuery.contains("localhost:25592")) {
                                        cleanQuery = cleanQuery.replaceAll("(?i)https?://(127\\.0\\.0\\.1|localhost):25592/?", "").trim();
                                    }
                                    String encoded = java.net.URLEncoder.encode(cleanQuery, java.nio.charset.StandardCharsets.UTF_8);
                                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) java.net.URI.create("http://127.0.0.1:25592/search?q=" + encoded).toURL().openConnection();
                                    conn.setRequestMethod("GET");
                                    conn.setConnectTimeout(5000);
                                    conn.setReadTimeout(8000);
                                    if (conn.getResponseCode() == 200) {
                                        try (java.io.InputStreamReader reader = new java.io.InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8)) {
                                            com.google.gson.JsonArray arr = com.google.gson.JsonParser.parseReader(reader).getAsJsonArray();
                                            if (arr.size() > 0) {
                                                com.google.gson.JsonObject first = arr.get(0).getAsJsonObject();
                                                String vidId = first.get("id").getAsString();
                                                String vidTitle = first.get("title").getAsString();
                                                mc.execute(() -> ShtarOSCinemaManager.playVideo(vidId, vidTitle));
                                                return;
                                            }
                                        }
                                    }
                                    if (mc.player != null) {
                                        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c[ShtarOS] No video results found for: " + query));
                                    }
                                } catch (Exception e) {
                                    if (mc.player != null) {
                                        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c[ShtarOS] Error playing video: " + e.getMessage()));
                                    }
                                }
                            });
                            return 1;
                        })
                    )
                )
                .then(ClientCommands.literal("setup").executes((CommandContext<FabricClientCommandSource> ctx) -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> {
                        if (mc.player != null) {
                            ShtarOSCinemaBuilder.buildCinema(mc.player);
                        }
                    });
                    return 1;
                }))
                .then(ClientCommands.literal("scan").executes((CommandContext<FabricClientCommandSource> ctx) -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> {
                        if (mc.player != null) {
                            ShtarOSCinemaManager.scanAndBindCinema(mc.player);
                        }
                    });
                    return 1;
                }))
                .then(ClientCommands.literal("speakers").executes((CommandContext<FabricClientCommandSource> ctx) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        for (ShtarOSSurroundManager.SpeakerInfo speaker : ShtarOSSurroundManager.getSpeakers()) {
                            net.minecraft.core.BlockPos p = speaker.pos;
                            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                    "§bSpeaker " + (speaker.role.ordinal() + 1) + " §f" + speaker.role.name()
                                    + " §7at " + p.getX() + ", " + p.getY() + ", " + p.getZ()));
                        }
                    }
                    return 1;
                }))
                .then(ClientCommands.literal("pause").executes((CommandContext<FabricClientCommandSource> ctx) -> {
                    ShtarOSCinemaManager.togglePause();
                    return 1;
                }))
                .then(ClientCommands.literal("stop").executes((CommandContext<FabricClientCommandSource> ctx) -> {
                    ShtarOSCinemaManager.stopPlayback();
                    return 1;
                }))
                .then(ClientCommands.literal("power").executes((CommandContext<FabricClientCommandSource> ctx) -> {
                    ShtarOSCinemaManager.togglePower();
                    return 1;
                }))
                .then(ClientCommands.argument("link", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                    .executes((CommandContext<FabricClientCommandSource> ctx) -> {
                        String link = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "link");
                        Minecraft.getInstance().execute(() -> openTheaterLink(link));
                        return 1;
                    }))
            );
        });

        System.out.println("[ShtarOS] Cinema display and physical surround initialized.");
    }

    private static void openTheaterLink(String input) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String link = input.trim();
        if (link.startsWith("<") && link.endsWith(">")) link = link.substring(1, link.length() - 1);
        if (!link.startsWith("http://") && !link.startsWith("https://")) link = "https://" + link;
        URI url;
        try {
            if (link.length() > 4096) throw new IllegalArgumentException("Link is too long");
            url = URI.create(link);
            if (!("http".equalsIgnoreCase(url.getScheme()) || "https".equalsIgnoreCase(url.getScheme()))
                    || url.getHost() == null || url.getHost().isBlank())
                throw new IllegalArgumentException("Use an HTTP or HTTPS web link");
        } catch (IllegalArgumentException e) {
            mc.player.sendSystemMessage(Component.literal("§c[ShtarOS] Invalid link. Paste a full web URL after /shtaros."));
            return;
        }
        if (!ShtarOSCinemaManager.hasScreen()) ShtarOSCinemaManager.scanAndBindCinema(mc.player);
        if (!ShtarOSCinemaView.isInsideTheater(mc.player)) {
            mc.player.sendSystemMessage(Component.literal("§c[ShtarOS] Stand within the theater radius near an intact screen."));
            return;
        }
        if (!ShtarOSBrowserBridge.open()) {
            mc.player.sendSystemMessage(Component.literal("§c[ShtarOS] Browser unavailable: " + ShtarOSBrowserBridge.error()));
            return;
        }
        ShtarOSCinemaView.exit();
        if (ShtarOSCinemaManager.getCurrentVideoId() != null) ShtarOSCinemaManager.stopPlayback();
        ShtarOSBrowserBridge.navigate(url.toString());
        mc.player.sendSystemMessage(Component.literal("§a[ShtarOS] Opening " + url.getHost() + " on the theater screen."));
    }
}
