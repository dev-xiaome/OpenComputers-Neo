package net.minecraft.core;

import com.google.common.collect.AbstractIterator;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.concurrent.Immutable;
import net.minecraft.Util;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;

@Immutable
public class BlockPos extends Vec3i {
    public static final Codec<BlockPos> CODEC = Codec.INT_STREAM
        .<BlockPos>comapFlatMap(
            p_337445_ -> Util.fixedSize(p_337445_, 3).map(p_175270_ -> new BlockPos(p_175270_[0], p_175270_[1], p_175270_[2])),
            p_121924_ -> IntStream.of(p_121924_.getX(), p_121924_.getY(), p_121924_.getZ())
        )
        .stable();
    public static final StreamCodec<ByteBuf, BlockPos> STREAM_CODEC = new StreamCodec<ByteBuf, BlockPos>() {
        public BlockPos decode(ByteBuf p_320431_) {
            return FriendlyByteBuf.readBlockPos(p_320431_);
        }

        public void encode(ByteBuf p_320258_, BlockPos p_320532_) {
            FriendlyByteBuf.writeBlockPos(p_320258_, p_320532_);
        }
    };
    private static final Logger LOGGER = LogUtils.getLogger();
    /**
     * An immutable BlockPos with zero as all coordinates.
     */
    public static final BlockPos ZERO = new BlockPos(0, 0, 0);
    private static final int PACKED_X_LENGTH = 1 + Mth.log2(Mth.smallestEncompassingPowerOfTwo(30000000));
    private static final int PACKED_Z_LENGTH = PACKED_X_LENGTH;
    public static final int PACKED_Y_LENGTH = 64 - PACKED_X_LENGTH - PACKED_Z_LENGTH;
    private static final long PACKED_X_MASK = (1L << PACKED_X_LENGTH) - 1L;
    private static final long PACKED_Y_MASK = (1L << PACKED_Y_LENGTH) - 1L;
    private static final long PACKED_Z_MASK = (1L << PACKED_Z_LENGTH) - 1L;
    private static final int Y_OFFSET = 0;
    private static final int Z_OFFSET = PACKED_Y_LENGTH;
    private static final int X_OFFSET = PACKED_Y_LENGTH + PACKED_Z_LENGTH;

    public BlockPos(int x, int y, int z) {
        super(x, y, z);
    }

    public BlockPos(Vec3i vector) {
        this(vector.getX(), vector.getY(), vector.getZ());
    }

    public static long offset(long pos, Direction direction) {
        return offset(pos, direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    public static long offset(long pos, int dx, int dy, int dz) {
        return asLong(getX(pos) + dx, getY(pos) + dy, getZ(pos) + dz);
    }

    public static int getX(long packedPos) {
        return (int)(packedPos << 64 - X_OFFSET - PACKED_X_LENGTH >> 64 - PACKED_X_LENGTH);
    }

    public static int getY(long packedPos) {
        return (int)(packedPos << 64 - PACKED_Y_LENGTH >> 64 - PACKED_Y_LENGTH);
    }

    public static int getZ(long packedPos) {
        return (int)(packedPos << 64 - Z_OFFSET - PACKED_Z_LENGTH >> 64 - PACKED_Z_LENGTH);
    }

    public static BlockPos of(long packedPos) {
        return new BlockPos(getX(packedPos), getY(packedPos), getZ(packedPos));
    }

    public static BlockPos containing(double x, double y, double z) {
        return new BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));
    }

    public static BlockPos containing(Position position) {
        return containing(position.x(), position.y(), position.z());
    }

    public static BlockPos min(BlockPos pos1, BlockPos pos2) {
        return new BlockPos(
            Math.min(pos1.getX(), pos2.getX()), Math.min(pos1.getY(), pos2.getY()), Math.min(pos1.getZ(), pos2.getZ())
        );
    }

    public static BlockPos max(BlockPos pos1, BlockPos pos2) {
        return new BlockPos(
            Math.max(pos1.getX(), pos2.getX()), Math.max(pos1.getY(), pos2.getY()), Math.max(pos1.getZ(), pos2.getZ())
        );
    }

    public long asLong() {
        return asLong(this.getX(), this.getY(), this.getZ());
    }

    public static long asLong(int x, int y, int z) {
        long i = 0L;
        i |= ((long)x & PACKED_X_MASK) << X_OFFSET;
        i |= ((long)y & PACKED_Y_MASK) << 0;
        return i | ((long)z & PACKED_Z_MASK) << Z_OFFSET;
    }

    public static long getFlatIndex(long packedPos) {
        return packedPos & -16L;
    }

