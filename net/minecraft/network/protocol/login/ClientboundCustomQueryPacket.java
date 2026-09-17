package net.minecraft.network.protocol.login;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.login.custom.CustomQueryPayload;
import net.minecraft.network.protocol.login.custom.DiscardedQueryPayload;
import net.minecraft.resources.ResourceLocation;

public record ClientboundCustomQueryPacket(int transactionId, CustomQueryPayload payload) implements Packet<ClientLoginPacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundCustomQueryPacket> STREAM_CODEC = Packet.codec(
        ClientboundCustomQueryPacket::write, ClientboundCustomQueryPacket::new
    );
    private static final int MAX_PAYLOAD_SIZE = 1048576;

    private ClientboundCustomQueryPacket(FriendlyByteBuf p_179810_) {
        this(p_179810_.readVarInt(), readPayload(p_179810_.readResourceLocation(), p_179810_));
    }

    private static CustomQueryPayload readPayload(ResourceLocation id, FriendlyByteBuf buffer) {
        return readUnknownPayload(id, buffer);
    }

    private static DiscardedQueryPayload readUnknownPayload(ResourceLocation id, FriendlyByteBuf buffer) {
        int i = buffer.readableBytes();
        if (i >= 0 && i <= 1048576) {
            buffer.skipBytes(i);
            return new DiscardedQueryPayload(id);
        } else {
            throw new IllegalArgumentException("Payload may not be larger than 1048576 bytes");
        }
    }

    /**
     * Writes the raw packet data to the data stream.
     */
    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.transactionId);
        buffer.writeResourceLocation(this.payload.id());
        this.payload.write(buffer);
    }

    @Override
    public PacketType<ClientboundCustomQueryPacket> type() {
        return LoginPacketTypes.CLIENTBOUND_CUSTOM_QUERY;
    }

    /**
     * Passes this Packet on to the NetHandler for processing.
     */
    public void handle(ClientLoginPacketListener handler) {
        handler.handleCustomQuery(this);
    }
}
