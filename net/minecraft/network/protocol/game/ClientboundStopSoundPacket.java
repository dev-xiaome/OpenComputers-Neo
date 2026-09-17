package net.minecraft.network.protocol.game;

import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

public class ClientboundStopSoundPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundStopSoundPacket> STREAM_CODEC = Packet.codec(
        ClientboundStopSoundPacket::write, ClientboundStopSoundPacket::new
    );
    private static final int HAS_SOURCE = 1;
    private static final int HAS_SOUND = 2;
    @Nullable
    private final ResourceLocation name;
    @Nullable
    private final SoundSource source;

    public ClientboundStopSoundPacket(@Nullable ResourceLocation name, @Nullable SoundSource source) {
        this.name = name;
        this.source = source;
    }

    private ClientboundStopSoundPacket(FriendlyByteBuf buffer) {
        int i = buffer.readByte();
        if ((i & 1) > 0) {
            this.source = buffer.readEnum(SoundSource.class);
        } else {
            this.source = null;
        }

        if ((i & 2) > 0) {
            this.name = buffer.readResourceLocation();
        } else {
            this.name = null;
        }
    }

    /**
     * Writes the raw packet data to the data stream.
     */
    private void write(FriendlyByteBuf buffer) {
        if (this.source != null) {
            if (this.name != null) {
                buffer.writeByte(3);
                buffer.writeEnum(this.source);
                buffer.writeResourceLocation(this.name);
            } else {
                buffer.writeByte(1);
                buffer.writeEnum(this.source);
            }
        } else if (this.name != null) {
            buffer.writeByte(2);
            buffer.writeResourceLocation(this.name);
        } else {
            buffer.writeByte(0);
        }
    }

    @Override
    public PacketType<ClientboundStopSoundPacket> type() {
        return GamePacketTypes.CLIENTBOUND_STOP_SOUND;
    }

    /**
     * Passes this Packet on to the NetHandler for processing.
     */
    public void handle(ClientGamePacketListener handler) {
        handler.handleStopSoundEvent(this);
    }

    @Nullable
    public ResourceLocation getName() {
        return this.name;
    }

    @Nullable
    public SoundSource getSource() {
        return this.source;
    }
}
