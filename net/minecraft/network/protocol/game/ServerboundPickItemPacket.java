package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ServerboundPickItemPacket implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundPickItemPacket> STREAM_CODEC = Packet.codec(
        ServerboundPickItemPacket::write, ServerboundPickItemPacket::new
    );
    private final int slot;

    public ServerboundPickItemPacket(int slot) {
        this.slot = slot;
    }

    private ServerboundPickItemPacket(FriendlyByteBuf buffer) {
        this.slot = buffer.readVarInt();
    }

    /**
     * Writes the raw packet data to the data stream.
     */
    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.slot);
    }

    @Override
    public PacketType<ServerboundPickItemPacket> type() {
        return GamePacketTypes.SERVERBOUND_PICK_ITEM;
    }

    /**
     * Passes this Packet on to the NetHandler for processing.
     */
    public void handle(ServerGamePacketListener handler) {
        handler.handlePickItem(this);
    }

    public int getSlot() {
        return this.slot;
    }
}
