package net.minecraft.world.level.saveddata.maps;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

public class MapIndex extends SavedData {
    public static final String FILE_NAME = "idcounts";
    private final Object2IntMap<String> usedAuxIds = new Object2IntOpenHashMap<>();

    public static SavedData.Factory<MapIndex> factory() {
        return new SavedData.Factory<>(MapIndex::new, MapIndex::load, DataFixTypes.SAVED_DATA_MAP_INDEX);
    }

    public MapIndex() {
        this.usedAuxIds.defaultReturnValue(-1);
    }

    public static MapIndex load(CompoundTag tag, HolderLookup.Provider registries) {
        MapIndex mapindex = new MapIndex();

        for (String s : tag.getAllKeys()) {
            if (tag.contains(s, 99)) {
                mapindex.usedAuxIds.put(s, tag.getInt(s));
            }
        }

        return mapindex;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        for (Entry<String> entry : this.usedAuxIds.object2IntEntrySet()) {
            tag.putInt(entry.getKey(), entry.getIntValue());
        }

        return tag;
    }

    public MapId getFreeAuxValueForMap() {
        int i = this.usedAuxIds.getInt("map") + 1;
        this.usedAuxIds.put("map", i);
        this.setDirty();
        return new MapId(i);
    }
}
