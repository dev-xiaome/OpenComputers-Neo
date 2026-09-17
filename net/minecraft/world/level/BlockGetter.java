package net.minecraft.world.level;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public interface BlockGetter extends LevelHeightAccessor, net.neoforged.neoforge.common.extensions.IBlockGetterExtension {
    @Nullable
    BlockEntity getBlockEntity(BlockPos pos);

    default <T extends BlockEntity> Optional<T> getBlockEntity(BlockPos pos, BlockEntityType<T> blockEntityType) {
        BlockEntity blockentity = this.getBlockEntity(pos);
        return blockentity != null && blockentity.getType() == blockEntityType ? Optional.of((T)blockentity) : Optional.empty();
    }

    BlockState getBlockState(BlockPos pos);

    FluidState getFluidState(BlockPos pos);

    default int getLightEmission(BlockPos pos) {
        return this.getBlockState(pos).getLightEmission(this, pos);
    }

    default int getMaxLightLevel() {
        return 15;
    }

    default Stream<BlockState> getBlockStates(AABB area) {
        return BlockPos.betweenClosedStream(area).map(this::getBlockState);
    }

    default BlockHitResult isBlockInLine(ClipBlockStateContext context) {
        return traverseBlocks(
            context.getFrom(),
            context.getTo(),
            context,
            (p_275154_, p_275155_) -> {
                BlockState blockstate = this.getBlockState(p_275155_);
                Vec3 vec3 = p_275154_.getFrom().subtract(p_275154_.getTo());
                return p_275154_.isTargetBlock().test(blockstate)
                    ? new BlockHitResult(p_275154_.getTo(), Direction.getNearest(vec3.x, vec3.y, vec3.z), BlockPos.containing(p_275154_.getTo()), false)
                    : null;
            },
            p_275156_ -> {
                Vec3 vec3 = p_275156_.getFrom().subtract(p_275156_.getTo());
                return BlockHitResult.miss(p_275156_.getTo(), Direction.getNearest(vec3.x, vec3.y, vec3.z), BlockPos.containing(p_275156_.getTo()));
            }
        );
    }

    /**
     * Checks if there's block between {@code from} and {@code to} of context.
     * This uses the collision shape of provided block.
     */
    default BlockHitResult clip(ClipContext context) {
        return traverseBlocks(context.getFrom(), context.getTo(), context, (p_151359_, p_151360_) -> {
            BlockState blockstate = this.getBlockState(p_151360_);
            FluidState fluidstate = this.getFluidState(p_151360_);
            Vec3 vec3 = p_151359_.getFrom();
            Vec3 vec31 = p_151359_.getTo();
            VoxelShape voxelshape = p_151359_.getBlockShape(blockstate, this, p_151360_);
            BlockHitResult blockhitresult = this.clipWithInteractionOverride(vec3, vec31, p_151360_, voxelshape, blockstate);
            VoxelShape voxelshape1 = p_151359_.getFluidShape(fluidstate, this, p_151360_);
            BlockHitResult blockhitresult1 = voxelshape1.clip(vec3, vec31, p_151360_);
            double d0 = blockhitresult == null ? Double.MAX_VALUE : p_151359_.getFrom().distanceToSqr(blockhitresult.getLocation());
            double d1 = blockhitresult1 == null ? Double.MAX_VALUE : p_151359_.getFrom().distanceToSqr(blockhitresult1.getLocation());
            return d0 <= d1 ? blockhitresult : blockhitresult1;
        }, p_275153_ -> {
            Vec3 vec3 = p_275153_.getFrom().subtract(p_275153_.getTo());
            return BlockHitResult.miss(p_275153_.getTo(), Direction.getNearest(vec3.x, vec3.y, vec3.z), BlockPos.containing(p_275153_.getTo()));
        });
    }

    @Nullable
    default BlockHitResult clipWithInteractionOverride(Vec3 startVec, Vec3 endVec, BlockPos pos, VoxelShape shape, BlockState state) {
        BlockHitResult blockhitresult = shape.clip(startVec, endVec, pos);
        if (blockhitresult != null) {
            BlockHitResult blockhitresult1 = state.getInteractionShape(this, pos).clip(startVec, endVec, pos);
            if (blockhitresult1 != null
                && blockhitresult1.getLocation().subtract(startVec).lengthSqr() < blockhitresult.getLocation().subtract(startVec).lengthSqr()) {
                return blockhitresult.withDirection(blockhitresult1.getDirection());
            }
        }

        return blockhitresult;
    }

    default double getBlockFloorHeight(VoxelShape shape, Supplier<VoxelShape> belowShapeSupplier) {
        if (!shape.isEmpty()) {
            return shape.max(Direction.Axis.Y);
        } else {
            double d0 = belowShapeSupplier.get().max(Direction.Axis.Y);
            return d0 >= 1.0 ? d0 - 1.0 : Double.NEGATIVE_INFINITY;
        }
    }

    default double getBlockFloorHeight(BlockPos pos) {
        return this.getBlockFloorHeight(this.getBlockState(pos).getCollisionShape(this, pos), () -> {
            BlockPos blockpos = pos.below();
            return this.getBlockState(blockpos).getCollisionShape(this, blockpos);
        });
    }

    static <T, C> T traverseBlocks(Vec3 from, Vec3 to, C context, BiFunction<C, BlockPos, T> tester, Function<C, T> onFail) {
        if (from.equals(to)) {
            return onFail.apply(context);
        } else {
            double d0 = Mth.lerp(-1.0E-7, to.x, from.x);
            double d1 = Mth.lerp(-1.0E-7, to.y, from.y);
            double d2 = Mth.lerp(-1.0E-7, to.z, from.z);
            double d3 = Mth.lerp(-1.0E-7, from.x, to.x);
            double d4 = Mth.lerp(-1.0E-7, from.y, to.y);
            double d5 = Mth.lerp(-1.0E-7, from.z, to.z);
            int i = Mth.floor(d3);
            int j = Mth.floor(d4);
            int k = Mth.floor(d5);
            BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos(i, j, k);
            T t = tester.apply(context, blockpos$mutableblockpos);
            if (t != null) {
                return t;
            } else {
                double d6 = d0 - d3;
                double d7 = d1 - d4;
                double d8 = d2 - d5;
                int l = Mth.sign(d6);
                int i1 = Mth.sign(d7);
                int j1 = Mth.sign(d8);
                double d9 = l == 0 ? Double.MAX_VALUE : (double)l / d6;
                double d10 = i1 == 0 ? Double.MAX_VALUE : (double)i1 / d7;
                double d11 = j1 == 0 ? Double.MAX_VALUE : (double)j1 / d8;
                double d12 = d9 * (l > 0 ? 1.0 - Mth.frac(d3) : Mth.frac(d3));
                double d13 = d10 * (i1 > 0 ? 1.0 - Mth.frac(d4) : Mth.frac(d4));
                double d14 = d11 * (j1 > 0 ? 1.0 - Mth.frac(d5) : Mth.frac(d5));

                while (d12 <= 1.0 || d13 <= 1.0 || d14 <= 1.0) {
                    if (d12 < d13) {
                        if (d12 < d14) {
                            i += l;
                            d12 += d9;
                        } else {
                            k += j1;
                            d14 += d11;
                        }
                    } else if (d13 < d14) {
                        j += i1;
                        d13 += d10;
                    } else {
                        k += j1;
                        d14 += d11;
                    }

                    T t1 = tester.apply(context, blockpos$mutableblockpos.set(i, j, k));
                    if (t1 != null) {
                        return t1;
                    }
                }

                return onFail.apply(context);
            }
        }
    }
}
