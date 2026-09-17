package net.minecraft.world.phys;

import com.mojang.serialization.Codec;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Vector3f;

public class Vec3 implements Position {
    public static final Codec<Vec3> CODEC = Codec.DOUBLE
        .listOf()
        .comapFlatMap(
            p_338175_ -> Util.fixedSize((List<Double>)p_338175_, 3).map(p_231081_ -> new Vec3(p_231081_.get(0), p_231081_.get(1), p_231081_.get(2))),
            p_231083_ -> List.of(p_231083_.x(), p_231083_.y(), p_231083_.z())
        );
    public static final Vec3 ZERO = new Vec3(0.0, 0.0, 0.0);
    public final double x;
    public final double y;
    public final double z;

    public static Vec3 fromRGB24(int packed) {
        double d0 = (double)(packed >> 16 & 0xFF) / 255.0;
        double d1 = (double)(packed >> 8 & 0xFF) / 255.0;
        double d2 = (double)(packed & 0xFF) / 255.0;
        return new Vec3(d0, d1, d2);
    }

    /**
     * Copies the coordinates of an int vector exactly.
     */
    public static Vec3 atLowerCornerOf(Vec3i toCopy) {
        return new Vec3((double)toCopy.getX(), (double)toCopy.getY(), (double)toCopy.getZ());
    }

    public static Vec3 atLowerCornerWithOffset(Vec3i toCopy, double offsetX, double offsetY, double offsetZ) {
        return new Vec3((double)toCopy.getX() + offsetX, (double)toCopy.getY() + offsetY, (double)toCopy.getZ() + offsetZ);
    }

    /**
     * Copies the coordinates of an Int vector and centers them.
     */
    public static Vec3 atCenterOf(Vec3i toCopy) {
        return atLowerCornerWithOffset(toCopy, 0.5, 0.5, 0.5);
    }

    /**
     * Copies the coordinates of an int vector and centers them horizontally (x and z)
     */
    public static Vec3 atBottomCenterOf(Vec3i toCopy) {
        return atLowerCornerWithOffset(toCopy, 0.5, 0.0, 0.5);
    }

    /**
     * Copies the coordinates of an int vector and centers them horizontally and applies a vertical offset.
     */
    public static Vec3 upFromBottomCenterOf(Vec3i toCopy, double verticalOffset) {
        return atLowerCornerWithOffset(toCopy, 0.5, verticalOffset, 0.5);
    }

    public Vec3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3(Vector3f vector) {
        this((double)vector.x(), (double)vector.y(), (double)vector.z());
    }

    /**
     * Returns a new vector with the result of the specified vector minus this.
     */
    public Vec3 vectorTo(Vec3 vec) {
        return new Vec3(vec.x - this.x, vec.y - this.y, vec.z - this.z);
    }

    public Vec3 normalize() {
        double d0 = Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
        return d0 < 1.0E-4 ? ZERO : new Vec3(this.x / d0, this.y / d0, this.z / d0);
    }

    public double dot(Vec3 vec) {
        return this.x * vec.x + this.y * vec.y + this.z * vec.z;
    }

    /**
     * Returns a new vector with the result of this vector x the specified vector.
     */
    public Vec3 cross(Vec3 vec) {
        return new Vec3(this.y * vec.z - this.z * vec.y, this.z * vec.x - this.x * vec.z, this.x * vec.y - this.y * vec.x);
    }

    public Vec3 subtract(Vec3 vec) {
        return this.subtract(vec.x, vec.y, vec.z);
    }

    public Vec3 subtract(double x, double y, double z) {
        return this.add(-x, -y, -z);
    }

    public Vec3 add(Vec3 vec) {
        return this.add(vec.x, vec.y, vec.z);
    }

    /**
     * Adds the specified x,y,z vector components to this vector and returns the resulting vector. Does not change this vector.
     */
    public Vec3 add(double x, double y, double z) {
        return new Vec3(this.x + x, this.y + y, this.z + z);
    }

    /**
     * Checks if a position is within a certain distance of the coordinates.
     */
    public boolean closerThan(Position pos, double distance) {
        return this.distanceToSqr(pos.x(), pos.y(), pos.z()) < distance * distance;
    }

    /**
     * Euclidean distance between this and the specified vector, returned as double.
     */
    public double distanceTo(Vec3 vec) {
        double d0 = vec.x - this.x;
        double d1 = vec.y - this.y;
        double d2 = vec.z - this.z;
        return Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
    }

    /**
     * The square of the Euclidean distance between this and the specified vector.
     */
    public double distanceToSqr(Vec3 vec) {
        double d0 = vec.x - this.x;
        double d1 = vec.y - this.y;
        double d2 = vec.z - this.z;
        return d0 * d0 + d1 * d1 + d2 * d2;
    }

    public double distanceToSqr(double x, double y, double z) {
        double d0 = x - this.x;
        double d1 = y - this.y;
        double d2 = z - this.z;
        return d0 * d0 + d1 * d1 + d2 * d2;
    }

    public boolean closerThan(Vec3 pos, double horizontalDistance, double verticalDistance) {
        double d0 = pos.x() - this.x;
        double d1 = pos.y() - this.y;
        double d2 = pos.z() - this.z;
        return Mth.lengthSquared(d0, d2) < Mth.square(horizontalDistance) && Math.abs(d1) < verticalDistance;
    }

    public Vec3 scale(double factor) {
        return this.multiply(factor, factor, factor);
    }

    public Vec3 reverse() {
        return this.scale(-1.0);
    }

