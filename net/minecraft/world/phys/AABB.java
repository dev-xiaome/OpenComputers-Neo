package net.minecraft.world.phys;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.joml.Vector3f;

public class AABB {
    private static final double EPSILON = 1.0E-7;
    public static final AABB INFINITE = new AABB(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
    public final double minX;
    public final double minY;
    public final double minZ;
    public final double maxX;
    public final double maxY;
    public final double maxZ;

    public AABB(double x1, double y1, double z1, double x2, double y2, double z2) {
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public AABB(BlockPos pos) {
        this(
            (double)pos.getX(),
            (double)pos.getY(),
            (double)pos.getZ(),
            (double)(pos.getX() + 1),
            (double)(pos.getY() + 1),
            (double)(pos.getZ() + 1)
        );
    }

    public AABB(Vec3 start, Vec3 end) {
        this(start.x, start.y, start.z, end.x, end.y, end.z);
    }

    public static AABB of(BoundingBox mutableBox) {
        return new AABB(
            (double)mutableBox.minX(),
            (double)mutableBox.minY(),
            (double)mutableBox.minZ(),
            (double)(mutableBox.maxX() + 1),
            (double)(mutableBox.maxY() + 1),
            (double)(mutableBox.maxZ() + 1)
        );
    }

    public static AABB unitCubeFromLowerCorner(Vec3 vector) {
        return new AABB(vector.x, vector.y, vector.z, vector.x + 1.0, vector.y + 1.0, vector.z + 1.0);
    }

    public static AABB encapsulatingFullBlocks(BlockPos startPos, BlockPos endPos) {
        return new AABB(
            (double)Math.min(startPos.getX(), endPos.getX()),
            (double)Math.min(startPos.getY(), endPos.getY()),
            (double)Math.min(startPos.getZ(), endPos.getZ()),
            (double)(Math.max(startPos.getX(), endPos.getX()) + 1),
            (double)(Math.max(startPos.getY(), endPos.getY()) + 1),
            (double)(Math.max(startPos.getZ(), endPos.getZ()) + 1)
        );
    }

    public AABB setMinX(double minX) {
        return new AABB(minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
    }

    public AABB setMinY(double minY) {
        return new AABB(this.minX, minY, this.minZ, this.maxX, this.maxY, this.maxZ);
    }

    public AABB setMinZ(double minZ) {
        return new AABB(this.minX, this.minY, minZ, this.maxX, this.maxY, this.maxZ);
    }

    public AABB setMaxX(double maxX) {
        return new AABB(this.minX, this.minY, this.minZ, maxX, this.maxY, this.maxZ);
    }

    public AABB setMaxY(double maxY) {
        return new AABB(this.minX, this.minY, this.minZ, this.maxX, maxY, this.maxZ);
    }

    public AABB setMaxZ(double maxZ) {
        return new AABB(this.minX, this.minY, this.minZ, this.maxX, this.maxY, maxZ);
    }

    public double min(Direction.Axis axis) {
        return axis.choose(this.minX, this.minY, this.minZ);
    }

    public double max(Direction.Axis axis) {
        return axis.choose(this.maxX, this.maxY, this.maxZ);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (!(other instanceof AABB aabb)) {
            return false;
        } else if (Double.compare(aabb.minX, this.minX) != 0) {
            return false;
        } else if (Double.compare(aabb.minY, this.minY) != 0) {
            return false;
        } else if (Double.compare(aabb.minZ, this.minZ) != 0) {
            return false;
        } else if (Double.compare(aabb.maxX, this.maxX) != 0) {
            return false;
        } else {
            return Double.compare(aabb.maxY, this.maxY) != 0 ? false : Double.compare(aabb.maxZ, this.maxZ) == 0;
        }
    }

    @Override
    public int hashCode() {
        long i = Double.doubleToLongBits(this.minX);
        int j = (int)(i ^ i >>> 32);
        i = Double.doubleToLongBits(this.minY);
        j = 31 * j + (int)(i ^ i >>> 32);
        i = Double.doubleToLongBits(this.minZ);
        j = 31 * j + (int)(i ^ i >>> 32);
        i = Double.doubleToLongBits(this.maxX);
        j = 31 * j + (int)(i ^ i >>> 32);
        i = Double.doubleToLongBits(this.maxY);
        j = 31 * j + (int)(i ^ i >>> 32);
        i = Double.doubleToLongBits(this.maxZ);
        return 31 * j + (int)(i ^ i >>> 32);
    }

    /**
     * Creates a new {@link AxisAlignedBB} that has been contracted by the given amount, with positive changes decreasing max values and negative changes increasing min values.
     * <br/>
     * If the amount to contract by is larger than the length of a side, then the side will wrap (still creating a valid AABB - see last sample).
     *
     * <h3>Samples:</h3>
     * <table>
     * <tr><th>Input</th><th>Result</th></tr>
     * <tr><td><pre><code>new AxisAlignedBB(0, 0, 0, 4, 4, 4).contract(2, 2, 2)</code></pre></td><td><pre><samp>box[0.0, 0.0, 0.0 -> 2.0, 2.0, 2.0]</samp></pre></td></tr>
     * <tr><td><pre><code>new AxisAlignedBB(0, 0, 0, 4, 4, 4).contract(-2, -2, -2)</code></pre></td><td><pre><samp>box[2.0, 2.0, 2.0 -> 4.0, 4.0, 4.0]</samp></pre></td></tr>
     * <tr><td><pre><code>new AxisAlignedBB(5, 5, 5, 7, 7, 7).contract(0, 1, -1)</code></pre></td><td><pre><samp>box[5.0, 5.0, 6.0 -> 7.0, 6.0, 7.0]</samp></pre></td></tr>
     * <tr><td><pre><code>new AxisAlignedBB(-2, -2, -2, 2, 2, 2).contract(4, -4, 0)</code></pre></td><td><pre><samp>box[-8.0, 2.0, -2.0 -> -2.0, 8.0, 2.0]</samp></pre></td></tr>
     * </table>
     *
     * <h3>See Also:</h3>
     * <ul>
     * <li>{@link #expand(double, double, double)} - like this, except for expanding.</li>
     * <li>{@link #grow(double, double, double)} and {@link #grow(double)} - expands in all directions.</li>
     * <li>{@link #shrink(double)} - contracts in all directions (like {@link #grow(double)})</li>
     * </ul>
     *
     * @return A new modified bounding box.
     */
    public AABB contract(double x, double y, double z) {
        double d0 = this.minX;
        double d1 = this.minY;
        double d2 = this.minZ;
        double d3 = this.maxX;
        double d4 = this.maxY;
        double d5 = this.maxZ;
        if (x < 0.0) {
            d0 -= x;
        } else if (x > 0.0) {
            d3 -= x;
        }

        if (y < 0.0) {
            d1 -= y;
        } else if (y > 0.0) {
            d4 -= y;
        }

        if (z < 0.0) {
            d2 -= z;
        } else if (z > 0.0) {
            d5 -= z;
        }

        return new AABB(d0, d1, d2, d3, d4, d5);
    }

    public AABB expandTowards(Vec3 vector) {
        return this.expandTowards(vector.x, vector.y, vector.z);
    }

    /**
     * Creates a new {@link AxisAlignedBB} that has been expanded by the given amount, with positive changes increasing max values and negative changes decreasing min values.
     *
     * <h3>Samples:</h3>
     * <table>
     * <tr><th>Input</th><th>Result</th></tr>
     * <tr><td><pre><code>new AxisAlignedBB(0, 0, 0, 1, 1, 1).expand(2, 2, 2)</code></pre></td><td><pre><samp>box[0, 0, 0 -> 3, 3, 3]</samp></pre></td><td>
     * <tr><td><pre><code>new AxisAlignedBB(0, 0, 0, 1, 1, 1).expand(-2, -2, -2)</code></pre></td><td><pre><samp>box[-2, -2, -2 -> 1, 1, 1]</samp></pre></td><td>
     * <tr><td><pre><code>new AxisAlignedBB(5, 5, 5, 7, 7, 7).expand(0, 1, -1)</code></pre></td><td><pre><samp>box[5, 5, 4, 7, 8, 7]</samp></pre></td><td>
     * </table>
     *
     * <h3>See Also:</h3>
     * <ul>
     * <li>{@link #contract(double, double, double)} - like this, except for shrinking.</li>
     * <li>{@link #grow(double, double, double)} and {@link #grow(double)} - expands in all directions.</li>
     * <li>{@link #shrink(double)} - contracts in all directions (like {@link #grow(double)})</li>
     * </ul>
     *
     * @return A modified bounding box that will always be equal or greater in volume to this bounding box.
     */
    public AABB expandTowards(double x, double y, double z) {
        double d0 = this.minX;
        double d1 = this.minY;
        double d2 = this.minZ;
        double d3 = this.maxX;
        double d4 = this.maxY;
        double d5 = this.maxZ;
        if (x < 0.0) {
            d0 += x;
        } else if (x > 0.0) {
            d3 += x;
        }

        if (y < 0.0) {
            d1 += y;
        } else if (y > 0.0) {
            d4 += y;
        }

        if (z < 0.0) {
            d2 += z;
        } else if (z > 0.0) {
            d5 += z;
        }

        return new AABB(d0, d1, d2, d3, d4, d5);
    }

    /**
     * Creates a new {@link AxisAlignedBB} that has been contracted by the given amount in both directions. Negative values will shrink the AABB instead of expanding it.
     * <br/>
     * Side lengths will be increased by 2 times the value of the parameters, since both min and max are changed.
     * <br/>
     * If contracting and the amount to contract by is larger than the length of a side, then the side will wrap (still creating a valid AABB - see last ample).
     *
     * <h3>Samples:</h3>
     * <table>
     * <tr><th>Input</th><th>Result</th></tr>
     * <tr><td><pre><code>new AxisAlignedBB(0, 0, 0, 1, 1, 1).grow(2, 2, 2)</code></pre></td><td><pre><samp>box[-2.0, -2.0, -2.0 -> 3.0, 3.0, 3.0]</samp></pre></td></tr>
     * <tr><td><pre><code>new AxisAlignedBB(0, 0, 0, 6, 6, 6).grow(-2, -2, -2)</code></pre></td><td><pre><samp>box[2.0, 2.0, 2.0 -> 4.0, 4.0, 4.0]</samp></pre></td></tr>
     * <tr><td><pre><code>new AxisAlignedBB(5, 5, 5, 7, 7, 7).grow(0, 1, -1)</code></pre></td><td><pre><samp>box[5.0, 4.0, 6.0 -> 7.0, 8.0, 6.0]</samp></pre></td></tr>
     * <tr><td><pre><code>new AxisAlignedBB(1, 1, 1, 3, 3, 3).grow(-4, -2, -3)</code></pre></td><td><pre><samp>box[-1.0, 1.0, 0.0 -> 5.0, 3.0, 4.0]</samp></pre></td></tr>
     * </table>
     *
     * <h3>See Also:</h3>
     * <ul>
     * <li>{@link #expand(double, double, double)} - expands in only one direction.</li>
     * <li>{@link #contract(double, double, double)} - contracts in only one direction.</li>
     * <lu>{@link #grow(double)} - version of this that expands in all directions from one parameter.</li>
     * <li>{@link #shrink(double)} - contracts in all directions</li>
     * </ul>
     *
     * @return A modified bounding box.
     */
    public AABB inflate(double x, double y, double z) {
        double d0 = this.minX - x;
        double d1 = this.minY - y;
        double d2 = this.minZ - z;
        double d3 = this.maxX + x;
        double d4 = this.maxY + y;
        double d5 = this.maxZ + z;
        return new AABB(d0, d1, d2, d3, d4, d5);
    }

    /**
     * Creates a new {@link AxisAlignedBB} that is expanded by the given value in all directions. Equivalent to {@link #grow(double, double, double)} with the given value for all 3 params. Negative values will shrink the AABB.
     * <br/>
     * Side lengths will be increased by 2 times the value of the parameter, since both min and max are changed.
     * <br/>
     * If contracting and the amount to contract by is larger than the length of a side, then the side will wrap (still creating a valid AABB - see samples on {@link #grow(double, double, double)}).
     *
     * @return A modified AABB.
     */
    public AABB inflate(double value) {
        return this.inflate(value, value, value);
    }

    public AABB intersect(AABB other) {
        double d0 = Math.max(this.minX, other.minX);
        double d1 = Math.max(this.minY, other.minY);
        double d2 = Math.max(this.minZ, other.minZ);
        double d3 = Math.min(this.maxX, other.maxX);
        double d4 = Math.min(this.maxY, other.maxY);
        double d5 = Math.min(this.maxZ, other.maxZ);
        return new AABB(d0, d1, d2, d3, d4, d5);
    }

    public AABB minmax(AABB other) {
        double d0 = Math.min(this.minX, other.minX);
        double d1 = Math.min(this.minY, other.minY);
        double d2 = Math.min(this.minZ, other.minZ);
        double d3 = Math.max(this.maxX, other.maxX);
        double d4 = Math.max(this.maxY, other.maxY);
        double d5 = Math.max(this.maxZ, other.maxZ);
        return new AABB(d0, d1, d2, d3, d4, d5);
    }

    /**
     * Offsets the current bounding box by the specified amount.
     */
    public AABB move(double x, double y, double z) {
        return new AABB(this.minX + x, this.minY + y, this.minZ + z, this.maxX + x, this.maxY + y, this.maxZ + z);
    }

    public AABB move(BlockPos pos) {
        return new AABB(
            this.minX + (double)pos.getX(),
            this.minY + (double)pos.getY(),
            this.minZ + (double)pos.getZ(),
            this.maxX + (double)pos.getX(),
            this.maxY + (double)pos.getY(),
            this.maxZ + (double)pos.getZ()
        );
    }

    public AABB move(Vec3 vec) {
        return this.move(vec.x, vec.y, vec.z);
    }

    public AABB move(Vector3f vec) {
        return this.move((double)vec.x, (double)vec.y, (double)vec.z);
    }

    /**
     * Checks if the bounding box intersects with another.
     */
    public boolean intersects(AABB other) {
        return this.intersects(other.minX, other.minY, other.minZ, other.maxX, other.maxY, other.maxZ);
    }

    public boolean intersects(double x1, double y1, double z1, double x2, double y2, double z2) {
        return this.minX < x2 && this.maxX > x1 && this.minY < y2 && this.maxY > y1 && this.minZ < z2 && this.maxZ > z1;
    }

    public boolean intersects(Vec3 min, Vec3 max) {
        return this.intersects(
            Math.min(min.x, max.x),
            Math.min(min.y, max.y),
            Math.min(min.z, max.z),
            Math.max(min.x, max.x),
            Math.max(min.y, max.y),
            Math.max(min.z, max.z)
        );
    }

    /**
     * Returns if the supplied Vec3D is completely inside the bounding box
     */
    public boolean contains(Vec3 vec) {
        return this.contains(vec.x, vec.y, vec.z);
    }

    public boolean contains(double x, double y, double z) {
        return x >= this.minX && x < this.maxX && y >= this.minY && y < this.maxY && z >= this.minZ && z < this.maxZ;
    }

    public double getSize() {
        double d0 = this.getXsize();
        double d1 = this.getYsize();
        double d2 = this.getZsize();
        return (d0 + d1 + d2) / 3.0;
    }

    public double getXsize() {
        return this.maxX - this.minX;
    }

    public double getYsize() {
        return this.maxY - this.minY;
    }

    public double getZsize() {
        return this.maxZ - this.minZ;
    }

    public AABB deflate(double x, double y, double z) {
        return this.inflate(-x, -y, -z);
    }

    /**
     * Creates a new {@link AxisAlignedBB} that is expanded by the given value in all directions. Equivalent to {@link #grow(double)} with value set to the negative of the value provided here. Passing a negative value to this method values will grow the AABB.
     * <br/>
     * Side lengths will be decreased by 2 times the value of the parameter, since both min and max are changed.
     * <br/>
     * If contracting and the amount to contract by is larger than the length of a side, then the side will wrap (still creating a valid AABB - see samples on {@link #grow(double, double, double)}).
     *
     * @return A modified AABB.
     */
    public AABB deflate(double value) {
        return this.inflate(-value);
    }

    public Optional<Vec3> clip(Vec3 from, Vec3 to) {
        double[] adouble = new double[]{1.0};
        double d0 = to.x - from.x;
        double d1 = to.y - from.y;
        double d2 = to.z - from.z;
        Direction direction = getDirection(this, from, adouble, null, d0, d1, d2);
        if (direction == null) {
            return Optional.empty();
        } else {
            double d3 = adouble[0];
            return Optional.of(from.add(d3 * d0, d3 * d1, d3 * d2));
        }
    }

    @Nullable
    public static BlockHitResult clip(Iterable<AABB> boxes, Vec3 start, Vec3 end, BlockPos pos) {
        double[] adouble = new double[]{1.0};
        Direction direction = null;
        double d0 = end.x - start.x;
        double d1 = end.y - start.y;
        double d2 = end.z - start.z;

        for (AABB aabb : boxes) {
            direction = getDirection(aabb.move(pos), start, adouble, direction, d0, d1, d2);
        }

        if (direction == null) {
            return null;
        } else {
            double d3 = adouble[0];
            return new BlockHitResult(start.add(d3 * d0, d3 * d1, d3 * d2), direction, pos, false);
        }
    }

    @Nullable
    private static Direction getDirection(
        AABB aabb, Vec3 start, double[] minDistance, @Nullable Direction facing, double deltaX, double deltaY, double deltaZ
    ) {
        if (deltaX > 1.0E-7) {
            facing = clipPoint(
                minDistance,
                facing,
                deltaX,
                deltaY,
                deltaZ,
                aabb.minX,
                aabb.minY,
                aabb.maxY,
                aabb.minZ,
                aabb.maxZ,
                Direction.WEST,
                start.x,
                start.y,
                start.z
            );
        } else if (deltaX < -1.0E-7) {
            facing = clipPoint(
                minDistance,
                facing,
                deltaX,
                deltaY,
                deltaZ,
                aabb.maxX,
                aabb.minY,
                aabb.maxY,
                aabb.minZ,
                aabb.maxZ,
                Direction.EAST,
                start.x,
                start.y,
                start.z
            );
        }

        if (deltaY > 1.0E-7) {
            facing = clipPoint(
                minDistance,
                facing,
                deltaY,
                deltaZ,
                deltaX,
                aabb.minY,
                aabb.minZ,
                aabb.maxZ,
                aabb.minX,
                aabb.maxX,
                Direction.DOWN,
                start.y,
                start.z,
                start.x
            );
        } else if (deltaY < -1.0E-7) {
            facing = clipPoint(
                minDistance,
                facing,
                deltaY,
                deltaZ,
                deltaX,
                aabb.maxY,
                aabb.minZ,
                aabb.maxZ,
                aabb.minX,
                aabb.maxX,
                Direction.UP,
                start.y,
                start.z,
                start.x
            );
        }

        if (deltaZ > 1.0E-7) {
            facing = clipPoint(
                minDistance,
                facing,
                deltaZ,
                deltaX,
                deltaY,
                aabb.minZ,
                aabb.minX,
                aabb.maxX,
                aabb.minY,
                aabb.maxY,
                Direction.NORTH,
                start.z,
                start.x,
                start.y
            );
        } else if (deltaZ < -1.0E-7) {
            facing = clipPoint(
                minDistance,
                facing,
                deltaZ,
                deltaX,
                deltaY,
                aabb.maxZ,
                aabb.minX,
                aabb.maxX,
                aabb.minY,
                aabb.maxY,
                Direction.SOUTH,
                start.z,
                start.x,
                start.y
            );
        }

        return facing;
    }

    @Nullable
    private static Direction clipPoint(
        double[] minDistance,
        @Nullable Direction prevDirection,
        double distanceSide,
        double distanceOtherA,
        double distanceOtherB,
        double minSide,
        double minOtherA,
        double maxOtherA,
        double minOtherB,
        double maxOtherB,
        Direction hitSide,
        double startSide,
        double startOtherA,
        double startOtherB
    ) {
        double d0 = (minSide - startSide) / distanceSide;
        double d1 = startOtherA + d0 * distanceOtherA;
        double d2 = startOtherB + d0 * distanceOtherB;
        if (0.0 < d0 && d0 < minDistance[0] && minOtherA - 1.0E-7 < d1 && d1 < maxOtherA + 1.0E-7 && minOtherB - 1.0E-7 < d2 && d2 < maxOtherB + 1.0E-7) {
            minDistance[0] = d0;
            return hitSide;
        } else {
            return prevDirection;
        }
    }

    public double distanceToSqr(Vec3 vec) {
        double d0 = Math.max(Math.max(this.minX - vec.x, vec.x - this.maxX), 0.0);
        double d1 = Math.max(Math.max(this.minY - vec.y, vec.y - this.maxY), 0.0);
        double d2 = Math.max(Math.max(this.minZ - vec.z, vec.z - this.maxZ), 0.0);
        return Mth.lengthSquared(d0, d1, d2);
    }

    @Override
    public String toString() {
        return "AABB[" + this.minX + ", " + this.minY + ", " + this.minZ + "] -> [" + this.maxX + ", " + this.maxY + ", " + this.maxZ + "]";
    }

    public boolean hasNaN() {
        return Double.isNaN(this.minX)
            || Double.isNaN(this.minY)
            || Double.isNaN(this.minZ)
            || Double.isNaN(this.maxX)
            || Double.isNaN(this.maxY)
            || Double.isNaN(this.maxZ);
    }

    public Vec3 getCenter() {
        return new Vec3(Mth.lerp(0.5, this.minX, this.maxX), Mth.lerp(0.5, this.minY, this.maxY), Mth.lerp(0.5, this.minZ, this.maxZ));
    }

    public Vec3 getBottomCenter() {
        return new Vec3(Mth.lerp(0.5, this.minX, this.maxX), this.minY, Mth.lerp(0.5, this.minZ, this.maxZ));
    }

    public Vec3 getMinPosition() {
        return new Vec3(this.minX, this.minY, this.minZ);
    }

    public Vec3 getMaxPosition() {
        return new Vec3(this.maxX, this.maxY, this.maxZ);
    }

    public static AABB ofSize(Vec3 center, double xSize, double ySize, double zSize) {
        return new AABB(
            center.x - xSize / 2.0,
            center.y - ySize / 2.0,
            center.z - zSize / 2.0,
            center.x + xSize / 2.0,
            center.y + ySize / 2.0,
            center.z + zSize / 2.0
        );
    }

    /**
     * {@return true if this AABB is infinite in all directions}
     */
    public boolean isInfinite() {
        return this == INFINITE || (Double.isInfinite(this.minX) && Double.isInfinite(this.minY) && Double.isInfinite(this.minZ)
                && Double.isInfinite(this.maxX) && Double.isInfinite(this.maxY) && Double.isInfinite(this.maxZ));
    }
}
