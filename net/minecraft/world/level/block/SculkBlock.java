package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;

public class SculkBlock extends DropExperienceBlock implements SculkBehaviour {
    public static final MapCodec<SculkBlock> CODEC = simpleCodec(SculkBlock::new);

    @Override
    public MapCodec<SculkBlock> codec() {
        return CODEC;
    }

    public SculkBlock(BlockBehaviour.Properties p_222063_) {
        super(ConstantInt.of(1), p_222063_);
    }

    @Override
    public int attemptUseCharge(
        SculkSpreader.ChargeCursor cursor, LevelAccessor level, BlockPos pos, RandomSource random, SculkSpreader spreader, boolean shouldConvertBlocks
    ) {
        int i = cursor.getCharge();
        if (i != 0 && random.nextInt(spreader.chargeDecayRate()) == 0) {
            BlockPos blockpos = cursor.getPos();
            boolean flag = blockpos.closerThan(pos, (double)spreader.noGrowthRadius());
            if (!flag && canPlaceGrowth(level, blockpos)) {
                int j = spreader.growthSpawnCost();
                if (random.nextInt(j) < i) {
                    BlockPos blockpos1 = blockpos.above();
                    BlockState blockstate = this.getRandomGrowthState(level, blockpos1, random, spreader.isWorldGeneration());
                    level.setBlock(blockpos1, blockstate, 3);
                    level.playSound(null, blockpos, blockstate.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
                }

                return Math.max(0, i - j);
            } else {
                return random.nextInt(spreader.additionalDecayRate()) != 0 ? i : i - (flag ? 1 : getDecayPenalty(spreader, blockpos, pos, i));
            }
        } else {
            return i;
        }
    }

    private static int getDecayPenalty(SculkSpreader spreader, BlockPos cursorPos, BlockPos rootPos, int charge) {
        int i = spreader.noGrowthRadius();
        float f = Mth.square((float)Math.sqrt(cursorPos.distSqr(rootPos)) - (float)i);
        int j = Mth.square(24 - i);
        float f1 = Math.min(1.0F, f / (float)j);
        return Math.max(1, (int)((float)charge * f1 * 0.5F));
    }

    private BlockState getRandomGrowthState(LevelAccessor level, BlockPos pos, RandomSource random, boolean isWorldGeneration) {
        BlockState blockstate;
        if (random.nextInt(11) == 0) {
            blockstate = Blocks.SCULK_SHRIEKER.defaultBlockState().setValue(SculkShriekerBlock.CAN_SUMMON, Boolean.valueOf(isWorldGeneration));
        } else {
            blockstate = Blocks.SCULK_SENSOR.defaultBlockState();
        }

        return blockstate.hasProperty(BlockStateProperties.WATERLOGGED) && !level.getFluidState(pos).isEmpty()
            ? blockstate.setValue(BlockStateProperties.WATERLOGGED, Boolean.valueOf(true))
            : blockstate;
    }

    private static boolean canPlaceGrowth(LevelAccessor level, BlockPos pos) {
        BlockState blockstate = level.getBlockState(pos.above());
        if (blockstate.isAir() || blockstate.is(Blocks.WATER) && blockstate.getFluidState().is(Fluids.WATER)) {
            int i = 0;

            for (BlockPos blockpos : BlockPos.betweenClosed(pos.offset(-4, 0, -4), pos.offset(4, 2, 4))) {
                BlockState blockstate1 = level.getBlockState(blockpos);
                if (blockstate1.is(Blocks.SCULK_SENSOR) || blockstate1.is(Blocks.SCULK_SHRIEKER)) {
                    i++;
                }

                if (i > 2) {
                    return false;
                }
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean canChangeBlockStateOnSpread() {
        return false;
    }
}
