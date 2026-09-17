package net.minecraft.core;

import com.google.common.base.MoreObjects;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.stream.IntStream;
import javax.annotation.concurrent.Immutable;
import net.minecraft.Util;
import net.minecraft.util.Mth;

@Immutable
public class Vec3i implements Comparable<Vec3i> {
    public static final Codec<Vec3i> CODEC = Codec.INT_STREAM
        .comapFlatMap(
            p_337447_ -> Util.fixedSize(p_337447_, 3).map(p_175586_ -> new Vec3i(p_175586_[0], p_175586_[1], p_175586_[2])),
            p_123313_ -> IntStream.of(p_123313_.getX(), p_123313_.getY(), p_123313_.getZ())
        );
    /**
     * An immutable vector with zero as all coordinates.
     */
    public static final Vec3i ZERO = new Vec3i(0, 0, 0);
    private int x;
    private int y;
    private int z;

    public static Codec<Vec3i> offsetCodec(int maxOffset) {
        return CODEC.validate(
            p_274739_ -> Math.abs(p_274739_.getX()) < maxOffset && Math.abs(p_274739_.getY()) < maxOffset && Math.abs(p_274739_.getZ()) < maxOffset
                    ? DataResult.success(p_274739_)
                    : DataResult.error(() -> "Position out of range, expected at most " + maxOffset + ": " + p_274739_)
        );
    }

    public Vec3i(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (!(other instanceof Vec3i vec3i)) {
            return false;
        } else if (this.getX() != vec3i.getX()) {
            return false;
        } else {
            return this.getY() != vec3i.getY() ? false : this.getZ() == vec3i.getZ();
        }
    }

    @Override
    public int hashCode() {
        return (this.getY() + this.getZ() * 31) * 31 + this.getX();
    }

    public int compareTo(Vec3i other) {
        if (this.getY() == other.getY()) {
            return this.getZ() == other.getZ() ? this.getX() - other.getX() : this.getZ() - other.getZ();
        } else {
            return this.getY() - other.getY();
        }
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getZ() {
        return this.z;
    }

    protected Vec3i setX(int x) {
        this.x = x;
        return this;
    }

    protected Vec3i setY(int y) {
        this.y = y;
        return this;
    }

    protected Vec3i setZ(int z) {
        this.z = z;
        return this;
    }

    public Vec3i offset(int dx, int dy, int dz) {
        return dx == 0 && dy == 0 && dz == 0 ? this : new Vec3i(this.getX() + dx, this.getY() + dy, this.getZ() + dz);
    }

    public Vec3i offset(Vec3i vector) {
        return this.offset(vector.getX(), vector.getY(), vector.getZ());
    }

    public Vec3i subtract(Vec3i vector) {
        return this.offset(-vector.getX(), -vector.getY(), -vector.getZ());
    }

    public Vec3i multiply(int scalar) {
        if (scalar == 1) {
            return this;
        } else {
            return scalar == 0 ? ZERO : new Vec3i(this.getX() * scalar, this.getY() * scalar, this.getZ() * scalar);
        }
    }

    public Vec3i above() {
        return this.above(1);
    }

    /**
     * Offset this vector upwards by the given distance.
     */
    public Vec3i above(int distance) {
        return this.relative(Direction.UP, distance);
    }

    public Vec3i below() {
        return this.below(1);
    }

    /**
     * Offset this vector downwards by the given distance.
     */
    public Vec3i below(int distance) {
        return this.relative(Direction.DOWN, distance);
    }

    public Vec3i north() {
        return this.north(1);
    }

    public Vec3i north(int distance) {
        return this.relative(Direction.NORTH, distance);
    }

    public Vec3i south() {
        return this.south(1);
    }

    public Vec3i south(int distance) {
        return this.relative(Direction.SOUTH, distance);
    }

    public Vec3i west() {
        return this.west(1);
    }

    public Vec3i west(int distance) {
        return this.relative(Direction.WEST, distance);
    }

    public Vec3i east() {
        return this.east(1);
    }

    public Vec3i east(int distance) {
        return this.relative(Direction.EAST, distance);
    }

    public Vec3i relative(Direction direction) {
        return this.relative(direction, 1);
    }

    /**
     * Offsets this Vector by the given distance in the specified direction.
     */
    public Vec3i relative(Direction direction, int distance) {
        return distance == 0
            ? this
            : new Vec3i(
                this.getX() + direction.getStepX() * distance, this.getY() + direction.getStepY() * distance, this.getZ() + direction.getStepZ() * distance
            );
    }

    public Vec3i relative(Direction.Axis axis, int amount) {
        if (amount == 0) {
            return this;
        } else {
            int i = axis == Direction.Axis.X ? amount : 0;
            int j = axis == Direction.Axis.Y ? amount : 0;
            int k = axis == Direction.Axis.Z ? amount : 0;
            return new Vec3i(this.getX() + i, this.getY() + j, this.getZ() + k);
        }
    }

    /**
     * Calculate the cross product of this and the given Vector
     */
    public Vec3i cross(Vec3i vector) {
        return new Vec3i(
            this.getY() * vector.getZ() - this.getZ() * vector.getY(),
            this.getZ() * vector.getX() - this.getX() * vector.getZ(),
            this.getX() * vector.getY() - this.getY() * vector.getX()
        );
    }

    public boolean closerThan(Vec3i vector, double distance) {
        return this.distSqr(vector) < Mth.square(distance);
    }

    public boolean closerToCenterThan(Position position, double distance) {
        return this.distToCenterSqr(position) < Mth.square(distance);
    }

    /**
     * Calculate squared distance to the given Vector
     */
    public double distSqr(Vec3i vector) {
        return this.distToLowCornerSqr((double)vector.getX(), (double)vector.getY(), (double)vector.getZ());
    }

    public double distToCenterSqr(Position position) {
        return this.distToCenterSqr(position.x(), position.y(), position.z());
    }

    public double distToCenterSqr(double x, double y, double z) {
        double d0 = (double)this.getX() + 0.5 - x;
        double d1 = (double)this.getY() + 0.5 - y;
        double d2 = (double)this.getZ() + 0.5 - z;
        return d0 * d0 + d1 * d1 + d2 * d2;
    }

    public double distToLowCornerSqr(double x, double y, double z) {
        double d0 = (double)this.getX() - x;
        double d1 = (double)this.getY() - y;
        double d2 = (double)this.getZ() - z;
        return d0 * d0 + d1 * d1 + d2 * d2;
    }

    public int distManhattan(Vec3i vector) {
        float f = (float)Math.abs(vector.getX() - this.getX());
        float f1 = (float)Math.abs(vector.getY() - this.getY());
        float f2 = (float)Math.abs(vector.getZ() - this.getZ());
        return (int)(f + f1 + f2);
    }

    public int get(Direction.Axis axis) {
        return axis.choose(this.x, this.y, this.z);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("x", this.getX()).add("y", this.getY()).add("z", this.getZ()).toString();
    }

    public String toShortString() {
        return this.getX() + ", " + this.getY() + ", " + this.getZ();
    }
}
