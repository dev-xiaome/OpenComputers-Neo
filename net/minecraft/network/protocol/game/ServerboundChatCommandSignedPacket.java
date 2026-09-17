package net.minecraft.network.protocol.game;

import java.time.Instant;
import net.minecraft.commands.arguments.ArgumentSignatures;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public record ServerboundChatCommandSignedPacket(
    String command, Instant timeStamp, long salt, ArgumentSignatures argumentSignatures, LastSeenMessages.Update lastSeenMessages
) implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundChatCommandSignedPacket> STREAM_CODEC = Packet.codec(
        ServerboundChatCommandSignedPacket::write, ServerboundChatCommandSignedPacket::new
    );

    private ServerboundChatCommandSignedPacket(FriendlyByteBuf p_338652_) {
        this(p_338652_.readUtf(), p_338652_.readInstant(), p_338652_.readLong(), new ArgumentSignatures(p_338652_), new LastSeenMessages.Update(p_338652_));
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.command);
        buffer.writeInstant(this.timeStamp);
        buffer.writeLong(this.salt);
        this.argumentSignatures.write(buffer);
        this.lastSeenMessages.write(buffer);
    }

    @Override
    public PacketType<ServerboundChatCommandSignedPacket> type() {
        return GamePacketTypes.SERVERBOUND_CHAT_COMMAND_SIGNED;
    }

    /**
     * Passes this Packet on to the PacketListener for processing.
     */
    public void handle(ServerGamePacketListener handler) {
        handler.handleSignedChatCommand(this);
    }
}
