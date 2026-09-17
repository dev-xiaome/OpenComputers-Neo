package net.minecraft.world.level;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

public interface LevelWriter {
    boolean setBlock(BlockPos pos, BlockState state, int flags, int recursionLeft);

    /**
     * Sets a block state into this world.Flags are as follows:
     * 1 will cause a block update.
     * 2 will send the change to clients.
     * 4 will prevent the block from being re-rendered.
     * 8 will force any re-renders to run on the main thread instead
     * 16 will prevent neighbor reactions (e.g. fences connecting, observers pulsing).
     * 32 will prevent neighbor reactions from spawning drops.
     * 64 will signify the block is being moved.
     * Flags can be OR-ed
     */
    default boolean setBlock(BlockPos pos, BlockState newState, int flags) {
        return this.setBlock(pos, newState, flags, 512);
    }

    boolean removeBlock(BlockPos pos, boolean isMoving);

    /**
     * Sets a block to air, but also plays the sound and particles and can spawn drops
     */
    default boolean destroyBlock(BlockPos pos, boolean dropBlock) {
        return this.destroyBlock(pos, dropBlock, null);
    }

    default boolean destroyBlock(BlockPos pos, boolean dropBlock, @Nullable Entity entity) {
        return this.destroyBlock(pos, dropBlock, entity, 512);
    }

    boolean destroyBlock(BlockPos pos, boolean dropBlock, @Nullable Entity entity, int recursionLeft);

    default boolean addFreshEntity(Entity entity) {
        return false;
    }
}
