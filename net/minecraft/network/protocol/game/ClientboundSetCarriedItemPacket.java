package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ClientboundSetCarriedItemPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundSetCarriedItemPacket> STREAM_CODEC = Packet.codec(
        ClientboundSetCarriedItemPacket::write, ClientboundSetCarriedItemPacket::new
    );
    private final int slot;

    public ClientboundSetCarriedItemPacket(int slot) {
        this.slot = slot;
    }

    private ClientboundSetCarriedItemPacket(FriendlyByteBuf buffer) {
        this.slot = buffer.readByte();
    }

    /**
     * Writes the raw packet data to the data stream.
     */
    private void write(FriendlyByteBuf buffer) {
        buffer.writeByte(this.slot);
    }

    @Override
    public PacketType<ClientboundSetCarriedItemPacket> type() {
        return GamePacketTypes.CLIENTBOUND_SET_CARRIED_ITEM;
    }

    /**
     * Passes this Packet on to the NetHandler for processing.
     */
    public void handle(ClientGamePacketListener handler) {
        handler.handleSetCarriedItem(this);
    }

    public int getSlot() {
        return this.slot;
    }
}
