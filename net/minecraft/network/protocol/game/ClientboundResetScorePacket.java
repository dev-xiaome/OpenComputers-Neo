package net.minecraft.network.protocol.game;

import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public record ClientboundResetScorePacket(String owner, @Nullable String objectiveName) implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundResetScorePacket> STREAM_CODEC = Packet.codec(
        ClientboundResetScorePacket::write, ClientboundResetScorePacket::new
    );

    private ClientboundResetScorePacket(FriendlyByteBuf p_313852_) {
        this(p_313852_.readUtf(), p_313852_.readNullable(FriendlyByteBuf::readUtf));
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.owner);
        buffer.writeNullable(this.objectiveName, FriendlyByteBuf::writeUtf);
    }

    @Override
    public PacketType<ClientboundResetScorePacket> type() {
        return GamePacketTypes.CLIENTBOUND_RESET_SCORE;
    }

    /**
     * Passes this Packet on to the PacketListener for processing.
     */
    public void handle(ClientGamePacketListener handler) {
        handler.handleResetScore(this);
    }
}
