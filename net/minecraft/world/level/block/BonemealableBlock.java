package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

public interface BonemealableBlock {
    boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state);

    boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state);

    void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state);

    default BlockPos getParticlePos(BlockPos pos) {
        return switch (this.getType()) {
            case NEIGHBOR_SPREADER -> pos.above();
            case GROWER -> pos;
        };
    }

    default BonemealableBlock.Type getType() {
        return BonemealableBlock.Type.GROWER;
    }

    public static enum Type {
        NEIGHBOR_SPREADER,
        GROWER;
    }
}
