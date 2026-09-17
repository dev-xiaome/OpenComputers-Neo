package net.minecraft.world.level.redstone;

import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public interface NeighborUpdater {
    Direction[] UPDATE_ORDER = new Direction[]{Direction.WEST, Direction.EAST, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH};

    void shapeUpdate(Direction direction, BlockState state, BlockPos pos, BlockPos neighborPos, int flags, int recursionLevel);

    void neighborChanged(BlockPos pos, Block neighborBlock, BlockPos neighborPos);

    void neighborChanged(BlockState state, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston);

    default void updateNeighborsAtExceptFromFacing(BlockPos pos, Block block, @Nullable Direction facing) {
        for (Direction direction : UPDATE_ORDER) {
            if (direction != facing) {
                this.neighborChanged(pos.relative(direction), block, pos);
            }
        }
    }

    static void executeShapeUpdate(
        LevelAccessor level, Direction direction, BlockState state, BlockPos pos, BlockPos neighborPos, int flags, int recursionLevel
    ) {
        BlockState blockstate = level.getBlockState(pos);
        BlockState blockstate1 = blockstate.updateShape(direction, state, level, pos, neighborPos);
        Block.updateOrDestroy(blockstate, blockstate1, level, pos, flags, recursionLevel);
    }

    static void executeUpdate(Level level, BlockState state, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        try {
            state.handleNeighborChanged(level, pos, neighborBlock, neighborPos, movedByPiston);
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Exception while updating neighbours");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Block being updated");
            crashreportcategory.setDetail(
                "Source block type",
                () -> {
                    try {
                        return String.format(
                            Locale.ROOT,
                            "ID #%s (%s // %s)",
                            BuiltInRegistries.BLOCK.getKey(neighborBlock),
                            neighborBlock.getDescriptionId(),
                            neighborBlock.getClass().getCanonicalName()
                        );
                    } catch (Throwable throwable1) {
                        return "ID #" + BuiltInRegistries.BLOCK.getKey(neighborBlock);
                    }
                }
            );
            CrashReportCategory.populateBlockDetails(crashreportcategory, level, pos, state);
            throw new ReportedException(crashreport);
        }
    }
}
