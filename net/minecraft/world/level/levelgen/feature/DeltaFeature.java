package net.minecraft.world.level.levelgen.feature;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.DeltaFeatureConfiguration;

public class DeltaFeature extends Feature<DeltaFeatureConfiguration> {
    private static final ImmutableList<Block> CANNOT_REPLACE = ImmutableList.of(
        Blocks.BEDROCK, Blocks.NETHER_BRICKS, Blocks.NETHER_BRICK_FENCE, Blocks.NETHER_BRICK_STAIRS, Blocks.NETHER_WART, Blocks.CHEST, Blocks.SPAWNER
    );
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final double RIM_SPAWN_CHANCE = 0.9;

    public DeltaFeature(Codec<DeltaFeatureConfiguration> codec) {
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
    public boolean place(FeaturePlaceContext<DeltaFeatureConfiguration> context) {
        boolean flag = false;
        RandomSource randomsource = context.random();
        WorldGenLevel worldgenlevel = context.level();
        DeltaFeatureConfiguration deltafeatureconfiguration = context.config();
        BlockPos blockpos = context.origin();
        boolean flag1 = randomsource.nextDouble() < 0.9;
        int i = flag1 ? deltafeatureconfiguration.rimSize().sample(randomsource) : 0;
        int j = flag1 ? deltafeatureconfiguration.rimSize().sample(randomsource) : 0;
        boolean flag2 = flag1 && i != 0 && j != 0;
        int k = deltafeatureconfiguration.size().sample(randomsource);
        int l = deltafeatureconfiguration.size().sample(randomsource);
        int i1 = Math.max(k, l);

        for (BlockPos blockpos1 : BlockPos.withinManhattan(blockpos, k, 0, l)) {
            if (blockpos1.distManhattan(blockpos) > i1) {
                break;
            }

            if (isClear(worldgenlevel, blockpos1, deltafeatureconfiguration)) {
                if (flag2) {
                    flag = true;
                    this.setBlock(worldgenlevel, blockpos1, deltafeatureconfiguration.rim());
                }

                BlockPos blockpos2 = blockpos1.offset(i, 0, j);
                if (isClear(worldgenlevel, blockpos2, deltafeatureconfiguration)) {
                    flag = true;
                    this.setBlock(worldgenlevel, blockpos2, deltafeatureconfiguration.contents());
                }
            }
        }

        return flag;
    }

    private static boolean isClear(LevelAccessor level, BlockPos pos, DeltaFeatureConfiguration config) {
        BlockState blockstate = level.getBlockState(pos);
        if (blockstate.is(config.contents().getBlock())) {
            return false;
        } else if (CANNOT_REPLACE.contains(blockstate.getBlock())) {
            return false;
        } else {
            for (Direction direction : DIRECTIONS) {
                boolean flag = level.getBlockState(pos.relative(direction)).isAir();
                if (flag && direction != Direction.UP || !flag && direction == Direction.UP) {
                    return false;
                }
            }

            return true;
        }
    }
}
