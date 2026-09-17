package net.minecraft.world.phys;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class BlockHitResult extends HitResult {
    private final Direction direction;
    private final BlockPos blockPos;
    private final boolean miss;
    private final boolean inside;

    /**
     * Creates a new BlockRayTraceResult marked as a miss.
     */
    public static BlockHitResult miss(Vec3 location, Direction direction, BlockPos pos) {
        return new BlockHitResult(true, location, direction, pos, false);
    }

    public BlockHitResult(Vec3 location, Direction direction, BlockPos blockPos, boolean inside) {
        this(false, location, direction, blockPos, inside);
    }

    private BlockHitResult(boolean miss, Vec3 location, Direction direction, BlockPos blockPos, boolean inside) {
        super(location);
        this.miss = miss;
        this.direction = direction;
        this.blockPos = blockPos;
        this.inside = inside;
    }

    /**
     * Creates a new BlockRayTraceResult, with the clicked face replaced with the given one
     */
    public BlockHitResult withDirection(Direction newFace) {
        return new BlockHitResult(this.miss, this.location, newFace, this.blockPos, this.inside);
    }

    public BlockHitResult withPosition(BlockPos pos) {
        return new BlockHitResult(this.miss, this.location, this.direction, pos, this.inside);
    }

    public BlockPos getBlockPos() {
        return this.blockPos;
    }

    public Direction getDirection() {
        return this.direction;
    }

    @Override
    public HitResult.Type getType() {
        return this.miss ? HitResult.Type.MISS : HitResult.Type.BLOCK;
    }

    public boolean isInside() {
        return this.inside;
    }
}
