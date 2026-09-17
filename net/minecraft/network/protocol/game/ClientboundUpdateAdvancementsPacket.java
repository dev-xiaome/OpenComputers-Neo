package net.minecraft.network.protocol.game;

import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.ResourceLocation;

public class ClientboundUpdateAdvancementsPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundUpdateAdvancementsPacket> STREAM_CODEC = Packet.codec(
        ClientboundUpdateAdvancementsPacket::write, ClientboundUpdateAdvancementsPacket::new
    );
    private final boolean reset;
    private final List<AdvancementHolder> added;
    private final Set<ResourceLocation> removed;
    private final Map<ResourceLocation, AdvancementProgress> progress;

    public ClientboundUpdateAdvancementsPacket(
        boolean reset, Collection<AdvancementHolder> added, Set<ResourceLocation> removed, Map<ResourceLocation, AdvancementProgress> progress
    ) {
        this.reset = reset;
        this.added = List.copyOf(added);
        this.removed = Set.copyOf(removed);
        this.progress = Map.copyOf(progress);
    }

    private ClientboundUpdateAdvancementsPacket(RegistryFriendlyByteBuf buffer) {
        this.reset = buffer.readBoolean();
        this.added = AdvancementHolder.LIST_STREAM_CODEC.decode(buffer);
        this.removed = buffer.readCollection(Sets::newLinkedHashSetWithExpectedSize, FriendlyByteBuf::readResourceLocation);
        this.progress = buffer.readMap(FriendlyByteBuf::readResourceLocation, AdvancementProgress::fromNetwork);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(this.reset);
        AdvancementHolder.LIST_STREAM_CODEC.encode(buffer, this.added);
        buffer.writeCollection(this.removed, FriendlyByteBuf::writeResourceLocation);
        buffer.writeMap(this.progress, FriendlyByteBuf::writeResourceLocation, (p_179444_, p_179445_) -> p_179445_.serializeToNetwork(p_179444_));
    }

    @Override
    public PacketType<ClientboundUpdateAdvancementsPacket> type() {
        return GamePacketTypes.CLIENTBOUND_UPDATE_ADVANCEMENTS;
    }

    /**
     * Passes this Packet on to the NetHandler for processing.
     */
    public void handle(ClientGamePacketListener handler) {
        handler.handleUpdateAdvancementsPacket(this);
    }

    public List<AdvancementHolder> getAdded() {
        return this.added;
    }

    public Set<ResourceLocation> getRemoved() {
        return this.removed;
    }

    public Map<ResourceLocation, AdvancementProgress> getProgress() {
        return this.progress;
    }

    public boolean shouldReset() {
        return this.reset;
    }
}
