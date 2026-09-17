package net.minecraft.world.level.chunk.storage;

import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Dynamic;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;

public class SimpleRegionStorage implements AutoCloseable {
    private final IOWorker worker;
    private final DataFixer fixerUpper;
    private final DataFixTypes dataFixType;

    public SimpleRegionStorage(RegionStorageInfo info, Path folder, DataFixer fixerUpper, boolean sync, DataFixTypes dataFixType) {
        this.fixerUpper = fixerUpper;
        this.dataFixType = dataFixType;
        this.worker = new IOWorker(info, folder, sync);
    }

    public CompletableFuture<Optional<CompoundTag>> read(ChunkPos chunkPos) {
        return this.worker.loadAsync(chunkPos);
    }

    public CompletableFuture<Void> write(ChunkPos chunkPos, @Nullable CompoundTag data) {
        return this.worker.store(chunkPos, data);
    }

    public CompoundTag upgradeChunkTag(CompoundTag tag, int version) {
        int i = NbtUtils.getDataVersion(tag, version);
        return this.dataFixType.updateToCurrentVersion(this.fixerUpper, tag, i);
    }

    public Dynamic<Tag> upgradeChunkTag(Dynamic<Tag> tag, int version) {
        return this.dataFixType.updateToCurrentVersion(this.fixerUpper, tag, version);
    }

    public CompletableFuture<Void> synchronize(boolean flushStorage) {
        return this.worker.synchronize(flushStorage);
    }

    @Override
    public void close() throws IOException {
        this.worker.close();
    }

    public RegionStorageInfo storageInfo() {
        return this.worker.storageInfo();
    }
}
