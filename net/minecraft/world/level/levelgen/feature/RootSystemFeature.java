package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.configurations.RootSystemConfiguration;

public class RootSystemFeature extends Feature<RootSystemConfiguration> {
    public RootSystemFeature(Codec<RootSystemConfiguration> codec) {
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
    public boolean place(FeaturePlaceContext<RootSystemConfiguration> context) {
        WorldGenLevel worldgenlevel = context.level();
        BlockPos blockpos = context.origin();
        if (!worldgenlevel.getBlockState(blockpos).isAir()) {
            return false;
        } else {
            RandomSource randomsource = context.random();
            BlockPos blockpos1 = context.origin();
            RootSystemConfiguration rootsystemconfiguration = context.config();
            BlockPos.MutableBlockPos blockpos$mutableblockpos = blockpos1.mutable();
            if (placeDirtAndTree(worldgenlevel, context.chunkGenerator(), rootsystemconfiguration, randomsource, blockpos$mutableblockpos, blockpos1)) {
                placeRoots(worldgenlevel, rootsystemconfiguration, randomsource, blockpos1, blockpos$mutableblockpos);
            }

            return true;
        }
    }

    private static boolean spaceForTree(WorldGenLevel level, RootSystemConfiguration config, BlockPos pos) {
        BlockPos.MutableBlockPos blockpos$mutableblockpos = pos.mutable();

        for (int i = 1; i <= config.requiredVerticalSpaceForTree; i++) {
            blockpos$mutableblockpos.move(Direction.UP);
            BlockState blockstate = level.getBlockState(blockpos$mutableblockpos);
            if (!isAllowedTreeSpace(blockstate, i, config.allowedVerticalWaterForTree)) {
                return false;
            }
        }

        return true;
    }

    private static boolean isAllowedTreeSpace(BlockState state, int y, int allowedVerticalWater) {
        if (state.isAir()) {
            return true;
        } else {
            int i = y + 1;
            return i <= allowedVerticalWater && state.getFluidState().is(FluidTags.WATER);
        }
    }

    private static boolean placeDirtAndTree(
        WorldGenLevel level,
        ChunkGenerator chunkGenerator,
        RootSystemConfiguration config,
        RandomSource random,
        BlockPos.MutableBlockPos mutablePos,
        BlockPos basePos
    ) {
        for (int i = 0; i < config.rootColumnMaxHeight; i++) {
            mutablePos.move(Direction.UP);
            if (config.allowedTreePosition.test(level, mutablePos) && spaceForTree(level, config, mutablePos)) {
                BlockPos blockpos = mutablePos.below();
                if (level.getFluidState(blockpos).is(FluidTags.LAVA) || !level.getBlockState(blockpos).isSolid()) {
                    return false;
                }

                if (config.treeFeature.value().place(level, chunkGenerator, random, mutablePos)) {
                    placeDirt(basePos, basePos.getY() + i, level, config, random);
                    return true;
                }
            }
        }

        return false;
    }

    private static void placeDirt(BlockPos pos, int maxY, WorldGenLevel level, RootSystemConfiguration config, RandomSource random) {
        int i = pos.getX();
        int j = pos.getZ();
        BlockPos.MutableBlockPos blockpos$mutableblockpos = pos.mutable();

        for (int k = pos.getY(); k < maxY; k++) {
            placeRootedDirt(level, config, random, i, j, blockpos$mutableblockpos.set(i, k, j));
        }
    }

    private static void placeRootedDirt(
        WorldGenLevel level, RootSystemConfiguration config, RandomSource random, int x, int z, BlockPos.MutableBlockPos pos
    ) {
        int i = config.rootRadius;
        Predicate<BlockState> predicate = p_204762_ -> p_204762_.is(config.rootReplaceable);

        for (int j = 0; j < config.rootPlacementAttempts; j++) {
            pos.setWithOffset(pos, random.nextInt(i) - random.nextInt(i), 0, random.nextInt(i) - random.nextInt(i));
            if (predicate.test(level.getBlockState(pos))) {
                level.setBlock(pos, config.rootStateProvider.getState(random, pos), 2);
            }

            pos.setX(x);
            pos.setZ(z);
        }
    }

    private static void placeRoots(
        WorldGenLevel level, RootSystemConfiguration config, RandomSource random, BlockPos basePos, BlockPos.MutableBlockPos mutablePos
    ) {
        int i = config.hangingRootRadius;
        int j = config.hangingRootsVerticalSpan;

        for (int k = 0; k < config.hangingRootPlacementAttempts; k++) {
            mutablePos.setWithOffset(
                basePos,
                random.nextInt(i) - random.nextInt(i),
                random.nextInt(j) - random.nextInt(j),
                random.nextInt(i) - random.nextInt(i)
            );
            if (level.isEmptyBlock(mutablePos)) {
                BlockState blockstate = config.hangingRootStateProvider.getState(random, mutablePos);
                if (blockstate.canSurvive(level, mutablePos)
                    && level.getBlockState(mutablePos.above()).isFaceSturdy(level, mutablePos, Direction.DOWN)) {
                    level.setBlock(mutablePos, blockstate, 2);
                }
            }
        }
    }
}
