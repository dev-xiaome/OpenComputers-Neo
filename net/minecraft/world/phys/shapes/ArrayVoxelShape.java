package net.minecraft.world.phys.shapes;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import java.util.Arrays;
import net.minecraft.Util;
import net.minecraft.core.Direction;

public class ArrayVoxelShape extends VoxelShape {
    private final DoubleList xs;
    private final DoubleList ys;
    private final DoubleList zs;

    protected ArrayVoxelShape(DiscreteVoxelShape shape, double[] xs, double[] ys, double[] zs) {
        this(
            shape,
            DoubleArrayList.wrap(Arrays.copyOf(xs, shape.getXSize() + 1)),
            DoubleArrayList.wrap(Arrays.copyOf(ys, shape.getYSize() + 1)),
            DoubleArrayList.wrap(Arrays.copyOf(zs, shape.getZSize() + 1))
        );
    }

    ArrayVoxelShape(DiscreteVoxelShape shape, DoubleList xs, DoubleList ys, DoubleList zs) {
        super(shape);
        int i = shape.getXSize() + 1;
        int j = shape.getYSize() + 1;
        int k = shape.getZSize() + 1;
        if (i == xs.size() && j == ys.size() && k == zs.size()) {
            this.xs = xs;
            this.ys = ys;
            this.zs = zs;
        } else {
            throw (IllegalArgumentException)Util.pauseInIde(
                new IllegalArgumentException("Lengths of point arrays must be consistent with the size of the VoxelShape.")
            );
        }
    }

    @Override
    public DoubleList getCoords(Direction.Axis axis) {
        switch (axis) {
            case X:
                return this.xs;
            case Y:
                return this.ys;
            case Z:
                return this.zs;
            default:
                throw new IllegalArgumentException();
        }
    }
}
