package net.minecraft.world.level.lighting;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;

public abstract class LayerLightSectionStorage<M extends DataLayerStorageMap<M>> {
    private final LightLayer layer;
    protected final LightChunkGetter chunkSource;
    protected final Long2ByteMap sectionStates = new Long2ByteOpenHashMap();
    private final LongSet columnsWithSources = new LongOpenHashSet();
    protected volatile M visibleSectionData;
    protected final M updatingSectionData;
    protected final LongSet changedSections = new LongOpenHashSet();
    protected final LongSet sectionsAffectedByLightUpdates = new LongOpenHashSet();
    protected final Long2ObjectMap<DataLayer> queuedSections = Long2ObjectMaps.synchronize(new Long2ObjectOpenHashMap<>());
    /**
     * Section column positions (section positions with Y=0) that need to be kept even if some of their sections could otherwise be removed.
     */
    private final LongSet columnsToRetainQueuedDataFor = new LongOpenHashSet();
    /**
     * Set of section positions that can be removed, because their light won't affect any blocks.
     */
    private final LongSet toRemove = new LongOpenHashSet();
    protected volatile boolean hasInconsistencies;

    protected LayerLightSectionStorage(LightLayer layer, LightChunkGetter chunkSource, M updatingSectionData) {
        this.layer = layer;
        this.chunkSource = chunkSource;
        this.updatingSectionData = updatingSectionData;
        this.visibleSectionData = updatingSectionData.copy();
        this.visibleSectionData.disableCache();
        this.sectionStates.defaultReturnValue((byte)0);
    }

    protected boolean storingLightForSection(long sectionPos) {
        return this.getDataLayer(sectionPos, true) != null;
    }

    @Nullable
    protected DataLayer getDataLayer(long sectionPos, boolean cached) {
        return this.getDataLayer(cached ? this.updatingSectionData : this.visibleSectionData, sectionPos);
    }

    @Nullable
    protected DataLayer getDataLayer(M map, long sectionPos) {
        return map.getLayer(sectionPos);
    }

    @Nullable
    protected DataLayer getDataLayerToWrite(long sectionPos) {
        DataLayer datalayer = this.updatingSectionData.getLayer(sectionPos);
        if (datalayer == null) {
            return null;
        } else {
            if (this.changedSections.add(sectionPos)) {
                datalayer = datalayer.copy();
                this.updatingSectionData.setLayer(sectionPos, datalayer);
                this.updatingSectionData.clearCache();
            }

            return datalayer;
        }
    }

    @Nullable
    public DataLayer getDataLayerData(long sectionPos) {
        DataLayer datalayer = this.queuedSections.get(sectionPos);
        return datalayer != null ? datalayer : this.getDataLayer(sectionPos, false);
    }

    protected abstract int getLightValue(long levelPos);

    protected int getStoredLevel(long levelPos) {
        long i = SectionPos.blockToSection(levelPos);
        DataLayer datalayer = this.getDataLayer(i, true);
        return datalayer.get(
            SectionPos.sectionRelative(BlockPos.getX(levelPos)),
            SectionPos.sectionRelative(BlockPos.getY(levelPos)),
            SectionPos.sectionRelative(BlockPos.getZ(levelPos))
        );
    }

    protected void setStoredLevel(long levelPos, int lightLevel) {
        long i = SectionPos.blockToSection(levelPos);
        DataLayer datalayer;
        if (this.changedSections.add(i)) {
            datalayer = this.updatingSectionData.copyDataLayer(i);
        } else {
            datalayer = this.getDataLayer(i, true);
        }

        datalayer.set(
            SectionPos.sectionRelative(BlockPos.getX(levelPos)),
            SectionPos.sectionRelative(BlockPos.getY(levelPos)),
            SectionPos.sectionRelative(BlockPos.getZ(levelPos)),
            lightLevel
        );
        SectionPos.aroundAndAtBlockPos(levelPos, this.sectionsAffectedByLightUpdates::add);
    }

    protected void markSectionAndNeighborsAsAffected(long sectionPos) {
        int i = SectionPos.x(sectionPos);
        int j = SectionPos.y(sectionPos);
        int k = SectionPos.z(sectionPos);

        for (int l = -1; l <= 1; l++) {
            for (int i1 = -1; i1 <= 1; i1++) {
                for (int j1 = -1; j1 <= 1; j1++) {
                    this.sectionsAffectedByLightUpdates.add(SectionPos.asLong(i + i1, j + j1, k + l));
                }
            }
        }
    }

    protected DataLayer createDataLayer(long sectionPos) {
        DataLayer datalayer = this.queuedSections.get(sectionPos);
        return datalayer != null ? datalayer : new DataLayer();
    }

    protected boolean hasInconsistencies() {
        return this.hasInconsistencies;
    }

    protected void markNewInconsistencies(LightEngine<M, ?> lightEngine) {
        if (this.hasInconsistencies) {
            this.hasInconsistencies = false;

            for (long i : this.toRemove) {
                DataLayer datalayer = this.queuedSections.remove(i);
                DataLayer datalayer1 = this.updatingSectionData.removeLayer(i);
                if (this.columnsToRetainQueuedDataFor.contains(SectionPos.getZeroNode(i))) {
                    if (datalayer != null) {
                        this.queuedSections.put(i, datalayer);
                    } else if (datalayer1 != null) {
                        this.queuedSections.put(i, datalayer1);
                    }
                }
            }

            this.updatingSectionData.clearCache();

            for (long k : this.toRemove) {
                this.onNodeRemoved(k);
                this.changedSections.add(k);
            }

            this.toRemove.clear();
            ObjectIterator<Entry<DataLayer>> objectiterator = Long2ObjectMaps.fastIterator(this.queuedSections);

            while (objectiterator.hasNext()) {
                Entry<DataLayer> entry = objectiterator.next();
                long j = entry.getLongKey();
                if (this.storingLightForSection(j)) {
                    DataLayer datalayer2 = entry.getValue();
                    if (this.updatingSectionData.getLayer(j) != datalayer2) {
                        this.updatingSectionData.setLayer(j, datalayer2);
                        this.changedSections.add(j);
                    }

                    objectiterator.remove();
                }
            }

            this.updatingSectionData.clearCache();
        }
    }

