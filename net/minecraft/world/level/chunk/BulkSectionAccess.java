package net.minecraft.world.level.chunk;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class BulkSectionAccess implements AutoCloseable {
    private final LevelAccessor level;
    private final Long2ObjectMap<LevelChunkSection> acquiredSections = new Long2ObjectOpenHashMap<>();
    @Nullable
    private LevelChunkSection lastSection;
    private long lastSectionKey;

    public BulkSectionAccess(LevelAccessor level) {
        this.level = level;
    }

    @Nullable
    public LevelChunkSection getSection(BlockPos pos) {
        int i = this.level.getSectionIndex(pos.getY());
        if (i >= 0 && i < this.level.getSectionsCount()) {
            long j = SectionPos.asLong(pos);
            if (this.lastSection == null || this.lastSectionKey != j) {
                this.lastSection = this.acquiredSections
                    .computeIfAbsent(
                        j,
                        p_156109_ -> {
                            ChunkAccess chunkaccess = this.level
                                .getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
                            LevelChunkSection levelchunksection = chunkaccess.getSection(i);
                            levelchunksection.acquire();
                            return levelchunksection;
                        }
                    );
                this.lastSectionKey = j;
            }

            return this.lastSection;
        } else {
            return null;
        }
    }

    public BlockState getBlockState(BlockPos pos) {
        LevelChunkSection levelchunksection = this.getSection(pos);
        if (levelchunksection == null) {
            return Blocks.AIR.defaultBlockState();
        } else {
            int i = SectionPos.sectionRelative(pos.getX());
            int j = SectionPos.sectionRelative(pos.getY());
            int k = SectionPos.sectionRelative(pos.getZ());
            return levelchunksection.getBlockState(i, j, k);
        }
    }

    @Override
    public void close() {
        for (LevelChunkSection levelchunksection : this.acquiredSections.values()) {
            levelchunksection.release();
        }
    }
}
