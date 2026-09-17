package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.network.protocol.Packet;

public class UnconfiguredPipelineHandler {
    public static <T extends PacketListener> UnconfiguredPipelineHandler.InboundConfigurationTask setupInboundProtocol(ProtocolInfo<T> protocolInfo) {
        return setupInboundHandler(new PacketDecoder<>(protocolInfo));
    }

    private static UnconfiguredPipelineHandler.InboundConfigurationTask setupInboundHandler(ChannelInboundHandler handler) {
        return p_320663_ -> {
            p_320663_.pipeline().replace(p_320663_.name(), "decoder", handler);
            p_320663_.channel().config().setAutoRead(true);
        };
    }

    public static <T extends PacketListener> UnconfiguredPipelineHandler.OutboundConfigurationTask setupOutboundProtocol(ProtocolInfo<T> protocolInfo) {
        return setupOutboundHandler(new PacketEncoder<>(protocolInfo));
    }

    private static UnconfiguredPipelineHandler.OutboundConfigurationTask setupOutboundHandler(ChannelOutboundHandler handler) {
        return p_320755_ -> p_320755_.pipeline().replace(p_320755_.name(), "encoder", handler);
    }

    public static class Inbound extends ChannelDuplexHandler {
        @Override
        public void channelRead(ChannelHandlerContext context, Object message) {
            if (!(message instanceof ByteBuf) && !(message instanceof Packet)) {
                context.fireChannelRead(message);
            } else {
                ReferenceCountUtil.release(message);
                throw new DecoderException("Pipeline has no inbound protocol configured, can't process packet " + message);
            }
        }

        @Override
        public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
            if (message instanceof UnconfiguredPipelineHandler.InboundConfigurationTask unconfiguredpipelinehandler$inboundconfigurationtask) {
                try {
                    unconfiguredpipelinehandler$inboundconfigurationtask.run(context);
                } finally {
                    ReferenceCountUtil.release(message);
                }

                promise.setSuccess();
            } else {
                context.write(message, promise);
            }
        }
    }

    @FunctionalInterface
    public interface InboundConfigurationTask {
        void run(ChannelHandlerContext context);

        default UnconfiguredPipelineHandler.InboundConfigurationTask andThen(UnconfiguredPipelineHandler.InboundConfigurationTask task) {
            return p_320785_ -> {
                this.run(p_320785_);
                task.run(p_320785_);
            };
        }
    }

    public static class Outbound extends ChannelOutboundHandlerAdapter {
        @Override
        public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
            if (message instanceof Packet) {
                ReferenceCountUtil.release(message);
                throw new EncoderException("Pipeline has no outbound protocol configured, can't process packet " + message);
            } else {
                if (message instanceof UnconfiguredPipelineHandler.OutboundConfigurationTask unconfiguredpipelinehandler$outboundconfigurationtask) {
                    try {
                        unconfiguredpipelinehandler$outboundconfigurationtask.run(context);
                    } finally {
                        ReferenceCountUtil.release(message);
                    }

                    promise.setSuccess();
                } else {
                    context.write(message, promise);
                }
            }
        }
    }

    @FunctionalInterface
    public interface OutboundConfigurationTask {
        void run(ChannelHandlerContext context);

        default UnconfiguredPipelineHandler.OutboundConfigurationTask andThen(UnconfiguredPipelineHandler.OutboundConfigurationTask task) {
            return p_320612_ -> {
                this.run(p_320612_);
                task.run(p_320612_);
            };
        }
    }
}
