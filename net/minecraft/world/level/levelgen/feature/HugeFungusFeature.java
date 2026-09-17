package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;

public class HugeFungusFeature extends Feature<HugeFungusConfiguration> {
    private static final float HUGE_PROBABILITY = 0.06F;

    public HugeFungusFeature(Codec<HugeFungusConfiguration> codec) {
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
    public boolean place(FeaturePlaceContext<HugeFungusConfiguration> context) {
        WorldGenLevel worldgenlevel = context.level();
        BlockPos blockpos = context.origin();
        RandomSource randomsource = context.random();
        ChunkGenerator chunkgenerator = context.chunkGenerator();
        HugeFungusConfiguration hugefungusconfiguration = context.config();
        Block block = hugefungusconfiguration.validBaseState.getBlock();
        BlockPos blockpos1 = null;
        BlockState blockstate = worldgenlevel.getBlockState(blockpos.below());
        if (blockstate.is(block)) {
            blockpos1 = blockpos;
        }

        if (blockpos1 == null) {
            return false;
        } else {
            int i = Mth.nextInt(randomsource, 4, 13);
            if (randomsource.nextInt(12) == 0) {
                i *= 2;
            }

            if (!hugefungusconfiguration.planted) {
                int j = chunkgenerator.getGenDepth();
                if (blockpos1.getY() + i + 1 >= j) {
                    return false;
                }
            }

            boolean flag = !hugefungusconfiguration.planted && randomsource.nextFloat() < 0.06F;
            worldgenlevel.setBlock(blockpos, Blocks.AIR.defaultBlockState(), 4);
            this.placeStem(worldgenlevel, randomsource, hugefungusconfiguration, blockpos1, i, flag);
            this.placeHat(worldgenlevel, randomsource, hugefungusconfiguration, blockpos1, i, flag);
            return true;
        }
    }

    private static boolean isReplaceable(WorldGenLevel level, BlockPos pos, HugeFungusConfiguration config, boolean checkConfig) {
        if (level.isStateAtPosition(pos, BlockBehaviour.BlockStateBase::canBeReplaced)) {
            return true;
        } else {
            return checkConfig ? config.replaceableBlocks.test(level, pos) : false;
        }
    }

    private void placeStem(
        WorldGenLevel level, RandomSource random, HugeFungusConfiguration config, BlockPos pos, int height, boolean huge
    ) {
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();
        BlockState blockstate = config.stemState;
        int i = huge ? 1 : 0;

        for (int j = -i; j <= i; j++) {
            for (int k = -i; k <= i; k++) {
                boolean flag = huge && Mth.abs(j) == i && Mth.abs(k) == i;

                for (int l = 0; l < height; l++) {
                    blockpos$mutableblockpos.setWithOffset(pos, j, l, k);
                    if (isReplaceable(level, blockpos$mutableblockpos, config, true)) {
                        if (config.planted) {
                            if (!level.getBlockState(blockpos$mutableblockpos.below()).isAir()) {
                                level.destroyBlock(blockpos$mutableblockpos, true);
                            }

                            level.setBlock(blockpos$mutableblockpos, blockstate, 3);
                        } else if (flag) {
                            if (random.nextFloat() < 0.1F) {
                                this.setBlock(level, blockpos$mutableblockpos, blockstate);
                            }
                        } else {
                            this.setBlock(level, blockpos$mutableblockpos, blockstate);
                        }
                    }
                }
            }
        }
    }

    private void placeHat(
        WorldGenLevel level, RandomSource random, HugeFungusConfiguration config, BlockPos pos, int height, boolean huge
    ) {
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();
        boolean flag = config.hatState.is(Blocks.NETHER_WART_BLOCK);
        int i = Math.min(random.nextInt(1 + height / 3) + 5, height);
        int j = height - i;

        for (int k = j; k <= height; k++) {
            int l = k < height - random.nextInt(3) ? 2 : 1;
            if (i > 8 && k < j + 4) {
                l = 3;
            }

            if (huge) {
                l++;
            }

            for (int i1 = -l; i1 <= l; i1++) {
                for (int j1 = -l; j1 <= l; j1++) {
                    boolean flag1 = i1 == -l || i1 == l;
                    boolean flag2 = j1 == -l || j1 == l;
                    boolean flag3 = !flag1 && !flag2 && k != height;
                    boolean flag4 = flag1 && flag2;
                    boolean flag5 = k < j + 3;
                    blockpos$mutableblockpos.setWithOffset(pos, i1, k, j1);
                    if (isReplaceable(level, blockpos$mutableblockpos, config, false)) {
                        if (config.planted && !level.getBlockState(blockpos$mutableblockpos.below()).isAir()) {
                            level.destroyBlock(blockpos$mutableblockpos, true);
                        }

                        if (flag5) {
                            if (!flag3) {
                                this.placeHatDropBlock(level, random, blockpos$mutableblockpos, config.hatState, flag);
                            }
                        } else if (flag3) {
                            this.placeHatBlock(level, random, config, blockpos$mutableblockpos, 0.1F, 0.2F, flag ? 0.1F : 0.0F);
                        } else if (flag4) {
                            this.placeHatBlock(level, random, config, blockpos$mutableblockpos, 0.01F, 0.7F, flag ? 0.083F : 0.0F);
                        } else {
                            this.placeHatBlock(level, random, config, blockpos$mutableblockpos, 5.0E-4F, 0.98F, flag ? 0.07F : 0.0F);
                        }
                    }
                }
            }
        }
    }

    private void placeHatBlock(
        LevelAccessor level,
        RandomSource random,
        HugeFungusConfiguration config,
        BlockPos.MutableBlockPos pos,
        float decorationChance,
        float hatChance,
        float weepingVineChance
    ) {
        if (random.nextFloat() < decorationChance) {
            this.setBlock(level, pos, config.decorState);
        } else if (random.nextFloat() < hatChance) {
            this.setBlock(level, pos, config.hatState);
            if (random.nextFloat() < weepingVineChance) {
                tryPlaceWeepingVines(pos, level, random);
            }
        }
    }

    private void placeHatDropBlock(LevelAccessor level, RandomSource random, BlockPos pos, BlockState state, boolean weepingVines) {
        if (level.getBlockState(pos.below()).is(state.getBlock())) {
            this.setBlock(level, pos, state);
        } else if ((double)random.nextFloat() < 0.15) {
            this.setBlock(level, pos, state);
            if (weepingVines && random.nextInt(11) == 0) {
                tryPlaceWeepingVines(pos, level, random);
            }
        }
    }

    private static void tryPlaceWeepingVines(BlockPos pos, LevelAccessor level, RandomSource random) {
        BlockPos.MutableBlockPos blockpos$mutableblockpos = pos.mutable().move(Direction.DOWN);
        if (level.isEmptyBlock(blockpos$mutableblockpos)) {
            int i = Mth.nextInt(random, 1, 5);
            if (random.nextInt(7) == 0) {
                i *= 2;
            }

            int j = 23;
            int k = 25;
            WeepingVinesFeature.placeWeepingVinesColumn(level, random, blockpos$mutableblockpos, i, 23, 25);
        }
    }
}
