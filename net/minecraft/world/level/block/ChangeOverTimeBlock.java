package net.minecraft.world.level.block;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

public interface ChangeOverTimeBlock<T extends Enum<T>> {
    int SCAN_DISTANCE = 4;

    Optional<BlockState> getNext(BlockState state);

    float getChanceModifier();

    default void changeOverTime(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        float f = 0.05688889F;
        if (random.nextFloat() < 0.05688889F) {
            this.getNextState(state, level, pos, random).ifPresent(p_153039_ -> level.setBlockAndUpdate(pos, p_153039_));
        }
    }

    T getAge();

    default Optional<BlockState> getNextState(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int i = this.getAge().ordinal();
        int j = 0;
        int k = 0;

        for (BlockPos blockpos : BlockPos.withinManhattan(pos, 4, 4, 4)) {
            int l = blockpos.distManhattan(pos);
            if (l > 4) {
                break;
            }

            if (!blockpos.equals(pos) && level.getBlockState(blockpos).getBlock() instanceof ChangeOverTimeBlock<?> changeovertimeblock) {
                Enum<?> oenum = changeovertimeblock.getAge();
                if (this.getAge().getClass() == oenum.getClass()) {
                    int i1 = oenum.ordinal();
                    if (i1 < i) {
                        return Optional.empty();
                    }

                    if (i1 > i) {
                        k++;
                    } else {
                        j++;
                    }
                }
            }
        }

        float f = (float)(k + 1) / (float)(k + j + 1);
        float f1 = f * f * this.getChanceModifier();
        return random.nextFloat() < f1 ? this.getNext(state) : Optional.empty();
    }
}
