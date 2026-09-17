package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import java.util.zip.Deflater;

/**
 * Handles compression of network traffic.
 *
 * @see Connection#setupCompression
 */
public class CompressionEncoder extends MessageToByteEncoder<ByteBuf> {
    private final byte[] encodeBuf = new byte[8192];
    private final Deflater deflater;
    private int threshold;
    private static final boolean DISABLE_PACKET_DEBUG = Boolean.parseBoolean(System.getProperty("neoforge.disablePacketCompressionDebug", "false"));
    private static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger();

    public CompressionEncoder(int threshold) {
        this.threshold = threshold;
        this.deflater = new Deflater();
    }

    protected void encode(ChannelHandlerContext context, ByteBuf encodingByteBuf, ByteBuf byteBuf) {
        int i = encodingByteBuf.readableBytes();
        if (!DISABLE_PACKET_DEBUG && i > net.minecraft.network.CompressionDecoder.MAXIMUM_UNCOMPRESSED_LENGTH) {
            encodingByteBuf.markReaderIndex();
            LOGGER.error("Attempted to send packet over maximum protocol size: {} > {}\nData:\n{}", i, net.minecraft.network.CompressionDecoder.MAXIMUM_UNCOMPRESSED_LENGTH,
                    net.neoforged.neoforge.logging.PacketDump.getContentDump(encodingByteBuf));
            encodingByteBuf.resetReaderIndex();
        }
        if (i > 8388608) {
            throw new IllegalArgumentException("Packet too big (is " + i + ", should be less than 8388608)");
        } else {
            if (i < this.threshold) {
                VarInt.write(byteBuf, 0);
                byteBuf.writeBytes(encodingByteBuf);
            } else {
                byte[] abyte = new byte[i];
                encodingByteBuf.readBytes(abyte);
                VarInt.write(byteBuf, abyte.length);
                this.deflater.setInput(abyte, 0, i);
                this.deflater.finish();

                while (!this.deflater.finished()) {
                    int j = this.deflater.deflate(this.encodeBuf);
                    byteBuf.writeBytes(this.encodeBuf, 0, j);
                }

                this.deflater.reset();
            }
        }
    }

    public int getThreshold() {
        return this.threshold;
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }
}
