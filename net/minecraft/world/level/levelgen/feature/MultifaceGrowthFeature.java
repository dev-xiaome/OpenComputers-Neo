package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.MultifaceGrowthConfiguration;

public class MultifaceGrowthFeature extends Feature<MultifaceGrowthConfiguration> {
    public MultifaceGrowthFeature(Codec<MultifaceGrowthConfiguration> codec) {
        super(codec);
    }

    /**
     * Places the given feature at the given location.
     * During world generation, features are provided with a 3x3 region of chunks, centered on the chunk being generated, that they can safely generate into.
     *
     * @param context A context object with a reference to the level and the position
     *                the feature is being placed at
     */
    @Override
    public boolean place(FeaturePlaceContext<MultifaceGrowthConfiguration> context) {
        WorldGenLevel worldgenlevel = context.level();
        BlockPos blockpos = context.origin();
        RandomSource randomsource = context.random();
        MultifaceGrowthConfiguration multifacegrowthconfiguration = context.config();
        if (!isAirOrWater(worldgenlevel.getBlockState(blockpos))) {
            return false;
        } else {
            List<Direction> list = multifacegrowthconfiguration.getShuffledDirections(randomsource);
            if (placeGrowthIfPossible(worldgenlevel, blockpos, worldgenlevel.getBlockState(blockpos), multifacegrowthconfiguration, randomsource, list)) {
                return true;
            } else {
                BlockPos.MutableBlockPos blockpos$mutableblockpos = blockpos.mutable();

                for (Direction direction : list) {
                    blockpos$mutableblockpos.set(blockpos);
                    List<Direction> list1 = multifacegrowthconfiguration.getShuffledDirectionsExcept(randomsource, direction.getOpposite());

                    for (int i = 0; i < multifacegrowthconfiguration.searchRange; i++) {
                        blockpos$mutableblockpos.setWithOffset(blockpos, direction);
                        BlockState blockstate = worldgenlevel.getBlockState(blockpos$mutableblockpos);
                        if (!isAirOrWater(blockstate) && !blockstate.is(multifacegrowthconfiguration.placeBlock)) {
                            break;
                        }

                        if (placeGrowthIfPossible(worldgenlevel, blockpos$mutableblockpos, blockstate, multifacegrowthconfiguration, randomsource, list1)) {
                            return true;
                        }
                    }
                }

                return false;
            }
        }
    }

    public static boolean placeGrowthIfPossible(
        WorldGenLevel level,
        BlockPos pos,
        BlockState state,
        MultifaceGrowthConfiguration config,
        RandomSource random,
        List<Direction> directions
    ) {
        BlockPos.MutableBlockPos blockpos$mutableblockpos = pos.mutable();

        for (Direction direction : directions) {
            BlockState blockstate = level.getBlockState(blockpos$mutableblockpos.setWithOffset(pos, direction));
            if (blockstate.is(config.canBePlacedOn)) {
                BlockState blockstate1 = config.placeBlock.getStateForPlacement(state, level, pos, direction);
                if (blockstate1 == null) {
                    return false;
                }

                level.setBlock(pos, blockstate1, 3);
                level.getChunk(pos).markPosForPostprocessing(pos);
                if (random.nextFloat() < config.chanceOfSpreading) {
                    config.placeBlock.getSpreader().spreadFromFaceTowardRandomDirection(blockstate1, level, pos, direction, random, true);
                }

                return true;
            }
        }

        return false;
    }

    private static boolean isAirOrWater(BlockState state) {
        return state.isAir() || state.is(Blocks.WATER);
    }
}
