package net.minecraft.client.renderer.chunk;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RenderRegionCache {
    private final Long2ObjectMap<RenderRegionCache.ChunkInfo> chunkInfoCache = new Long2ObjectOpenHashMap<>();

    @Nullable
    public RenderChunkRegion createRegion(Level level, SectionPos sectionPos) {
        return createRegion(level, sectionPos, true);
    }

    @Nullable
    public RenderChunkRegion createRegion(Level level, SectionPos sectionPos, boolean nullForEmpty) {
        RenderRegionCache.ChunkInfo renderregioncache$chunkinfo = this.getChunkInfo(level, sectionPos.x(), sectionPos.z());
        if (nullForEmpty && renderregioncache$chunkinfo.chunk().isSectionEmpty(sectionPos.y())) {
            return null;
        } else {
            int i = sectionPos.x() - 1;
            int j = sectionPos.z() - 1;
            int k = sectionPos.x() + 1;
            int l = sectionPos.z() + 1;
            RenderChunk[] arenderchunk = new RenderChunk[9];

            for (int i1 = j; i1 <= l; i1++) {
                for (int j1 = i; j1 <= k; j1++) {
                    int k1 = RenderChunkRegion.index(i, j, j1, i1);
                    RenderRegionCache.ChunkInfo renderregioncache$chunkinfo1 = j1 == sectionPos.x() && i1 == sectionPos.z()
                        ? renderregioncache$chunkinfo
                        : this.getChunkInfo(level, j1, i1);
                    arenderchunk[k1] = renderregioncache$chunkinfo1.renderChunk();
                }
            }

            int sectionMinY = sectionPos.getY() - RenderChunkRegion.RADIUS;
            int sectionMaxY = sectionPos.getY() + RenderChunkRegion.RADIUS;
            var modelDataManager = level.getModelDataManager().snapshotSectionRegion(i, sectionMinY, j, k, sectionMaxY, l);
            return new RenderChunkRegion(level, i, j, arenderchunk, modelDataManager);
        }
    }

    private RenderRegionCache.ChunkInfo getChunkInfo(Level level, int x, int z) {
        return this.chunkInfoCache
            .computeIfAbsent(
                ChunkPos.asLong(x, z),
                p_200464_ -> new RenderRegionCache.ChunkInfo(level.getChunk(ChunkPos.getX(p_200464_), ChunkPos.getZ(p_200464_)))
            );
    }

    @OnlyIn(Dist.CLIENT)
    static final class ChunkInfo {
        private final LevelChunk chunk;
        @Nullable
        private RenderChunk renderChunk;

        ChunkInfo(LevelChunk chunk) {
            this.chunk = chunk;
        }

        public LevelChunk chunk() {
            return this.chunk;
        }

        public RenderChunk renderChunk() {
            if (this.renderChunk == null) {
                this.renderChunk = new RenderChunk(this.chunk);
            }

            return this.renderChunk;
        }
    }
}
