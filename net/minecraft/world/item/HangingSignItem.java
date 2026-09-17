package net.minecraft.world.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.state.BlockState;

public class HangingSignItem extends SignItem {
    public HangingSignItem(Block block, Block wallBlock, Item.Properties properties) {
        super(properties, block, wallBlock, Direction.UP);
    }

    @Override
    protected boolean canPlace(LevelReader level, BlockState state, BlockPos pos) {
        if (state.getBlock() instanceof WallHangingSignBlock wallhangingsignblock && !wallhangingsignblock.canPlace(state, level, pos)) {
            return false;
        }

        return super.canPlace(level, state, pos);
    }
}
