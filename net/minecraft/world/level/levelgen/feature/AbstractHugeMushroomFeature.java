package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.HugeMushroomFeatureConfiguration;

public abstract class AbstractHugeMushroomFeature extends Feature<HugeMushroomFeatureConfiguration> {
    public AbstractHugeMushroomFeature(Codec<HugeMushroomFeatureConfiguration> codec) {
        super(codec);
    }

    protected void placeTrunk(
        LevelAccessor level,
        RandomSource random,
        BlockPos pos,
        HugeMushroomFeatureConfiguration config,
        int maxHeight,
        BlockPos.MutableBlockPos mutablePos
    ) {
        for (int i = 0; i < maxHeight; i++) {
            mutablePos.set(pos).move(Direction.UP, i);
            if (!level.getBlockState(mutablePos).isSolidRender(level, mutablePos)) {
                this.setBlock(level, mutablePos, config.stemProvider.getState(random, pos));
            }
        }
    }

    protected int getTreeHeight(RandomSource random) {
        int i = random.nextInt(3) + 4;
        if (random.nextInt(12) == 0) {
            i *= 2;
        }

        return i;
    }

    protected boolean isValidPosition(
        LevelAccessor level, BlockPos pos, int maxHeight, BlockPos.MutableBlockPos mutablePos, HugeMushroomFeatureConfiguration config
    ) {
        int i = pos.getY();
        if (i >= level.getMinBuildHeight() + 1 && i + maxHeight + 1 < level.getMaxBuildHeight()) {
            BlockState blockstate = level.getBlockState(pos.below());
            if (!isDirt(blockstate) && !blockstate.is(BlockTags.MUSHROOM_GROW_BLOCK)) {
                return false;
            } else {
                for (int j = 0; j <= maxHeight; j++) {
                    int k = this.getTreeRadiusForHeight(-1, -1, config.foliageRadius, j);

                    for (int l = -k; l <= k; l++) {
                        for (int i1 = -k; i1 <= k; i1++) {
                            BlockState blockstate1 = level.getBlockState(mutablePos.setWithOffset(pos, l, j, i1));
                            if (!blockstate1.isAir() && !blockstate1.is(BlockTags.LEAVES)) {
                                return false;
                            }
                        }
                    }
                }

                return true;
            }
        } else {
            return false;
        }
    }

    /**
     * Places the given feature at the given location.
     * During world generation, features are provided with a 3x3 region of chunks, centered on the chunk being generated, that they can safely generate into.
     *
     * @param context A context object with a reference to the level and the position
     *                the feature is being placed at
     */
    @Override
    public boolean place(FeaturePlaceContext<HugeMushroomFeatureConfiguration> context) {
        WorldGenLevel worldgenlevel = context.level();
        BlockPos blockpos = context.origin();
        RandomSource randomsource = context.random();
        HugeMushroomFeatureConfiguration hugemushroomfeatureconfiguration = context.config();
        int i = this.getTreeHeight(randomsource);
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();
        if (!this.isValidPosition(worldgenlevel, blockpos, i, blockpos$mutableblockpos, hugemushroomfeatureconfiguration)) {
            return false;
        } else {
            this.makeCap(worldgenlevel, randomsource, blockpos, i, blockpos$mutableblockpos, hugemushroomfeatureconfiguration);
            this.placeTrunk(worldgenlevel, randomsource, blockpos, hugemushroomfeatureconfiguration, i, blockpos$mutableblockpos);
            return true;
        }
    }

    protected abstract int getTreeRadiusForHeight(int p_65094_, int height, int foliageRadius, int y);

    protected abstract void makeCap(
        LevelAccessor level,
        RandomSource random,
        BlockPos pos,
        int treeHeight,
        BlockPos.MutableBlockPos mutablePos,
        HugeMushroomFeatureConfiguration config
    );
}
