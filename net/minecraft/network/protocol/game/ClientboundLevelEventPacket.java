package net.minecraft.network.protocol.game;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ClientboundLevelEventPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundLevelEventPacket> STREAM_CODEC = Packet.codec(
        ClientboundLevelEventPacket::write, ClientboundLevelEventPacket::new
    );
    private final int type;
    private final BlockPos pos;
    /**
     * can be a block/item id or other depending on the soundtype
     */
    private final int data;
    /**
     * If true the sound is played across the server
     */
    private final boolean globalEvent;

    public ClientboundLevelEventPacket(int type, BlockPos pos, int data, boolean globalEvent) {
        this.type = type;
        this.pos = pos.immutable();
        this.data = data;
        this.globalEvent = globalEvent;
    }

    private ClientboundLevelEventPacket(FriendlyByteBuf buffer) {
        this.type = buffer.readInt();
        this.pos = buffer.readBlockPos();
        this.data = buffer.readInt();
        this.globalEvent = buffer.readBoolean();
    }

    /**
     * Writes the raw packet data to the data stream.
     */
    private void write(FriendlyByteBuf buffer) {
        buffer.writeInt(this.type);
        buffer.writeBlockPos(this.pos);
        buffer.writeInt(this.data);
        buffer.writeBoolean(this.globalEvent);
    }

    @Override
    public PacketType<ClientboundLevelEventPacket> type() {
        return GamePacketTypes.CLIENTBOUND_LEVEL_EVENT;
    }

    /**
     * Passes this Packet on to the NetHandler for processing.
     */
    public void handle(ClientGamePacketListener handler) {
        handler.handleLevelEvent(this);
    }

    public boolean isGlobalEvent() {
        return this.globalEvent;
    }

    public int getType() {
        return this.type;
    }

    public int getData() {
        return this.data;
    }

    public BlockPos getPos() {
        return this.pos;
    }
}
