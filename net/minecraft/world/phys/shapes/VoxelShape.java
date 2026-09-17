package net.minecraft.world.phys.shapes;

import com.google.common.collect.Lists;
import com.google.common.math.DoubleMath;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.AxisCycle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public abstract class VoxelShape {
    protected final DiscreteVoxelShape shape;
    @Nullable
    private VoxelShape[] faces;

    protected VoxelShape(DiscreteVoxelShape shape) {
        this.shape = shape;
    }

    public double min(Direction.Axis axis) {
        int i = this.shape.firstFull(axis);
        return i >= this.shape.getSize(axis) ? Double.POSITIVE_INFINITY : this.get(axis, i);
    }

    public double max(Direction.Axis axis) {
        int i = this.shape.lastFull(axis);
        return i <= 0 ? Double.NEGATIVE_INFINITY : this.get(axis, i);
    }

    public AABB bounds() {
        if (this.isEmpty()) {
            throw (UnsupportedOperationException)Util.pauseInIde(new UnsupportedOperationException("No bounds for empty shape."));
        } else {
            return new AABB(
                this.min(Direction.Axis.X),
                this.min(Direction.Axis.Y),
                this.min(Direction.Axis.Z),
                this.max(Direction.Axis.X),
                this.max(Direction.Axis.Y),
                this.max(Direction.Axis.Z)
            );
        }
    }

    public VoxelShape singleEncompassing() {
        return this.isEmpty()
            ? Shapes.empty()
            : Shapes.box(
                this.min(Direction.Axis.X),
                this.min(Direction.Axis.Y),
                this.min(Direction.Axis.Z),
                this.max(Direction.Axis.X),
                this.max(Direction.Axis.Y),
                this.max(Direction.Axis.Z)
            );
    }

    protected double get(Direction.Axis axis, int index) {
        return this.getCoords(axis).getDouble(index);
    }

    public abstract DoubleList getCoords(Direction.Axis axis);

    public boolean isEmpty() {
        return this.shape.isEmpty();
    }

    public VoxelShape move(double xOffset, double yOffset, double zOffset) {
        return (VoxelShape)(this.isEmpty()
            ? Shapes.empty()
            : new ArrayVoxelShape(
                this.shape,
                new OffsetDoubleList(this.getCoords(Direction.Axis.X), xOffset),
                new OffsetDoubleList(this.getCoords(Direction.Axis.Y), yOffset),
                new OffsetDoubleList(this.getCoords(Direction.Axis.Z), zOffset)
            ));
    }

    public VoxelShape optimize() {
        VoxelShape[] avoxelshape = new VoxelShape[]{Shapes.empty()};
        this.forAllBoxes(
            (p_83275_, p_83276_, p_83277_, p_83278_, p_83279_, p_83280_) -> avoxelshape[0] = Shapes.joinUnoptimized(
                    avoxelshape[0], Shapes.box(p_83275_, p_83276_, p_83277_, p_83278_, p_83279_, p_83280_), BooleanOp.OR
                )
        );
        return avoxelshape[0];
    }

    public void forAllEdges(Shapes.DoubleLineConsumer action) {
        this.shape
            .forAllEdges(
                (p_83228_, p_83229_, p_83230_, p_83231_, p_83232_, p_83233_) -> action.consume(
                        this.get(Direction.Axis.X, p_83228_),
                        this.get(Direction.Axis.Y, p_83229_),
                        this.get(Direction.Axis.Z, p_83230_),
                        this.get(Direction.Axis.X, p_83231_),
                        this.get(Direction.Axis.Y, p_83232_),
                        this.get(Direction.Axis.Z, p_83233_)
                    ),
                true
            );
    }

    public void forAllBoxes(Shapes.DoubleLineConsumer action) {
        DoubleList doublelist = this.getCoords(Direction.Axis.X);
        DoubleList doublelist1 = this.getCoords(Direction.Axis.Y);
        DoubleList doublelist2 = this.getCoords(Direction.Axis.Z);
        this.shape
            .forAllBoxes(
                (p_83239_, p_83240_, p_83241_, p_83242_, p_83243_, p_83244_) -> action.consume(
                        doublelist.getDouble(p_83239_),
                        doublelist1.getDouble(p_83240_),
                        doublelist2.getDouble(p_83241_),
                        doublelist.getDouble(p_83242_),
                        doublelist1.getDouble(p_83243_),
                        doublelist2.getDouble(p_83244_)
                    ),
                true
            );
    }

    public List<AABB> toAabbs() {
        List<AABB> list = Lists.newArrayList();
        this.forAllBoxes(
            (p_83267_, p_83268_, p_83269_, p_83270_, p_83271_, p_83272_) -> list.add(new AABB(p_83267_, p_83268_, p_83269_, p_83270_, p_83271_, p_83272_))
        );
        return list;
    }

    public double min(Direction.Axis axis, double primaryPosition, double secondaryPosition) {
        Direction.Axis direction$axis = AxisCycle.FORWARD.cycle(axis);
        Direction.Axis direction$axis1 = AxisCycle.BACKWARD.cycle(axis);
        int i = this.findIndex(direction$axis, primaryPosition);
        int j = this.findIndex(direction$axis1, secondaryPosition);
        int k = this.shape.firstFull(axis, i, j);
        return k >= this.shape.getSize(axis) ? Double.POSITIVE_INFINITY : this.get(axis, k);
    }

    public double max(Direction.Axis axis, double primaryPosition, double secondaryPosition) {
        Direction.Axis direction$axis = AxisCycle.FORWARD.cycle(axis);
        Direction.Axis direction$axis1 = AxisCycle.BACKWARD.cycle(axis);
        int i = this.findIndex(direction$axis, primaryPosition);
        int j = this.findIndex(direction$axis1, secondaryPosition);
        int k = this.shape.lastFull(axis, i, j);
        return k <= 0 ? Double.NEGATIVE_INFINITY : this.get(axis, k);
    }

    protected int findIndex(Direction.Axis axis, double position) {
        return Mth.binarySearch(0, this.shape.getSize(axis) + 1, p_166066_ -> position < this.get(axis, p_166066_)) - 1;
    }

    @Nullable
    public BlockHitResult clip(Vec3 startVec, Vec3 endVec, BlockPos pos) {
        if (this.isEmpty()) {
            return null;
        } else {
            Vec3 vec3 = endVec.subtract(startVec);
            if (vec3.lengthSqr() < 1.0E-7) {
                return null;
            } else {
                Vec3 vec31 = startVec.add(vec3.scale(0.001));
                return this.shape
                        .isFullWide(
                            this.findIndex(Direction.Axis.X, vec31.x - (double)pos.getX()),
                            this.findIndex(Direction.Axis.Y, vec31.y - (double)pos.getY()),
                            this.findIndex(Direction.Axis.Z, vec31.z - (double)pos.getZ())
                        )
                    ? new BlockHitResult(vec31, Direction.getNearest(vec3.x, vec3.y, vec3.z).getOpposite(), pos, true)
                    : AABB.clip(this.toAabbs(), startVec, endVec, pos);
            }
        }
    }

    public Optional<Vec3> closestPointTo(Vec3 point) {
        if (this.isEmpty()) {
            return Optional.empty();
        } else {
            Vec3[] avec3 = new Vec3[1];
            this.forAllBoxes((p_166072_, p_166073_, p_166074_, p_166075_, p_166076_, p_166077_) -> {
                double d0 = Mth.clamp(point.x(), p_166072_, p_166075_);
                double d1 = Mth.clamp(point.y(), p_166073_, p_166076_);
                double d2 = Mth.clamp(point.z(), p_166074_, p_166077_);
                if (avec3[0] == null || point.distanceToSqr(d0, d1, d2) < point.distanceToSqr(avec3[0])) {
                    avec3[0] = new Vec3(d0, d1, d2);
                }
            });
            return Optional.of(avec3[0]);
        }
    }

    /**
     * Projects this shape onto the given side. For each box in the shape, if it does not touch the given side, it is eliminated. Otherwise, the box is extended in the given axis to cover the entire range [0, 1].
     */
    public VoxelShape getFaceShape(Direction side) {
        if (!this.isEmpty() && this != Shapes.block()) {
            if (this.faces != null) {
                VoxelShape voxelshape = this.faces[side.ordinal()];
                if (voxelshape != null) {
                    return voxelshape;
                }
            } else {
                this.faces = new VoxelShape[6];
            }

            VoxelShape voxelshape1 = this.calculateFace(side);
            this.faces[side.ordinal()] = voxelshape1;
            return voxelshape1;
        } else {
            return this;
        }
    }

    private VoxelShape calculateFace(Direction side) {
        Direction.Axis direction$axis = side.getAxis();
        DoubleList doublelist = this.getCoords(direction$axis);
        if (doublelist.size() == 2
            && DoubleMath.fuzzyEquals(doublelist.getDouble(0), 0.0, 1.0E-7)
            && DoubleMath.fuzzyEquals(doublelist.getDouble(1), 1.0, 1.0E-7)) {
            return this;
        } else {
            Direction.AxisDirection direction$axisdirection = side.getAxisDirection();
            int i = this.findIndex(direction$axis, direction$axisdirection == Direction.AxisDirection.POSITIVE ? 0.9999999 : 1.0E-7);
            return new SliceShape(this, direction$axis, i);
        }
    }

    public double collide(Direction.Axis movementAxis, AABB collisionBox, double desiredOffset) {
        return this.collideX(AxisCycle.between(movementAxis, Direction.Axis.X), collisionBox, desiredOffset);
    }

    protected double collideX(AxisCycle movementAxis, AABB collisionBox, double desiredOffset) {
        if (this.isEmpty()) {
            return desiredOffset;
        } else if (Math.abs(desiredOffset) < 1.0E-7) {
            return 0.0;
        } else {
            AxisCycle axiscycle = movementAxis.inverse();
            Direction.Axis direction$axis = axiscycle.cycle(Direction.Axis.X);
            Direction.Axis direction$axis1 = axiscycle.cycle(Direction.Axis.Y);
            Direction.Axis direction$axis2 = axiscycle.cycle(Direction.Axis.Z);
            double d0 = collisionBox.max(direction$axis);
            double d1 = collisionBox.min(direction$axis);
            int i = this.findIndex(direction$axis, d1 + 1.0E-7);
            int j = this.findIndex(direction$axis, d0 - 1.0E-7);
            int k = Math.max(0, this.findIndex(direction$axis1, collisionBox.min(direction$axis1) + 1.0E-7));
            int l = Math.min(this.shape.getSize(direction$axis1), this.findIndex(direction$axis1, collisionBox.max(direction$axis1) - 1.0E-7) + 1);
            int i1 = Math.max(0, this.findIndex(direction$axis2, collisionBox.min(direction$axis2) + 1.0E-7));
            int j1 = Math.min(this.shape.getSize(direction$axis2), this.findIndex(direction$axis2, collisionBox.max(direction$axis2) - 1.0E-7) + 1);
            int k1 = this.shape.getSize(direction$axis);
            if (desiredOffset > 0.0) {
                for (int l1 = j + 1; l1 < k1; l1++) {
                    for (int i2 = k; i2 < l; i2++) {
                        for (int j2 = i1; j2 < j1; j2++) {
                            if (this.shape.isFullWide(axiscycle, l1, i2, j2)) {
                                double d2 = this.get(direction$axis, l1) - d0;
                                if (d2 >= -1.0E-7) {
                                    desiredOffset = Math.min(desiredOffset, d2);
                                }

                                return desiredOffset;
                            }
                        }
                    }
                }
            } else if (desiredOffset < 0.0) {
                for (int k2 = i - 1; k2 >= 0; k2--) {
                    for (int l2 = k; l2 < l; l2++) {
                        for (int i3 = i1; i3 < j1; i3++) {
                            if (this.shape.isFullWide(axiscycle, k2, l2, i3)) {
                                double d3 = this.get(direction$axis, k2 + 1) - d1;
                                if (d3 <= 1.0E-7) {
                                    desiredOffset = Math.max(desiredOffset, d3);
                                }

                                return desiredOffset;
                            }
                        }
                    }
                }
            }

            return desiredOffset;
        }
    }

    @Override
    public String toString() {
        return this.isEmpty() ? "EMPTY" : "VoxelShape[" + this.bounds() + "]";
    }
}
