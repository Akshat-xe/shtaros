package shtaros.audio;

import com.mojang.blaze3d.audio.Channel;
import com.mojang.blaze3d.audio.Library;
import shtaros.model.SongMetadata;
import shtaros.model.ShtarOSLibrary;
import shtaros.mixin.SoundEngineAccessor;
import shtaros.mixin.SoundManagerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEngineExecutor;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 3D Spatial Audio Manager for ShtarOS Jukeboxes.
 * Binds OpenAL sound sources to placed Jukebox block positions in the 3D Minecraft world.
 * Leverages OpenAL Soft hardware distance attenuation (AL_LINEAR_DISTANCE_CLAMPED) and
 * real-time listener head vector orientation for true 3D spatial panning.
 */
public class ShtarOSSpatialManager {

    public static class JukeboxPlayback {
        public final BlockPos pos;
        public final SongMetadata song;
        public final Channel channel;
        public final ShtarOSAudioStream audioStream;
        public boolean paused = false;

        public JukeboxPlayback(BlockPos pos, SongMetadata song, Channel channel, ShtarOSAudioStream audioStream) {
            this.pos = pos;
            this.song = song;
            this.channel = channel;
            this.audioStream = audioStream;
        }

        public void stop() {
            try {
                this.channel.stop();
            } catch (Throwable ignored) {}
            try {
                this.audioStream.close();
            } catch (Throwable ignored) {}
            releaseChannel(this.channel);
        }
    }

    private static final Map<BlockPos, JukeboxPlayback> activePlaybacks = new ConcurrentHashMap<>();
    private static final Set<BlockPos> backgroundPaused = new HashSet<>();
    private static final Map<BlockPos, List<SongMetadata>> playOrders = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Integer> playIndexes = new ConcurrentHashMap<>();
    private static final Map<BlockPos, String> bufferingStatus = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Long> pendingRequests = new ConcurrentHashMap<>();
    private static final AtomicLong requestIdGen = new AtomicLong(0);

    private static float currentVolume = 1.0f;

    public static float getVolume() {
        return currentVolume;
    }

    public static void setVolume(float volume) {
        currentVolume = Math.max(0.0f, Math.min(1.0f, volume));
        onSoundThread(() -> {
            for (JukeboxPlayback playback : activePlaybacks.values()) {
                if (playback.channel != null) {
                    playback.channel.setVolume(currentVolume);
                }
            }
        });
    }

    public static void cycleVolume() {
        if (currentVolume >= 1.0f) setVolume(0.75f);
        else if (currentVolume >= 0.75f) setVolume(0.50f);
        else if (currentVolume >= 0.50f) setVolume(0.25f);
        else setVolume(1.0f);
    }

    public static JukeboxPlayback getPlayback(BlockPos pos) {
        return activePlaybacks.get(pos.immutable());
    }

    public static boolean isBuffering(BlockPos pos) {
        return bufferingStatus.containsKey(pos.immutable());
    }

    public static String getPlaybackInfo(BlockPos rawPos) {
        BlockPos pos = rawPos.immutable();
        String buff = bufferingStatus.get(pos);
        if (buff != null) {
            return "§e⏳ " + buff;
        }

        JukeboxPlayback pb = activePlaybacks.get(pos);
        if (pb == null) {
            return "§7⏹ Status: Idle (Select a song to stream)";
        }
        if (pb.paused) {
            return "§e⏸ Paused: §f" + pb.song.title();
        }
        return "§a▶ Playing: §f" + pb.song.title() + " §e[" + pb.song.durationStr() + "]";
    }

    public static void play(BlockPos rawPos, SongMetadata song) {
        final BlockPos pos = rawPos.immutable();
        playOrders.put(pos, new ArrayList<>(List.of(song)));
        playIndexes.put(pos, 0);
        start(pos, song);
    }

    public static List<SongMetadata> getPlayOrder(BlockPos rawPos) {
        return List.copyOf(playOrders.getOrDefault(rawPos.immutable(), List.of()));
    }

    public static int getPlayIndex(BlockPos rawPos) {
        return playIndexes.getOrDefault(rawPos.immutable(), -1);
    }

