package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.BlockPileConfiguration;

public class BlockPileFeature extends Feature<BlockPileConfiguration> {
    public BlockPileFeature(Codec<BlockPileConfiguration> codec) {
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
    public boolean place(FeaturePlaceContext<BlockPileConfiguration> context) {
        BlockPos blockpos = context.origin();
        WorldGenLevel worldgenlevel = context.level();
        RandomSource randomsource = context.random();
        BlockPileConfiguration blockpileconfiguration = context.config();
        if (blockpos.getY() < worldgenlevel.getMinBuildHeight() + 5) {
            return false;
        } else {
            int i = 2 + randomsource.nextInt(2);
            int j = 2 + randomsource.nextInt(2);

            for (BlockPos blockpos1 : BlockPos.betweenClosed(blockpos.offset(-i, 0, -j), blockpos.offset(i, 1, j))) {
                int k = blockpos.getX() - blockpos1.getX();
                int l = blockpos.getZ() - blockpos1.getZ();
                if ((float)(k * k + l * l) <= randomsource.nextFloat() * 10.0F - randomsource.nextFloat() * 6.0F) {
                    this.tryPlaceBlock(worldgenlevel, blockpos1, randomsource, blockpileconfiguration);
                } else if ((double)randomsource.nextFloat() < 0.031) {
                    this.tryPlaceBlock(worldgenlevel, blockpos1, randomsource, blockpileconfiguration);
                }
            }

            return true;
        }
    }

    private boolean mayPlaceOn(LevelAccessor level, BlockPos pos, RandomSource random) {
        BlockPos blockpos = pos.below();
        BlockState blockstate = level.getBlockState(blockpos);
        return blockstate.is(Blocks.DIRT_PATH) ? random.nextBoolean() : blockstate.isFaceSturdy(level, blockpos, Direction.UP);
    }

    private void tryPlaceBlock(LevelAccessor level, BlockPos pos, RandomSource random, BlockPileConfiguration config) {
        if (level.isEmptyBlock(pos) && this.mayPlaceOn(level, pos, random)) {
            level.setBlock(pos, config.stateProvider.getState(random, pos), 4);
        }
    }
}
