package net.minecraft.world.level.levelgen.placement;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;

public class FixedPlacement extends PlacementModifier {
    public static final MapCodec<FixedPlacement> CODEC = RecordCodecBuilder.mapCodec(
        p_352897_ -> p_352897_.group(BlockPos.CODEC.listOf().fieldOf("positions").forGetter(p_352962_ -> p_352962_.positions))
                .apply(p_352897_, FixedPlacement::new)
    );
    private final List<BlockPos> positions;

    public static FixedPlacement of(BlockPos... positions) {
        return new FixedPlacement(List.of(positions));
    }

    private FixedPlacement(List<BlockPos> positions) {
        this.positions = positions;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        int i = SectionPos.blockToSectionCoord(pos.getX());
        int j = SectionPos.blockToSectionCoord(pos.getZ());
        boolean flag = false;

        for (BlockPos blockpos : this.positions) {
            if (isSameChunk(i, j, blockpos)) {
                flag = true;
                break;
            }
        }

        return !flag ? Stream.empty() : this.positions.stream().filter(p_352956_ -> isSameChunk(i, j, p_352956_));
    }

    private static boolean isSameChunk(int x, int z, BlockPos pos) {
        return x == SectionPos.blockToSectionCoord(pos.getX()) && z == SectionPos.blockToSectionCoord(pos.getZ());
    }

    @Override
    public PlacementModifierType<?> type() {
        return PlacementModifierType.FIXED_PLACEMENT;
    }
}
