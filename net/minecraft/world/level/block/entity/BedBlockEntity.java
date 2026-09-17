package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;

public class BedBlockEntity extends BlockEntity {
    private DyeColor color;

    public BedBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityType.BED, pos, blockState);
        this.color = ((BedBlock)blockState.getBlock()).getColor();
    }

    public BedBlockEntity(BlockPos pos, BlockState blockState, DyeColor color) {
        super(BlockEntityType.BED, pos, blockState);
        this.color = color;
    }

    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public DyeColor getColor() {
        return this.color;
    }

    public void setColor(DyeColor color) {
        this.color = color;
    }
}
