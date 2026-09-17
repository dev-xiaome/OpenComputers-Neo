package net.minecraft.network.protocol.common;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public record ClientboundTransferPacket(String host, int port) implements Packet<ClientCommonPacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundTransferPacket> STREAM_CODEC = Packet.codec(
        ClientboundTransferPacket::write, ClientboundTransferPacket::new
    );

    private ClientboundTransferPacket(FriendlyByteBuf p_320164_) {
        this(p_320164_.readUtf(), p_320164_.readVarInt());
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.host);
        buffer.writeVarInt(this.port);
    }

    @Override
    public PacketType<ClientboundTransferPacket> type() {
        return CommonPacketTypes.CLIENTBOUND_TRANSFER;
    }

    /**
     * Passes this Packet on to the PacketListener for processing.
     */
    public void handle(ClientCommonPacketListener handler) {
        handler.handleTransfer(this);
    }
}
