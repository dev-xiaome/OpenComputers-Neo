package net.minecraft.world.level.redstone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class InstantNeighborUpdater implements NeighborUpdater {
    private final Level level;

    public InstantNeighborUpdater(Level level) {
        this.level = level;
    }

    @Override
    public void shapeUpdate(Direction direction, BlockState state, BlockPos pos, BlockPos neighborPos, int flags, int recursionLevel) {
        NeighborUpdater.executeShapeUpdate(this.level, direction, state, pos, neighborPos, flags, recursionLevel - 1);
    }

    @Override
    public void neighborChanged(BlockPos pos, Block neighborBlock, BlockPos neighborPos) {
        BlockState blockstate = this.level.getBlockState(pos);
        this.neighborChanged(blockstate, pos, neighborBlock, neighborPos, false);
    }

    @Override
    public void neighborChanged(BlockState state, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        NeighborUpdater.executeUpdate(this.level, state, pos, neighborBlock, neighborPos, movedByPiston);
    }
}