    public BlockPos offset(int dx, int dy, int dz) {
        return dx == 0 && dy == 0 && dz == 0
            ? this
            : new BlockPos(this.getX() + dx, this.getY() + dy, this.getZ() + dz);
    }

    public Vec3 getCenter() {
        return Vec3.atCenterOf(this);
    }

    public Vec3 getBottomCenter() {
        return Vec3.atBottomCenterOf(this);
    }

    public BlockPos offset(Vec3i vector) {
        return this.offset(vector.getX(), vector.getY(), vector.getZ());
    }

    public BlockPos subtract(Vec3i vector) {
        return this.offset(-vector.getX(), -vector.getY(), -vector.getZ());
    }

    public BlockPos multiply(int scalar) {
        if (scalar == 1) {
            return this;
        } else {
            return scalar == 0 ? ZERO : new BlockPos(this.getX() * scalar, this.getY() * scalar, this.getZ() * scalar);
        }
    }

    public BlockPos above() {
        return this.relative(Direction.UP);
    }

    /**
     * Offset this vector upwards by the given distance.
     */
    public BlockPos above(int distance) {
        return this.relative(Direction.UP, distance);
    }

    public BlockPos below() {
        return this.relative(Direction.DOWN);
    }

    /**
     * Offset this vector downwards by the given distance.
     */
    public BlockPos below(int distance) {
        return this.relative(Direction.DOWN, distance);
    }

    public BlockPos north() {
        return this.relative(Direction.NORTH);
    }

    public BlockPos north(int distance) {
        return this.relative(Direction.NORTH, distance);
    }

    public BlockPos south() {
        return this.relative(Direction.SOUTH);
    }

    public BlockPos south(int distance) {
        return this.relative(Direction.SOUTH, distance);
    }

    public BlockPos west() {
        return this.relative(Direction.WEST);
    }

    public BlockPos west(int distance) {
        return this.relative(Direction.WEST, distance);
    }

    public BlockPos east() {
        return this.relative(Direction.EAST);
    }

    public BlockPos east(int distance) {
        return this.relative(Direction.EAST, distance);
    }

    public BlockPos relative(Direction direction) {
        return new BlockPos(this.getX() + direction.getStepX(), this.getY() + direction.getStepY(), this.getZ() + direction.getStepZ());
    }

    /**
     * Offsets this Vector by the given distance in the specified direction.
     */
    public BlockPos relative(Direction direction, int distance) {
        return distance == 0
            ? this
            : new BlockPos(
                this.getX() + direction.getStepX() * distance, this.getY() + direction.getStepY() * distance, this.getZ() + direction.getStepZ() * distance
            );
    }

    public BlockPos relative(Direction.Axis axis, int amount) {
        if (amount == 0) {
            return this;
        } else {
            int i = axis == Direction.Axis.X ? amount : 0;
            int j = axis == Direction.Axis.Y ? amount : 0;
            int k = axis == Direction.Axis.Z ? amount : 0;
            return new BlockPos(this.getX() + i, this.getY() + j, this.getZ() + k);
        }
    }

    public BlockPos rotate(Rotation rotation) {
        switch (rotation) {
            case NONE:
            default:
                return this;
            case CLOCKWISE_90:
                return new BlockPos(-this.getZ(), this.getY(), this.getX());
            case CLOCKWISE_180:
                return new BlockPos(-this.getX(), this.getY(), -this.getZ());
            case COUNTERCLOCKWISE_90:
                return new BlockPos(this.getZ(), this.getY(), -this.getX());
        }
    }

    /**
     * Calculate the cross product of this and the given Vector
     */
    public BlockPos cross(Vec3i vector) {
        return new BlockPos(
            this.getY() * vector.getZ() - this.getZ() * vector.getY(),
            this.getZ() * vector.getX() - this.getX() * vector.getZ(),
            this.getX() * vector.getY() - this.getY() * vector.getX()
        );
    }

    public BlockPos atY(int y) {
        return new BlockPos(this.getX(), y, this.getZ());
    }

    public BlockPos immutable() {
        return this;
    }

    public BlockPos.MutableBlockPos mutable() {
        return new BlockPos.MutableBlockPos(this.getX(), this.getY(), this.getZ());
    }

    public Vec3 clampLocationWithin(Vec3 pos) {
        return new Vec3(
            Mth.clamp(pos.x, (double)((float)this.getX() + 1.0E-5F), (double)this.getX() + 1.0 - 1.0E-5F),
            Mth.clamp(pos.y, (double)((float)this.getY() + 1.0E-5F), (double)this.getY() + 1.0 - 1.0E-5F),
            Mth.clamp(pos.z, (double)((float)this.getZ() + 1.0E-5F), (double)this.getZ() + 1.0 - 1.0E-5F)
        );
    }

