package net.minecraft.world.phys.shapes;

import java.util.BitSet;
import net.minecraft.core.Direction;

public final class BitSetDiscreteVoxelShape extends DiscreteVoxelShape {
    private final BitSet storage;
    private int xMin;
    private int yMin;
    private int zMin;
    private int xMax;
    private int yMax;
    private int zMax;

    public BitSetDiscreteVoxelShape(int xSize, int ySize, int zSize) {
        super(xSize, ySize, zSize);
        this.storage = new BitSet(xSize * ySize * zSize);
        this.xMin = xSize;
        this.yMin = ySize;
        this.zMin = zSize;
    }

    public static BitSetDiscreteVoxelShape withFilledBounds(
        int x, int y, int z, int xMin, int yMin, int zMin, int xMax, int yMax, int zMax
    ) {
        BitSetDiscreteVoxelShape bitsetdiscretevoxelshape = new BitSetDiscreteVoxelShape(x, y, z);
        bitsetdiscretevoxelshape.xMin = xMin;
        bitsetdiscretevoxelshape.yMin = yMin;
        bitsetdiscretevoxelshape.zMin = zMin;
        bitsetdiscretevoxelshape.xMax = xMax;
        bitsetdiscretevoxelshape.yMax = yMax;
        bitsetdiscretevoxelshape.zMax = zMax;

        for (int i = xMin; i < xMax; i++) {
            for (int j = yMin; j < yMax; j++) {
                for (int k = zMin; k < zMax; k++) {
                    bitsetdiscretevoxelshape.fillUpdateBounds(i, j, k, false);
                }
            }
        }

        return bitsetdiscretevoxelshape;
    }

    public BitSetDiscreteVoxelShape(DiscreteVoxelShape shape) {
        super(shape.xSize, shape.ySize, shape.zSize);
        if (shape instanceof BitSetDiscreteVoxelShape) {
            this.storage = (BitSet)((BitSetDiscreteVoxelShape)shape).storage.clone();
        } else {
            this.storage = new BitSet(this.xSize * this.ySize * this.zSize);

            for (int i = 0; i < this.xSize; i++) {
                for (int j = 0; j < this.ySize; j++) {
                    for (int k = 0; k < this.zSize; k++) {
                        if (shape.isFull(i, j, k)) {
                            this.storage.set(this.getIndex(i, j, k));
                        }
                    }
                }
            }
        }

        this.xMin = shape.firstFull(Direction.Axis.X);
        this.yMin = shape.firstFull(Direction.Axis.Y);
        this.zMin = shape.firstFull(Direction.Axis.Z);
        this.xMax = shape.lastFull(Direction.Axis.X);
        this.yMax = shape.lastFull(Direction.Axis.Y);
        this.zMax = shape.lastFull(Direction.Axis.Z);
    }

    protected int getIndex(int x, int y, int z) {
        return (x * this.ySize + y) * this.zSize + z;
    }

    @Override
    public boolean isFull(int x, int y, int z) {
        return this.storage.get(this.getIndex(x, y, z));
    }

    private void fillUpdateBounds(int x, int y, int z, boolean update) {
        this.storage.set(this.getIndex(x, y, z));
        if (update) {
            this.xMin = Math.min(this.xMin, x);
            this.yMin = Math.min(this.yMin, y);
            this.zMin = Math.min(this.zMin, z);
            this.xMax = Math.max(this.xMax, x + 1);
            this.yMax = Math.max(this.yMax, y + 1);
            this.zMax = Math.max(this.zMax, z + 1);
        }
    }

    @Override
    public void fill(int x, int y, int z) {
        this.fillUpdateBounds(x, y, z, true);
    }

    @Override
    public boolean isEmpty() {
        return this.storage.isEmpty();
    }

    @Override
    public int firstFull(Direction.Axis axis) {
        return axis.choose(this.xMin, this.yMin, this.zMin);
    }

    @Override
    public int lastFull(Direction.Axis axis) {
        return axis.choose(this.xMax, this.yMax, this.zMax);
    }

