package net.minecraft.world.level.storage;

import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

public class CommandStorage {
    private static final String ID_PREFIX = "command_storage_";
    private final Map<String, CommandStorage.Container> namespaces = Maps.newHashMap();
    private final DimensionDataStorage storage;

    public CommandStorage(DimensionDataStorage storage) {
        this.storage = storage;
    }

    private CommandStorage.Container newStorage(String namespace) {
        CommandStorage.Container commandstorage$container = new CommandStorage.Container();
        this.namespaces.put(namespace, commandstorage$container);
        return commandstorage$container;
    }

    private SavedData.Factory<CommandStorage.Container> factory(String namespace) {
        return new SavedData.Factory<>(
            () -> this.newStorage(namespace), (p_164844_, p_323732_) -> this.newStorage(namespace).load(p_164844_), DataFixTypes.SAVED_DATA_COMMAND_STORAGE
        );
    }

    public CompoundTag get(ResourceLocation id) {
        String s = id.getNamespace();
        CommandStorage.Container commandstorage$container = this.storage.get(this.factory(s), createId(s));
        return commandstorage$container != null ? commandstorage$container.get(id.getPath()) : new CompoundTag();
    }

    public void set(ResourceLocation id, CompoundTag nbt) {
        String s = id.getNamespace();
        this.storage.computeIfAbsent(this.factory(s), createId(s)).put(id.getPath(), nbt);
    }

    public Stream<ResourceLocation> keys() {
        return this.namespaces.entrySet().stream().flatMap(p_164841_ -> p_164841_.getValue().getKeys(p_164841_.getKey()));
    }

    private static String createId(String namespace) {
        return "command_storage_" + namespace;
    }

    static class Container extends SavedData {
        private static final String TAG_CONTENTS = "contents";
        private final Map<String, CompoundTag> storage = Maps.newHashMap();

        CommandStorage.Container load(CompoundTag compoundTag) {
            CompoundTag compoundtag = compoundTag.getCompound("contents");

            for (String s : compoundtag.getAllKeys()) {
                this.storage.put(s, compoundtag.getCompound(s));
            }

            return this;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            CompoundTag compoundtag = new CompoundTag();
            this.storage.forEach((p_78070_, p_78071_) -> compoundtag.put(p_78070_, p_78071_.copy()));
            tag.put("contents", compoundtag);
            return tag;
        }

        public CompoundTag get(String id) {
            CompoundTag compoundtag = this.storage.get(id);
            return compoundtag != null ? compoundtag : new CompoundTag();
        }

        public void put(String id, CompoundTag nbt) {
            if (nbt.isEmpty()) {
                this.storage.remove(id);
            } else {
                this.storage.put(id, nbt);
            }

            this.setDirty();
        }

        public Stream<ResourceLocation> getKeys(String namespace) {
            return this.storage.keySet().stream().map(p_350257_ -> ResourceLocation.fromNamespaceAndPath(namespace, p_350257_));
        }
    }
}
