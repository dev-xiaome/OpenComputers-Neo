package net.minecraft.network.protocol.handshake;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public record ClientIntentionPacket(int protocolVersion, String hostName, int port, ClientIntent intention) implements Packet<ServerHandshakePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientIntentionPacket> STREAM_CODEC = Packet.codec(
        ClientIntentionPacket::write, ClientIntentionPacket::new
    );
    private static final int MAX_HOST_LENGTH = 255;

    @Deprecated
    public ClientIntentionPacket(int protocolVersion, String hostName, int port, ClientIntent intention) {
        this.protocolVersion = protocolVersion;
        this.hostName = hostName;
        this.port = port;
        this.intention = intention;
    }

    private ClientIntentionPacket(FriendlyByteBuf p_179801_) {
        this(p_179801_.readVarInt(), p_179801_.readUtf(255), p_179801_.readUnsignedShort(), ClientIntent.byId(p_179801_.readVarInt()));
    }

    /**
     * Writes the raw packet data to the data stream.
     */
    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.protocolVersion);
        buffer.writeUtf(this.hostName);
        buffer.writeShort(this.port);
        buffer.writeVarInt(this.intention.id());
    }

    @Override
    public PacketType<ClientIntentionPacket> type() {
        return HandshakePacketTypes.CLIENT_INTENTION;
    }

    /**
     * Passes this Packet on to the NetHandler for processing.
     */
    public void handle(ServerHandshakePacketListener handler) {
        handler.handleIntention(this);
    }

    @Override
    public boolean isTerminal() {
        return true;
    }
}
