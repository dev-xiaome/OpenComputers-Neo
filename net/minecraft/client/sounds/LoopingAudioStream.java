package net.minecraft.client.sounds;

import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import javax.sound.sampled.AudioFormat;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The LoopingAudioStream class provides an AudioStream that loops indefinitely over the provided InputStream.
 */
@OnlyIn(Dist.CLIENT)
public class LoopingAudioStream implements AudioStream {
    private final LoopingAudioStream.AudioStreamProvider provider;
    private AudioStream stream;
    private final BufferedInputStream bufferedInputStream;

    public LoopingAudioStream(LoopingAudioStream.AudioStreamProvider provider, InputStream inputStream) throws IOException {
        this.provider = provider;
        this.bufferedInputStream = new BufferedInputStream(inputStream);
        this.bufferedInputStream.mark(Integer.MAX_VALUE);
        this.stream = provider.create(new LoopingAudioStream.NoCloseBuffer(this.bufferedInputStream));
    }

    @Override
    public AudioFormat getFormat() {
        return this.stream.getFormat();
    }

    /**
     * Reads audio data from the stream and returns a byte buffer containing at most the specified number of bytes.
     * The method reads audio frames from the stream and adds them to the output buffer until the buffer contains at least the specified number of bytes or the end fo the stream is reached.
     * @return a byte buffer containing at most the specified number of bytes to read
     * @throws IOException if an I/O error occurs while reading the audio data
     *
     * @param size the maximum number of bytes to read
     */
    @Override
    public ByteBuffer read(int size) throws IOException {
        ByteBuffer bytebuffer = this.stream.read(size);
        if (!bytebuffer.hasRemaining()) {
            this.stream.close();
            this.bufferedInputStream.reset();
            this.stream = this.provider.create(new LoopingAudioStream.NoCloseBuffer(this.bufferedInputStream));
            bytebuffer = this.stream.read(size);
        }

        return bytebuffer;
    }

    @Override
    public void close() throws IOException {
        this.stream.close();
        this.bufferedInputStream.close();
    }

    /**
     * A functional interface for providing an {@linkplain AudioStream} from an {@linkplain InputStream}.
     */
    @FunctionalInterface
    @OnlyIn(Dist.CLIENT)
    public interface AudioStreamProvider {
        /**
         * Creates an {@linkplain AudioStream} from the specified {@linkplain InputStream}.
         * @return the created {@linkplain AudioStream}
         * @throws IOException if an I/O error occurs while creating the {@linkplain AudioStream}
         *
         * @param inputStream the input stream to create the {@linkplain AudioStream} from
         */
        AudioStream create(InputStream inputStream) throws IOException;
    }

    /**
     * A {@linkplain FilterInputStream} that does not close the underlying {@linkplain InputStream}.
     */
    @OnlyIn(Dist.CLIENT)
    static class NoCloseBuffer extends FilterInputStream {
        NoCloseBuffer(InputStream inputStream) {
            super(inputStream);
        }

        @Override
        public void close() {
        }
    }
}
