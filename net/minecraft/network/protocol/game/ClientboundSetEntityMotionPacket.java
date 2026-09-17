package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class ClientboundSetEntityMotionPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundSetEntityMotionPacket> STREAM_CODEC = Packet.codec(
        ClientboundSetEntityMotionPacket::write, ClientboundSetEntityMotionPacket::new
    );
    private final int id;
    private final int xa;
    private final int ya;
    private final int za;

    public ClientboundSetEntityMotionPacket(Entity entity) {
        this(entity.getId(), entity.getDeltaMovement());
    }

    public ClientboundSetEntityMotionPacket(int id, Vec3 deltaMovement) {
        this.id = id;
        double d0 = 3.9;
        double d1 = Mth.clamp(deltaMovement.x, -3.9, 3.9);
        double d2 = Mth.clamp(deltaMovement.y, -3.9, 3.9);
        double d3 = Mth.clamp(deltaMovement.z, -3.9, 3.9);
        this.xa = (int)(d1 * 8000.0);
        this.ya = (int)(d2 * 8000.0);
        this.za = (int)(d3 * 8000.0);
    }

    private ClientboundSetEntityMotionPacket(FriendlyByteBuf buffer) {
        this.id = buffer.readVarInt();
        this.xa = buffer.readShort();
        this.ya = buffer.readShort();
        this.za = buffer.readShort();
    }

    /**
     * Writes the raw packet data to the data stream.
     */
    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.id);
        buffer.writeShort(this.xa);
        buffer.writeShort(this.ya);
        buffer.writeShort(this.za);
    }

    @Override
    public PacketType<ClientboundSetEntityMotionPacket> type() {
        return GamePacketTypes.CLIENTBOUND_SET_ENTITY_MOTION;
    }

    /**
     * Passes this Packet on to the NetHandler for processing.
     */
    public void handle(ClientGamePacketListener handler) {
        handler.handleSetEntityMotion(this);
    }

    public int getId() {
        return this.id;
    }

    public double getXa() {
        return (double)this.xa / 8000.0;
    }

    public double getYa() {
        return (double)this.ya / 8000.0;
    }

    public double getZa() {
        return (double)this.za / 8000.0;
    }
}
