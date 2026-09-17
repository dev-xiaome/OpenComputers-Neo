package net.minecraft.network.protocol.login;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public record ServerboundHelloPacket(String name, UUID profileId) implements Packet<ServerLoginPacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundHelloPacket> STREAM_CODEC = Packet.codec(
        ServerboundHelloPacket::write, ServerboundHelloPacket::new
    );

    private ServerboundHelloPacket(FriendlyByteBuf p_179827_) {
        this(p_179827_.readUtf(16), p_179827_.readUUID());
    }

    /**
     * Writes the raw packet data to the data stream.
     */
    private void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.name, 16);
        buffer.writeUUID(this.profileId);
    }

    @Override
    public PacketType<ServerboundHelloPacket> type() {
        return LoginPacketTypes.SERVERBOUND_HELLO;
    }

    /**
     * Passes this Packet on to the NetHandler for processing.
     */
    public void handle(ServerLoginPacketListener handler) {
        handler.handleHello(this);
    }
}
