package net.minecraft.network;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import java.util.List;
import net.minecraft.network.protocol.BundlerInfo;
import net.minecraft.network.protocol.Packet;

public class PacketBundleUnpacker extends MessageToMessageEncoder<Packet<?>> {
    private final BundlerInfo bundlerInfo;

    public PacketBundleUnpacker(BundlerInfo bundlerInfo) {
        this.bundlerInfo = bundlerInfo;
    }

    protected void encode(ChannelHandlerContext context, Packet<?> packet, List<Object> p_265735_) throws Exception {
        this.bundlerInfo.unbundlePacket(packet, p_265735_::add, context);
        if (packet.isTerminal()) {
            context.pipeline().remove(context.name());
        }
    }
}
