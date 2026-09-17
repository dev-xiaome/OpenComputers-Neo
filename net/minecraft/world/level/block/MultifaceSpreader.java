package net.minecraft.world.level.block;

import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

public class MultifaceSpreader {
    public static final MultifaceSpreader.SpreadType[] DEFAULT_SPREAD_ORDER = new MultifaceSpreader.SpreadType[]{
        MultifaceSpreader.SpreadType.SAME_POSITION, MultifaceSpreader.SpreadType.SAME_PLANE, MultifaceSpreader.SpreadType.WRAP_AROUND
    };
    private final MultifaceSpreader.SpreadConfig config;

    public MultifaceSpreader(MultifaceBlock block) {
        this(new MultifaceSpreader.DefaultSpreaderConfig(block));
    }

    public MultifaceSpreader(MultifaceSpreader.SpreadConfig config) {
        this.config = config;
    }

    public boolean canSpreadInAnyDirection(BlockState state, BlockGetter level, BlockPos pos, Direction spreadDirection) {
        return Direction.stream()
            .anyMatch(
                p_221611_ -> this.getSpreadFromFaceTowardDirection(state, level, pos, spreadDirection, p_221611_, this.config::canSpreadInto)
                        .isPresent()
            );
    }

    public Optional<MultifaceSpreader.SpreadPos> spreadFromRandomFaceTowardRandomDirection(
        BlockState state, LevelAccessor level, BlockPos pos, RandomSource random
    ) {
        return Direction.allShuffled(random)
            .stream()
            .filter(p_221680_ -> this.config.canSpreadFrom(state, p_221680_))
            .map(p_221629_ -> this.spreadFromFaceTowardRandomDirection(state, level, pos, p_221629_, random, false))
            .filter(Optional::isPresent)
            .findFirst()
            .orElse(Optional.empty());
    }

    public long spreadAll(BlockState state, LevelAccessor level, BlockPos pos, boolean markForPostprocessing) {
        return Direction.stream()
            .filter(p_221670_ -> this.config.canSpreadFrom(state, p_221670_))
            .map(p_221667_ -> this.spreadFromFaceTowardAllDirections(state, level, pos, p_221667_, markForPostprocessing))
            .reduce(0L, Long::sum);
    }

    public Optional<MultifaceSpreader.SpreadPos> spreadFromFaceTowardRandomDirection(
        BlockState state, LevelAccessor level, BlockPos pos, Direction spreadDirection, RandomSource random, boolean markForPostprocessing
    ) {
        return Direction.allShuffled(random)
            .stream()
            .map(p_221677_ -> this.spreadFromFaceTowardDirection(state, level, pos, spreadDirection, p_221677_, markForPostprocessing))
            .filter(Optional::isPresent)
            .findFirst()
            .orElse(Optional.empty());
    }

    private long spreadFromFaceTowardAllDirections(BlockState state, LevelAccessor level, BlockPos pos, Direction spreadDirection, boolean markForPostprocessing) {
        return Direction.stream()
            .map(p_221656_ -> this.spreadFromFaceTowardDirection(state, level, pos, spreadDirection, p_221656_, markForPostprocessing))
            .filter(Optional::isPresent)
            .count();
    }

    @VisibleForTesting
    public Optional<MultifaceSpreader.SpreadPos> spreadFromFaceTowardDirection(
        BlockState state, LevelAccessor level, BlockPos pos, Direction spreadDirection, Direction face, boolean markForPostprocessing
    ) {
        return this.getSpreadFromFaceTowardDirection(state, level, pos, spreadDirection, face, this.config::canSpreadInto)
            .flatMap(p_221600_ -> this.spreadToFace(level, p_221600_, markForPostprocessing));
    }

    public Optional<MultifaceSpreader.SpreadPos> getSpreadFromFaceTowardDirection(
        BlockState state, BlockGetter level, BlockPos pos, Direction spreadDirection, Direction face, MultifaceSpreader.SpreadPredicate predicate
    ) {
        if (face.getAxis() == spreadDirection.getAxis()) {
            return Optional.empty();
        } else if (this.config.isOtherBlockValidAsSource(state) || this.config.hasFace(state, spreadDirection) && !this.config.hasFace(state, face)) {
            for (MultifaceSpreader.SpreadType multifacespreader$spreadtype : this.config.getSpreadTypes()) {
                MultifaceSpreader.SpreadPos multifacespreader$spreadpos = multifacespreader$spreadtype.getSpreadPos(pos, face, spreadDirection);
                if (predicate.test(level, pos, multifacespreader$spreadpos)) {
                    return Optional.of(multifacespreader$spreadpos);
                }
            }

            return Optional.empty();
        } else {
            return Optional.empty();
        }
    }

