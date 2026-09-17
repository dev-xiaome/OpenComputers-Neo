package net.minecraft.world.level.lighting;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;

public class SkyLightSectionStorage extends LayerLightSectionStorage<SkyLightSectionStorage.SkyDataLayerStorageMap> {
    protected SkyLightSectionStorage(LightChunkGetter chunkSource) {
        super(
            LightLayer.SKY,
            chunkSource,
            new SkyLightSectionStorage.SkyDataLayerStorageMap(new Long2ObjectOpenHashMap<>(), new Long2IntOpenHashMap(), Integer.MAX_VALUE)
        );
    }

    @Override
    protected int getLightValue(long levelPos) {
        return this.getLightValue(levelPos, false);
    }

    protected int getLightValue(long packedPos, boolean updateAll) {
        long i = SectionPos.blockToSection(packedPos);
        int j = SectionPos.y(i);
        SkyLightSectionStorage.SkyDataLayerStorageMap skylightsectionstorage$skydatalayerstoragemap = updateAll
            ? this.updatingSectionData
            : this.visibleSectionData;
        int k = skylightsectionstorage$skydatalayerstoragemap.topSections.get(SectionPos.getZeroNode(i));
        if (k != skylightsectionstorage$skydatalayerstoragemap.currentLowestY && j < k) {
            DataLayer datalayer = this.getDataLayer(skylightsectionstorage$skydatalayerstoragemap, i);
            if (datalayer == null) {
                for (packedPos = BlockPos.getFlatIndex(packedPos);
                    datalayer == null;
                    datalayer = this.getDataLayer(skylightsectionstorage$skydatalayerstoragemap, i)
                ) {
                    if (++j >= k) {
                        return 15;
                    }

                    i = SectionPos.offset(i, Direction.UP);
                }
            }

            return datalayer.get(
                SectionPos.sectionRelative(BlockPos.getX(packedPos)),
                SectionPos.sectionRelative(BlockPos.getY(packedPos)),
                SectionPos.sectionRelative(BlockPos.getZ(packedPos))
            );
        } else {
            return updateAll && !this.lightOnInSection(i) ? 0 : 15;
        }
    }

    @Override
    protected void onNodeAdded(long sectionPos) {
        int i = SectionPos.y(sectionPos);
        if (this.updatingSectionData.currentLowestY > i) {
            this.updatingSectionData.currentLowestY = i;
            this.updatingSectionData.topSections.defaultReturnValue(this.updatingSectionData.currentLowestY);
        }

        long j = SectionPos.getZeroNode(sectionPos);
        int k = this.updatingSectionData.topSections.get(j);
        if (k < i + 1) {
            this.updatingSectionData.topSections.put(j, i + 1);
        }
    }

    @Override
    protected void onNodeRemoved(long sectionPos) {
        long i = SectionPos.getZeroNode(sectionPos);
        int j = SectionPos.y(sectionPos);
        if (this.updatingSectionData.topSections.get(i) == j + 1) {
            long k;
            for (k = sectionPos; !this.storingLightForSection(k) && this.hasLightDataAtOrBelow(j); k = SectionPos.offset(k, Direction.DOWN)) {
                j--;
            }

            if (this.storingLightForSection(k)) {
                this.updatingSectionData.topSections.put(i, j + 1);
            } else {
                this.updatingSectionData.topSections.remove(i);
            }
        }
    }

    @Override
    protected DataLayer createDataLayer(long sectionPos) {
        DataLayer datalayer = this.queuedSections.get(sectionPos);
        if (datalayer != null) {
            return datalayer;
        } else {
            int i = this.updatingSectionData.topSections.get(SectionPos.getZeroNode(sectionPos));
            if (i != this.updatingSectionData.currentLowestY && SectionPos.y(sectionPos) < i) {
                long j = SectionPos.offset(sectionPos, Direction.UP);

                DataLayer datalayer1;
                while ((datalayer1 = this.getDataLayer(j, true)) == null) {
                    j = SectionPos.offset(j, Direction.UP);
                }

                return repeatFirstLayer(datalayer1);
            } else {
                return this.lightOnInSection(sectionPos) ? new DataLayer(15) : new DataLayer();
            }
        }
    }

    private static DataLayer repeatFirstLayer(DataLayer dataLayer) {
        if (dataLayer.isDefinitelyHomogenous()) {
            return dataLayer.copy();
        } else {
            byte[] abyte = dataLayer.getData();
            byte[] abyte1 = new byte[2048];

            for (int i = 0; i < 16; i++) {
                System.arraycopy(abyte, 0, abyte1, i * 128, 128);
            }

            return new DataLayer(abyte1);
        }
    }

    protected boolean hasLightDataAtOrBelow(int y) {
        return y >= this.updatingSectionData.currentLowestY;
    }

    protected boolean isAboveData(long sectionPos) {
        long i = SectionPos.getZeroNode(sectionPos);
        int j = this.updatingSectionData.topSections.get(i);
        return j == this.updatingSectionData.currentLowestY || SectionPos.y(sectionPos) >= j;
    }

    protected int getTopSectionY(long sectionPos) {
        return this.updatingSectionData.topSections.get(sectionPos);
    }

    protected int getBottomSectionY() {
        return this.updatingSectionData.currentLowestY;
    }

    protected static final class SkyDataLayerStorageMap extends DataLayerStorageMap<SkyLightSectionStorage.SkyDataLayerStorageMap> {
        int currentLowestY;
        final Long2IntOpenHashMap topSections;

        public SkyDataLayerStorageMap(Long2ObjectOpenHashMap<DataLayer> map, Long2IntOpenHashMap topSections, int currentLowestY) {
            super(map);
            this.topSections = topSections;
            topSections.defaultReturnValue(currentLowestY);
            this.currentLowestY = currentLowestY;
        }

        public SkyLightSectionStorage.SkyDataLayerStorageMap copy() {
            return new SkyLightSectionStorage.SkyDataLayerStorageMap(this.map.clone(), this.topSections.clone(), this.currentLowestY);
        }
    }
}