    public Vec3 multiply(Vec3 vec) {
        return this.multiply(vec.x, vec.y, vec.z);
    }

    public Vec3 multiply(double factorX, double factorY, double factorZ) {
        return new Vec3(this.x * factorX, this.y * factorY, this.z * factorZ);
    }

    public Vec3 offsetRandom(RandomSource random, float factor) {
        return this.add(
            (double)((random.nextFloat() - 0.5F) * factor),
            (double)((random.nextFloat() - 0.5F) * factor),
            (double)((random.nextFloat() - 0.5F) * factor)
        );
    }

    public double length() {
        return Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
    }

    public double lengthSqr() {
        return this.x * this.x + this.y * this.y + this.z * this.z;
    }

    public double horizontalDistance() {
        return Math.sqrt(this.x * this.x + this.z * this.z);
    }

    public double horizontalDistanceSqr() {
        return this.x * this.x + this.z * this.z;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (!(other instanceof Vec3 vec3)) {
            return false;
        } else if (Double.compare(vec3.x, this.x) != 0) {
            return false;
        } else {
            return Double.compare(vec3.y, this.y) != 0 ? false : Double.compare(vec3.z, this.z) == 0;
        }
    }

    @Override
    public int hashCode() {
        long j = Double.doubleToLongBits(this.x);
        int i = (int)(j ^ j >>> 32);
        j = Double.doubleToLongBits(this.y);
        i = 31 * i + (int)(j ^ j >>> 32);
        j = Double.doubleToLongBits(this.z);
        return 31 * i + (int)(j ^ j >>> 32);
    }

    @Override
    public String toString() {
        return "(" + this.x + ", " + this.y + ", " + this.z + ")";
    }

    /**
     * Lerps between this vector and the given vector.
     * @see net.minecraft.util.Mth#lerp(double, double, double)
     */
    public Vec3 lerp(Vec3 to, double delta) {
        return new Vec3(Mth.lerp(delta, this.x, to.x), Mth.lerp(delta, this.y, to.y), Mth.lerp(delta, this.z, to.z));
    }

    public Vec3 xRot(float pitch) {
        float f = Mth.cos(pitch);
        float f1 = Mth.sin(pitch);
        double d0 = this.x;
        double d1 = this.y * (double)f + this.z * (double)f1;
        double d2 = this.z * (double)f - this.y * (double)f1;
        return new Vec3(d0, d1, d2);
    }

    public Vec3 yRot(float yaw) {
        float f = Mth.cos(yaw);
        float f1 = Mth.sin(yaw);
        double d0 = this.x * (double)f + this.z * (double)f1;
        double d1 = this.y;
        double d2 = this.z * (double)f - this.x * (double)f1;
        return new Vec3(d0, d1, d2);
    }

    public Vec3 zRot(float roll) {
        float f = Mth.cos(roll);
        float f1 = Mth.sin(roll);
        double d0 = this.x * (double)f + this.y * (double)f1;
        double d1 = this.y * (double)f - this.x * (double)f1;
        double d2 = this.z;
        return new Vec3(d0, d1, d2);
    }

    /**
     * Returns a {@link net.minecraft.world.phys.Vec3} from the given pitch and yaw degrees as {@link net.minecraft.world.phys.Vec2}.
     */
    public static Vec3 directionFromRotation(Vec2 vec) {
        return directionFromRotation(vec.x, vec.y);
    }

    /**
     * Returns a {@link net.minecraft.world.phys.Vec3} from the given pitch and yaw degrees.
     */
    public static Vec3 directionFromRotation(float pitch, float yaw) {
        float f = Mth.cos(-yaw * (float) (Math.PI / 180.0) - (float) Math.PI);
        float f1 = Mth.sin(-yaw * (float) (Math.PI / 180.0) - (float) Math.PI);
        float f2 = -Mth.cos(-pitch * (float) (Math.PI / 180.0));
        float f3 = Mth.sin(-pitch * (float) (Math.PI / 180.0));
        return new Vec3((double)(f1 * f2), (double)f3, (double)(f * f2));
    }

    public Vec3 align(EnumSet<Direction.Axis> axes) {
        double d0 = axes.contains(Direction.Axis.X) ? (double)Mth.floor(this.x) : this.x;
        double d1 = axes.contains(Direction.Axis.Y) ? (double)Mth.floor(this.y) : this.y;
        double d2 = axes.contains(Direction.Axis.Z) ? (double)Mth.floor(this.z) : this.z;
        return new Vec3(d0, d1, d2);
    }

    public double get(Direction.Axis axis) {
        return axis.choose(this.x, this.y, this.z);
    }

    public Vec3 with(Direction.Axis axis, double length) {
        double d0 = axis == Direction.Axis.X ? length : this.x;
        double d1 = axis == Direction.Axis.Y ? length : this.y;
        double d2 = axis == Direction.Axis.Z ? length : this.z;
        return new Vec3(d0, d1, d2);
    }

    public Vec3 relative(Direction direction, double length) {
        Vec3i vec3i = direction.getNormal();
        return new Vec3(this.x + length * (double)vec3i.getX(), this.y + length * (double)vec3i.getY(), this.z + length * (double)vec3i.getZ());
    }

    @Override
    public final double x() {
        return this.x;
    }

    @Override
    public final double y() {
        return this.y;
    }

    @Override
    public final double z() {
        return this.z;
    }

    public Vector3f toVector3f() {
        return new Vector3f((float)this.x, (float)this.y, (float)this.z);
    }
}
