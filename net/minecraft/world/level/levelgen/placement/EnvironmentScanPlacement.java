package net.minecraft.world.level.levelgen.placement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;

public class EnvironmentScanPlacement extends PlacementModifier {
    private final Direction directionOfSearch;
    private final BlockPredicate targetCondition;
    private final BlockPredicate allowedSearchCondition;
    private final int maxSteps;
    public static final MapCodec<EnvironmentScanPlacement> CODEC = RecordCodecBuilder.mapCodec(
        p_191650_ -> p_191650_.group(
                    // Neo: Allow any direction, not just vertical. The code already handles it fine.
                    Direction.CODEC.fieldOf("direction_of_search").forGetter(p_191672_ -> p_191672_.directionOfSearch),
                    BlockPredicate.CODEC.fieldOf("target_condition").forGetter(p_191670_ -> p_191670_.targetCondition),
                    BlockPredicate.CODEC
                        .optionalFieldOf("allowed_search_condition", BlockPredicate.alwaysTrue())
                        .forGetter(p_191668_ -> p_191668_.allowedSearchCondition),
                    Codec.intRange(1, 32).fieldOf("max_steps").forGetter(p_191652_ -> p_191652_.maxSteps)
                )
                .apply(p_191650_, EnvironmentScanPlacement::new)
    );

    private EnvironmentScanPlacement(Direction directionOfSearch, BlockPredicate targetCondition, BlockPredicate allowedSearchCondition, int maxSteps) {
        this.directionOfSearch = directionOfSearch;
        this.targetCondition = targetCondition;
        this.allowedSearchCondition = allowedSearchCondition;
        this.maxSteps = maxSteps;
    }

    public static EnvironmentScanPlacement scanningFor(Direction directionOfSearch, BlockPredicate targetCondition, BlockPredicate allowedSearchCondition, int maxSteps) {
        return new EnvironmentScanPlacement(directionOfSearch, targetCondition, allowedSearchCondition, maxSteps);
    }

    public static EnvironmentScanPlacement scanningFor(Direction directionOfSearch, BlockPredicate targetCondition, int maxSteps) {
        return scanningFor(directionOfSearch, targetCondition, BlockPredicate.alwaysTrue(), maxSteps);
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        BlockPos.MutableBlockPos blockpos$mutableblockpos = pos.mutable();
        WorldGenLevel worldgenlevel = context.getLevel();
        if (!this.allowedSearchCondition.test(worldgenlevel, blockpos$mutableblockpos)) {
            return Stream.of();
        } else {
            for (int i = 0; i < this.maxSteps; i++) {
                if (this.targetCondition.test(worldgenlevel, blockpos$mutableblockpos)) {
                    return Stream.of(blockpos$mutableblockpos);
                }

                blockpos$mutableblockpos.move(this.directionOfSearch);
                if (worldgenlevel.isOutsideBuildHeight(blockpos$mutableblockpos.getY())) {
                    return Stream.of();
                }

                if (!this.allowedSearchCondition.test(worldgenlevel, blockpos$mutableblockpos)) {
                    break;
                }
            }

            return this.targetCondition.test(worldgenlevel, blockpos$mutableblockpos) ? Stream.of(blockpos$mutableblockpos) : Stream.of();
        }
    }

    @Override
    public PlacementModifierType<?> type() {
        return PlacementModifierType.ENVIRONMENT_SCAN;
    }
}
