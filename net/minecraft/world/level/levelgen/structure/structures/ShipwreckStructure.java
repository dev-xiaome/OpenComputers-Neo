package net.minecraft.world.level.levelgen.structure.structures;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

public class ShipwreckStructure extends Structure {
    public static final MapCodec<ShipwreckStructure> CODEC = RecordCodecBuilder.mapCodec(
        p_229401_ -> p_229401_.group(settingsCodec(p_229401_), Codec.BOOL.fieldOf("is_beached").forGetter(p_229399_ -> p_229399_.isBeached))
                .apply(p_229401_, ShipwreckStructure::new)
    );
    public final boolean isBeached;

    public ShipwreckStructure(Structure.StructureSettings settings, boolean isBeached) {
        super(settings);
        this.isBeached = isBeached;
    }

    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        Heightmap.Types heightmap$types = this.isBeached ? Heightmap.Types.WORLD_SURFACE_WG : Heightmap.Types.OCEAN_FLOOR_WG;
        return onTopOfChunkCenter(context, heightmap$types, p_229394_ -> this.generatePieces(p_229394_, context));
    }

    private void generatePieces(StructurePiecesBuilder builder, Structure.GenerationContext context) {
        Rotation rotation = Rotation.getRandom(context.random());
        BlockPos blockpos = new BlockPos(context.chunkPos().getMinBlockX(), 90, context.chunkPos().getMinBlockZ());
        ShipwreckPieces.ShipwreckPiece shipwreckpieces$shipwreckpiece = ShipwreckPieces.addRandomPiece(
            context.structureTemplateManager(), blockpos, rotation, builder, context.random(), this.isBeached
        );
        if (shipwreckpieces$shipwreckpiece.isTooBigToFitInWorldGenRegion()) {
            BoundingBox boundingbox = shipwreckpieces$shipwreckpiece.getBoundingBox();
            int i;
            if (this.isBeached) {
                int j = Structure.getLowestY(context, boundingbox.minX(), boundingbox.getXSpan(), boundingbox.minZ(), boundingbox.getZSpan());
                i = shipwreckpieces$shipwreckpiece.calculateBeachedPosition(j, context.random());
            } else {
                i = Structure.getMeanFirstOccupiedHeight(context, boundingbox.minX(), boundingbox.getXSpan(), boundingbox.minZ(), boundingbox.getZSpan());
            }

            shipwreckpieces$shipwreckpiece.adjustPositionHeight(i);
        }
    }

    @Override
    public StructureType<?> type() {
        return StructureType.SHIPWRECK;
    }
}
