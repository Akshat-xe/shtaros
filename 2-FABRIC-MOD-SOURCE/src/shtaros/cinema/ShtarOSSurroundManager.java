package shtaros.cinema;

import com.mojang.blaze3d.audio.Channel;
import com.mojang.blaze3d.audio.Library;
import shtaros.audio.ShtarOSSurroundFeed;
import shtaros.audio.ShtarOSBrowserSurroundFeed;
import shtaros.audio.ShtarOSSpeakerFeed;
import shtaros.mixin.SoundEngineAccessor;
import shtaros.mixin.SoundManagerAccessor;
import shtaros.mixin.ChannelAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEngineExecutor;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.AL10;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Eight synchronized mono feeds, each attached to a physical Minecraft speaker block. */
public final class ShtarOSSurroundManager {
    public enum SurroundMode {
        PHYSICAL("Physical 8-speaker surround");
        public final String displayName;
        SurroundMode(String name) { displayName = name; }
    }

    // Ordinals match the interleaved PCM channels from the daemon.
    public enum SpeakerRole {
        FRONT_LEFT, FRONT_RIGHT, SIDE_LEFT_FRONT, SIDE_LEFT_REAR,
        SIDE_RIGHT_FRONT, SIDE_RIGHT_REAR, REAR_LEFT, REAR_RIGHT
    }

    public static final class SpeakerInfo {
        public final BlockPos pos;
        public final SpeakerRole role;
        private volatile Channel channel;
        SpeakerInfo(BlockPos pos, SpeakerRole role) { this.pos = pos; this.role = role; }
    }

    private static final List<SpeakerInfo> speakers = new CopyOnWriteArrayList<>();
    private static volatile ShtarOSSpeakerFeed feed;
    private static volatile float masterVolume = 1.0f;
    private static volatile boolean playing;
    private static volatile boolean started;
    private static volatile int generation;
    private static volatile int activeMask;

    public static SurroundMode getMode() { return SurroundMode.PHYSICAL; }
    public static void cycleMode() { /* A fixed physical layout has one honest mode. */ }
    public static float getVolume() { return masterVolume; }

    public static void setVolume(float vol) {
        masterVolume = Math.max(0, Math.min(1, vol));
        float volume = masterVolume;
        onSoundThread(() -> {
            for (SpeakerInfo speaker : speakers) {
                if (speaker.channel != null) speaker.channel.setVolume(volume);
            }
        });
    }

    public static void cycleVolume() {
        if (masterVolume >= 1) setVolume(0.75f);
        else if (masterVolume >= 0.75f) setVolume(0.5f);
        else if (masterVolume >= 0.5f) setVolume(0.25f);
        else setVolume(1);
    }

    public static void registerSpeaker(BlockPos pos, SpeakerRole role) {
        speakers.removeIf(s -> s.pos.equals(pos) || s.role == role);
        speakers.add(new SpeakerInfo(pos, role));
    }

    public static void clearSpeakers() {
        stop();
        speakers.clear();
    }

    public static List<SpeakerInfo> getSpeakers() { return Collections.unmodifiableList(speakers); }
    public static long activeSpeakerCount() {
        Minecraft mc = Minecraft.getInstance();
        return speakers.stream().filter(s -> valid(s, mc)).count();
    }
    public static boolean isPlaying() { return playing; }
    public static boolean isReady() { return started; }

