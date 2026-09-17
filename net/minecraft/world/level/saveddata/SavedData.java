package net.minecraft.world.level.saveddata;

import com.mojang.logging.LogUtils;
import java.io.File;
import java.io.IOException;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.datafix.DataFixTypes;
import org.slf4j.Logger;

public abstract class SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private boolean dirty;

    public abstract CompoundTag save(CompoundTag tag, HolderLookup.Provider registries);

    public void setDirty() {
        this.setDirty(true);
    }

    /**
     * Sets the dirty state of this {@code SavedData}, whether it needs saving to disk.
     */
    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public boolean isDirty() {
        return this.dirty;
    }

    public void save(File file, HolderLookup.Provider registries) {
        if (this.isDirty()) {
            CompoundTag compoundtag = new CompoundTag();
            compoundtag.put("data", this.save(new CompoundTag(), registries));
            NbtUtils.addCurrentDataVersion(compoundtag);

            //copy the contents to handle mods that just append an existing Tag to the Compound
            CompoundTag copied = compoundtag.copy();

            net.neoforged.neoforge.common.IOUtilities.withIOWorker(()->{
            try {
                net.neoforged.neoforge.common.IOUtilities.writeNbtCompressed(copied, file.toPath());
            } catch (IOException ioexception) {
                LOGGER.error("Could not save data {}", this, ioexception);
            }
            });

            this.setDirty(false);
        }
    }

    public static record Factory<T extends SavedData>(
        Supplier<T> constructor, BiFunction<CompoundTag, HolderLookup.Provider, T> deserializer, @org.jetbrains.annotations.Nullable DataFixTypes type // Neo: We do not have update logic compatible with DFU, several downstream patches from this record are made to support a nullable type.
    ) {
        public Factory(Supplier<T> constructor, BiFunction<CompoundTag, HolderLookup.Provider, T> deserializer) {
            this(constructor, deserializer, null);
        }
    }
}