    public static void playQueuedAt(BlockPos rawPos, int index) {
        BlockPos pos = rawPos.immutable();
        List<SongMetadata> songs = playOrders.getOrDefault(pos, List.of());
        if (index < 0 || index >= songs.size()) return;
        playIndexes.put(pos, index);
        start(pos, songs.get(index));
    }

    public static void previous(BlockPos rawPos) {
        BlockPos pos = rawPos.immutable();
        int index = playIndexes.getOrDefault(pos, -1);
        if (index < 0) return;
        playQueuedAt(pos, Math.max(0, index - 1));
    }

    public static void addToQueue(BlockPos rawPos, SongMetadata song) {
        BlockPos pos = rawPos.immutable();
        if (!activePlaybacks.containsKey(pos) && !bufferingStatus.containsKey(pos)) { play(pos, song); return; }
        playOrders.computeIfAbsent(pos, unused -> new ArrayList<>()).add(song);
    }

    public static void playPlaylist(BlockPos rawPos, List<SongMetadata> songs) {
        if (songs.isEmpty()) return;
        BlockPos pos = rawPos.immutable();
        playOrders.put(pos, new ArrayList<>(songs));
        playIndexes.put(pos, 0);
        start(pos, songs.get(0));
    }

    public static void next(BlockPos rawPos) {
        BlockPos pos = rawPos.immutable();
        int nextIndex = playIndexes.getOrDefault(pos, -1) + 1;
        List<SongMetadata> songs = playOrders.getOrDefault(pos, List.of());
        if (nextIndex >= songs.size()) { stop(pos); return; }
        playIndexes.put(pos, nextIndex);
        start(pos, songs.get(nextIndex));
    }

    private static void start(BlockPos pos, SongMetadata song) {
        ShtarOSLibrary.remember(song);

        // 1. Cleanly stop any existing active playback at this block
        JukeboxPlayback oldPlayback = activePlaybacks.remove(pos);
        if (oldPlayback != null) {
            oldPlayback.stop();
        }

        // 2. Set new unique request ID to debounce rapid clicks
        final long currentReqId = requestIdGen.incrementAndGet();
        pendingRequests.put(pos, currentReqId);
        bufferingStatus.put(pos, "Buffering: " + song.title() + "...");

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendOverlayMessage(
                    Component.literal("§d[ShtarOS Jukebox] §eBuffering: §f" + song.title() + "...")
            );
        }

