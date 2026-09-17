package net.minecraft;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntStack;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class BlockUtil {
    /**
     * Finds the rectangle with the largest area containing centerPos within the blocks specified by the predicate
     */
    public static BlockUtil.FoundRectangle getLargestRectangleAround(
        BlockPos centerPos, Direction.Axis axis1, int max1, Direction.Axis axis2, int max2, Predicate<BlockPos> posPredicate
    ) {
        BlockPos.MutableBlockPos blockpos$mutableblockpos = centerPos.mutable();
        Direction direction = Direction.get(Direction.AxisDirection.NEGATIVE, axis1);
        Direction direction1 = direction.getOpposite();
        Direction direction2 = Direction.get(Direction.AxisDirection.NEGATIVE, axis2);
        Direction direction3 = direction2.getOpposite();
        int i = getLimit(posPredicate, blockpos$mutableblockpos.set(centerPos), direction, max1);
        int j = getLimit(posPredicate, blockpos$mutableblockpos.set(centerPos), direction1, max1);
        int k = i;
        BlockUtil.IntBounds[] ablockutil$intbounds = new BlockUtil.IntBounds[i + 1 + j];
        ablockutil$intbounds[i] = new BlockUtil.IntBounds(
            getLimit(posPredicate, blockpos$mutableblockpos.set(centerPos), direction2, max2),
            getLimit(posPredicate, blockpos$mutableblockpos.set(centerPos), direction3, max2)
        );
        int l = ablockutil$intbounds[i].min;

        for (int i1 = 1; i1 <= i; i1++) {
            BlockUtil.IntBounds blockutil$intbounds = ablockutil$intbounds[k - (i1 - 1)];
            ablockutil$intbounds[k - i1] = new BlockUtil.IntBounds(
                getLimit(posPredicate, blockpos$mutableblockpos.set(centerPos).move(direction, i1), direction2, blockutil$intbounds.min),
                getLimit(posPredicate, blockpos$mutableblockpos.set(centerPos).move(direction, i1), direction3, blockutil$intbounds.max)
            );
        }

        for (int l2 = 1; l2 <= j; l2++) {
            BlockUtil.IntBounds blockutil$intbounds2 = ablockutil$intbounds[k + l2 - 1];
            ablockutil$intbounds[k + l2] = new BlockUtil.IntBounds(
                getLimit(posPredicate, blockpos$mutableblockpos.set(centerPos).move(direction1, l2), direction2, blockutil$intbounds2.min),
                getLimit(posPredicate, blockpos$mutableblockpos.set(centerPos).move(direction1, l2), direction3, blockutil$intbounds2.max)
            );
        }

        int i3 = 0;
        int j3 = 0;
        int j1 = 0;
        int k1 = 0;
        int[] aint = new int[ablockutil$intbounds.length];

        for (int l1 = l; l1 >= 0; l1--) {
            for (int i2 = 0; i2 < ablockutil$intbounds.length; i2++) {
                BlockUtil.IntBounds blockutil$intbounds1 = ablockutil$intbounds[i2];
                int j2 = l - blockutil$intbounds1.min;
                int k2 = l + blockutil$intbounds1.max;
                aint[i2] = l1 >= j2 && l1 <= k2 ? k2 + 1 - l1 : 0;
            }

            Pair<BlockUtil.IntBounds, Integer> pair = getMaxRectangleLocation(aint);
            BlockUtil.IntBounds blockutil$intbounds3 = pair.getFirst();
            int k3 = 1 + blockutil$intbounds3.max - blockutil$intbounds3.min;
            int l3 = pair.getSecond();
            if (k3 * l3 > j1 * k1) {
                i3 = blockutil$intbounds3.min;
                j3 = l1;
                j1 = k3;
                k1 = l3;
            }
        }

        return new BlockUtil.FoundRectangle(centerPos.relative(axis1, i3 - k).relative(axis2, j3 - l), j1, k1);
    }

    /**
     * Finds the distance we can travel in the given direction while the predicate returns true
     */
    private static int getLimit(Predicate<BlockPos> posPredicate, BlockPos.MutableBlockPos centerPos, Direction direction, int max) {
        int i = 0;

        while (i < max && posPredicate.test(centerPos.move(direction))) {
            i++;
        }

        return i;
    }

    /**
     * Finds the largest rectangle within the array of heights
     */
    @VisibleForTesting
    static Pair<BlockUtil.IntBounds, Integer> getMaxRectangleLocation(int[] heights) {
        int i = 0;
        int j = 0;
        int k = 0;
        IntStack intstack = new IntArrayList();
        intstack.push(0);

        for (int l = 1; l <= heights.length; l++) {
            int i1 = l == heights.length ? 0 : heights[l];

            while (!intstack.isEmpty()) {
                int j1 = heights[intstack.topInt()];
                if (i1 >= j1) {
                    intstack.push(l);
                    break;
                }

                intstack.popInt();
                int k1 = intstack.isEmpty() ? 0 : intstack.topInt() + 1;
                if (j1 * (l - k1) > k * (j - i)) {
                    j = l;
                    i = k1;
                    k = j1;
                }
            }

            if (intstack.isEmpty()) {
                intstack.push(l);
            }
        }

        return new Pair<>(new BlockUtil.IntBounds(i, j - 1), k);
    }

    public static Optional<BlockPos> getTopConnectedBlock(BlockGetter getter, BlockPos pos, Block baseBlock, Direction direction, Block endBlock) {
        BlockPos.MutableBlockPos blockpos$mutableblockpos = pos.mutable();

        BlockState blockstate;
        do {
            blockpos$mutableblockpos.move(direction);
            blockstate = getter.getBlockState(blockpos$mutableblockpos);
        } while (blockstate.is(baseBlock));

        return blockstate.is(endBlock) ? Optional.of(blockpos$mutableblockpos) : Optional.empty();
    }

    public static class FoundRectangle {
        /**
         * Starting position of the rectangle represented by this result
         */
        public final BlockPos minCorner;
        /**
         * Distance between minimum and maximum values on the first axis argument
         */
        public final int axis1Size;
        /**
         * Distance between minimum and maximum values on the second axis argument
         */
        public final int axis2Size;

        public FoundRectangle(BlockPos minCorner, int axis1Size, int axis2Size) {
            this.minCorner = minCorner;
            this.axis1Size = axis1Size;
            this.axis2Size = axis2Size;
        }
    }

    public static class IntBounds {
        /**
         * The minimum bound
         */
        public final int min;
        /**
         * The maximum bound
         */
        public final int max;

        public IntBounds(int min, int max) {
            this.min = min;
            this.max = max;
        }

        @Override
        public String toString() {
            return "IntBounds{min=" + this.min + ", max=" + this.max + "}";
        }
    }
}
