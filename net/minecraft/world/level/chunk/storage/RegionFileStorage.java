package net.minecraft.world.level.chunk.storage;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import javax.annotation.Nullable;
import net.minecraft.FileUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.util.ExceptionCollector;
import net.minecraft.world.level.ChunkPos;

/**
 * Handles reading and writing the {@link net.minecraft.world.level.chunk.storage.RegionFile region files} for a {@link net.minecraft.world.level.Level}.
 */
public final class RegionFileStorage implements AutoCloseable {
    public static final String ANVIL_EXTENSION = ".mca";
    private static final int MAX_CACHE_SIZE = 256;
    private final Long2ObjectLinkedOpenHashMap<RegionFile> regionCache = new Long2ObjectLinkedOpenHashMap<>();
    private final RegionStorageInfo info;
    private final Path folder;
    private final boolean sync;

    RegionFileStorage(RegionStorageInfo info, Path folder, boolean sync) {
        this.folder = folder;
        this.sync = sync;
        this.info = info;
    }

    private RegionFile getRegionFile(ChunkPos chunkPos) throws IOException {
        long i = ChunkPos.asLong(chunkPos.getRegionX(), chunkPos.getRegionZ());
        RegionFile regionfile = this.regionCache.getAndMoveToFirst(i);
        if (regionfile != null) {
            return regionfile;
        } else {
            if (this.regionCache.size() >= 256) {
                this.regionCache.removeLast().close();
            }

            FileUtil.createDirectoriesSafe(this.folder);
            Path path = this.folder.resolve("r." + chunkPos.getRegionX() + "." + chunkPos.getRegionZ() + ".mca");
            RegionFile regionfile1 = new RegionFile(this.info, path, this.folder, this.sync);
            this.regionCache.putAndMoveToFirst(i, regionfile1);
            return regionfile1;
        }
    }

    @Nullable
    public CompoundTag read(ChunkPos chunkPos) throws IOException {
        RegionFile regionfile = this.getRegionFile(chunkPos);

        CompoundTag compoundtag;
        try (DataInputStream datainputstream = regionfile.getChunkDataInputStream(chunkPos)) {
            if (datainputstream == null) {
                return null;
            }

            compoundtag = NbtIo.read(datainputstream);
        }

        return compoundtag;
    }

    public void scanChunk(ChunkPos chunkPos, StreamTagVisitor visitor) throws IOException {
        RegionFile regionfile = this.getRegionFile(chunkPos);

        try (DataInputStream datainputstream = regionfile.getChunkDataInputStream(chunkPos)) {
            if (datainputstream != null) {
                NbtIo.parse(datainputstream, visitor, NbtAccounter.unlimitedHeap());
            }
        }
    }

    protected void write(ChunkPos chunkPos, @Nullable CompoundTag chunkData) throws IOException {
        RegionFile regionfile = this.getRegionFile(chunkPos);
        if (chunkData == null) {
            regionfile.clear(chunkPos);
        } else {
            try (DataOutputStream dataoutputstream = regionfile.getChunkDataOutputStream(chunkPos)) {
                NbtIo.write(chunkData, dataoutputstream);
            }
        }
    }

    @Override
    public void close() throws IOException {
        ExceptionCollector<IOException> exceptioncollector = new ExceptionCollector<>();

        for (RegionFile regionfile : this.regionCache.values()) {
            try {
                regionfile.close();
            } catch (IOException ioexception) {
                exceptioncollector.add(ioexception);
            }
        }

        exceptioncollector.throwIfPresent();
    }

    public void flush() throws IOException {
        for (RegionFile regionfile : this.regionCache.values()) {
            regionfile.flush();
        }
    }

    public RegionStorageInfo info() {
        return this.info;
    }
}
