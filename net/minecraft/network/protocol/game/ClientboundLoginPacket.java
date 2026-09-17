package net.minecraft.network.protocol.game;

import com.google.common.collect.Sets;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * @param showDeathScreen Set to false when the doImmediateRespawn gamerule is true
 */
public record ClientboundLoginPacket(
    int playerId,
    boolean hardcore,
    Set<ResourceKey<Level>> levels,
    int maxPlayers,
    int chunkRadius,
    int simulationDistance,
    boolean reducedDebugInfo,
    boolean showDeathScreen,
    boolean doLimitedCrafting,
    CommonPlayerSpawnInfo commonPlayerSpawnInfo,
    boolean enforcesSecureChat
) implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundLoginPacket> STREAM_CODEC = Packet.codec(
        ClientboundLoginPacket::write, ClientboundLoginPacket::new
    );

    private ClientboundLoginPacket(RegistryFriendlyByteBuf p_321545_) {
        this(
            p_321545_.readInt(),
            p_321545_.readBoolean(),
            p_321545_.readCollection(Sets::newHashSetWithExpectedSize, p_258210_ -> p_258210_.readResourceKey(Registries.DIMENSION)),
            p_321545_.readVarInt(),
            p_321545_.readVarInt(),
            p_321545_.readVarInt(),
            p_321545_.readBoolean(),
            p_321545_.readBoolean(),
            p_321545_.readBoolean(),
            new CommonPlayerSpawnInfo(p_321545_),
            p_321545_.readBoolean()
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeInt(this.playerId);
        buffer.writeBoolean(this.hardcore);
        buffer.writeCollection(this.levels, FriendlyByteBuf::writeResourceKey);
        buffer.writeVarInt(this.maxPlayers);
        buffer.writeVarInt(this.chunkRadius);
        buffer.writeVarInt(this.simulationDistance);
        buffer.writeBoolean(this.reducedDebugInfo);
        buffer.writeBoolean(this.showDeathScreen);
        buffer.writeBoolean(this.doLimitedCrafting);
        this.commonPlayerSpawnInfo.write(buffer);
        buffer.writeBoolean(this.enforcesSecureChat);
    }

    @Override
    public PacketType<ClientboundLoginPacket> type() {
        return GamePacketTypes.CLIENTBOUND_LOGIN;
    }

    /**
     * Passes this Packet on to the NetHandler for processing.
     */
    public void handle(ClientGamePacketListener handler) {
        handler.handleLogin(this);
    }
}
