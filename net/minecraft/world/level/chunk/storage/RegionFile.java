package net.minecraft.world.level.chunk.storage;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.logging.LogUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;

/**
 * This class handles a single region (or anvil) file and all files for single chunks at chunk positions for that one region file.
 */
public class RegionFile implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SECTOR_BYTES = 4096;
    @VisibleForTesting
    protected static final int SECTOR_INTS = 1024;
    private static final int CHUNK_HEADER_SIZE = 5;
    private static final int HEADER_OFFSET = 0;
    private static final ByteBuffer PADDING_BUFFER = ByteBuffer.allocateDirect(1);
    private static final String EXTERNAL_FILE_EXTENSION = ".mcc";
    private static final int EXTERNAL_STREAM_FLAG = 128;
    private static final int EXTERNAL_CHUNK_THRESHOLD = 256;
    private static final int CHUNK_NOT_PRESENT = 0;
    final RegionStorageInfo info;
    private final Path path;
    private final FileChannel file;
    private final Path externalFileDir;
    final RegionFileVersion version;
    private final ByteBuffer header = ByteBuffer.allocateDirect(8192);
    private final IntBuffer offsets;
    private final IntBuffer timestamps;
    @VisibleForTesting
    protected final RegionBitmap usedSectors = new RegionBitmap();

    public RegionFile(RegionStorageInfo info, Path path, Path externalFileDir, boolean sync) throws IOException {
        this(info, path, externalFileDir, RegionFileVersion.getSelected(), sync);
    }

    public RegionFile(RegionStorageInfo info, Path path, Path externalFileDir, RegionFileVersion version, boolean sync) throws IOException {
        this.info = info;
        this.path = path;
        this.version = version;
        if (!Files.isDirectory(externalFileDir)) {
            throw new IllegalArgumentException("Expected directory, got " + externalFileDir.toAbsolutePath());
        } else {
            this.externalFileDir = externalFileDir;
            this.offsets = this.header.asIntBuffer();
            this.offsets.limit(1024);
            this.header.position(4096);
            this.timestamps = this.header.asIntBuffer();
            if (sync) {
                this.file = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.DSYNC);
            } else {
                this.file = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
            }

            this.usedSectors.force(0, 2);
            this.header.position(0);
            int i = this.file.read(this.header, 0L);
            if (i != -1) {
                if (i != 8192) {
                    LOGGER.warn("Region file {} has truncated header: {}", path, i);
                }

                long j = Files.size(path);

                for (int k = 0; k < 1024; k++) {
                    int l = this.offsets.get(k);
                    if (l != 0) {
                        int i1 = getSectorNumber(l);
                        int j1 = getNumSectors(l);
                        if (i1 < 2) {
                            LOGGER.warn("Region file {} has invalid sector at index: {}; sector {} overlaps with header", path, k, i1);
                            this.offsets.put(k, 0);
                        } else if (j1 == 0) {
                            LOGGER.warn("Region file {} has an invalid sector at index: {}; size has to be > 0", path, k);
                            this.offsets.put(k, 0);
                        } else if ((long)i1 * 4096L > j) {
                            LOGGER.warn("Region file {} has an invalid sector at index: {}; sector {} is out of bounds", path, k, i1);
                            this.offsets.put(k, 0);
                        } else {
                            this.usedSectors.force(i1, j1);
                        }
                    }
                }
            }
        }
    }

    public Path getPath() {
        return this.path;
    }

    /**
     * Gets the path to store a chunk that can not be stored within the region file because it's larger than 1 MiB.
     */
    private Path getExternalChunkPath(ChunkPos chunkPos) {
        String s = "c." + chunkPos.x + "." + chunkPos.z + ".mcc";
        return this.externalFileDir.resolve(s);
    }

    @Nullable
    public synchronized DataInputStream getChunkDataInputStream(ChunkPos chunkPos) throws IOException {
        int i = this.getOffset(chunkPos);
        if (i == 0) {
            return null;
        } else {
            int j = getSectorNumber(i);
            int k = getNumSectors(i);
            int l = k * 4096;
            ByteBuffer bytebuffer = ByteBuffer.allocate(l);
            this.file.read(bytebuffer, (long)(j * 4096));
            bytebuffer.flip();
            if (bytebuffer.remaining() < 5) {
                LOGGER.error("Chunk {} header is truncated: expected {} but read {}", chunkPos, l, bytebuffer.remaining());
                return null;
            } else {
                int i1 = bytebuffer.getInt();
                byte b0 = bytebuffer.get();
                if (i1 == 0) {
                    LOGGER.warn("Chunk {} is allocated, but stream is missing", chunkPos);
                    return null;
                } else {
                    int j1 = i1 - 1;
                    if (isExternalStreamChunk(b0)) {
                        if (j1 != 0) {
                            LOGGER.warn("Chunk has both internal and external streams");
                        }

                        return this.createExternalChunkInputStream(chunkPos, getExternalChunkVersion(b0));
                    } else if (j1 > bytebuffer.remaining()) {
                        LOGGER.error("Chunk {} stream is truncated: expected {} but read {}", chunkPos, j1, bytebuffer.remaining());
                        return null;
                    } else if (j1 < 0) {
                        LOGGER.error("Declared size {} of chunk {} is negative", i1, chunkPos);
                        return null;
                    } else {
                        JvmProfiler.INSTANCE.onRegionFileRead(this.info, chunkPos, this.version, j1);
                        return this.createChunkInputStream(chunkPos, b0, createStream(bytebuffer, j1));
                    }
                }
            }
        }
    }

    private static int getTimestamp() {
        return (int)(Util.getEpochMillis() / 1000L);
    }

    private static boolean isExternalStreamChunk(byte versionByte) {
        return (versionByte & 128) != 0;
    }

    private static byte getExternalChunkVersion(byte versionByte) {
        return (byte)(versionByte & -129);
    }

    @Nullable
    private DataInputStream createChunkInputStream(ChunkPos chunkPos, byte versionByte, InputStream inputStream) throws IOException {
        RegionFileVersion regionfileversion = RegionFileVersion.fromId(versionByte);
        if (regionfileversion == RegionFileVersion.VERSION_CUSTOM) {
            String s = new DataInputStream(inputStream).readUTF();
            ResourceLocation resourcelocation = ResourceLocation.tryParse(s);
            if (resourcelocation != null) {
                LOGGER.error("Unrecognized custom compression {}", resourcelocation);
                return null;
            } else {
                LOGGER.error("Invalid custom compression id {}", s);
                return null;
            }
        } else if (regionfileversion == null) {
            LOGGER.error("Chunk {} has invalid chunk stream version {}", chunkPos, versionByte);
            return null;
        } else {
            return new DataInputStream(regionfileversion.wrap(inputStream));
        }
    }

    @Nullable
    private DataInputStream createExternalChunkInputStream(ChunkPos chunkPos, byte versionByte) throws IOException {
        Path path = this.getExternalChunkPath(chunkPos);
        if (!Files.isRegularFile(path)) {
            LOGGER.error("External chunk path {} is not file", path);
            return null;
        } else {
            return this.createChunkInputStream(chunkPos, versionByte, Files.newInputStream(path));
        }
    }

    private static ByteArrayInputStream createStream(ByteBuffer sourceBuffer, int length) {
        return new ByteArrayInputStream(sourceBuffer.array(), sourceBuffer.position(), length);
    }

    /**
     * Packs the offset in 4 KiB sectors from the region file start and the amount of 4 KiB sectors used to store a chunk into one {@code int}.
     */
    private int packSectorOffset(int sectorOffset, int sectorCount) {
        return sectorOffset << 8 | sectorCount;
    }

    /**
     * Gets the amount of 4 KiB sectors used to store a chunk.
     */
    private static int getNumSectors(int packedSectorOffset) {
        return packedSectorOffset & 0xFF;
    }

    /**
     * Gets the offset in 4 KiB sectors from the start of the region file, where the data for a chunk starts.
     */
    private static int getSectorNumber(int packedSectorOffset) {
        return packedSectorOffset >> 8 & 16777215;
    }

    /**
     * Gets the amount of sectors required to store chunk data of a certain size in bytes.
     */
    private static int sizeToSectors(int size) {
        return (size + 4096 - 1) / 4096;
    }

    public boolean doesChunkExist(ChunkPos chunkPos) {
        int i = this.getOffset(chunkPos);
        if (i == 0) {
            return false;
        } else {
            int j = getSectorNumber(i);
            int k = getNumSectors(i);
            ByteBuffer bytebuffer = ByteBuffer.allocate(5);

            try {
                this.file.read(bytebuffer, (long)(j * 4096));
                bytebuffer.flip();
                if (bytebuffer.remaining() != 5) {
                    return false;
                } else {
                    int l = bytebuffer.getInt();
                    byte b0 = bytebuffer.get();
                    if (isExternalStreamChunk(b0)) {
                        if (!RegionFileVersion.isValidVersion(getExternalChunkVersion(b0))) {
                            return false;
                        }

                        if (!Files.isRegularFile(this.getExternalChunkPath(chunkPos))) {
                            return false;
                        }
                    } else {
                        if (!RegionFileVersion.isValidVersion(b0)) {
                            return false;
                        }

                        if (l == 0) {
                            return false;
                        }

                        int i1 = l - 1;
                        if (i1 < 0 || i1 > 4096 * k) {
                            return false;
                        }
                    }

                    return true;
                }
            } catch (IOException ioexception) {
                return false;
            }
        }
    }

    /**
     * Creates a new {@link java.io.InputStream} for a chunk stored in a separate file.
     */
    public DataOutputStream getChunkDataOutputStream(ChunkPos chunkPos) throws IOException {
        return new DataOutputStream(this.version.wrap(new RegionFile.ChunkBuffer(chunkPos)));
    }

    public void flush() throws IOException {
        this.file.force(true);
    }

    public void clear(ChunkPos chunkPos) throws IOException {
        int i = getOffsetIndex(chunkPos);
        int j = this.offsets.get(i);
        if (j != 0) {
            this.offsets.put(i, 0);
            this.timestamps.put(i, getTimestamp());
            this.writeHeader();
            Files.deleteIfExists(this.getExternalChunkPath(chunkPos));
            this.usedSectors.free(getSectorNumber(j), getNumSectors(j));
        }
    }

    protected synchronized void write(ChunkPos chunkPos, ByteBuffer chunkData) throws IOException {
        int i = getOffsetIndex(chunkPos);
        int j = this.offsets.get(i);
        int k = getSectorNumber(j);
        int l = getNumSectors(j);
        int i1 = chunkData.remaining();
        int j1 = sizeToSectors(i1);
        int k1;
        RegionFile.CommitOp regionfile$commitop;
        if (j1 >= 256) {
            Path path = this.getExternalChunkPath(chunkPos);
            LOGGER.warn("Saving oversized chunk {} ({} bytes} to external file {}", chunkPos, i1, path);
            j1 = 1;
            k1 = this.usedSectors.allocate(j1);
            regionfile$commitop = this.writeToExternalFile(path, chunkData);
            ByteBuffer bytebuffer = this.createExternalStub();
            this.file.write(bytebuffer, (long)(k1 * 4096));
        } else {
            k1 = this.usedSectors.allocate(j1);
            regionfile$commitop = () -> Files.deleteIfExists(this.getExternalChunkPath(chunkPos));
            this.file.write(chunkData, (long)(k1 * 4096));
        }

        this.offsets.put(i, this.packSectorOffset(k1, j1));
        this.timestamps.put(i, getTimestamp());
        this.writeHeader();
        regionfile$commitop.run();
        if (k != 0) {
            this.usedSectors.free(k, l);
        }
    }

    private ByteBuffer createExternalStub() {
        ByteBuffer bytebuffer = ByteBuffer.allocate(5);
        bytebuffer.putInt(1);
        bytebuffer.put((byte)(this.version.getId() | 128));
        bytebuffer.flip();
        return bytebuffer;
    }

    /**
     * Writes a chunk to a separate file with only that chunk. This is used for chunks larger than 1 MiB
     */
    private RegionFile.CommitOp writeToExternalFile(Path externalChunkFile, ByteBuffer chunkData) throws IOException {
        Path path = Files.createTempFile(this.externalFileDir, "tmp", null);

        try (FileChannel filechannel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            chunkData.position(5);
            filechannel.write(chunkData);
        }

        return () -> Files.move(path, externalChunkFile, StandardCopyOption.REPLACE_EXISTING);
    }

    private void writeHeader() throws IOException {
        this.header.position(0);
        this.file.write(this.header, 0L);
    }

    private int getOffset(ChunkPos chunkPos) {
        return this.offsets.get(getOffsetIndex(chunkPos));
    }

    public boolean hasChunk(ChunkPos chunkPos) {
        return this.getOffset(chunkPos) != 0;
    }

    /**
     * Gets the offset within the region file where the chunk metadata for a chunk can be found.
     */
    private static int getOffsetIndex(ChunkPos chunkPos) {
        return chunkPos.getRegionLocalX() + chunkPos.getRegionLocalZ() * 32;
    }

    @Override
    public void close() throws IOException {
        try {
            this.padToFullSector();
        } finally {
            try {
                this.file.force(true);
            } finally {
                this.file.close();
            }
        }
    }

    private void padToFullSector() throws IOException {
        int i = (int)this.file.size();
        int j = sizeToSectors(i) * 4096;
        if (i != j) {
            ByteBuffer bytebuffer = PADDING_BUFFER.duplicate();
            bytebuffer.position(0);
            this.file.write(bytebuffer, (long)(j - 1));
        }
    }

    class ChunkBuffer extends ByteArrayOutputStream {
        private final ChunkPos pos;

        public ChunkBuffer(ChunkPos pos) {
            super(8096);
            super.write(0);
            super.write(0);
            super.write(0);
            super.write(0);
            super.write(RegionFile.this.version.getId());
            this.pos = pos;
        }

        @Override
        public void close() throws IOException {
            ByteBuffer bytebuffer = ByteBuffer.wrap(this.buf, 0, this.count);
            int i = this.count - 5 + 1;
            JvmProfiler.INSTANCE.onRegionFileWrite(RegionFile.this.info, this.pos, RegionFile.this.version, i);
            bytebuffer.putInt(0, i);
            RegionFile.this.write(this.pos, bytebuffer);
        }
    }

    interface CommitOp {
        void run() throws IOException;
    }
}
