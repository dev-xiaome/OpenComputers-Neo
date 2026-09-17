package net.minecraft.world.level.levelgen.structure.pieces;

import com.google.common.collect.Lists;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePieceAccessor;

public class StructurePiecesBuilder implements StructurePieceAccessor {
    private final List<StructurePiece> pieces = Lists.newArrayList();

    @Override
    public void addPiece(StructurePiece piece) {
        this.pieces.add(piece);
    }

    @Nullable
    @Override
    public StructurePiece findCollisionPiece(BoundingBox box) {
        return StructurePiece.findCollisionPiece(this.pieces, box);
    }

    @Deprecated
    public void offsetPiecesVertically(int offset) {
        for (StructurePiece structurepiece : this.pieces) {
            structurepiece.move(0, offset, 0);
        }
    }

    @Deprecated
    public int moveBelowSeaLevel(int seaLevel, int minY, RandomSource random, int amount) {
        int i = seaLevel - amount;
        BoundingBox boundingbox = this.getBoundingBox();
        int j = boundingbox.getYSpan() + minY + 1;
        if (j < i) {
            j += random.nextInt(i - j);
        }

        int k = j - boundingbox.maxY();
        this.offsetPiecesVertically(k);
        return k;
    }

    /**
 * @deprecated
 */
    public void moveInsideHeights(RandomSource random, int minY, int maxY) {
        BoundingBox boundingbox = this.getBoundingBox();
        int i = maxY - minY + 1 - boundingbox.getYSpan();
        int j;
        if (i > 1) {
            j = minY + random.nextInt(i);
        } else {
            j = minY;
        }

        int k = j - boundingbox.minY();
        this.offsetPiecesVertically(k);
    }

    public PiecesContainer build() {
        return new PiecesContainer(this.pieces);
    }

    public void clear() {
        this.pieces.clear();
    }

    public boolean isEmpty() {
        return this.pieces.isEmpty();
    }

    public BoundingBox getBoundingBox() {
        return StructurePiece.createBoundingBox(this.pieces.stream());
    }
}
