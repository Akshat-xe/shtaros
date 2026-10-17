package shtaros.audio;

import net.minecraft.client.sounds.AudioStream;

/** A source of eight synchronized mono streams for the physical theater speakers. */
public interface ShtarOSSpeakerFeed extends AutoCloseable {
    AudioStream speaker(int index);
    void disable(int index);
    boolean isFinished();
    @Override void close();
}
