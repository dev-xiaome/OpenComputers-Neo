package net.minecraft.world.level.storage;

import com.google.common.collect.Maps;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.Map;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.FastBufferedInputStream;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

public class DimensionDataStorage {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<String, SavedData> cache = Maps.newHashMap();
    private final DataFixer fixerUpper;
    private final HolderLookup.Provider registries;
    private final File dataFolder;

    public DimensionDataStorage(File dataFolder, DataFixer fixerUpper, HolderLookup.Provider registries) {
        this.fixerUpper = fixerUpper;
        this.dataFolder = dataFolder;
        this.registries = registries;
    }

    private File getDataFile(String name) {
        return new File(this.dataFolder, name + ".dat");
    }

    public <T extends SavedData> T computeIfAbsent(SavedData.Factory<T> factory, String name) {
        T t = this.get(factory, name);
        if (t != null) {
            return t;
        } else {
            T t1 = (T)factory.constructor().get();
            this.set(name, t1);
            return t1;
        }
    }

    @Nullable
    public <T extends SavedData> T get(SavedData.Factory<T> factory, String name) {
        SavedData saveddata = this.cache.get(name);
        if (saveddata == null && !this.cache.containsKey(name)) {
            saveddata = this.readSavedData(factory.deserializer(), factory.type(), name);
            this.cache.put(name, saveddata);
        }

        return (T)saveddata;
    }

    @Nullable
    private <T extends SavedData> T readSavedData(BiFunction<CompoundTag, HolderLookup.Provider, T> reader, @Nullable DataFixTypes dataFixType, String filename) {
        try {
            File file1 = this.getDataFile(filename);
            if (file1.exists()) {
                CompoundTag compoundtag = this.readTagFromDisk(filename, dataFixType, SharedConstants.getCurrentVersion().getDataVersion().getVersion());
                return reader.apply(compoundtag.getCompound("data"), this.registries);
            }
        } catch (Exception exception) {
            LOGGER.error("Error loading saved data: {}", filename, exception);
        }

        return null;
    }

    public void set(String name, SavedData savedData) {
        this.cache.put(name, savedData);
    }

    public CompoundTag readTagFromDisk(String filename, @Nullable DataFixTypes dataFixType, int version) throws IOException {
        File file1 = this.getDataFile(filename);

        CompoundTag compoundtag1;
        try (
            InputStream inputstream = new FileInputStream(file1);
            PushbackInputStream pushbackinputstream = new PushbackInputStream(new FastBufferedInputStream(inputstream), 2);
        ) {
            CompoundTag compoundtag;
            if (this.isGzip(pushbackinputstream)) {
                compoundtag = NbtIo.readCompressed(pushbackinputstream, NbtAccounter.unlimitedHeap());
            } else {
                try (DataInputStream datainputstream = new DataInputStream(pushbackinputstream)) {
                    compoundtag = NbtIo.read(datainputstream);
                }
            }

            if (dataFixType != null) {
                int i = NbtUtils.getDataVersion(compoundtag, 1343);
                compoundtag1 = dataFixType.update(this.fixerUpper, compoundtag, i, version);
            } else {
                compoundtag1 = compoundtag;
            }
        }

        // Neo: delete any temporary files so that we don't inflate disk space unnecessarily.
        net.neoforged.neoforge.common.IOUtilities.withIOWorker(() -> {
            net.neoforged.neoforge.common.IOUtilities.tryCleanupTempFiles(this.dataFolder.toPath(), filename);
        });

        return compoundtag1;
    }

    private boolean isGzip(PushbackInputStream inputStream) throws IOException {
        byte[] abyte = new byte[2];
        boolean flag = false;
        int i = inputStream.read(abyte, 0, 2);
        if (i == 2) {
            int j = (abyte[1] & 255) << 8 | abyte[0] & 255;
            if (j == 35615) {
                flag = true;
            }
        }

        if (i != 0) {
            inputStream.unread(abyte, 0, i);
        }

        return flag;
    }

    public void save() {
        this.cache.forEach((p_323449_, p_323450_) -> {
            if (p_323450_ != null) {
                p_323450_.save(this.getDataFile(p_323449_), this.registries);
            }
        });
    }
}
