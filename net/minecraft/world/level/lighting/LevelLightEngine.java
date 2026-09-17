package net.minecraft.world.level.lighting;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;

public class LevelLightEngine implements LightEventListener {
    public static final int LIGHT_SECTION_PADDING = 1;
    protected final LevelHeightAccessor levelHeightAccessor;
    @Nullable
    private final LightEngine<?, ?> blockEngine;
    @Nullable
    private final LightEngine<?, ?> skyEngine;

    public LevelLightEngine(LightChunkGetter lightChunkGetter, boolean blockLight, boolean skyLight) {
        this.levelHeightAccessor = lightChunkGetter.getLevel();
        this.blockEngine = blockLight ? new BlockLightEngine(lightChunkGetter) : null;
        this.skyEngine = skyLight ? new SkyLightEngine(lightChunkGetter) : null;
    }

    @Override
    public void checkBlock(BlockPos pos) {
        if (this.blockEngine != null) {
            this.blockEngine.checkBlock(pos);
        }

        if (this.skyEngine != null) {
            this.skyEngine.checkBlock(pos);
        }
    }

    @Override
    public boolean hasLightWork() {
        return this.skyEngine != null && this.skyEngine.hasLightWork() ? true : this.blockEngine != null && this.blockEngine.hasLightWork();
    }

    @Override
    public int runLightUpdates() {
        int i = 0;
        if (this.blockEngine != null) {
            i += this.blockEngine.runLightUpdates();
        }

        if (this.skyEngine != null) {
            i += this.skyEngine.runLightUpdates();
        }

        return i;
    }

    @Override
    public void updateSectionStatus(SectionPos pos, boolean isEmpty) {
        if (this.blockEngine != null) {
            this.blockEngine.updateSectionStatus(pos, isEmpty);
        }

        if (this.skyEngine != null) {
            this.skyEngine.updateSectionStatus(pos, isEmpty);
        }
    }

    @Override
    public void setLightEnabled(ChunkPos chunkPos, boolean lightEnabled) {
        if (this.blockEngine != null) {
            this.blockEngine.setLightEnabled(chunkPos, lightEnabled);
        }

        if (this.skyEngine != null) {
            this.skyEngine.setLightEnabled(chunkPos, lightEnabled);
        }
    }

    @Override
    public void propagateLightSources(ChunkPos chunkPos) {
        if (this.blockEngine != null) {
            this.blockEngine.propagateLightSources(chunkPos);
        }

        if (this.skyEngine != null) {
            this.skyEngine.propagateLightSources(chunkPos);
        }
    }

    public LayerLightEventListener getLayerListener(LightLayer type) {
        if (type == LightLayer.BLOCK) {
            return (LayerLightEventListener)(this.blockEngine == null ? LayerLightEventListener.DummyLightLayerEventListener.INSTANCE : this.blockEngine);
        } else {
            return (LayerLightEventListener)(this.skyEngine == null ? LayerLightEventListener.DummyLightLayerEventListener.INSTANCE : this.skyEngine);
        }
    }

    public String getDebugData(LightLayer lightLayer, SectionPos sectionPos) {
        if (lightLayer == LightLayer.BLOCK) {
            if (this.blockEngine != null) {
                return this.blockEngine.getDebugData(sectionPos.asLong());
            }
        } else if (this.skyEngine != null) {
            return this.skyEngine.getDebugData(sectionPos.asLong());
        }

        return "n/a";
    }

    public LayerLightSectionStorage.SectionType getDebugSectionType(LightLayer lightLayer, SectionPos sectionPos) {
        if (lightLayer == LightLayer.BLOCK) {
            if (this.blockEngine != null) {
                return this.blockEngine.getDebugSectionType(sectionPos.asLong());
            }
        } else if (this.skyEngine != null) {
            return this.skyEngine.getDebugSectionType(sectionPos.asLong());
        }

        return LayerLightSectionStorage.SectionType.EMPTY;
    }

    public void queueSectionData(LightLayer lightLayer, SectionPos sectionPos, @Nullable DataLayer dataLayer) {
        if (lightLayer == LightLayer.BLOCK) {
            if (this.blockEngine != null) {
                this.blockEngine.queueSectionData(sectionPos.asLong(), dataLayer);
            }
        } else if (this.skyEngine != null) {
            this.skyEngine.queueSectionData(sectionPos.asLong(), dataLayer);
        }
    }

    public void retainData(ChunkPos pos, boolean retain) {
        if (this.blockEngine != null) {
            this.blockEngine.retainData(pos, retain);
        }

        if (this.skyEngine != null) {
            this.skyEngine.retainData(pos, retain);
        }
    }

    public int getRawBrightness(BlockPos blockPos, int amount) {
        int i = this.skyEngine == null ? 0 : this.skyEngine.getLightValue(blockPos) - amount;
        int j = this.blockEngine == null ? 0 : this.blockEngine.getLightValue(blockPos);
        return Math.max(j, i);
    }

    public boolean lightOnInSection(SectionPos sectionPos) {
        long i = sectionPos.asLong();
        return this.blockEngine == null
            || this.blockEngine.storage.lightOnInSection(i) && (this.skyEngine == null || this.skyEngine.storage.lightOnInSection(i));
    }

    public int getLightSectionCount() {
        return this.levelHeightAccessor.getSectionsCount() + 2;
    }

    public int getMinLightSection() {
        return this.levelHeightAccessor.getMinSection() - 1;
    }

    public int getMaxLightSection() {
        return this.getMinLightSection() + this.getLightSectionCount();
    }
}
