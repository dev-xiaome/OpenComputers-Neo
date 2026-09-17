package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.DecoderException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Handles decompression of network traffic.
 *
 * @see Connection#setupCompression
 */
public class CompressionDecoder extends ByteToMessageDecoder {
    public static final int MAXIMUM_COMPRESSED_LENGTH = 2097152;
    public static final int MAXIMUM_UNCOMPRESSED_LENGTH = 8388608;
    private final Inflater inflater;
    private int threshold;
    private boolean validateDecompressed;

    public CompressionDecoder(int threshold, boolean validateDecompressed) {
        this.threshold = threshold;
        this.validateDecompressed = validateDecompressed;
        this.inflater = new Inflater();
    }

    @Override
    protected void decode(ChannelHandlerContext context, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() != 0) {
            int i = VarInt.read(in);
            if (i == 0) {
                out.add(in.readBytes(in.readableBytes()));
            } else {
                if (this.validateDecompressed) {
                    if (i < this.threshold) {
                        throw new DecoderException("Badly compressed packet - size of " + i + " is below server threshold of " + this.threshold);
                    }

                    if (i > 8388608) {
                        throw new DecoderException("Badly compressed packet - size of " + i + " is larger than protocol maximum of 8388608");
                    }
                }

                this.setupInflaterInput(in);
                ByteBuf bytebuf = this.inflate(context, i);
                this.inflater.reset();
                out.add(bytebuf);
            }
        }
    }

    private void setupInflaterInput(ByteBuf buffer) {
        ByteBuffer bytebuffer;
        if (buffer.nioBufferCount() > 0) {
            bytebuffer = buffer.nioBuffer();
            buffer.skipBytes(buffer.readableBytes());
        } else {
            bytebuffer = ByteBuffer.allocateDirect(buffer.readableBytes());
            buffer.readBytes(bytebuffer);
            bytebuffer.flip();
        }

        this.inflater.setInput(bytebuffer);
    }

    private ByteBuf inflate(ChannelHandlerContext context, int size) throws DataFormatException {
        ByteBuf bytebuf = context.alloc().directBuffer(size);

        try {
            ByteBuffer bytebuffer = bytebuf.internalNioBuffer(0, size);
            int i = bytebuffer.position();
            this.inflater.inflate(bytebuffer);
            int j = bytebuffer.position() - i;
            if (j != size) {
                throw new DecoderException(
                    "Badly compressed packet - actual length of uncompressed payload " + j + " is does not match declared size " + size
                );
            } else {
                bytebuf.writerIndex(bytebuf.writerIndex() + j);
                return bytebuf;
            }
        } catch (Exception exception) {
            bytebuf.release();
            throw exception;
        }
    }

    public void setThreshold(int threshold, boolean validateDecompressed) {
        this.threshold = threshold;
        this.validateDecompressed = validateDecompressed;
    }
}
