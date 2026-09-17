package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class WeepingVinesFeature extends Feature<NoneFeatureConfiguration> {
    private static final Direction[] DIRECTIONS = Direction.values();

    public WeepingVinesFeature(Codec<NoneFeatureConfiguration> codec) {
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
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel worldgenlevel = context.level();
        BlockPos blockpos = context.origin();
        RandomSource randomsource = context.random();
        if (!worldgenlevel.isEmptyBlock(blockpos)) {
            return false;
        } else {
            BlockState blockstate = worldgenlevel.getBlockState(blockpos.above());
            if (!blockstate.is(Blocks.NETHERRACK) && !blockstate.is(Blocks.NETHER_WART_BLOCK)) {
                return false;
            } else {
                this.placeRoofNetherWart(worldgenlevel, randomsource, blockpos);
                this.placeRoofWeepingVines(worldgenlevel, randomsource, blockpos);
                return true;
            }
        }
    }

    private void placeRoofNetherWart(LevelAccessor level, RandomSource random, BlockPos pos) {
        level.setBlock(pos, Blocks.NETHER_WART_BLOCK.defaultBlockState(), 2);
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos blockpos$mutableblockpos1 = new BlockPos.MutableBlockPos();

        for (int i = 0; i < 200; i++) {
            blockpos$mutableblockpos.setWithOffset(
                pos,
                random.nextInt(6) - random.nextInt(6),
                random.nextInt(2) - random.nextInt(5),
                random.nextInt(6) - random.nextInt(6)
            );
            if (level.isEmptyBlock(blockpos$mutableblockpos)) {
                int j = 0;

                for (Direction direction : DIRECTIONS) {
                    BlockState blockstate = level.getBlockState(blockpos$mutableblockpos1.setWithOffset(blockpos$mutableblockpos, direction));
                    if (blockstate.is(Blocks.NETHERRACK) || blockstate.is(Blocks.NETHER_WART_BLOCK)) {
                        j++;
                    }

                    if (j > 1) {
                        break;
                    }
                }

                if (j == 1) {
                    level.setBlock(blockpos$mutableblockpos, Blocks.NETHER_WART_BLOCK.defaultBlockState(), 2);
                }
            }
        }
    }

    private void placeRoofWeepingVines(LevelAccessor level, RandomSource random, BlockPos pos) {
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

        for (int i = 0; i < 100; i++) {
            blockpos$mutableblockpos.setWithOffset(
                pos,
                random.nextInt(8) - random.nextInt(8),
                random.nextInt(2) - random.nextInt(7),
                random.nextInt(8) - random.nextInt(8)
            );
            if (level.isEmptyBlock(blockpos$mutableblockpos)) {
                BlockState blockstate = level.getBlockState(blockpos$mutableblockpos.above());
                if (blockstate.is(Blocks.NETHERRACK) || blockstate.is(Blocks.NETHER_WART_BLOCK)) {
                    int j = Mth.nextInt(random, 1, 8);
                    if (random.nextInt(6) == 0) {
                        j *= 2;
                    }

                    if (random.nextInt(5) == 0) {
                        j = 1;
                    }

                    int k = 17;
                    int l = 25;
                    placeWeepingVinesColumn(level, random, blockpos$mutableblockpos, j, 17, 25);
                }
            }
        }
    }

    public static void placeWeepingVinesColumn(
        LevelAccessor level, RandomSource random, BlockPos.MutableBlockPos pos, int height, int minAge, int maxAge
    ) {
        for (int i = 0; i <= height; i++) {
            if (level.isEmptyBlock(pos)) {
                if (i == height || !level.isEmptyBlock(pos.below())) {
                    level.setBlock(
                        pos,
                        Blocks.WEEPING_VINES
                            .defaultBlockState()
                            .setValue(GrowingPlantHeadBlock.AGE, Integer.valueOf(Mth.nextInt(random, minAge, maxAge))),
                        2
                    );
                    break;
                }

                level.setBlock(pos, Blocks.WEEPING_VINES_PLANT.defaultBlockState(), 2);
            }

            pos.move(Direction.DOWN);
        }
    }
}
