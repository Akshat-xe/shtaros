import shtaros.audio.ShtarOSBrowserSurroundFeed;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;

public final class ShtarOSBrowserFeedCheck {
    public static void main(String[] args) throws Exception {
        ShtarOSBrowserSurroundFeed feed = new ShtarOSBrowserSurroundFeed(44100, 1);
        for (int packet = 0; packet < 300; packet++) {
            float[] samples = new float[441];
            Arrays.fill(samples, packet / 300f);
            feed.accept(FloatBuffer.wrap(samples), FloatBuffer.wrap(samples), samples.length);
        }
        if (!feed.isPrimed()) throw new AssertionError("Feed never primed");
        ByteBuffer recent = feed.speaker(0).read(8192);
        if (recent.remaining() != 8192) throw new AssertionError("Unexpected OpenAL buffer length: " + recent.remaining());
        if (recent.order(ByteOrder.LITTLE_ENDIAN).getShort(0) < 5000)
            throw new AssertionError("Packets older than 2 seconds were retained: " + recent.order(ByteOrder.LITTLE_ENDIAN).getShort(0));
        if (feed.droppedPackets() == 0) throw new AssertionError("Old packets were not dropped");
        feed.close();

        ShtarOSBrowserSurroundFeed startup = new ShtarOSBrowserSurroundFeed(44100, 1);
        float[] samples = new float[1024];
        Arrays.fill(samples, .5f);
        for (int i = 0; i < 16; i++)
            startup.accept(FloatBuffer.wrap(samples), FloatBuffer.wrap(samples), samples.length);
        if (!startup.isPrimed()) throw new AssertionError("Startup reserve not reached");
        int bytes = 0;
        for (int i = 0; i < 4; i++) {
            ByteBuffer chunk = startup.speaker(0).read(8192);
            if (chunk != null) bytes += chunk.remaining();
        }
        if (bytes != 16 * 1024 * 2) throw new AssertionError("PCM padded or lost at startup: " + bytes);
        if (startup.framesRead() != 16L * 1024) throw new AssertionError("Output frame counter wrong: " + startup.framesRead());
        
        // Test underrun concealment: when queue is empty, returns silence cushion to prevent channel stop
        ByteBuffer cushion = startup.speaker(0).read(8192);
        if (cushion == null || cushion.remaining() != 512)
            throw new AssertionError("Underrun concealment failed to provide silence cushion: " + (cushion == null ? "null" : cushion.remaining()));
        
        startup.close();
        System.out.println("PASS: bounded recent PCM, 350 ms reserve, underrun concealment verified");
    }
}
