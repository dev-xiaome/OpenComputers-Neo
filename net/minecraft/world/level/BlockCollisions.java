package net.minecraft.world.level;

import com.google.common.collect.AbstractIterator;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Cursor3D;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BlockCollisions<T> extends AbstractIterator<T> {
    private final AABB box;
    private final CollisionContext context;
    private final Cursor3D cursor;
    private final BlockPos.MutableBlockPos pos;
    private final VoxelShape entityShape;
    private final CollisionGetter collisionGetter;
    private final boolean onlySuffocatingBlocks;
    @Nullable
    private BlockGetter cachedBlockGetter;
    private long cachedBlockGetterPos;
    private final BiFunction<BlockPos.MutableBlockPos, VoxelShape, T> resultProvider;

    public BlockCollisions(
        CollisionGetter collisionGetter, @Nullable Entity entity, AABB box, boolean onlySuffocatingBlocks, BiFunction<BlockPos.MutableBlockPos, VoxelShape, T> resultProvider
    ) {
        this.context = entity == null ? CollisionContext.empty() : CollisionContext.of(entity);
        this.pos = new BlockPos.MutableBlockPos();
        this.entityShape = Shapes.create(box);
        this.collisionGetter = collisionGetter;
        this.box = box;
        this.onlySuffocatingBlocks = onlySuffocatingBlocks;
        this.resultProvider = resultProvider;
        int i = Mth.floor(box.minX - 1.0E-7) - 1;
        int j = Mth.floor(box.maxX + 1.0E-7) + 1;
        int k = Mth.floor(box.minY - 1.0E-7) - 1;
        int l = Mth.floor(box.maxY + 1.0E-7) + 1;
        int i1 = Mth.floor(box.minZ - 1.0E-7) - 1;
        int j1 = Mth.floor(box.maxZ + 1.0E-7) + 1;
        this.cursor = new Cursor3D(i, k, i1, j, l, j1);
    }

    @Nullable
    private BlockGetter getChunk(int x, int z) {
        int i = SectionPos.blockToSectionCoord(x);
        int j = SectionPos.blockToSectionCoord(z);
        long k = ChunkPos.asLong(i, j);
        if (this.cachedBlockGetter != null && this.cachedBlockGetterPos == k) {
            return this.cachedBlockGetter;
        } else {
            BlockGetter blockgetter = this.collisionGetter.getChunkForCollisions(i, j);
            this.cachedBlockGetter = blockgetter;
            this.cachedBlockGetterPos = k;
            return blockgetter;
        }
    }

    @Override
    protected T computeNext() {
        while (this.cursor.advance()) {
            int i = this.cursor.nextX();
            int j = this.cursor.nextY();
            int k = this.cursor.nextZ();
            int l = this.cursor.getNextType();
            if (l != 3) {
                BlockGetter blockgetter = this.getChunk(i, k);
                if (blockgetter != null) {
                    this.pos.set(i, j, k);
                    BlockState blockstate = blockgetter.getBlockState(this.pos);
                    if ((!this.onlySuffocatingBlocks || blockstate.isSuffocating(blockgetter, this.pos))
                        && (l != 1 || blockstate.hasLargeCollisionShape())
                        && (l != 2 || blockstate.is(Blocks.MOVING_PISTON))) {
                        VoxelShape voxelshape = blockstate.getCollisionShape(this.collisionGetter, this.pos, this.context);
                        if (voxelshape == Shapes.block()) {
                            if (this.box.intersects((double)i, (double)j, (double)k, (double)i + 1.0, (double)j + 1.0, (double)k + 1.0)) {
                                return this.resultProvider.apply(this.pos, voxelshape.move((double)i, (double)j, (double)k));
                            }
                        } else {
                            VoxelShape voxelshape1 = voxelshape.move((double)i, (double)j, (double)k);
                            if (!voxelshape1.isEmpty() && Shapes.joinIsNotEmpty(voxelshape1, this.entityShape, BooleanOp.AND)) {
                                return this.resultProvider.apply(this.pos, voxelshape1);
                            }
                        }
                    }
                }
            }
        }

        return this.endOfData();
    }
}
