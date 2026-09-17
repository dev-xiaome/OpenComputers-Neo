package net.minecraft.network.protocol.game;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

public class ClientboundAddEntityPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundAddEntityPacket> STREAM_CODEC = Packet.codec(
        ClientboundAddEntityPacket::write, ClientboundAddEntityPacket::new
    );
    private static final double MAGICAL_QUANTIZATION = 8000.0;
    private static final double LIMIT = 3.9;
    private final int id;
    private final UUID uuid;
    private final EntityType<?> type;
    private final double x;
    private final double y;
    private final double z;
    private final int xa;
    private final int ya;
    private final int za;
    private final byte xRot;
    private final byte yRot;
    private final byte yHeadRot;
    private final int data;

    public ClientboundAddEntityPacket(Entity entity, ServerEntity serverEntity) {
        this(entity, serverEntity, 0);
    }

    public ClientboundAddEntityPacket(Entity entity, ServerEntity serverEntity, int data) {
        this(
            entity.getId(),
            entity.getUUID(),
            serverEntity.getPositionBase().x(),
            serverEntity.getPositionBase().y(),
            serverEntity.getPositionBase().z(),
            serverEntity.getLastSentXRot(),
            serverEntity.getLastSentYRot(),
            entity.getType(),
            data,
            serverEntity.getLastSentMovement(),
            (double)serverEntity.getLastSentYHeadRot()
        );
    }

    public ClientboundAddEntityPacket(Entity entity, int data, BlockPos pos) {
        this(
            entity.getId(),
            entity.getUUID(),
            (double)pos.getX(),
            (double)pos.getY(),
            (double)pos.getZ(),
            entity.getXRot(),
            entity.getYRot(),
            entity.getType(),
            data,
            entity.getDeltaMovement(),
            (double)entity.getYHeadRot()
        );
    }

    public ClientboundAddEntityPacket(
        int id,
        UUID uuid,
        double x,
        double y,
        double z,
        float xRot,
        float yRot,
        EntityType<?> type,
        int data,
        Vec3 deltaMovement,
        double yHeadRot
    ) {
        this.id = id;
        this.uuid = uuid;
        this.x = x;
        this.y = y;
        this.z = z;
        this.xRot = (byte)Mth.floor(xRot * 256.0F / 360.0F);
        this.yRot = (byte)Mth.floor(yRot * 256.0F / 360.0F);
        this.yHeadRot = (byte)Mth.floor(yHeadRot * 256.0 / 360.0);
        this.type = type;
        this.data = data;
        this.xa = (int)(Mth.clamp(deltaMovement.x, -3.9, 3.9) * 8000.0);
        this.ya = (int)(Mth.clamp(deltaMovement.y, -3.9, 3.9) * 8000.0);
        this.za = (int)(Mth.clamp(deltaMovement.z, -3.9, 3.9) * 8000.0);
    }

    private ClientboundAddEntityPacket(RegistryFriendlyByteBuf buffer) {
        this.id = buffer.readVarInt();
        this.uuid = buffer.readUUID();
        this.type = ByteBufCodecs.registry(Registries.ENTITY_TYPE).decode(buffer);
        this.x = buffer.readDouble();
        this.y = buffer.readDouble();
        this.z = buffer.readDouble();
        this.xRot = buffer.readByte();
        this.yRot = buffer.readByte();
        this.yHeadRot = buffer.readByte();
        this.data = buffer.readVarInt();
        this.xa = buffer.readShort();
        this.ya = buffer.readShort();
        this.za = buffer.readShort();
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(this.id);
        buffer.writeUUID(this.uuid);
        ByteBufCodecs.registry(Registries.ENTITY_TYPE).encode(buffer, this.type);
        buffer.writeDouble(this.x);
        buffer.writeDouble(this.y);
        buffer.writeDouble(this.z);
        buffer.writeByte(this.xRot);
        buffer.writeByte(this.yRot);
        buffer.writeByte(this.yHeadRot);
        buffer.writeVarInt(this.data);
        buffer.writeShort(this.xa);
        buffer.writeShort(this.ya);
        buffer.writeShort(this.za);
    }

    @Override
    public PacketType<ClientboundAddEntityPacket> type() {
        return GamePacketTypes.CLIENTBOUND_ADD_ENTITY;
    }

    /**
     * Passes this Packet on to the NetHandler for processing.
     */
    public void handle(ClientGamePacketListener handler) {
        handler.handleAddEntity(this);
    }

    public int getId() {
        return this.id;
    }

    public UUID getUUID() {
        return this.uuid;
    }

    public EntityType<?> getType() {
        return this.type;
    }

    public double getX() {
        return this.x;
    }

    public double getY() {
        return this.y;
    }

    public double getZ() {
        return this.z;
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

    public float getXRot() {
        return (float)(this.xRot * 360) / 256.0F;
    }

    public float getYRot() {
        return (float)(this.yRot * 360) / 256.0F;
    }

    public float getYHeadRot() {
        return (float)(this.yHeadRot * 360) / 256.0F;
    }

    public int getData() {
        return this.data;
    }
}
