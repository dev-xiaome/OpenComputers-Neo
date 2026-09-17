package net.minecraft.network.protocol.common.custom;

import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.codec.StreamMemberEncoder;
import net.minecraft.resources.ResourceLocation;

public interface CustomPacketPayload {
    CustomPacketPayload.Type<? extends CustomPacketPayload> type();

    static <B extends ByteBuf, T extends CustomPacketPayload> StreamCodec<B, T> codec(StreamMemberEncoder<B, T> encoder, StreamDecoder<B, T> decoder) {
        return StreamCodec.ofMember(encoder, decoder);
    }

    static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> createType(String id) {
        return new CustomPacketPayload.Type<>(ResourceLocation.withDefaultNamespace(id));
    }

    static <B extends FriendlyByteBuf> StreamCodec<B, CustomPacketPayload> codec(
        final CustomPacketPayload.FallbackProvider<B> fallbackProvider, List<CustomPacketPayload.TypeAndCodec<? super B, ?>> typeAndCodecs, net.minecraft.network.ConnectionProtocol protocol, net.minecraft.network.protocol.PacketFlow packetFlow
    ) {
        final Map<ResourceLocation, StreamCodec<? super B, ? extends CustomPacketPayload>> map = typeAndCodecs.stream()
            .collect(Collectors.toUnmodifiableMap(p_320895_ -> p_320895_.type().id(), CustomPacketPayload.TypeAndCodec::codec));
        return new StreamCodec<B, CustomPacketPayload>() {
            private StreamCodec<? super B, ? extends CustomPacketPayload> findCodec(ResourceLocation p_320938_) {
                StreamCodec<? super B, ? extends CustomPacketPayload> streamcodec = map.get(p_320938_);
                if (streamcodec == null) streamcodec = net.neoforged.neoforge.network.registration.NetworkRegistry.getCodec(p_320938_, protocol, packetFlow);
                return streamcodec != null ? streamcodec : fallbackProvider.create(p_320938_);
            }

            private <T extends CustomPacketPayload> void writeCap(B buffer, CustomPacketPayload.Type<T> type, CustomPacketPayload payload) {
                buffer.writeResourceLocation(type.id());
                StreamCodec<B, T> streamcodec = (StreamCodec<B, T>)this.findCodec(type.id);
                try {
                streamcodec.encode(buffer, (T)payload);
                } catch (RuntimeException e) {
                    throw new RuntimeException("Failed encoding custom payload " + type.id() + ": " + e, e); // Make it easier to debug which mod payload failed to be encoded
                }
            }

            public void encode(B buffer, CustomPacketPayload value) {
                this.writeCap(buffer, value.type(), value);
            }

            public CustomPacketPayload decode(B buffer) {
                ResourceLocation resourcelocation = buffer.readResourceLocation();
                try {
                    return (CustomPacketPayload)this.findCodec(resourcelocation).decode(buffer);
                } catch (RuntimeException e) {
                    throw new RuntimeException("Failed decoding custom payload " + resourcelocation + ": " + e, e); // Make it easier to debug which mod payload failed to be decoded
                }
            }
        };
    }

    /**
     * {@return the vanilla clientbound packet representation of this payload}
     */
    default net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket toVanillaClientbound() {
        return new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(this);
    }

    /**
     * {@return the vanilla serverbound packet representation of this payload}
     */
    default net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket toVanillaServerbound() {
        return new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(this);
    }

    public interface FallbackProvider<B extends FriendlyByteBuf> {
        StreamCodec<B, ? extends CustomPacketPayload> create(ResourceLocation id);
    }

    public static record Type<T extends CustomPacketPayload>(ResourceLocation id) {
    }

    public static record TypeAndCodec<B extends FriendlyByteBuf, T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type, StreamCodec<B, T> codec) {
    }
}