        CompletableFuture.runAsync(() -> {
            ShtarOSAudioStream audioStream = null;
            try {
                String streamUrl = "http://127.0.0.1:25592/stream/" + song.id();
                audioStream = new ShtarOSAudioStream(streamUrl);

                boolean ready = audioStream.waitForInitialData(12000);

                // Check if this request was superseded by another song click
                Long activeReq = pendingRequests.get(pos);
                if (activeReq == null || activeReq != currentReqId) {
                    if (audioStream != null) {
                        try { audioStream.close(); } catch (Exception ignored) {}
                    }
                    return;
                }

                if (!ready) {
                    System.err.println("[ShtarOS Jukebox] Timed out waiting for audio data: " + song.title());
                    bufferingStatus.remove(pos);
                    final ShtarOSAudioStream streamToClose = audioStream;
                    mc.execute(() -> {
                        if (mc.player != null) {
                            mc.player.sendSystemMessage(
                                    Component.literal("§c[ShtarOS Jukebox] Failed to stream '" + song.title() + "'.")
                            );
                        }
                        try { streamToClose.close(); } catch (Exception ignored) {}
                    });
                    return;
                }

                final ShtarOSAudioStream finalAudioStream = audioStream;

                mc.execute(() -> {
                    // Check again if superseded while hopping to render thread
                    Long current = pendingRequests.get(pos);
                    if (current == null || current != currentReqId) {
                        try { finalAudioStream.close(); } catch (Exception ignored) {}
                        return;
                    }

                    bufferingStatus.remove(pos);

                    // Check if block is still a jukebox in the world
                    if (mc.level == null || !mc.level.getBlockState(pos).is(Blocks.JUKEBOX)) {
                        try { finalAudioStream.close(); } catch (Exception ignored) {}
                        return;
                    }

                    onSoundThread(() -> {
                        Channel channel = acquireChannel();
                        if (channel == null) {
                            System.err.println("[ShtarOS Jukebox] No OpenAL channel available!");
                            mc.execute(() -> {
                                if (mc.player != null) {
                                    mc.player.sendSystemMessage(
                                            Component.literal("§c[ShtarOS Jukebox] Error: Sound engine channel busy.")
                                    );
                                }
                            });
                            try { finalAudioStream.close(); } catch (Exception ignored) {}
                            return;
                        }

                        try {
                            // Place sound source at center-top of Jukebox block
                            Vec3 soundPos = new Vec3(pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5);
                            channel.setSelfPosition(soundPos);
                            channel.setRelative(false); // In world coordinates for listener head tracking
                            channel.linearAttenuation(96.0f); // Twice the theater's 48-block output range
                            channel.setVolume(currentVolume);

                            channel.attachBufferStream(finalAudioStream);
                            channel.play();

                            JukeboxPlayback playback = new JukeboxPlayback(pos, song, channel, finalAudioStream);
                            activePlaybacks.put(pos, playback);

                            mc.execute(() -> {
                                if (mc.player != null) {
                                    mc.player.sendOverlayMessage(
                                            Component.literal("§d[ShtarOS Jukebox] §aNow Streaming: §f" + song.title() + " §7(" + song.durationStr() + ")")
                                    );
                                    mc.player.sendSystemMessage(
                                            Component.literal("§d[ShtarOS Jukebox] §aStreaming: §f" + song.title() + " §7(" + song.durationStr() + ") §e[Spatial Audio Active]")
                                    );
                                }
                            });
                            System.out.println("[ShtarOS Jukebox] Active playback started for '" + song.title() + "' at [" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]");

                        } catch (Throwable t) {
                            System.err.println("[ShtarOS Jukebox] Error attaching stream: " + t.getMessage());
                            t.printStackTrace();
                            releaseChannel(channel);
                            try { finalAudioStream.close(); } catch (Exception ignored) {}
                        }
                    });
                });

            } catch (Exception e) {
                bufferingStatus.remove(pos);
                System.err.println("[ShtarOS Jukebox] Pre-buffer failed: " + e.getMessage());
                if (audioStream != null) {
                    try { audioStream.close(); } catch (Exception ignored) {}
                }
            }
        });
    }

    public static void togglePause(BlockPos rawPos) {
        BlockPos pos = rawPos.immutable();
        JukeboxPlayback pb = activePlaybacks.get(pos);
        if (pb != null && pb.channel != null) {
            if (pb.paused) {
                pb.channel.unpause();
                pb.paused = false;
            } else {
                pb.channel.pause();
                pb.paused = true;
            }
        }
    }

    public static void pauseForBackground() {
        backgroundPaused.clear();
        for (Map.Entry<BlockPos, JukeboxPlayback> entry : activePlaybacks.entrySet()) {
            if (!entry.getValue().paused) {
                togglePause(entry.getKey());
                backgroundPaused.add(entry.getKey());
            }
        }
    }

    public static void resumeFromBackground() {
        for (BlockPos pos : backgroundPaused) {
            JukeboxPlayback playback = activePlaybacks.get(pos);
            if (playback != null && playback.paused) togglePause(pos);
        }
        backgroundPaused.clear();
    }

    public static void stop(BlockPos rawPos) {
        BlockPos pos = rawPos.immutable();
        pendingRequests.remove(pos);
        bufferingStatus.remove(pos);
        JukeboxPlayback pb = activePlaybacks.remove(pos);
        if (pb != null) {
            pb.stop();
        }
        playOrders.remove(pos);
        playIndexes.remove(pos);
    }

    public static void stopAll() {
        pendingRequests.clear();
        bufferingStatus.clear();
        for (JukeboxPlayback pb : activePlaybacks.values()) {
            pb.stop();
        }
        activePlaybacks.clear();
        playOrders.clear();
        playIndexes.clear();
        backgroundPaused.clear();
    }

    public static void tick() {
        if (activePlaybacks.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            stopAll();
            return;
        }

        List<BlockPos> finishedTracks = new ArrayList<>();
        Iterator<Map.Entry<BlockPos, JukeboxPlayback>> it = activePlaybacks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, JukeboxPlayback> entry = it.next();
            BlockPos pos = entry.getKey();
            JukeboxPlayback pb = entry.getValue();

            // 1. Verify Jukebox block still exists at position
            if (!mc.level.getBlockState(pos).is(Blocks.JUKEBOX)) {
                pb.stop();
                it.remove();
                if (mc.player != null) {
                    mc.player.sendSystemMessage(
                            Component.literal("§c[ShtarOS Jukebox] Jukebox at [" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "] was broken.")
                    );
                }
                continue;
            }

            // 2. Update streaming buffers
            if (!pb.paused && pb.channel != null) {
                onSoundThread(() -> {
                    try {
                        pb.channel.updateStream();

                        // Starvation recovery:
                        // If OpenAL momentarily exhausted queued buffers, state transitions to AL_STOPPED.
                        // When updateStream() refills new buffers, we MUST call play() to restart hardware output.
                        if (pb.channel.stopped()) {
                            if (pb.audioStream.isFinished()) {
                                System.out.println("[ShtarOS Jukebox] Finished playback for: " + pb.song.title());
                                pb.stop();
                                activePlaybacks.remove(pos);
                                mc.execute(() -> next(pos));
                            } else {
                                // Buffer starvation recovery
                                pb.channel.play();
                            }
                        }
                    } catch (Throwable t) {
                        System.err.println("[ShtarOS Jukebox] Error updating stream: " + t.getMessage());
                        pb.stop();
                        activePlaybacks.remove(pos);
                    }
                });
            }
        }
        for (BlockPos pos : finishedTracks) next(pos);
    }

    private static Channel acquireChannel() {
        try {
            SoundManager sm = Minecraft.getInstance().getSoundManager();
            if (sm instanceof SoundManagerAccessor smAccessor) {
                SoundEngine se = smAccessor.getSoundEngine();
                if (se instanceof SoundEngineAccessor seAccessor) {
                    Library library = seAccessor.getLibrary();
                    if (library != null) {
                        Channel ch = library.acquireChannel(Library.Pool.STREAMING);
                        if (ch != null) {
                            return ch;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            System.err.println("[ShtarOS Jukebox] Library channel acquisition warning: " + t.getMessage());
        }

        try {
            Method createMethod = Channel.class.getDeclaredMethod("create");
            createMethod.setAccessible(true);
            return (Channel) createMethod.invoke(null);
        } catch (Throwable t) {
            System.err.println("[ShtarOS Jukebox] Reflection channel creation failed: " + t.getMessage());
            return null;
        }
    }

    private static void releaseChannel(Channel channel) {
        if (channel == null) return;
        onSoundThread(() -> {
            try {
                channel.stop();
                SoundManager sm = Minecraft.getInstance().getSoundManager();
                if (sm instanceof SoundManagerAccessor smAccessor) {
                    SoundEngine se = smAccessor.getSoundEngine();
                    if (se instanceof SoundEngineAccessor seAccessor) {
                        Library library = seAccessor.getLibrary();
                        if (library != null) {
                            library.releaseChannel(channel);
                            return;
                        }
                    }
                }
            } catch (Throwable ignored) {}

            try {
                channel.destroy();
            } catch (Throwable ignored) {}
        });
    }

    private static void onSoundThread(Runnable task) {
        SoundManager manager = Minecraft.getInstance().getSoundManager();
        if (manager instanceof SoundManagerAccessor soundManager) {
            SoundEngine engine = soundManager.getSoundEngine();
            if (engine instanceof SoundEngineAccessor soundEngine) {
                SoundEngineExecutor executor = soundEngine.getExecutor();
                if (executor != null) {
                    executor.execute(task);
                    return;
                }
            }
        }
        task.run();
    }
}
