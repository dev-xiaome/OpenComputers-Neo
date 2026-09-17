package net.minecraft.world.level.levelgen.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

public abstract class ScatteredFeaturePiece extends StructurePiece {
    protected final int width;
    protected final int height;
    protected final int depth;
    protected int heightPosition = -1;

    protected ScatteredFeaturePiece(
        StructurePieceType type, int x, int y, int z, int width, int height, int depth, Direction orientation
    ) {
        super(type, 0, StructurePiece.makeBoundingBox(x, y, z, orientation, width, height, depth));
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.setOrientation(orientation);
    }

    protected ScatteredFeaturePiece(StructurePieceType type, CompoundTag tag) {
        super(type, tag);
        this.width = tag.getInt("Width");
        this.height = tag.getInt("Height");
        this.depth = tag.getInt("Depth");
        this.heightPosition = tag.getInt("HPos");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("Width", this.width);
        tag.putInt("Height", this.height);
        tag.putInt("Depth", this.depth);
        tag.putInt("HPos", this.heightPosition);
    }

    protected boolean updateAverageGroundHeight(LevelAccessor level, BoundingBox bounds, int height) {
        if (this.heightPosition >= 0) {
            return true;
        } else {
            int i = 0;
            int j = 0;
            BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

            for (int k = this.boundingBox.minZ(); k <= this.boundingBox.maxZ(); k++) {
                for (int l = this.boundingBox.minX(); l <= this.boundingBox.maxX(); l++) {
                    blockpos$mutableblockpos.set(l, 64, k);
                    if (bounds.isInside(blockpos$mutableblockpos)) {
                        i += level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockpos$mutableblockpos).getY();
                        j++;
                    }
                }
            }

            if (j == 0) {
                return false;
            } else {
                this.heightPosition = i / j;
                this.boundingBox.move(0, this.heightPosition - this.boundingBox.minY() + height, 0);
                return true;
            }
        }
    }

    protected boolean updateHeightPositionToLowestGroundHeight(LevelAccessor level, int height) {
        if (this.heightPosition >= 0) {
            return true;
        } else {
            int i = level.getMaxBuildHeight();
            boolean flag = false;
            BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

            for (int j = this.boundingBox.minZ(); j <= this.boundingBox.maxZ(); j++) {
                for (int k = this.boundingBox.minX(); k <= this.boundingBox.maxX(); k++) {
                    blockpos$mutableblockpos.set(k, 0, j);
                    i = Math.min(i, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockpos$mutableblockpos).getY());
                    flag = true;
                }
            }

            if (!flag) {
                return false;
            } else {
                this.heightPosition = i;
                this.boundingBox.move(0, this.heightPosition - this.boundingBox.minY() + height, 0);
                return true;
            }
        }
    }
}
