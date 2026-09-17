package net.minecraft.world.level.chunk.storage;

import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.MapCodec;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.LegacyStructureDataHandler;
import net.minecraft.world.level.storage.DimensionDataStorage;

public class ChunkStorage implements AutoCloseable {
    public static final int LAST_MONOLYTH_STRUCTURE_DATA_VERSION = 1493;
    private final IOWorker worker;
    protected final DataFixer fixerUpper;
    @Nullable
    private volatile LegacyStructureDataHandler legacyStructureHandler;

    public ChunkStorage(RegionStorageInfo info, Path folder, DataFixer fixerUpper, boolean sync) {
        this.fixerUpper = fixerUpper;
        this.worker = new IOWorker(info, folder, sync);
    }

    public boolean isOldChunkAround(ChunkPos pos, int radius) {
        return this.worker.isOldChunkAround(pos, radius);
    }

    public CompoundTag upgradeChunkTag(
        ResourceKey<Level> levelKey,
        Supplier<DimensionDataStorage> storage,
        CompoundTag chunkData,
        Optional<ResourceKey<MapCodec<? extends ChunkGenerator>>> chunkGeneratorKey
    ) {
        int i = getVersion(chunkData);
        if (i == SharedConstants.getCurrentVersion().getDataVersion().getVersion()) {
            return chunkData;
        } else {
            try {
                if (i < 1493) {
                    chunkData = DataFixTypes.CHUNK.update(this.fixerUpper, chunkData, i, 1493);
                    if (chunkData.getCompound("Level").getBoolean("hasLegacyStructureData")) {
                        LegacyStructureDataHandler legacystructuredatahandler = this.getLegacyStructureHandler(levelKey, storage);
                        chunkData = legacystructuredatahandler.updateFromLegacy(chunkData);
                    }
                }

                injectDatafixingContext(chunkData, levelKey, chunkGeneratorKey);
                chunkData = DataFixTypes.CHUNK.updateToCurrentVersion(this.fixerUpper, chunkData, Math.max(1493, i));
                removeDatafixingContext(chunkData);
                NbtUtils.addCurrentDataVersion(chunkData);
                return chunkData;
            } catch (Exception exception) {
                CrashReport crashreport = CrashReport.forThrowable(exception, "Updated chunk");
                CrashReportCategory crashreportcategory = crashreport.addCategory("Updated chunk details");
                crashreportcategory.setDetail("Data version", i);
                throw new ReportedException(crashreport);
            }
        }
    }

    private LegacyStructureDataHandler getLegacyStructureHandler(ResourceKey<Level> level, Supplier<DimensionDataStorage> storage) {
        LegacyStructureDataHandler legacystructuredatahandler = this.legacyStructureHandler;
        if (legacystructuredatahandler == null) {
            synchronized (this) {
                legacystructuredatahandler = this.legacyStructureHandler;
                if (legacystructuredatahandler == null) {
                    this.legacyStructureHandler = legacystructuredatahandler = LegacyStructureDataHandler.getLegacyStructureHandler(level, storage.get());
                }
            }
        }

        return legacystructuredatahandler;
    }

    public static void injectDatafixingContext(
        CompoundTag chunkData, ResourceKey<Level> levelKey, Optional<ResourceKey<MapCodec<? extends ChunkGenerator>>> chunkGeneratorKey
    ) {
        CompoundTag compoundtag = new CompoundTag();
        compoundtag.putString("dimension", levelKey.location().toString());
        chunkGeneratorKey.ifPresent(p_196917_ -> compoundtag.putString("generator", p_196917_.location().toString()));
        chunkData.put("__context", compoundtag);
    }

    private static void removeDatafixingContext(CompoundTag tag) {
        tag.remove("__context");
    }

    public static int getVersion(CompoundTag chunkData) {
        return NbtUtils.getDataVersion(chunkData, -1);
    }

    public CompletableFuture<Optional<CompoundTag>> read(ChunkPos chunkPos) {
        return this.worker.loadAsync(chunkPos);
    }

    public CompletableFuture<Void> write(ChunkPos chunkPos, CompoundTag data) {
        this.handleLegacyStructureIndex(chunkPos);
        return this.worker.store(chunkPos, data);
    }

    protected void handleLegacyStructureIndex(ChunkPos chunkPos) {
        if (this.legacyStructureHandler != null) {
            this.legacyStructureHandler.removeIndex(chunkPos.toLong());
        }
    }

    public void flushWorker() {
        this.worker.synchronize(true).join();
    }

    @Override
    public void close() throws IOException {
        this.worker.close();
    }

    public ChunkScanAccess chunkScanner() {
        return this.worker;
    }

    protected RegionStorageInfo storageInfo() {
        return this.worker.storageInfo();
    }
}
