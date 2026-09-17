package net.minecraft.network.protocol.login;

import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.login.custom.CustomQueryAnswerPayload;
import net.minecraft.network.protocol.login.custom.DiscardedQueryAnswerPayload;

public record ServerboundCustomQueryAnswerPacket(int transactionId, @Nullable CustomQueryAnswerPayload payload) implements Packet<ServerLoginPacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundCustomQueryAnswerPacket> STREAM_CODEC = Packet.codec(
        ServerboundCustomQueryAnswerPacket::write, ServerboundCustomQueryAnswerPacket::read
    );
    private static final int MAX_PAYLOAD_SIZE = 1048576;

    private static ServerboundCustomQueryAnswerPacket read(FriendlyByteBuf buffer) {
        int i = buffer.readVarInt();
        return new ServerboundCustomQueryAnswerPacket(i, readPayload(i, buffer));
    }

    private static CustomQueryAnswerPayload readPayload(int transactionId, FriendlyByteBuf buffer) {
        return readUnknownPayload(buffer);
    }

    private static CustomQueryAnswerPayload readUnknownPayload(FriendlyByteBuf buffer) {
        int i = buffer.readableBytes();
        if (i >= 0 && i <= 1048576) {
            buffer.skipBytes(i);
            return DiscardedQueryAnswerPayload.INSTANCE;
        } else {
            throw new IllegalArgumentException("Payload may not be larger than 1048576 bytes");
        }
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.transactionId);
        buffer.writeNullable(this.payload, (p_295443_, p_295588_) -> p_295588_.write(p_295443_));
    }

    @Override
    public PacketType<ServerboundCustomQueryAnswerPacket> type() {
        return LoginPacketTypes.SERVERBOUND_CUSTOM_QUERY_ANSWER;
    }

    /**
     * Passes this Packet on to the PacketListener for processing.
     */
    public void handle(ServerLoginPacketListener handler) {
        handler.handleCustomQueryPacket(this);
    }
}
