package net.minecraft.world.phys.shapes;

import net.minecraft.core.AxisCycle;
import net.minecraft.core.Direction;

public abstract class DiscreteVoxelShape {
    private static final Direction.Axis[] AXIS_VALUES = Direction.Axis.values();
    protected final int xSize;
    protected final int ySize;
    protected final int zSize;

    protected DiscreteVoxelShape(int xSize, int ySize, int zSize) {
        if (xSize >= 0 && ySize >= 0 && zSize >= 0) {
            this.xSize = xSize;
            this.ySize = ySize;
            this.zSize = zSize;
        } else {
            throw new IllegalArgumentException("Need all positive sizes: x: " + xSize + ", y: " + ySize + ", z: " + zSize);
        }
    }

    public boolean isFullWide(AxisCycle axis, int x, int y, int z) {
        return this.isFullWide(
            axis.cycle(x, y, z, Direction.Axis.X),
            axis.cycle(x, y, z, Direction.Axis.Y),
            axis.cycle(x, y, z, Direction.Axis.Z)
        );
    }

    public boolean isFullWide(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0) {
            return false;
        } else {
            return x < this.xSize && y < this.ySize && z < this.zSize ? this.isFull(x, y, z) : false;
        }
    }

    public boolean isFull(AxisCycle rotation, int x, int y, int z) {
        return this.isFull(
            rotation.cycle(x, y, z, Direction.Axis.X),
            rotation.cycle(x, y, z, Direction.Axis.Y),
            rotation.cycle(x, y, z, Direction.Axis.Z)
        );
    }

    public abstract boolean isFull(int x, int y, int z);

    public abstract void fill(int x, int y, int z);

    public boolean isEmpty() {
        for (Direction.Axis direction$axis : AXIS_VALUES) {
            if (this.firstFull(direction$axis) >= this.lastFull(direction$axis)) {
                return true;
            }
        }

        return false;
    }

    public abstract int firstFull(Direction.Axis axis);

    public abstract int lastFull(Direction.Axis axis);

    public int firstFull(Direction.Axis axis, int y, int z) {
        int i = this.getSize(axis);
        if (y >= 0 && z >= 0) {
            Direction.Axis direction$axis = AxisCycle.FORWARD.cycle(axis);
            Direction.Axis direction$axis1 = AxisCycle.BACKWARD.cycle(axis);
            if (y < this.getSize(direction$axis) && z < this.getSize(direction$axis1)) {
                AxisCycle axiscycle = AxisCycle.between(Direction.Axis.X, axis);

                for (int j = 0; j < i; j++) {
                    if (this.isFull(axiscycle, j, y, z)) {
                        return j;
                    }
                }

                return i;
            } else {
                return i;
            }
        } else {
            return i;
        }
    }

    /**
     * Gives the index of the last filled part in the column.
     */
    public int lastFull(Direction.Axis axis, int y, int z) {
        if (y >= 0 && z >= 0) {
            Direction.Axis direction$axis = AxisCycle.FORWARD.cycle(axis);
            Direction.Axis direction$axis1 = AxisCycle.BACKWARD.cycle(axis);
            if (y < this.getSize(direction$axis) && z < this.getSize(direction$axis1)) {
                int i = this.getSize(axis);
                AxisCycle axiscycle = AxisCycle.between(Direction.Axis.X, axis);

                for (int j = i - 1; j >= 0; j--) {
                    if (this.isFull(axiscycle, j, y, z)) {
                        return j + 1;
                    }
                }

                return 0;
            } else {
                return 0;
            }
        } else {
            return 0;
        }
    }

    public int getSize(Direction.Axis axis) {
        return axis.choose(this.xSize, this.ySize, this.zSize);
    }

    public int getXSize() {
        return this.getSize(Direction.Axis.X);
    }

    public int getYSize() {
        return this.getSize(Direction.Axis.Y);
    }

    public int getZSize() {
        return this.getSize(Direction.Axis.Z);
    }

    public void forAllEdges(DiscreteVoxelShape.IntLineConsumer consumer, boolean combine) {
        this.forAllAxisEdges(consumer, AxisCycle.NONE, combine);
        this.forAllAxisEdges(consumer, AxisCycle.FORWARD, combine);
        this.forAllAxisEdges(consumer, AxisCycle.BACKWARD, combine);
    }

    private void forAllAxisEdges(DiscreteVoxelShape.IntLineConsumer lineConsumer, AxisCycle axis, boolean combine) {
        AxisCycle axiscycle = axis.inverse();
        int j = this.getSize(axiscycle.cycle(Direction.Axis.X));
        int k = this.getSize(axiscycle.cycle(Direction.Axis.Y));
        int l = this.getSize(axiscycle.cycle(Direction.Axis.Z));

        for (int i1 = 0; i1 <= j; i1++) {
            for (int j1 = 0; j1 <= k; j1++) {
                int i = -1;

                for (int k1 = 0; k1 <= l; k1++) {
                    int l1 = 0;
                    int i2 = 0;

                    for (int j2 = 0; j2 <= 1; j2++) {
                        for (int k2 = 0; k2 <= 1; k2++) {
                            if (this.isFullWide(axiscycle, i1 + j2 - 1, j1 + k2 - 1, k1)) {
                                l1++;
                                i2 ^= j2 ^ k2;
                            }
                        }
                    }

                    if (l1 == 1 || l1 == 3 || l1 == 2 && (i2 & 1) == 0) {
                        if (combine) {
                            if (i == -1) {
                                i = k1;
                            }
                        } else {
                            lineConsumer.consume(
                                axiscycle.cycle(i1, j1, k1, Direction.Axis.X),
                                axiscycle.cycle(i1, j1, k1, Direction.Axis.Y),
                                axiscycle.cycle(i1, j1, k1, Direction.Axis.Z),
                                axiscycle.cycle(i1, j1, k1 + 1, Direction.Axis.X),
                                axiscycle.cycle(i1, j1, k1 + 1, Direction.Axis.Y),
                                axiscycle.cycle(i1, j1, k1 + 1, Direction.Axis.Z)
                            );
                        }
                    } else if (i != -1) {
                        lineConsumer.consume(
                            axiscycle.cycle(i1, j1, i, Direction.Axis.X),
                            axiscycle.cycle(i1, j1, i, Direction.Axis.Y),
                            axiscycle.cycle(i1, j1, i, Direction.Axis.Z),
                            axiscycle.cycle(i1, j1, k1, Direction.Axis.X),
                            axiscycle.cycle(i1, j1, k1, Direction.Axis.Y),
                            axiscycle.cycle(i1, j1, k1, Direction.Axis.Z)
                        );
                        i = -1;
                    }
                }
            }
        }
    }

    public void forAllBoxes(DiscreteVoxelShape.IntLineConsumer consumer, boolean combine) {
        BitSetDiscreteVoxelShape.forAllBoxes(this, consumer, combine);
    }

    public void forAllFaces(DiscreteVoxelShape.IntFaceConsumer faceConsumer) {
        this.forAllAxisFaces(faceConsumer, AxisCycle.NONE);
        this.forAllAxisFaces(faceConsumer, AxisCycle.FORWARD);
        this.forAllAxisFaces(faceConsumer, AxisCycle.BACKWARD);
    }

    private void forAllAxisFaces(DiscreteVoxelShape.IntFaceConsumer faceConsumer, AxisCycle axisRotation) {
        AxisCycle axiscycle = axisRotation.inverse();
        Direction.Axis direction$axis = axiscycle.cycle(Direction.Axis.Z);
        int i = this.getSize(axiscycle.cycle(Direction.Axis.X));
        int j = this.getSize(axiscycle.cycle(Direction.Axis.Y));
        int k = this.getSize(direction$axis);
        Direction direction = Direction.fromAxisAndDirection(direction$axis, Direction.AxisDirection.NEGATIVE);
        Direction direction1 = Direction.fromAxisAndDirection(direction$axis, Direction.AxisDirection.POSITIVE);

        for (int l = 0; l < i; l++) {
            for (int i1 = 0; i1 < j; i1++) {
                boolean flag = false;

                for (int j1 = 0; j1 <= k; j1++) {
                    boolean flag1 = j1 != k && this.isFull(axiscycle, l, i1, j1);
                    if (!flag && flag1) {
                        faceConsumer.consume(
                            direction,
                            axiscycle.cycle(l, i1, j1, Direction.Axis.X),
                            axiscycle.cycle(l, i1, j1, Direction.Axis.Y),
                            axiscycle.cycle(l, i1, j1, Direction.Axis.Z)
                        );
                    }

                    if (flag && !flag1) {
                        faceConsumer.consume(
                            direction1,
                            axiscycle.cycle(l, i1, j1 - 1, Direction.Axis.X),
                            axiscycle.cycle(l, i1, j1 - 1, Direction.Axis.Y),
                            axiscycle.cycle(l, i1, j1 - 1, Direction.Axis.Z)
                        );
                    }

                    flag = flag1;
                }
            }
        }
    }

    public interface IntFaceConsumer {
        void consume(Direction direction, int x, int y, int z);
    }

    public interface IntLineConsumer {
        void consume(int x1, int y1, int z1, int x2, int y2, int z2);
    }
}
