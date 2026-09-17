package net.minecraft.world.level;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;

public interface SignalGetter extends BlockGetter {
    Direction[] DIRECTIONS = Direction.values();

    /**
     * Returns the direct redstone signal emitted from the given position in the given direction.
     *
     * <p>
     * NOTE: directions in redstone signal related methods are backwards, so this method
     * checks for the signal emitted in the <i>opposite</i> direction of the one given.
     */
    default int getDirectSignal(BlockPos pos, Direction direction) {
        return this.getBlockState(pos).getDirectSignal(this, pos, direction);
    }

    /**
     * Returns the direct redstone signal the given position receives from neighboring blocks.
     */
    default int getDirectSignalTo(BlockPos pos) {
        int i = 0;
        i = Math.max(i, this.getDirectSignal(pos.below(), Direction.DOWN));
        if (i >= 15) {
            return i;
        } else {
            i = Math.max(i, this.getDirectSignal(pos.above(), Direction.UP));
            if (i >= 15) {
                return i;
            } else {
                i = Math.max(i, this.getDirectSignal(pos.north(), Direction.NORTH));
                if (i >= 15) {
                    return i;
                } else {
                    i = Math.max(i, this.getDirectSignal(pos.south(), Direction.SOUTH));
                    if (i >= 15) {
                        return i;
                    } else {
                        i = Math.max(i, this.getDirectSignal(pos.west(), Direction.WEST));
                        if (i >= 15) {
                            return i;
                        } else {
                            i = Math.max(i, this.getDirectSignal(pos.east(), Direction.EAST));
                            return i >= 15 ? i : i;
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns the control signal emitted from the given position in the given direction.
     * If {@code diodesOnly} is {@code true}, this method returns the direct signal emitted if
     * and only if this position is occupied by a diode (i.e. a repeater or comparator).
     * Otherwise, if this position is occupied by a
     * {@linkplain net.minecraft.world.level.block.Blocks#REDSTONE_BLOCK redstone block},
     * this method will return the redstone signal emitted by it. If not, this method will
     * return the direct signal emitted from this position in the given direction.
     *
     * <p>
     * NOTE: directions in redstone signal related methods are backwards, so this method
     * checks for the signal emitted in the <i>opposite</i> direction of the one given.
     */
    default int getControlInputSignal(BlockPos pos, Direction direction, boolean diodesOnly) {
        BlockState blockstate = this.getBlockState(pos);
        if (diodesOnly) {
            return DiodeBlock.isDiode(blockstate) ? this.getDirectSignal(pos, direction) : 0;
        } else if (blockstate.is(Blocks.REDSTONE_BLOCK)) {
            return 15;
        } else if (blockstate.is(Blocks.REDSTONE_WIRE)) {
            return blockstate.getValue(RedStoneWireBlock.POWER);
        } else {
            return blockstate.isSignalSource() ? this.getDirectSignal(pos, direction) : 0;
        }
    }

    /**
     * Returns whether a redstone signal is emitted from the given position in the given direction.
     *
     * <p>
     * NOTE: directions in redstone signal related methods are backwards, so this method
     * checks for the signal emitted in the <i>opposite</i> direction of the one given.
     */
    default boolean hasSignal(BlockPos pos, Direction direction) {
        return this.getSignal(pos, direction) > 0;
    }

    /**
     * Returns the redstone signal emitted from the given position in the given direction.
     * This is the highest value between the signal emitted by the block itself, and the direct signal
     * received from neighboring blocks if the block is a redstone conductor.
     *
     * <p>
     * NOTE: directions in redstone signal related methods are backwards, so this method
     * checks for the signal emitted in the <i>opposite</i> direction of the one given.
     */
    default int getSignal(BlockPos pos, Direction direction) {
        BlockState blockstate = this.getBlockState(pos);
        int i = blockstate.getSignal(this, pos, direction);
        return blockstate.shouldCheckWeakPower(this, pos, direction) ? Math.max(i, this.getDirectSignalTo(pos)) : i;
    }

    /**
     * Returns whether the given position receives any redstone signal from neighboring blocks.
     */
    default boolean hasNeighborSignal(BlockPos pos) {
        if (this.getSignal(pos.below(), Direction.DOWN) > 0) {
            return true;
        } else if (this.getSignal(pos.above(), Direction.UP) > 0) {
            return true;
        } else if (this.getSignal(pos.north(), Direction.NORTH) > 0) {
            return true;
        } else if (this.getSignal(pos.south(), Direction.SOUTH) > 0) {
            return true;
        } else {
            return this.getSignal(pos.west(), Direction.WEST) > 0 ? true : this.getSignal(pos.east(), Direction.EAST) > 0;
        }
    }

    /**
     * Returns the highest redstone signal the given position receives from neighboring blocks.
     */
    default int getBestNeighborSignal(BlockPos pos) {
        int i = 0;

        for (Direction direction : DIRECTIONS) {
            int j = this.getSignal(pos.relative(direction), direction);
            if (j >= 15) {
                return 15;
            }

            if (j > i) {
                i = j;
            }
        }

        return i;
    }
}
