package net.minecraft.network.protocol.game;

import java.time.Instant;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public record ServerboundChatPacket(String message, Instant timeStamp, long salt, @Nullable MessageSignature signature, LastSeenMessages.Update lastSeenMessages)
    implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundChatPacket> STREAM_CODEC = Packet.codec(
        ServerboundChatPacket::write, ServerboundChatPacket::new
    );

    private ServerboundChatPacket(FriendlyByteBuf p_179545_) {
        this(
            p_179545_.readUtf(256),
            p_179545_.readInstant(),
            p_179545_.readLong(),
            p_179545_.readNullable(MessageSignature::read),
            new LastSeenMessages.Update(p_179545_)
        );
    }

    /**
     * Writes the raw packet data to the data stream.
     */
    private void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.message, 256);
        buffer.writeInstant(this.timeStamp);
        buffer.writeLong(this.salt);
        buffer.writeNullable(this.signature, MessageSignature::write);
        this.lastSeenMessages.write(buffer);
    }

    @Override
    public PacketType<ServerboundChatPacket> type() {
        return GamePacketTypes.SERVERBOUND_CHAT;
    }

    /**
     * Passes this Packet on to the NetHandler for processing.
     */
    public void handle(ServerGamePacketListener handler) {
        handler.handleChat(this);
    }
}