    protected void onNodeAdded(long sectionPos) {
    }

    protected void onNodeRemoved(long sectionPos) {
    }

    protected void setLightEnabled(long sectionPos, boolean lightEnabled) {
        if (lightEnabled) {
            this.columnsWithSources.add(sectionPos);
        } else {
            this.columnsWithSources.remove(sectionPos);
        }
    }

    protected boolean lightOnInSection(long sectionPos) {
        long i = SectionPos.getZeroNode(sectionPos);
        return this.columnsWithSources.contains(i);
    }

    public void retainData(long sectionColumnPos, boolean retain) {
        if (retain) {
            this.columnsToRetainQueuedDataFor.add(sectionColumnPos);
        } else {
            this.columnsToRetainQueuedDataFor.remove(sectionColumnPos);
        }
    }

    protected void queueSectionData(long sectionPos, @Nullable DataLayer data) {
        if (data != null) {
            this.queuedSections.put(sectionPos, data);
            this.hasInconsistencies = true;
        } else {
            this.queuedSections.remove(sectionPos);
        }
    }

    protected void updateSectionStatus(long sectionPos, boolean isEmpty) {
        byte b0 = this.sectionStates.get(sectionPos);
        byte b1 = LayerLightSectionStorage.SectionState.hasData(b0, !isEmpty);
        if (b0 != b1) {
            this.putSectionState(sectionPos, b1);
            int i = isEmpty ? -1 : 1;

            for (int j = -1; j <= 1; j++) {
                for (int k = -1; k <= 1; k++) {
                    for (int l = -1; l <= 1; l++) {
                        if (j != 0 || k != 0 || l != 0) {
                            long i1 = SectionPos.offset(sectionPos, j, k, l);
                            byte b2 = this.sectionStates.get(i1);
                            this.putSectionState(
                                i1, LayerLightSectionStorage.SectionState.neighborCount(b2, LayerLightSectionStorage.SectionState.neighborCount(b2) + i)
                            );
                        }
                    }
                }
            }
        }
    }

    protected void putSectionState(long sectionPos, byte sectionState) {
        if (sectionState != 0) {
            if (this.sectionStates.put(sectionPos, sectionState) == 0) {
                this.initializeSection(sectionPos);
            }
        } else if (this.sectionStates.remove(sectionPos) != 0) {
            this.removeSection(sectionPos);
        }
    }

    private void initializeSection(long sectionPos) {
        if (!this.toRemove.remove(sectionPos)) {
            this.updatingSectionData.setLayer(sectionPos, this.createDataLayer(sectionPos));
            this.changedSections.add(sectionPos);
            this.onNodeAdded(sectionPos);
            this.markSectionAndNeighborsAsAffected(sectionPos);
            this.hasInconsistencies = true;
        }
    }

    private void removeSection(long sectionPos) {
        this.toRemove.add(sectionPos);
        this.hasInconsistencies = true;
    }

    protected void swapSectionMap() {
        if (!this.changedSections.isEmpty()) {
            M m = this.updatingSectionData.copy();
            m.disableCache();
            this.visibleSectionData = m;
            this.changedSections.clear();
        }

        if (!this.sectionsAffectedByLightUpdates.isEmpty()) {
            LongIterator longiterator = this.sectionsAffectedByLightUpdates.iterator();

            while (longiterator.hasNext()) {
                long i = longiterator.nextLong();
                this.chunkSource.onLightUpdate(this.layer, SectionPos.of(i));
            }

            this.sectionsAffectedByLightUpdates.clear();
        }
    }

    public LayerLightSectionStorage.SectionType getDebugSectionType(long sectionPos) {
        return LayerLightSectionStorage.SectionState.type(this.sectionStates.get(sectionPos));
    }

    protected static class SectionState {
        public static final byte EMPTY = 0;
        private static final int MIN_NEIGHBORS = 0;
        private static final int MAX_NEIGHBORS = 26;
        private static final byte HAS_DATA_BIT = 32;
        private static final byte NEIGHBOR_COUNT_BITS = 31;

        public static byte hasData(byte sectionState, boolean hasData) {
            return (byte)(hasData ? sectionState | 32 : sectionState & -33);
        }

        public static byte neighborCount(byte sectionState, int neighborCount) {
            if (neighborCount >= 0 && neighborCount <= 26) {
                return (byte)(sectionState & -32 | neighborCount & 31);
            } else {
                throw new IllegalArgumentException("Neighbor count was not within range [0; 26]");
            }
        }

        public static boolean hasData(byte sectionState) {
            return (sectionState & 32) != 0;
        }

        public static int neighborCount(byte sectionState) {
            return sectionState & 31;
        }

        public static LayerLightSectionStorage.SectionType type(byte sectionState) {
            if (sectionState == 0) {
                return LayerLightSectionStorage.SectionType.EMPTY;
            } else {
                return hasData(sectionState) ? LayerLightSectionStorage.SectionType.LIGHT_AND_DATA : LayerLightSectionStorage.SectionType.LIGHT_ONLY;
            }
        }
    }

    public static enum SectionType {
        EMPTY("2"),
        LIGHT_ONLY("1"),
        LIGHT_AND_DATA("0");

        private final String display;

        private SectionType(String display) {
            this.display = display;
        }

        public String display() {
            return this.display;
        }
    }
}
