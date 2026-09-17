package net.minecraft.client.sounds;

import com.jcraft.jogg.Packet;
import com.jcraft.jogg.Page;
import com.jcraft.jogg.StreamState;
import com.jcraft.jogg.SyncState;
import com.jcraft.jorbis.Block;
import com.jcraft.jorbis.Comment;
import com.jcraft.jorbis.DspState;
import com.jcraft.jorbis.Info;
import it.unimi.dsi.fastutil.floats.FloatConsumer;
import java.io.IOException;
import java.io.InputStream;
import javax.annotation.Nullable;
import javax.sound.sampled.AudioFormat;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class JOrbisAudioStream implements FloatSampleSource {
    private static final int BUFSIZE = 8192;
    private static final int PAGEOUT_RECAPTURE = -1;
    private static final int PAGEOUT_NEED_MORE_DATA = 0;
    private static final int PAGEOUT_OK = 1;
    private static final int PACKETOUT_ERROR = -1;
    private static final int PACKETOUT_NEED_MORE_DATA = 0;
    private static final int PACKETOUT_OK = 1;
    private final SyncState syncState = new SyncState();
    private final Page page = new Page();
    private final StreamState streamState = new StreamState();
    private final Packet packet = new Packet();
    private final Info info = new Info();
    private final DspState dspState = new DspState();
    private final Block block = new Block(this.dspState);
    private final AudioFormat audioFormat;
    private final InputStream input;
    private long samplesWritten;
    private long totalSamplesInStream = Long.MAX_VALUE;

    public JOrbisAudioStream(InputStream input) throws IOException {
        this.input = input;
        Comment comment = new Comment();
        Page page = this.readPage();
        if (page == null) {
            throw new IOException("Invalid Ogg file - can't find first page");
        } else {
            Packet packet = this.readIdentificationPacket(page);
            if (isError(this.info.synthesis_headerin(comment, packet))) {
                throw new IOException("Invalid Ogg identification packet");
            } else {
                for (int i = 0; i < 2; i++) {
                    packet = this.readPacket();
                    if (packet == null) {
                        throw new IOException("Unexpected end of Ogg stream");
                    }

                    if (isError(this.info.synthesis_headerin(comment, packet))) {
                        throw new IOException("Invalid Ogg header packet " + i);
                    }
                }

                this.dspState.synthesis_init(this.info);
                this.block.init(this.dspState);
                this.audioFormat = new AudioFormat((float)this.info.rate, 16, this.info.channels, true, false);
            }
        }
    }

    private static boolean isError(int value) {
        return value < 0;
    }

    @Override
    public AudioFormat getFormat() {
        return this.audioFormat;
    }

    private boolean readToBuffer() throws IOException {
        int i = this.syncState.buffer(8192);
        byte[] abyte = this.syncState.data;
        int j = this.input.read(abyte, i, 8192);
        if (j == -1) {
            return false;
        } else {
            this.syncState.wrote(j);
            return true;
        }
    }

    @Nullable
    private Page readPage() throws IOException {
        while (true) {
            int i = this.syncState.pageout(this.page);
            switch (i) {
                case -1:
                    throw new IllegalStateException("Corrupt or missing data in bitstream");
                case 0:
                    if (this.readToBuffer()) {
                        break;
                    }

                    return null;
                case 1:
                    if (this.page.eos() != 0) {
                        this.totalSamplesInStream = this.page.granulepos();
                    }

                    return this.page;
                default:
                    throw new IllegalStateException("Unknown page decode result: " + i);
            }
        }
    }

    private Packet readIdentificationPacket(Page page) throws IOException {
        this.streamState.init(page.serialno());
        if (isError(this.streamState.pagein(page))) {
            throw new IOException("Failed to parse page");
        } else {
            int i = this.streamState.packetout(this.packet);
            if (i != 1) {
                throw new IOException("Failed to read identification packet: " + i);
            } else {
                return this.packet;
            }
        }
    }

    @Nullable
    private Packet readPacket() throws IOException {
        while (true) {
            int i = this.streamState.packetout(this.packet);
            switch (i) {
                case -1:
                    throw new IOException("Failed to parse packet");
                case 0:
                    Page page = this.readPage();
                    if (page == null) {
                        return null;
                    }

                    if (!isError(this.streamState.pagein(page))) {
                        break;
                    }

                    throw new IOException("Failed to parse page");
                case 1:
                    return this.packet;
                default:
                    throw new IllegalStateException("Unknown packet decode result: " + i);
            }
        }
    }

    private long getSamplesToWrite(int samples) {
        long i = this.samplesWritten + (long)samples;
        long j;
        if (i > this.totalSamplesInStream) {
            j = this.totalSamplesInStream - this.samplesWritten;
            this.samplesWritten = this.totalSamplesInStream;
        } else {
            this.samplesWritten = i;
            j = (long)samples;
        }

        return j;
    }

    @Override
    public boolean readChunk(FloatConsumer output) throws IOException {
        float[][][] afloat = new float[1][][];
        int[] aint = new int[this.info.channels];
        Packet packet = this.readPacket();
        if (packet == null) {
            return false;
        } else if (isError(this.block.synthesis(packet))) {
            throw new IOException("Can't decode audio packet");
        } else {
            this.dspState.synthesis_blockin(this.block);

            int i;
            while ((i = this.dspState.synthesis_pcmout(afloat, aint)) > 0) {
                float[][] afloat1 = afloat[0];
                long j = this.getSamplesToWrite(i);
                switch (this.info.channels) {
                    case 1:
                        copyMono(afloat1[0], aint[0], j, output);
                        break;
                    case 2:
                        copyStereo(afloat1[0], aint[0], afloat1[1], aint[1], j, output);
                        break;
                    default:
                        copyAnyChannels(afloat1, this.info.channels, aint, j, output);
                }

                this.dspState.synthesis_read(i);
            }

            return true;
        }
    }

    private static void copyAnyChannels(float[][] source, int channels, int[] startIndexes, long samplesToWrite, FloatConsumer output) {
        for (int i = 0; (long)i < samplesToWrite; i++) {
            for (int j = 0; j < channels; j++) {
                int k = startIndexes[j];
                float f = source[j][k + i];
                output.accept(f);
            }
        }
    }

    private static void copyMono(float[] source, int startIndex, long samplesToWrite, FloatConsumer output) {
        for (int i = startIndex; (long)i < (long)startIndex + samplesToWrite; i++) {
            output.accept(source[i]);
        }
    }

    private static void copyStereo(float[] leftSource, int leftStartIndex, float[] rightSource, int rightStartIndex, long samplesToWrite, FloatConsumer output) {
        for (int i = 0; (long)i < samplesToWrite; i++) {
            output.accept(leftSource[leftStartIndex + i]);
            output.accept(rightSource[rightStartIndex + i]);
        }
    }

    @Override
    public void close() throws IOException {
        this.input.close();
    }
}
