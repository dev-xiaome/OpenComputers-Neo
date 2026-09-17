package net.minecraft.world.level.levelgen.placement;

import com.mojang.serialization.MapCodec;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.GenerationStep;

public class CarvingMaskPlacement extends PlacementModifier {
    public static final MapCodec<CarvingMaskPlacement> CODEC = GenerationStep.Carving.CODEC
        .fieldOf("step")
        .xmap(CarvingMaskPlacement::new, p_191593_ -> p_191593_.step);
    private final GenerationStep.Carving step;

    private CarvingMaskPlacement(GenerationStep.Carving step) {
        this.step = step;
    }

    public static CarvingMaskPlacement forStep(GenerationStep.Carving step) {
        return new CarvingMaskPlacement(step);
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        ChunkPos chunkpos = new ChunkPos(pos);
        return context.getCarvingMask(chunkpos, this.step).stream(chunkpos);
    }

    @Override
    public PlacementModifierType<?> type() {
        return PlacementModifierType.CARVING_MASK_PLACEMENT;
    }
}
