package net.minecraft.network;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.MessageToMessageDecoder;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.protocol.BundlerInfo;
import net.minecraft.network.protocol.Packet;

public class PacketBundlePacker extends MessageToMessageDecoder<Packet<?>> {
    private final BundlerInfo bundlerInfo;
    @Nullable
    private BundlerInfo.Bundler currentBundler;

    public PacketBundlePacker(BundlerInfo bundlerInfo) {
        this.bundlerInfo = bundlerInfo;
    }

    protected void decode(ChannelHandlerContext context, Packet<?> p_packet, List<Object> p_265368_) throws Exception {
        if (this.currentBundler != null) {
            verifyNonTerminalPacket(p_packet);
            Packet<?> packet = this.currentBundler.addPacket(p_packet);
            if (packet != null) {
                this.currentBundler = null;
                p_265368_.add(packet);
            }
        } else {
            BundlerInfo.Bundler bundlerinfo$bundler = this.bundlerInfo.startPacketBundling(p_packet);
            if (bundlerinfo$bundler != null) {
                verifyNonTerminalPacket(p_packet);
                this.currentBundler = bundlerinfo$bundler;
            } else {
                p_265368_.add(p_packet);
                if (p_packet.isTerminal()) {
                    context.pipeline().remove(context.name());
                }
            }
        }
    }

    private static void verifyNonTerminalPacket(Packet<?> packet) {
        if (packet.isTerminal()) {
            throw new DecoderException("Terminal message received in bundle");
        }
    }
}
