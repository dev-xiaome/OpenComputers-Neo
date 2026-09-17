package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ClientboundSetTimePacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundSetTimePacket> STREAM_CODEC = Packet.codec(
        ClientboundSetTimePacket::write, ClientboundSetTimePacket::new
    );
    private final long gameTime;
    private final long dayTime;

    public ClientboundSetTimePacket(long gameTime, long dayTime, boolean daylightCycleEnabled) {
        this.gameTime = gameTime;
        long i = dayTime;
        if (!daylightCycleEnabled) {
            i = -dayTime;
            if (i == 0L) {
                i = -1L;
            }
        }

        this.dayTime = i;
    }

    private ClientboundSetTimePacket(FriendlyByteBuf buffer) {
        this.gameTime = buffer.readLong();
        this.dayTime = buffer.readLong();
    }

    /**
     * Writes the raw packet data to the data stream.
     */
    private void write(FriendlyByteBuf buffer) {
        buffer.writeLong(this.gameTime);
        buffer.writeLong(this.dayTime);
    }

    @Override
    public PacketType<ClientboundSetTimePacket> type() {
        return GamePacketTypes.CLIENTBOUND_SET_TIME;
    }

    /**
     * Passes this Packet on to the NetHandler for processing.
     */
    public void handle(ClientGamePacketListener handler) {
        handler.handleSetTime(this);
    }

    public long getGameTime() {
        return this.gameTime;
    }

    public long getDayTime() {
        return this.dayTime;
    }
}
