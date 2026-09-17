package net.minecraft.network;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.io.IOException;
import java.util.List;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import org.slf4j.Logger;

/**
 * Main netty packet decoder. Reads the packet ID as a VarInt and creates the corresponding packet
 * based on the current {@link ConnectionProtocol}.
 */
public class PacketDecoder<T extends PacketListener> extends ByteToMessageDecoder implements ProtocolSwapHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ProtocolInfo<T> protocolInfo;

    public PacketDecoder(ProtocolInfo<T> protocolInfo) {
        this.protocolInfo = protocolInfo;
    }

    @Override
    protected void decode(ChannelHandlerContext context, ByteBuf in, List<Object> out) throws Exception {
        int i = in.readableBytes();
        if (i != 0) {
            Packet<? super T> packet = this.protocolInfo.codec().decode(in);
            PacketType<? extends Packet<? super T>> packettype = packet.type();
            JvmProfiler.INSTANCE.onPacketReceived(this.protocolInfo.id(), packettype, context.channel().remoteAddress(), i);
            if (in.readableBytes() > 0) {
                throw new IOException(
                    "Packet "
                        + this.protocolInfo.id().id()
                        + "/"
                        + packettype
                        + " ("
                        + packet.getClass().getSimpleName()
                        + ") was larger than I expected, found "
                        + in.readableBytes()
                        + " bytes extra whilst reading packet "
                        + packettype
                );
            } else {
                out.add(packet);
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(
                        Connection.PACKET_RECEIVED_MARKER,
                        " IN: [{}:{}] {} -> {} bytes",
                        this.protocolInfo.id().id(),
                        packettype,
                        packet.getClass().getName(),
                        i
                    );
                }

                ProtocolSwapHandler.handleInboundTerminalPacket(context, packet);
            }
        }
    }
}