    public Optional<MultifaceSpreader.SpreadPos> spreadToFace(LevelAccessor level, MultifaceSpreader.SpreadPos pos, boolean markForPostprocessing) {
        BlockState blockstate = level.getBlockState(pos.pos());
        return this.config.placeBlock(level, pos, blockstate, markForPostprocessing) ? Optional.of(pos) : Optional.empty();
    }

    public static class DefaultSpreaderConfig implements MultifaceSpreader.SpreadConfig {
        protected MultifaceBlock block;

        public DefaultSpreaderConfig(MultifaceBlock block) {
            this.block = block;
        }

        @Nullable
        @Override
        public BlockState getStateForPlacement(BlockState currentState, BlockGetter level, BlockPos pos, Direction lookingDirection) {
            return this.block.getStateForPlacement(currentState, level, pos, lookingDirection);
        }

        protected boolean stateCanBeReplaced(BlockGetter level, BlockPos pos, BlockPos spreadPos, Direction direction, BlockState state) {
            return state.isAir() || state.is(this.block) || state.is(Blocks.WATER) && state.getFluidState().isSource();
        }

        @Override
        public boolean canSpreadInto(BlockGetter level, BlockPos pos, MultifaceSpreader.SpreadPos spreadPos) {
            BlockState blockstate = level.getBlockState(spreadPos.pos());
            return this.stateCanBeReplaced(level, pos, spreadPos.pos(), spreadPos.face(), blockstate)
                && this.block.isValidStateForPlacement(level, blockstate, spreadPos.pos(), spreadPos.face());
        }
    }

    public interface SpreadConfig {
        @Nullable
        BlockState getStateForPlacement(BlockState currentState, BlockGetter level, BlockPos pos, Direction lookingDirection);

        boolean canSpreadInto(BlockGetter level, BlockPos pos, MultifaceSpreader.SpreadPos spreadPos);

        default MultifaceSpreader.SpreadType[] getSpreadTypes() {
            return MultifaceSpreader.DEFAULT_SPREAD_ORDER;
        }

        default boolean hasFace(BlockState state, Direction direction) {
            return MultifaceBlock.hasFace(state, direction);
        }

        default boolean isOtherBlockValidAsSource(BlockState otherBlock) {
            return false;
        }

        default boolean canSpreadFrom(BlockState state, Direction direction) {
            return this.isOtherBlockValidAsSource(state) || this.hasFace(state, direction);
        }

        default boolean placeBlock(LevelAccessor level, MultifaceSpreader.SpreadPos pos, BlockState state, boolean markForPostprocessing) {
            BlockState blockstate = this.getStateForPlacement(state, level, pos.pos(), pos.face());
            if (blockstate != null) {
                if (markForPostprocessing) {
                    level.getChunk(pos.pos()).markPosForPostprocessing(pos.pos());
                }

                return level.setBlock(pos.pos(), blockstate, 2);
            } else {
                return false;
            }
        }
    }

    public static record SpreadPos(BlockPos pos, Direction face) {
    }

    @FunctionalInterface
    public interface SpreadPredicate {
        boolean test(BlockGetter level, BlockPos pos, MultifaceSpreader.SpreadPos spreadPos);
    }

    public static enum SpreadType {
        SAME_POSITION {
            @Override
            public MultifaceSpreader.SpreadPos getSpreadPos(BlockPos p_221751_, Direction p_221752_, Direction p_221753_) {
                return new MultifaceSpreader.SpreadPos(p_221751_, p_221752_);
            }
        },
        SAME_PLANE {
            @Override
            public MultifaceSpreader.SpreadPos getSpreadPos(BlockPos p_221758_, Direction p_221759_, Direction p_221760_) {
                return new MultifaceSpreader.SpreadPos(p_221758_.relative(p_221759_), p_221760_);
            }
        },
        WRAP_AROUND {
            @Override
            public MultifaceSpreader.SpreadPos getSpreadPos(BlockPos p_221765_, Direction p_221766_, Direction p_221767_) {
                return new MultifaceSpreader.SpreadPos(p_221765_.relative(p_221766_).relative(p_221767_), p_221766_.getOpposite());
            }
        };

        public abstract MultifaceSpreader.SpreadPos getSpreadPos(BlockPos pos, Direction face, Direction spreadDirection);
    }
}
