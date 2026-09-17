package net.minecraft.world.entity;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;

public interface SpawnPlacementTypes {
    SpawnPlacementType NO_RESTRICTIONS = (p_321554_, p_321832_, p_321540_) -> true;
    SpawnPlacementType IN_WATER = (p_325672_, p_325673_, p_325674_) -> {
        if (p_325674_ != null && p_325672_.getWorldBorder().isWithinBounds(p_325673_)) {
            BlockPos blockpos = p_325673_.above();
            return p_325672_.getFluidState(p_325673_).is(FluidTags.WATER) && !p_325672_.getBlockState(blockpos).isRedstoneConductor(p_325672_, blockpos);
        } else {
            return false;
        }
    };
    SpawnPlacementType IN_LAVA = (p_325669_, p_325670_, p_325671_) -> p_325671_ != null && p_325669_.getWorldBorder().isWithinBounds(p_325670_)
            ? p_325669_.getFluidState(p_325670_).is(FluidTags.LAVA)
            : false;
    SpawnPlacementType ON_GROUND = new SpawnPlacementType() {
        @Override
        public boolean isSpawnPositionOk(LevelReader level, BlockPos pos, @Nullable EntityType<?> entityType) {
            if (entityType != null && level.getWorldBorder().isWithinBounds(pos)) {
                BlockPos blockpos = pos.above();
                BlockPos blockpos1 = pos.below();
                BlockState blockstate = level.getBlockState(blockpos1);
                return !blockstate.isValidSpawn(level, blockpos1, entityType)
                    ? false
                    : this.isValidEmptySpawnBlock(level, pos, entityType) && this.isValidEmptySpawnBlock(level, blockpos, entityType);
            } else {
                return false;
            }
        }

        private boolean isValidEmptySpawnBlock(LevelReader level, BlockPos pos, EntityType<?> entityType) {
            BlockState blockstate = level.getBlockState(pos);
            return NaturalSpawner.isValidEmptySpawnBlock(level, pos, blockstate, blockstate.getFluidState(), entityType);
        }

        @Override
        public BlockPos adjustSpawnPosition(LevelReader level, BlockPos pos) {
            BlockPos blockpos = pos.below();
            return level.getBlockState(blockpos).isPathfindable(PathComputationType.LAND) ? blockpos : pos;
        }
    };
}