    public static Iterable<BlockPos> randomInCube(RandomSource random, int amount, BlockPos center, int radius) {
        return randomBetweenClosed(
            random,
            amount,
            center.getX() - radius,
            center.getY() - radius,
            center.getZ() - radius,
            center.getX() + radius,
            center.getY() + radius,
            center.getZ() + radius
        );
    }

    @Deprecated
    public static Stream<BlockPos> squareOutSouthEast(BlockPos pos) {
        return Stream.of(pos, pos.south(), pos.east(), pos.south().east());
    }

    public static Iterable<BlockPos> randomBetweenClosed(
        RandomSource random, int amount, int minX, int minY, int minZ, int maxX, int maxY, int maxZ
    ) {
        int i = maxX - minX + 1;
        int j = maxY - minY + 1;
        int k = maxZ - minZ + 1;
        return () -> new AbstractIterator<BlockPos>() {
                final BlockPos.MutableBlockPos nextPos = new BlockPos.MutableBlockPos();
                int counter = amount;

                protected BlockPos computeNext() {
                    if (this.counter <= 0) {
                        return this.endOfData();
                    } else {
                        BlockPos blockpos = this.nextPos
                            .set(minX + random.nextInt(i), minY + random.nextInt(j), minZ + random.nextInt(k));
                        this.counter--;
                        return blockpos;
                    }
                }
            };
    }

    public static Iterable<BlockPos> withinManhattan(BlockPos pos, int xSize, int ySize, int zSize) {
        int i = xSize + ySize + zSize;
        int j = pos.getX();
        int k = pos.getY();
        int l = pos.getZ();
        return () -> new AbstractIterator<BlockPos>() {
                private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
                private int currentDepth;
                private int maxX;
                private int maxY;
                private int x;
                private int y;
                private boolean zMirror;

                protected BlockPos computeNext() {
                    if (this.zMirror) {
                        this.zMirror = false;
                        this.cursor.setZ(l - (this.cursor.getZ() - l));
                        return this.cursor;
                    } else {
                        BlockPos blockpos;
                        for (blockpos = null; blockpos == null; this.y++) {
                            if (this.y > this.maxY) {
                                this.x++;
                                if (this.x > this.maxX) {
                                    this.currentDepth++;
                                    if (this.currentDepth > i) {
                                        return this.endOfData();
                                    }

                                    this.maxX = Math.min(xSize, this.currentDepth);
                                    this.x = -this.maxX;
                                }

                                this.maxY = Math.min(ySize, this.currentDepth - Math.abs(this.x));
                                this.y = -this.maxY;
                            }

                            int i1 = this.x;
                            int j1 = this.y;
                            int k1 = this.currentDepth - Math.abs(i1) - Math.abs(j1);
                            if (k1 <= zSize) {
                                this.zMirror = k1 != 0;
                                blockpos = this.cursor.set(j + i1, k + j1, l + k1);
                            }
                        }

                        return blockpos;
                    }
                }
            };
    }

    public static Optional<BlockPos> findClosestMatch(BlockPos pos, int width, int height, Predicate<BlockPos> posFilter) {
        for (BlockPos blockpos : withinManhattan(pos, width, height, width)) {
            if (posFilter.test(blockpos)) {
                return Optional.of(blockpos);
            }
        }

        return Optional.empty();
    }

    /**
     * Returns a stream of positions in a box shape, ordered by closest to furthest. Returns by definition the given position as first element in the stream.
     */
    public static Stream<BlockPos> withinManhattanStream(BlockPos pos, int xSize, int ySize, int zSize) {
        return StreamSupport.stream(withinManhattan(pos, xSize, ySize, zSize).spliterator(), false);
    }

    public static Iterable<BlockPos> betweenClosed(BlockPos firstPos, BlockPos secondPos) {
        return betweenClosed(
            Math.min(firstPos.getX(), secondPos.getX()),
            Math.min(firstPos.getY(), secondPos.getY()),
            Math.min(firstPos.getZ(), secondPos.getZ()),
            Math.max(firstPos.getX(), secondPos.getX()),
            Math.max(firstPos.getY(), secondPos.getY()),
            Math.max(firstPos.getZ(), secondPos.getZ())
        );
    }

    public static Stream<BlockPos> betweenClosedStream(BlockPos firstPos, BlockPos secondPos) {
        return StreamSupport.stream(betweenClosed(firstPos, secondPos).spliterator(), false);
    }