    public static synchronized void play(String videoId, int startSeconds) {
        stop();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || speakers.stream().noneMatch(s -> valid(s, mc))) return;
        playing = true;
        started = false;
        int session = ++generation;
        int mask = 0;
        for (SpeakerInfo speaker : speakers) {
            if (valid(speaker, mc)) mask |= 1 << speaker.role.ordinal();
        }
        ShtarOSSurroundFeed next = new ShtarOSSurroundFeed(videoId, startSeconds, mask);
        feed = next;
        CompletableFuture.runAsync(() -> {
            if (!next.waitForInitialData(10000)) {
                mc.execute(() -> {
                    if (generation != session) return;
                    String reason = next.error() != null ? next.error() : "Audio did not start within 10 seconds";
                    ShtarOSCinemaManager.log("Audio error: " + reason);
                    ShtarOSCinemaManager.stopPlayback();
                    if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§c[ShtarOS] " + reason));
                });
                return;
            }
            mc.execute(() -> {
                if (!playing || generation != session || feed != next) return;
                int validMask = speakerMask(mc);
                onSoundThread(() -> {
                    if (!playing || generation != session || feed != next) return;
                    int attached = attach(next, validMask);
                    started = attached > 0;
                    mc.execute(() -> {
                        if (generation != session || feed != next) return;
                        if (attached == 0) {
                            ShtarOSCinemaManager.stopPlayback();
                            if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§c[ShtarOS] Audio failed: no usable speaker channels."));
                        } else if (mc.player != null) {
                            mc.player.sendSystemMessage(Component.literal("§a[ShtarOS] Audio live on " + attached + " speakers."));
                        }
                    });
                });
            });
        });
    }

    /** Start the same physical OpenAL layout from Rinku's browser audio capture. */
    public static synchronized void playBrowser(ShtarOSBrowserSurroundFeed next) {
        if (next == null || next.isFinished()) return;
        stop();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || speakers.stream().noneMatch(s -> valid(s, mc))) {
            System.err.println("[ShtarOS Surround] Browser audio has no valid note-block speakers (registered="
                    + speakers.size() + ")");
            next.close();
            return;
        }
        playing = true;
        feed = next;
        int validMask = speakerMask(mc);
        activeMask = validMask;
        onSoundThread(() -> {
            if (!playing || feed != next) return;
            int attached = attach(next, validMask);
            started = attached > 0;
            mc.execute(() -> {
                if (feed != next) return;
                if (attached == 0) {
                    stop();
                    ShtarOSCinemaManager.log("Browser audio: no OpenAL speaker channels available");
                    System.err.println("[ShtarOS Surround] Browser audio attached to 0 speakers");
                } else {
                    ShtarOSCinemaManager.log("Browser audio captured on " + attached + " physical speakers");
                    System.out.println("[ShtarOS Surround] Browser audio attached to " + attached + " speakers");
                }
            });
        });
    }

    public static synchronized void stopBrowser(ShtarOSBrowserSurroundFeed source) {
        if (feed == source) stop();
        else if (source != null) source.close();
    }

    private static int attach(ShtarOSSpeakerFeed source, int validMask) {
        int attached = 0;
        for (SpeakerInfo speaker : speakers) {
            int index = speaker.role.ordinal();
            if ((validMask & (1 << index)) == 0) { source.disable(index); continue; }
            Channel ch = acquireChannel();
            if (ch == null) { source.disable(index); continue; }
            try {
                ch.setRelative(false);
                ch.setSelfPosition(Vec3.atCenterOf(speaker.pos));
                ch.linearAttenuation(48.0f);
                ch.setVolume(masterVolume);
                ch.attachBufferStream(source.speaker(index));
                ch.play();
                speaker.channel = ch;
                attached++;
            } catch (Throwable t) {
                source.disable(index);
                releaseChannel(ch);
                System.err.println("[ShtarOS Surround] Speaker " + speaker.role + ": " + t.getMessage());
            }
        }
        return attached;
    }

    public static synchronized void stop() {
        playing = false;
        started = false;
        generation++;
        ShtarOSSpeakerFeed oldFeed = feed;
        feed = null;
        if (oldFeed != null) oldFeed.close();
        List<SpeakerInfo> oldSpeakers = List.copyOf(speakers);
        onSoundThread(() -> {
            for (SpeakerInfo speaker : oldSpeakers) {
                Channel channel = speaker.channel;
                speaker.channel = null;
                if (channel != null) releaseChannel(channel);
            }
        });
    }

    public static void tick() {
        if (!playing) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { stop(); return; }
        int validMask = speakerMask(mc);
        activeMask = validMask;
        ShtarOSSpeakerFeed current = feed;
        if (current == null) return;
        onSoundThread(() -> pumpChannels(current, validMask));
    }

    private static void pumpChannels(ShtarOSSpeakerFeed current, int validMask) {
        if (!playing || feed != current || current == null) return;
        Minecraft mc = Minecraft.getInstance();
        for (SpeakerInfo speaker : speakers) {
            Channel channel = speaker.channel;
            if (channel == null) continue;
            int roleIdx = speaker.role.ordinal();
            if ((validMask & (1 << roleIdx)) == 0) {
                current.disable(roleIdx);
                releaseChannel(channel);
                speaker.channel = null;
                continue;
            }
            try {
                channel.updateStream();
                if (channel.stopped() && !current.isFinished()) {
                    channel.play();
                }
            } catch (Throwable t) {
                current.disable(roleIdx);
                releaseChannel(channel);
                speaker.channel = null;
                System.err.println("[ShtarOS Surround] Speaker " + speaker.role + " error: " + t.getMessage());
            }
        }
        if (started && speakers.stream().noneMatch(s -> s.channel != null)) {
            mc.execute(() -> { if (feed == current) stop(); });
        } else if (current.isFinished() && speakers.stream().allMatch(s -> s.channel == null || s.channel.stopped())) {
            mc.execute(() -> { if (feed == current) stop(); });
        }
    }

    private static int speakerMask(Minecraft mc) {
        int mask = 0;
        for (SpeakerInfo speaker : speakers) if (valid(speaker, mc)) mask |= 1 << speaker.role.ordinal();
        return mask;
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
        // Keep audio usable if another mod replaces Minecraft's sound engine.
        task.run();
    }

    private static boolean valid(SpeakerInfo speaker, Minecraft mc) {
        return mc.level != null && (mc.level.getBlockState(speaker.pos).is(Blocks.NOTE_BLOCK)
                || mc.level.getBlockState(speaker.pos).is(Blocks.JUKEBOX));
    }

    private static Channel acquireChannel() {
        try {
            SoundManager sm = Minecraft.getInstance().getSoundManager();
            if (sm instanceof SoundManagerAccessor smAccessor) {
                SoundEngine se = smAccessor.getSoundEngine();
                if (se instanceof SoundEngineAccessor seAccessor) {
                    Library library = seAccessor.getLibrary();
                    if (library != null) {
                        Channel channel = library.acquireChannel(Library.Pool.STREAMING);
                        if (channel != null) return channel;
                    }
                }
            }
        } catch (Throwable t) {
            System.err.println("[ShtarOS Surround] Channel acquisition: " + t.getMessage());
        }
        try {
            Method create = Channel.class.getDeclaredMethod("create");
            create.setAccessible(true);
            return (Channel) create.invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void releaseChannel(Channel channel) {
        if (channel == null) return;
        try {
            channel.stop();
            SoundManager sm = Minecraft.getInstance().getSoundManager();
            if (sm instanceof SoundManagerAccessor smAccessor) {
                SoundEngine se = smAccessor.getSoundEngine();
                if (se instanceof SoundEngineAccessor seAccessor) {
                    Library library = seAccessor.getLibrary();
                    if (library != null) { library.releaseChannel(channel); return; }
                }
            }
        } catch (Throwable ignored) {}
        try { channel.destroy(); } catch (Throwable ignored) {}
    }
}
