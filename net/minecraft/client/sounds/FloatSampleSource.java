package net.minecraft.client.sounds;

import it.unimi.dsi.fastutil.floats.FloatConsumer;
import java.io.IOException;
import java.nio.ByteBuffer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface FloatSampleSource extends FiniteAudioStream {
    int EXPECTED_MAX_FRAME_SIZE = 8192;

    boolean readChunk(FloatConsumer output) throws IOException;

    /**
     * Reads audio data from the stream and returns a byte buffer containing at most the specified number of bytes.
     * The method reads audio frames from the stream and adds them to the output buffer until the buffer contains at least the specified number of bytes or the end fo the stream is reached.
     * @return a byte buffer containing at most the specified number of bytes to read
     * @throws IOException if an I/O error occurs while reading the audio data
     *
     * @param size the maximum number of bytes to read
     */
    @Override
    default ByteBuffer read(int size) throws IOException {
        ChunkedSampleByteBuf chunkedsamplebytebuf = new ChunkedSampleByteBuf(size + 8192);

        while (this.readChunk(chunkedsamplebytebuf) && chunkedsamplebytebuf.size() < size) {
        }

        return chunkedsamplebytebuf.get();
    }

    @Override
    default ByteBuffer readAll() throws IOException {
        ChunkedSampleByteBuf chunkedsamplebytebuf = new ChunkedSampleByteBuf(16384);

        while (this.readChunk(chunkedsamplebytebuf)) {
        }

        return chunkedsamplebytebuf.get();
    }
}
