package net.minecraft.core;

import it.unimi.dsi.fastutil.longs.LongConsumer;
import java.util.Spliterators.AbstractSpliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.entity.EntityAccess;

public class SectionPos extends Vec3i {
    public static final int SECTION_BITS = 4;
    public static final int SECTION_SIZE = 16;
    public static final int SECTION_MASK = 15;
    public static final int SECTION_HALF_SIZE = 8;
    public static final int SECTION_MAX_INDEX = 15;
    private static final int PACKED_X_LENGTH = 22;
    private static final int PACKED_Y_LENGTH = 20;
    private static final int PACKED_Z_LENGTH = 22;
    private static final long PACKED_X_MASK = 4194303L;
    private static final long PACKED_Y_MASK = 1048575L;
    private static final long PACKED_Z_MASK = 4194303L;
    private static final int Y_OFFSET = 0;
    private static final int Z_OFFSET = 20;
    private static final int X_OFFSET = 42;
    private static final int RELATIVE_X_SHIFT = 8;
    private static final int RELATIVE_Y_SHIFT = 0;
    private static final int RELATIVE_Z_SHIFT = 4;

    SectionPos(int x, int y, int z) {
        super(x, y, z);
    }

    public static SectionPos of(int chunkX, int chunkY, int chunkZ) {
        return new SectionPos(chunkX, chunkY, chunkZ);
    }

    public static SectionPos of(BlockPos pos) {
        return new SectionPos(blockToSectionCoord(pos.getX()), blockToSectionCoord(pos.getY()), blockToSectionCoord(pos.getZ()));
    }

    public static SectionPos of(ChunkPos chunkPos, int y) {
        return new SectionPos(chunkPos.x, y, chunkPos.z);
    }

    public static SectionPos of(EntityAccess entity) {
        return of(entity.blockPosition());
    }

    public static SectionPos of(Position position) {
        return new SectionPos(blockToSectionCoord(position.x()), blockToSectionCoord(position.y()), blockToSectionCoord(position.z()));
    }

    public static SectionPos of(long packed) {
        return new SectionPos(x(packed), y(packed), z(packed));
    }

    public static SectionPos bottomOf(ChunkAccess chunk) {
        return of(chunk.getPos(), chunk.getMinSection());
    }