    public static Stream<BlockPos> betweenClosedStream(BoundingBox box) {
        return betweenClosedStream(
            Math.min(box.minX(), box.maxX()),
            Math.min(box.minY(), box.maxY()),
            Math.min(box.minZ(), box.maxZ()),
            Math.max(box.minX(), box.maxX()),
            Math.max(box.minY(), box.maxY()),
            Math.max(box.minZ(), box.maxZ())
        );
    }

    public static Stream<BlockPos> betweenClosedStream(AABB aabb) {
        return betweenClosedStream(
            Mth.floor(aabb.minX),
            Mth.floor(aabb.minY),
            Mth.floor(aabb.minZ),
            Mth.floor(aabb.maxX),
            Mth.floor(aabb.maxY),
            Mth.floor(aabb.maxZ)
        );
    }

    public static Stream<BlockPos> betweenClosedStream(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return StreamSupport.stream(betweenClosed(minX, minY, minZ, maxX, maxY, maxZ).spliterator(), false);
    }

    /**
     * Creates an Iterable that returns all positions in the box specified by the given corners. <strong>Coordinates must be in order</strong>. e.g. x1 <= x2.
     *
     * This method uses {@link BlockPos.MutableBlockPos MutableBlockPos} instead of regular BlockPos, which grants better performance. However, the resulting BlockPos instances can only be used inside the iteration loop (as otherwise the value will change), unless {@link #toImmutable()} is called. This method is ideal for searching large areas and only storing a few locations.
     *
     * @see #betweenClosed(BlockPos, BlockPos)
     * @see #betweenClosed(int, int, int, int, int, int)
     */
    public static Iterable<BlockPos> betweenClosed(int x1, int y1, int z1, int x2, int y2, int z2) {
        int i = x2 - x1 + 1;
        int j = y2 - y1 + 1;
        int k = z2 - z1 + 1;
        int l = i * j * k;
        return () -> new AbstractIterator<BlockPos>() {
                private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
                private int index;

                protected BlockPos computeNext() {
                    if (this.index == l) {
                        return this.endOfData();
                    } else {
                        int i1 = this.index % i;
                        int j1 = this.index / i;
                        int k1 = j1 % j;
                        int l1 = j1 / j;
                        this.index++;
                        return this.cursor.set(x1 + i1, y1 + k1, z1 + l1);
                    }
                }
            };
    }

    public static Iterable<BlockPos.MutableBlockPos> spiralAround(BlockPos center, int size, Direction rotationDirection, Direction expansionDirection) {
        Validate.validState(rotationDirection.getAxis() != expansionDirection.getAxis(), "The two directions cannot be on the same axis");
        return () -> new AbstractIterator<BlockPos.MutableBlockPos>() {
                private final Direction[] directions = new Direction[]{rotationDirection, expansionDirection, rotationDirection.getOpposite(), expansionDirection.getOpposite()};
                private final BlockPos.MutableBlockPos cursor = center.mutable().move(expansionDirection);
                private final int legs = 4 * size;
                private int leg = -1;
                private int legSize;
                private int legIndex;
                private int lastX = this.cursor.getX();
                private int lastY = this.cursor.getY();
                private int lastZ = this.cursor.getZ();

                protected BlockPos.MutableBlockPos computeNext() {
                    this.cursor.set(this.lastX, this.lastY, this.lastZ).move(this.directions[(this.leg + 4) % 4]);
                    this.lastX = this.cursor.getX();
                    this.lastY = this.cursor.getY();
                    this.lastZ = this.cursor.getZ();
                    if (this.legIndex >= this.legSize) {
                        if (this.leg >= this.legs) {
                            return this.endOfData();
                        }

                        this.leg++;
                        this.legIndex = 0;
                        this.legSize = this.leg / 2 + 1;
                    }

                    this.legIndex++;
                    return this.cursor;
                }
            };
    }

    public static int breadthFirstTraversal(
        BlockPos startPos, int depth, int visitLimit, BiConsumer<BlockPos, Consumer<BlockPos>> action, Predicate<BlockPos> predicate
    ) {
        Queue<Pair<BlockPos, Integer>> queue = new ArrayDeque<>();
        LongSet longset = new LongOpenHashSet();
        queue.add(Pair.of(startPos, 0));
        int i = 0;

        while (!queue.isEmpty()) {
            Pair<BlockPos, Integer> pair = queue.poll();
            BlockPos blockpos = pair.getLeft();
            int j = pair.getRight();
            long k = blockpos.asLong();
            if (longset.add(k) && predicate.test(blockpos)) {
                if (++i >= visitLimit) {
                    return i;
                }

                if (j < depth) {
                    action.accept(blockpos, p_277234_ -> queue.add(Pair.of(p_277234_, j + 1)));
                }
            }
        }

        return i;
    }

