package net.minecraft.world.level.lighting;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import javax.annotation.Nullable;
import net.minecraft.world.level.chunk.DataLayer;

public abstract class DataLayerStorageMap<M extends DataLayerStorageMap<M>> {
    private static final int CACHE_SIZE = 2;
    private final long[] lastSectionKeys = new long[2];
    private final DataLayer[] lastSections = new DataLayer[2];
    private boolean cacheEnabled;
    protected final Long2ObjectOpenHashMap<DataLayer> map;

    protected DataLayerStorageMap(Long2ObjectOpenHashMap<DataLayer> map) {
        this.map = map;
        this.clearCache();
        this.cacheEnabled = true;
    }

    public abstract M copy();

    public DataLayer copyDataLayer(long index) {
        DataLayer datalayer = this.map.get(index).copy();
        this.map.put(index, datalayer);
        this.clearCache();
        return datalayer;
    }

    public boolean hasLayer(long sectionPos) {
        return this.map.containsKey(sectionPos);
    }

    @Nullable
    public DataLayer getLayer(long sectionPos) {
        if (this.cacheEnabled) {
            for (int i = 0; i < 2; i++) {
                if (sectionPos == this.lastSectionKeys[i]) {
                    return this.lastSections[i];
                }
            }
        }

        DataLayer datalayer = this.map.get(sectionPos);
        if (datalayer == null) {
            return null;
        } else {
            if (this.cacheEnabled) {
                for (int j = 1; j > 0; j--) {
                    this.lastSectionKeys[j] = this.lastSectionKeys[j - 1];
                    this.lastSections[j] = this.lastSections[j - 1];
                }

                this.lastSectionKeys[0] = sectionPos;
                this.lastSections[0] = datalayer;
            }

            return datalayer;
        }
    }

    @Nullable
    public DataLayer removeLayer(long sectionPos) {
        return this.map.remove(sectionPos);
    }

    public void setLayer(long sectionPos, DataLayer array) {
        this.map.put(sectionPos, array);
    }

    public void clearCache() {
        for (int i = 0; i < 2; i++) {
            this.lastSectionKeys[i] = Long.MAX_VALUE;
            this.lastSections[i] = null;
        }
    }

    public void disableCache() {
        this.cacheEnabled = false;
    }
}
