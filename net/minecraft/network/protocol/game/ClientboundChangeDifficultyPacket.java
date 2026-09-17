package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.Difficulty;

public class ClientboundChangeDifficultyPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundChangeDifficultyPacket> STREAM_CODEC = Packet.codec(
        ClientboundChangeDifficultyPacket::write, ClientboundChangeDifficultyPacket::new
    );
    private final Difficulty difficulty;
    private final boolean locked;

    public ClientboundChangeDifficultyPacket(Difficulty difficulty, boolean locked) {
        this.difficulty = difficulty;
        this.locked = locked;
    }

    private ClientboundChangeDifficultyPacket(FriendlyByteBuf buffer) {
        this.difficulty = Difficulty.byId(buffer.readUnsignedByte());
        this.locked = buffer.readBoolean();
    }

    /**
     * Writes the raw packet data to the data stream.
     */
    private void write(FriendlyByteBuf buffer) {
        buffer.writeByte(this.difficulty.getId());
        buffer.writeBoolean(this.locked);
    }

    @Override
    public PacketType<ClientboundChangeDifficultyPacket> type() {
        return GamePacketTypes.CLIENTBOUND_CHANGE_DIFFICULTY;
    }

    /**
     * Passes this Packet on to the NetHandler for processing.
     */
    public void handle(ClientGamePacketListener handler) {
        handler.handleChangeDifficulty(this);
    }

    public boolean isLocked() {
        return this.locked;
    }

    public Difficulty getDifficulty() {
        return this.difficulty;
    }
}