    public static class MutableBlockPos extends BlockPos {
        public MutableBlockPos() {
            this(0, 0, 0);
        }

        public MutableBlockPos(int x, int y, int z) {
            super(x, y, z);
        }

        public MutableBlockPos(double x, double y, double z) {
            this(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        }

        @Override
        public BlockPos offset(int dx, int dy, int dz) {
            return super.offset(dx, dy, dz).immutable();
        }

        @Override
        public BlockPos multiply(int scalar) {
            return super.multiply(scalar).immutable();
        }

        /**
         * Offsets this Vector by the given distance in the specified direction.
         */
        @Override
        public BlockPos relative(Direction direction, int distance) {
            return super.relative(direction, distance).immutable();
        }

        @Override
        public BlockPos relative(Direction.Axis axis, int amount) {
            return super.relative(axis, amount).immutable();
        }

        @Override
        public BlockPos rotate(Rotation rotation) {
            return super.rotate(rotation).immutable();
        }

        public BlockPos.MutableBlockPos set(int x, int y, int z) {
            this.setX(x);
            this.setY(y);
            this.setZ(z);
            return this;
        }

        public BlockPos.MutableBlockPos set(double x, double y, double z) {
            return this.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        }

        public BlockPos.MutableBlockPos set(Vec3i vector) {
            return this.set(vector.getX(), vector.getY(), vector.getZ());
        }

        public BlockPos.MutableBlockPos set(long packedPos) {
            return this.set(getX(packedPos), getY(packedPos), getZ(packedPos));
        }

        public BlockPos.MutableBlockPos set(AxisCycle cycle, int x, int y, int z) {
            return this.set(
                cycle.cycle(x, y, z, Direction.Axis.X),
                cycle.cycle(x, y, z, Direction.Axis.Y),
                cycle.cycle(x, y, z, Direction.Axis.Z)
            );
        }

        public BlockPos.MutableBlockPos setWithOffset(Vec3i pos, Direction direction) {
            return this.set(pos.getX() + direction.getStepX(), pos.getY() + direction.getStepY(), pos.getZ() + direction.getStepZ());
        }

        public BlockPos.MutableBlockPos setWithOffset(Vec3i vector, int offsetX, int offsetY, int offsetZ) {
            return this.set(vector.getX() + offsetX, vector.getY() + offsetY, vector.getZ() + offsetZ);
        }

        public BlockPos.MutableBlockPos setWithOffset(Vec3i pos, Vec3i offset) {
            return this.set(pos.getX() + offset.getX(), pos.getY() + offset.getY(), pos.getZ() + offset.getZ());
        }

        public BlockPos.MutableBlockPos move(Direction direction) {
            return this.move(direction, 1);
        }

        public BlockPos.MutableBlockPos move(Direction direction, int n) {
            return this.set(
                this.getX() + direction.getStepX() * n, this.getY() + direction.getStepY() * n, this.getZ() + direction.getStepZ() * n
            );
        }

        public BlockPos.MutableBlockPos move(int x, int y, int z) {
            return this.set(this.getX() + x, this.getY() + y, this.getZ() + z);
        }

        public BlockPos.MutableBlockPos move(Vec3i offset) {
            return this.set(this.getX() + offset.getX(), this.getY() + offset.getY(), this.getZ() + offset.getZ());
        }

        public BlockPos.MutableBlockPos clamp(Direction.Axis axis, int min, int max) {
            switch (axis) {
                case X:
                    return this.set(Mth.clamp(this.getX(), min, max), this.getY(), this.getZ());
                case Y:
                    return this.set(this.getX(), Mth.clamp(this.getY(), min, max), this.getZ());
                case Z:
                    return this.set(this.getX(), this.getY(), Mth.clamp(this.getZ(), min, max));
                default:
                    throw new IllegalStateException("Unable to clamp axis " + axis);
            }
        }

        public BlockPos.MutableBlockPos setX(int x) {
            super.setX(x);
            return this;
        }

        public BlockPos.MutableBlockPos setY(int y) {
            super.setY(y);
            return this;
        }

        public BlockPos.MutableBlockPos setZ(int z) {
            super.setZ(z);
            return this;
        }

        @Override
        public BlockPos immutable() {
            return new BlockPos(this);
        }
    }
}
