package net.minecraft.network.protocol.common.custom;

import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public record StructuresDebugPayload(ResourceKey<Level> dimension, BoundingBox mainBB, List<StructuresDebugPayload.PieceInfo> pieces)
    implements CustomPacketPayload {
    public static final StreamCodec<FriendlyByteBuf, StructuresDebugPayload> STREAM_CODEC = CustomPacketPayload.codec(
        StructuresDebugPayload::write, StructuresDebugPayload::new
    );
    public static final CustomPacketPayload.Type<StructuresDebugPayload> TYPE = CustomPacketPayload.createType("debug/structures");

    private StructuresDebugPayload(FriendlyByteBuf p_294983_) {
        this(p_294983_.readResourceKey(Registries.DIMENSION), readBoundingBox(p_294983_), p_294983_.readList(StructuresDebugPayload.PieceInfo::new));
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeResourceKey(this.dimension);
        writeBoundingBox(buffer, this.mainBB);
        buffer.writeCollection(this.pieces, (p_294583_, p_296047_) -> p_296047_.write(buffer));
    }

    @Override
    public CustomPacketPayload.Type<StructuresDebugPayload> type() {
        return TYPE;
    }

    static BoundingBox readBoundingBox(FriendlyByteBuf buffer) {
        return new BoundingBox(buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt());
    }

    static void writeBoundingBox(FriendlyByteBuf buffer, BoundingBox boundingBox) {
        buffer.writeInt(boundingBox.minX());
        buffer.writeInt(boundingBox.minY());
        buffer.writeInt(boundingBox.minZ());
        buffer.writeInt(boundingBox.maxX());
        buffer.writeInt(boundingBox.maxY());
        buffer.writeInt(boundingBox.maxZ());
    }

    public static record PieceInfo(BoundingBox boundingBox, boolean isStart) {
        public PieceInfo(FriendlyByteBuf p_294562_) {
            this(StructuresDebugPayload.readBoundingBox(p_294562_), p_294562_.readBoolean());
        }

        public void write(FriendlyByteBuf buffer) {
            StructuresDebugPayload.writeBoundingBox(buffer, this.boundingBox);
            buffer.writeBoolean(this.isStart);
        }
    }
}
