package net.minecraft.server.network;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.net.SocketAddress;
import java.util.Locale;
import net.minecraft.server.ServerInfo;
import org.slf4j.Logger;

public class LegacyQueryHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ServerInfo server;

    public LegacyQueryHandler(ServerInfo server) {
        this.server = server;
    }

    @Override
    public void channelRead(ChannelHandlerContext context, Object message) {
        ByteBuf bytebuf = (ByteBuf)message;
        bytebuf.markReaderIndex();
        boolean flag = true;

        try {
            try {
                if (bytebuf.readUnsignedByte() != 254) {
                    return;
                }

                SocketAddress socketaddress = context.channel().remoteAddress();
                int i = bytebuf.readableBytes();
                if (i == 0) {
                    LOGGER.debug("Ping: (<1.3.x) from {}", socketaddress);
                    String s = createVersion0Response(this.server);
                    sendFlushAndClose(context, createLegacyDisconnectPacket(context.alloc(), s));
                } else {
                    if (bytebuf.readUnsignedByte() != 1) {
                        return;
                    }

                    if (bytebuf.isReadable()) {
                        if (!readCustomPayloadPacket(bytebuf)) {
                            return;
                        }

                        LOGGER.debug("Ping: (1.6) from {}", socketaddress);
                    } else {
                        LOGGER.debug("Ping: (1.4-1.5.x) from {}", socketaddress);
                    }

                    String s1 = createVersion1Response(this.server);
                    sendFlushAndClose(context, createLegacyDisconnectPacket(context.alloc(), s1));
                }

                bytebuf.release();
                flag = false;
            } catch (RuntimeException runtimeexception) {
            }
        } finally {
            if (flag) {
                bytebuf.resetReaderIndex();
                context.channel().pipeline().remove(this);
                context.fireChannelRead(message);
            }
        }
    }

    private static boolean readCustomPayloadPacket(ByteBuf buffer) {
        short short1 = buffer.readUnsignedByte();
        if (short1 != 250) {
            return false;
        } else {
            String s = LegacyProtocolUtils.readLegacyString(buffer);
            if (!"MC|PingHost".equals(s)) {
                return false;
            } else {
                int i = buffer.readUnsignedShort();
                if (buffer.readableBytes() != i) {
                    return false;
                } else {
                    short short2 = buffer.readUnsignedByte();
                    if (short2 < 73) {
                        return false;
                    } else {
                        String s1 = LegacyProtocolUtils.readLegacyString(buffer);
                        int j = buffer.readInt();
                        return j <= 65535;
                    }
                }
            }
        }
    }

    private static String createVersion0Response(ServerInfo server) {
        return String.format(Locale.ROOT, "%s\u00a7%d\u00a7%d", server.getMotd(), server.getPlayerCount(), server.getMaxPlayers());
    }

    private static String createVersion1Response(ServerInfo server) {
        return String.format(
            Locale.ROOT,
            "\u00a71\u0000%d\u0000%s\u0000%s\u0000%d\u0000%d",
            127,
            server.getServerVersion(),
            server.getMotd(),
            server.getPlayerCount(),
            server.getMaxPlayers()
        );
    }

    private static void sendFlushAndClose(ChannelHandlerContext context, ByteBuf buffer) {
        context.pipeline().firstContext().writeAndFlush(buffer).addListener(ChannelFutureListener.CLOSE);
    }

    private static ByteBuf createLegacyDisconnectPacket(ByteBufAllocator bufferAllocator, String reason) {
        ByteBuf bytebuf = bufferAllocator.buffer();
        bytebuf.writeByte(255);
        LegacyProtocolUtils.writeLegacyString(bytebuf, reason);
        return bytebuf;
    }
}
