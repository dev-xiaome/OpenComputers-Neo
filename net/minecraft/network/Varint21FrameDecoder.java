package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Counterpart to {@link Varint21LengthFieldPrepender}. Decodes each frame ("packet") by first reading its length and then its data.
 */
public class Varint21FrameDecoder extends ByteToMessageDecoder {
    private static final int MAX_VARINT21_BYTES = 3;
    private final ByteBuf helperBuf = Unpooled.directBuffer(3);
    @Nullable
    private final BandwidthDebugMonitor monitor;

    public Varint21FrameDecoder(@Nullable BandwidthDebugMonitor monitor) {
        this.monitor = monitor;
    }

    @Override
    protected void handlerRemoved0(ChannelHandlerContext context) {
        this.helperBuf.release();
    }

    private static boolean copyVarint(ByteBuf in, ByteBuf out) {
        for (int i = 0; i < 3; i++) {
            if (!in.isReadable()) {
                return false;
            }

            byte b0 = in.readByte();
            out.writeByte(b0);
            if (!VarInt.hasContinuationBit(b0)) {
                return true;
            }
        }

        throw new CorruptedFrameException("length wider than 21-bit");
    }

    @Override
    protected void decode(ChannelHandlerContext context, ByteBuf in, List<Object> out) {
        in.markReaderIndex();
        this.helperBuf.clear();
        if (!copyVarint(in, this.helperBuf)) {
            in.resetReaderIndex();
        } else {
            int i = VarInt.read(this.helperBuf);
            if (in.readableBytes() < i) {
                in.resetReaderIndex();
            } else {
                if (this.monitor != null) {
                    this.monitor.onReceive(i + VarInt.getByteSize(i));
                }

                out.add(in.readBytes(i));
            }
        }
    }
}
