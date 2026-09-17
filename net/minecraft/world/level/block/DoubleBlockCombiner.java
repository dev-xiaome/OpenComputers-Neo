package net.minecraft.world.level.block;

import java.util.function.BiPredicate;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

public class DoubleBlockCombiner {
    public static <S extends BlockEntity> DoubleBlockCombiner.NeighborCombineResult<S> combineWithNeigbour(
        BlockEntityType<S> blockEntityType,
        Function<BlockState, DoubleBlockCombiner.BlockType> doubleBlockTypeGetter,
        Function<BlockState, Direction> directionGetter,
        DirectionProperty directionProperty,
        BlockState state,
        LevelAccessor level,
        BlockPos pos,
        BiPredicate<LevelAccessor, BlockPos> blockedChestTest
    ) {
        S s = blockEntityType.getBlockEntity(level, pos);
        if (s == null) {
            return DoubleBlockCombiner.Combiner::acceptNone;
        } else if (blockedChestTest.test(level, pos)) {
            return DoubleBlockCombiner.Combiner::acceptNone;
        } else {
            DoubleBlockCombiner.BlockType doubleblockcombiner$blocktype = doubleBlockTypeGetter.apply(state);
            boolean flag = doubleblockcombiner$blocktype == DoubleBlockCombiner.BlockType.SINGLE;
            boolean flag1 = doubleblockcombiner$blocktype == DoubleBlockCombiner.BlockType.FIRST;
            if (flag) {
                return new DoubleBlockCombiner.NeighborCombineResult.Single<>(s);
            } else {
                BlockPos blockpos = pos.relative(directionGetter.apply(state));
                BlockState blockstate = level.getBlockState(blockpos);
                if (blockstate.is(state.getBlock())) {
                    DoubleBlockCombiner.BlockType doubleblockcombiner$blocktype1 = doubleBlockTypeGetter.apply(blockstate);
                    if (doubleblockcombiner$blocktype1 != DoubleBlockCombiner.BlockType.SINGLE
                        && doubleblockcombiner$blocktype != doubleblockcombiner$blocktype1
                        && blockstate.getValue(directionProperty) == state.getValue(directionProperty)) {
                        if (blockedChestTest.test(level, blockpos)) {
                            return DoubleBlockCombiner.Combiner::acceptNone;
                        }

                        S s1 = blockEntityType.getBlockEntity(level, blockpos);
                        if (s1 != null) {
                            S s2 = flag1 ? s : s1;
                            S s3 = flag1 ? s1 : s;
                            return new DoubleBlockCombiner.NeighborCombineResult.Double<>(s2, s3);
                        }
                    }
                }

                return new DoubleBlockCombiner.NeighborCombineResult.Single<>(s);
            }
        }
    }

    public static enum BlockType {
        SINGLE,
        FIRST,
        SECOND;
    }

    public interface Combiner<S, T> {
        T acceptDouble(S first, S second);

        T acceptSingle(S single);

        T acceptNone();
    }

    public interface NeighborCombineResult<S> {
        <T> T apply(DoubleBlockCombiner.Combiner<? super S, T> combiner);

        public static final class Double<S> implements DoubleBlockCombiner.NeighborCombineResult<S> {
            private final S first;
            private final S second;

            public Double(S first, S second) {
                this.first = first;
                this.second = second;
            }

            @Override
            public <T> T apply(DoubleBlockCombiner.Combiner<? super S, T> combiner) {
                return combiner.acceptDouble(this.first, this.second);
            }
        }

        public static final class Single<S> implements DoubleBlockCombiner.NeighborCombineResult<S> {
            private final S single;

            public Single(S single) {
                this.single = single;
            }

            @Override
            public <T> T apply(DoubleBlockCombiner.Combiner<? super S, T> combiner) {
                return combiner.acceptSingle(this.single);
            }
        }
    }
}