    static BitSetDiscreteVoxelShape join(
        DiscreteVoxelShape mainShape, DiscreteVoxelShape secondaryShape, IndexMerger mergerX, IndexMerger mergerY, IndexMerger mergerZ, BooleanOp operator
    ) {
        BitSetDiscreteVoxelShape bitsetdiscretevoxelshape = new BitSetDiscreteVoxelShape(mergerX.size() - 1, mergerY.size() - 1, mergerZ.size() - 1);
        int[] aint = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        mergerX.forMergedIndexes((p_82670_, p_82671_, p_82672_) -> {
            boolean[] aboolean = new boolean[]{false};
            mergerY.forMergedIndexes((p_165978_, p_165979_, p_165980_) -> {
                boolean[] aboolean1 = new boolean[]{false};
                mergerZ.forMergedIndexes((p_165960_, p_165961_, p_165962_) -> {
                    if (operator.apply(mainShape.isFullWide(p_82670_, p_165978_, p_165960_), secondaryShape.isFullWide(p_82671_, p_165979_, p_165961_))) {
                        bitsetdiscretevoxelshape.storage.set(bitsetdiscretevoxelshape.getIndex(p_82672_, p_165980_, p_165962_));
                        aint[2] = Math.min(aint[2], p_165962_);
                        aint[5] = Math.max(aint[5], p_165962_);
                        aboolean1[0] = true;
                    }

                    return true;
                });
                if (aboolean1[0]) {
                    aint[1] = Math.min(aint[1], p_165980_);
                    aint[4] = Math.max(aint[4], p_165980_);
                    aboolean[0] = true;
                }

                return true;
            });
            if (aboolean[0]) {
                aint[0] = Math.min(aint[0], p_82672_);
                aint[3] = Math.max(aint[3], p_82672_);
            }

            return true;
        });
        bitsetdiscretevoxelshape.xMin = aint[0];
        bitsetdiscretevoxelshape.yMin = aint[1];
        bitsetdiscretevoxelshape.zMin = aint[2];
        bitsetdiscretevoxelshape.xMax = aint[3] + 1;
        bitsetdiscretevoxelshape.yMax = aint[4] + 1;
        bitsetdiscretevoxelshape.zMax = aint[5] + 1;
        return bitsetdiscretevoxelshape;
    }

    protected static void forAllBoxes(DiscreteVoxelShape shape, DiscreteVoxelShape.IntLineConsumer consumer, boolean combine) {
        BitSetDiscreteVoxelShape bitsetdiscretevoxelshape = new BitSetDiscreteVoxelShape(shape);

        for (int i = 0; i < bitsetdiscretevoxelshape.ySize; i++) {
            for (int j = 0; j < bitsetdiscretevoxelshape.xSize; j++) {
                int k = -1;

                for (int l = 0; l <= bitsetdiscretevoxelshape.zSize; l++) {
                    if (bitsetdiscretevoxelshape.isFullWide(j, i, l)) {
                        if (combine) {
                            if (k == -1) {
                                k = l;
                            }
                        } else {
                            consumer.consume(j, i, l, j + 1, i + 1, l + 1);
                        }
                    } else if (k != -1) {
                        int i1 = j;
                        int j1 = i;
                        bitsetdiscretevoxelshape.clearZStrip(k, l, j, i);

                        while (bitsetdiscretevoxelshape.isZStripFull(k, l, i1 + 1, i)) {
                            bitsetdiscretevoxelshape.clearZStrip(k, l, i1 + 1, i);
                            i1++;
                        }

                        while (bitsetdiscretevoxelshape.isXZRectangleFull(j, i1 + 1, k, l, j1 + 1)) {
                            for (int k1 = j; k1 <= i1; k1++) {
                                bitsetdiscretevoxelshape.clearZStrip(k, l, k1, j1 + 1);
                            }

                            j1++;
                        }

                        consumer.consume(j, i, k, i1 + 1, j1 + 1, l);
                        k = -1;
                    }
                }
            }
        }
    }

    private boolean isZStripFull(int zMin, int zMax, int x, int y) {
        return x < this.xSize && y < this.ySize
            ? this.storage.nextClearBit(this.getIndex(x, y, zMin)) >= this.getIndex(x, y, zMax)
            : false;
    }

    private boolean isXZRectangleFull(int xMin, int xMax, int zMin, int zMax, int y) {
        for (int i = xMin; i < xMax; i++) {
            if (!this.isZStripFull(zMin, zMax, i, y)) {
                return false;
            }
        }

        return true;
    }

    private void clearZStrip(int zMin, int zMax, int x, int y) {
        this.storage.clear(this.getIndex(x, y, zMin), this.getIndex(x, y, zMax));
    }

    public boolean isInterior(int x, int y, int z) {
        boolean flag = x > 0
            && x < this.xSize - 1
            && y > 0
            && y < this.ySize - 1
            && z > 0
            && z < this.zSize - 1;
        return flag
            && this.isFull(x, y, z)
            && this.isFull(x - 1, y, z)
            && this.isFull(x + 1, y, z)
            && this.isFull(x, y - 1, z)
            && this.isFull(x, y + 1, z)
            && this.isFull(x, y, z - 1)
            && this.isFull(x, y, z + 1);
    }
}
