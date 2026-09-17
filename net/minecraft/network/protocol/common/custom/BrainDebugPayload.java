package net.minecraft.network.protocol.common.custom;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public record BrainDebugPayload(BrainDebugPayload.BrainDump brainDump) implements CustomPacketPayload {
    public static final StreamCodec<FriendlyByteBuf, BrainDebugPayload> STREAM_CODEC = CustomPacketPayload.codec(
        BrainDebugPayload::write, BrainDebugPayload::new
    );
    public static final CustomPacketPayload.Type<BrainDebugPayload> TYPE = CustomPacketPayload.createType("debug/brain");

    private BrainDebugPayload(FriendlyByteBuf p_295683_) {
        this(new BrainDebugPayload.BrainDump(p_295683_));
    }

    private void write(FriendlyByteBuf buffer) {
        this.brainDump.write(buffer);
    }

    @Override
    public CustomPacketPayload.Type<BrainDebugPayload> type() {
        return TYPE;
    }

    public static record BrainDump(
        UUID uuid,
        int id,
        String name,
        String profession,
        int xp,
        float health,
        float maxHealth,
        Vec3 pos,
        String inventory,
        @Nullable Path path,
        boolean wantsGolem,
        int angerLevel,
        List<String> activities,
        List<String> behaviors,
        List<String> memories,
        List<String> gossips,
        Set<BlockPos> pois,
        Set<BlockPos> potentialPois
    ) {
        public BrainDump(FriendlyByteBuf p_294290_) {
            this(
                p_294290_.readUUID(),
                p_294290_.readInt(),
                p_294290_.readUtf(),
                p_294290_.readUtf(),
                p_294290_.readInt(),
                p_294290_.readFloat(),
                p_294290_.readFloat(),
                p_294290_.readVec3(),
                p_294290_.readUtf(),
                p_294290_.readNullable(Path::createFromStream),
                p_294290_.readBoolean(),
                p_294290_.readInt(),
                p_294290_.readList(FriendlyByteBuf::readUtf),
                p_294290_.readList(FriendlyByteBuf::readUtf),
                p_294290_.readList(FriendlyByteBuf::readUtf),
                p_294290_.readList(FriendlyByteBuf::readUtf),
                p_294290_.readCollection(HashSet::new, BlockPos.STREAM_CODEC),
                p_294290_.readCollection(HashSet::new, BlockPos.STREAM_CODEC)
            );
        }

        public void write(FriendlyByteBuf buffer) {
            buffer.writeUUID(this.uuid);
            buffer.writeInt(this.id);
            buffer.writeUtf(this.name);
            buffer.writeUtf(this.profession);
            buffer.writeInt(this.xp);
            buffer.writeFloat(this.health);
            buffer.writeFloat(this.maxHealth);
            buffer.writeVec3(this.pos);
            buffer.writeUtf(this.inventory);
            buffer.writeNullable(this.path, (p_296121_, p_295181_) -> p_295181_.writeToStream(p_296121_));
            buffer.writeBoolean(this.wantsGolem);
            buffer.writeInt(this.angerLevel);
            buffer.writeCollection(this.activities, FriendlyByteBuf::writeUtf);
            buffer.writeCollection(this.behaviors, FriendlyByteBuf::writeUtf);
            buffer.writeCollection(this.memories, FriendlyByteBuf::writeUtf);
            buffer.writeCollection(this.gossips, FriendlyByteBuf::writeUtf);
            buffer.writeCollection(this.pois, BlockPos.STREAM_CODEC);
            buffer.writeCollection(this.potentialPois, BlockPos.STREAM_CODEC);
        }

        public boolean hasPoi(BlockPos pos) {
            return this.pois.contains(pos);
        }

        public boolean hasPotentialPoi(BlockPos pos) {
            return this.potentialPois.contains(pos);
        }
    }
}
