package net.minecraft.world.level;

import java.util.Spliterators.AbstractSpliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

public class ChunkPos {
    private static final int SAFETY_MARGIN = 1056;
    /**
     * Value representing an absent or invalid chunkpos
     */
    public static final long INVALID_CHUNK_POS = asLong(1875066, 1875066);
    public static final ChunkPos ZERO = new ChunkPos(0, 0);
    private static final long COORD_BITS = 32L;
    private static final long COORD_MASK = 4294967295L;
    private static final int REGION_BITS = 5;
    public static final int REGION_SIZE = 32;
    private static final int REGION_MASK = 31;
    public static final int REGION_MAX_INDEX = 31;
    public final int x;
    public final int z;
    private static final int HASH_A = 1664525;
    private static final int HASH_C = 1013904223;
    private static final int HASH_Z_XOR = -559038737;

    public ChunkPos(int x, int y) {
        this.x = x;
        this.z = y;
    }

    public ChunkPos(BlockPos pos) {
        this.x = SectionPos.blockToSectionCoord(pos.getX());
        this.z = SectionPos.blockToSectionCoord(pos.getZ());
    }

    public ChunkPos(long packedPos) {
        this.x = (int)packedPos;
        this.z = (int)(packedPos >> 32);
    }

    public static ChunkPos minFromRegion(int chunkX, int chunkZ) {
        return new ChunkPos(chunkX << 5, chunkZ << 5);
    }

    public static ChunkPos maxFromRegion(int chunkX, int chunkZ) {
        return new ChunkPos((chunkX << 5) + 31, (chunkZ << 5) + 31);
    }

    public long toLong() {
        return asLong(this.x, this.z);
    }

    /**
     * Converts the chunk coordinate pair to a long
     */
    public static long asLong(int x, int z) {
        return (long)x & 4294967295L | ((long)z & 4294967295L) << 32;
    }

    public static long asLong(BlockPos pos) {
        return asLong(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    public static int getX(long chunkAsLong) {
        return (int)(chunkAsLong & 4294967295L);
    }

    public static int getZ(long chunkAsLong) {
        return (int)(chunkAsLong >>> 32 & 4294967295L);
    }

    @Override
    public int hashCode() {
        return hash(this.x, this.z);
    }

    public static int hash(int x, int z) {
        int i = 1664525 * x + 1013904223;
        int j = 1664525 * (z ^ -559038737) + 1013904223;
        return i ^ j;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else {
            return !(other instanceof ChunkPos chunkpos) ? false : this.x == chunkpos.x && this.z == chunkpos.z;
        }
    }

    public int getMiddleBlockX() {
        return this.getBlockX(8);
    }

    public int getMiddleBlockZ() {
        return this.getBlockZ(8);
    }

    public int getMinBlockX() {
        return SectionPos.sectionToBlockCoord(this.x);
    }

    public int getMinBlockZ() {
        return SectionPos.sectionToBlockCoord(this.z);
    }

    public int getMaxBlockX() {
        return this.getBlockX(15);
    }

    public int getMaxBlockZ() {
        return this.getBlockZ(15);
    }

    public int getRegionX() {
        return this.x >> 5;
    }

    public int getRegionZ() {
        return this.z >> 5;
    }

    public int getRegionLocalX() {
        return this.x & 31;
    }

    public int getRegionLocalZ() {
        return this.z & 31;
    }

    public BlockPos getBlockAt(int xSection, int y, int zSection) {
        return new BlockPos(this.getBlockX(xSection), y, this.getBlockZ(zSection));
    }

    public int getBlockX(int x) {
        return SectionPos.sectionToBlockCoord(this.x, x);
    }

    public int getBlockZ(int z) {
        return SectionPos.sectionToBlockCoord(this.z, z);
    }

    public BlockPos getMiddleBlockPosition(int y) {
        return new BlockPos(this.getMiddleBlockX(), y, this.getMiddleBlockZ());
    }

    @Override
    public String toString() {
        return "[" + this.x + ", " + this.z + "]";
    }

    public BlockPos getWorldPosition() {
        return new BlockPos(this.getMinBlockX(), 0, this.getMinBlockZ());
    }

    public int getChessboardDistance(ChunkPos chunkPos) {
        return this.getChessboardDistance(chunkPos.x, chunkPos.z);
    }

    public int getChessboardDistance(int x, int z) {
        return Math.max(Math.abs(this.x - x), Math.abs(this.z - z));
    }

    public int distanceSquared(ChunkPos chunkPos) {
        return this.distanceSquared(chunkPos.x, chunkPos.z);
    }

    public int distanceSquared(long packedPos) {
        return this.distanceSquared(getX(packedPos), getZ(packedPos));
    }

    private int distanceSquared(int x, int z) {
        int i = x - this.x;
        int j = z - this.z;
        return i * i + j * j;
    }

    public static Stream<ChunkPos> rangeClosed(ChunkPos center, int radius) {
        return rangeClosed(new ChunkPos(center.x - radius, center.z - radius), new ChunkPos(center.x + radius, center.z + radius));
    }

    public static Stream<ChunkPos> rangeClosed(final ChunkPos start, final ChunkPos end) {
        int i = Math.abs(start.x - end.x) + 1;
        int j = Math.abs(start.z - end.z) + 1;
        final int k = start.x < end.x ? 1 : -1;
        final int l = start.z < end.z ? 1 : -1;
        return StreamSupport.stream(new AbstractSpliterator<ChunkPos>((long)(i * j), 64) {
            @Nullable
            private ChunkPos pos;

            @Override
            public boolean tryAdvance(Consumer<? super ChunkPos> consumer) {
                if (this.pos == null) {
                    this.pos = start;
                } else {
                    int i1 = this.pos.x;
                    int j1 = this.pos.z;
                    if (i1 == end.x) {
                        if (j1 == end.z) {
                            return false;
                        }

                        this.pos = new ChunkPos(start.x, j1 + l);
                    } else {
                        this.pos = new ChunkPos(i1 + k, j1);
                    }
                }

                consumer.accept(this.pos);
                return true;
            }
        }, false);
    }
}