    public static long offset(long packed, Direction direction) {
        return offset(packed, direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    public static long offset(long packed, int dx, int dy, int dz) {
        return asLong(x(packed) + dx, y(packed) + dy, z(packed) + dz);
    }

    public static int posToSectionCoord(double pos) {
        return blockToSectionCoord(Mth.floor(pos));
    }

    public static int blockToSectionCoord(int blockCoord) {
        return blockCoord >> 4;
    }

    public static int blockToSectionCoord(double coord) {
        return Mth.floor(coord) >> 4;
    }

    public static int sectionRelative(int rel) {
        return rel & 15;
    }

    public static short sectionRelativePos(BlockPos pos) {
        int i = sectionRelative(pos.getX());
        int j = sectionRelative(pos.getY());
        int k = sectionRelative(pos.getZ());
        return (short)(i << 8 | k << 4 | j << 0);
    }

    public static int sectionRelativeX(short x) {
        return x >>> 8 & 15;
    }

    public static int sectionRelativeY(short y) {
        return y >>> 0 & 15;
    }

    public static int sectionRelativeZ(short z) {
        return z >>> 4 & 15;
    }

    public int relativeToBlockX(short x) {
        return this.minBlockX() + sectionRelativeX(x);
    }

    public int relativeToBlockY(short y) {
        return this.minBlockY() + sectionRelativeY(y);
    }

    public int relativeToBlockZ(short z) {
        return this.minBlockZ() + sectionRelativeZ(z);
    }

    public BlockPos relativeToBlockPos(short pos) {
        return new BlockPos(this.relativeToBlockX(pos), this.relativeToBlockY(pos), this.relativeToBlockZ(pos));
    }

    public static int sectionToBlockCoord(int sectionCoord) {
        return sectionCoord << 4;
    }

    public static int sectionToBlockCoord(int pos, int offset) {
        return sectionToBlockCoord(pos) + offset;
    }

    public static int x(long packed) {
        return (int)(packed << 0 >> 42);
    }

    public static int y(long packed) {
        return (int)(packed << 44 >> 44);
    }

    public static int z(long packed) {
        return (int)(packed << 22 >> 42);
    }

    public int x() {
        return this.getX();
    }

    public int y() {
        return this.getY();
    }

    public int z() {
        return this.getZ();
    }

    public int minBlockX() {
        return sectionToBlockCoord(this.x());
    }

    public int minBlockY() {
        return sectionToBlockCoord(this.y());
    }

    public int minBlockZ() {
        return sectionToBlockCoord(this.z());
    }

    public int maxBlockX() {
        return sectionToBlockCoord(this.x(), 15);
    }

    public int maxBlockY() {
        return sectionToBlockCoord(this.y(), 15);
    }

    public int maxBlockZ() {
        return sectionToBlockCoord(this.z(), 15);
    }

    public static long blockToSection(long levelPos) {
        return asLong(
            blockToSectionCoord(BlockPos.getX(levelPos)), blockToSectionCoord(BlockPos.getY(levelPos)), blockToSectionCoord(BlockPos.getZ(levelPos))
        );
    }

    public static long getZeroNode(int x, int z) {
        return getZeroNode(asLong(x, 0, z));
    }

    public static long getZeroNode(long pos) {
        return pos & -1048576L;
    }

    public BlockPos origin() {
        return new BlockPos(sectionToBlockCoord(this.x()), sectionToBlockCoord(this.y()), sectionToBlockCoord(this.z()));
    }

    public BlockPos center() {
        int i = 8;
        return this.origin().offset(8, 8, 8);
    }

    public ChunkPos chunk() {
        return new ChunkPos(this.x(), this.z());
    }

    public static long asLong(BlockPos blockPos) {
        return asLong(blockToSectionCoord(blockPos.getX()), blockToSectionCoord(blockPos.getY()), blockToSectionCoord(blockPos.getZ()));
    }

    public static long asLong(int x, int y, int z) {
        long i = 0L;
        i |= ((long)x & 4194303L) << 42;
        i |= ((long)y & 1048575L) << 0;
        return i | ((long)z & 4194303L) << 20;
    }

    public long asLong() {
        return asLong(this.x(), this.y(), this.z());
    }

    public SectionPos offset(int dx, int dy, int dz) {
        return dx == 0 && dy == 0 && dz == 0 ? this : new SectionPos(this.x() + dx, this.y() + dy, this.z() + dz);
    }

    public Stream<BlockPos> blocksInside() {
        return BlockPos.betweenClosedStream(this.minBlockX(), this.minBlockY(), this.minBlockZ(), this.maxBlockX(), this.maxBlockY(), this.maxBlockZ());
    }

    public static Stream<SectionPos> cube(SectionPos center, int radius) {
        int i = center.x();
        int j = center.y();
        int k = center.z();
        return betweenClosedStream(i - radius, j - radius, k - radius, i + radius, j + radius, k + radius);
    }

    public static Stream<SectionPos> aroundChunk(ChunkPos chunkPos, int x, int y, int z) {
        int i = chunkPos.x;
        int j = chunkPos.z;
        return betweenClosedStream(i - x, y, j - x, i + x, z - 1, j + x);
    }

    public static Stream<SectionPos> betweenClosedStream(
        final int x1, final int y1, final int z1, final int x2, final int y2, final int z2
    ) {
        return StreamSupport.stream(
            new AbstractSpliterator<SectionPos>((long)((x2 - x1 + 1) * (y2 - y1 + 1) * (z2 - z1 + 1)), 64) {
                final Cursor3D cursor = new Cursor3D(x1, y1, z1, x2, y2, z2);

                @Override
                public boolean tryAdvance(Consumer<? super SectionPos> consumer) {
                    if (this.cursor.advance()) {
                        consumer.accept(new SectionPos(this.cursor.nextX(), this.cursor.nextY(), this.cursor.nextZ()));
                        return true;
                    } else {
                        return false;
                    }
                }
            }, false
        );
    }

    public static void aroundAndAtBlockPos(BlockPos pos, LongConsumer consumer) {
        aroundAndAtBlockPos(pos.getX(), pos.getY(), pos.getZ(), consumer);
    }

    public static void aroundAndAtBlockPos(long pos, LongConsumer consumer) {
        aroundAndAtBlockPos(BlockPos.getX(pos), BlockPos.getY(pos), BlockPos.getZ(pos), consumer);
    }

    public static void aroundAndAtBlockPos(int x, int y, int z, LongConsumer consumer) {
        int i = blockToSectionCoord(x - 1);
        int j = blockToSectionCoord(x + 1);
        int k = blockToSectionCoord(y - 1);
        int l = blockToSectionCoord(y + 1);
        int i1 = blockToSectionCoord(z - 1);
        int j1 = blockToSectionCoord(z + 1);
        if (i == j && k == l && i1 == j1) {
            consumer.accept(asLong(i, k, i1));
        } else {
            for (int k1 = i; k1 <= j; k1++) {
                for (int l1 = k; l1 <= l; l1++) {
                    for (int i2 = i1; i2 <= j1; i2++) {
                        consumer.accept(asLong(k1, l1, i2));
                    }
                }
            }
        }
    }
}
