package net.minecraft.network;

import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.protocol.Packet;

public interface ProtocolSwapHandler {
    static void handleInboundTerminalPacket(ChannelHandlerContext context, Packet<?> packet) {
        if (packet.isTerminal()) {
            context.channel().config().setAutoRead(false);
            context.pipeline().addBefore(context.name(), "inbound_config", new UnconfiguredPipelineHandler.Inbound());
            context.pipeline().remove(context.name());
        }
    }

    static void handleOutboundTerminalPacket(ChannelHandlerContext context, Packet<?> packet) {
        if (packet.isTerminal()) {
            context.pipeline().addAfter(context.name(), "outbound_config", new UnconfiguredPipelineHandler.Outbound());
            context.pipeline().remove(context.name());
        }
    }
}
