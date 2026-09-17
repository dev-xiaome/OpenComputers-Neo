package net.minecraft.network.protocol.game;

import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public record ClientboundDamageEventPacket(int entityId, Holder<DamageType> sourceType, int sourceCauseId, int sourceDirectId, Optional<Vec3> sourcePosition)
    implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundDamageEventPacket> STREAM_CODEC = Packet.codec(
        ClientboundDamageEventPacket::write, ClientboundDamageEventPacket::new
    );

    public ClientboundDamageEventPacket(Entity p_270474_, DamageSource p_270781_) {
        this(
            p_270474_.getId(),
            p_270781_.typeHolder(),
            p_270781_.getEntity() != null ? p_270781_.getEntity().getId() : -1,
            p_270781_.getDirectEntity() != null ? p_270781_.getDirectEntity().getId() : -1,
            Optional.ofNullable(p_270781_.sourcePositionRaw())
        );
    }

    private ClientboundDamageEventPacket(RegistryFriendlyByteBuf p_321729_) {
        this(
            p_321729_.readVarInt(),
            DamageType.STREAM_CODEC.decode(p_321729_),
            readOptionalEntityId(p_321729_),
            readOptionalEntityId(p_321729_),
            p_321729_.readOptional(p_270813_ -> new Vec3(p_270813_.readDouble(), p_270813_.readDouble(), p_270813_.readDouble()))
        );
    }

    private static void writeOptionalEntityId(FriendlyByteBuf buffer, int optionalEntityId) {
        buffer.writeVarInt(optionalEntityId + 1);
    }

    private static int readOptionalEntityId(FriendlyByteBuf buffer) {
        return buffer.readVarInt() - 1;
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(this.entityId);
        DamageType.STREAM_CODEC.encode(buffer, this.sourceType);
        writeOptionalEntityId(buffer, this.sourceCauseId);
        writeOptionalEntityId(buffer, this.sourceDirectId);
        buffer.writeOptional(this.sourcePosition, (p_293723_, p_293724_) -> {
            p_293723_.writeDouble(p_293724_.x());
            p_293723_.writeDouble(p_293724_.y());
            p_293723_.writeDouble(p_293724_.z());
        });
    }

    @Override
    public PacketType<ClientboundDamageEventPacket> type() {
        return GamePacketTypes.CLIENTBOUND_DAMAGE_EVENT;
    }

    /**
     * Passes this Packet on to the PacketListener for processing.
     */
    public void handle(ClientGamePacketListener handler) {
        handler.handleDamageEvent(this);
    }

    public DamageSource getSource(Level level) {
        if (this.sourcePosition.isPresent()) {
            return new DamageSource(this.sourceType, this.sourcePosition.get());
        } else {
            Entity entity = level.getEntity(this.sourceCauseId);
            Entity entity1 = level.getEntity(this.sourceDirectId);
            return new DamageSource(this.sourceType, entity1, entity);
        }
    }
}
