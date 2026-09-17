package net.minecraft.world.level;

import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobCategory;

public class LocalMobCapCalculator {
    private final Long2ObjectMap<List<ServerPlayer>> playersNearChunk = new Long2ObjectOpenHashMap<>();
    private final Map<ServerPlayer, LocalMobCapCalculator.MobCounts> playerMobCounts = Maps.newHashMap();
    private final ChunkMap chunkMap;

    public LocalMobCapCalculator(ChunkMap chunkMap) {
        this.chunkMap = chunkMap;
    }

    private List<ServerPlayer> getPlayersNear(ChunkPos pos) {
        return this.playersNearChunk.computeIfAbsent(pos.toLong(), p_186511_ -> this.chunkMap.getPlayersCloseForSpawning(pos));
    }

    public void addMob(ChunkPos pos, MobCategory category) {
        for (ServerPlayer serverplayer : this.getPlayersNear(pos)) {
            this.playerMobCounts.computeIfAbsent(serverplayer, p_186503_ -> new LocalMobCapCalculator.MobCounts()).add(category);
        }
    }

    public boolean canSpawn(MobCategory category, ChunkPos pos) {
        for (ServerPlayer serverplayer : this.getPlayersNear(pos)) {
            LocalMobCapCalculator.MobCounts localmobcapcalculator$mobcounts = this.playerMobCounts.get(serverplayer);
            if (localmobcapcalculator$mobcounts == null || localmobcapcalculator$mobcounts.canSpawn(category)) {
                return true;
            }
        }

        return false;
    }

    static class MobCounts {
        private final Object2IntMap<MobCategory> counts = new Object2IntOpenHashMap<>(MobCategory.values().length);

        public void add(MobCategory category) {
            this.counts.computeInt(category, (p_186520_, p_186521_) -> p_186521_ == null ? 1 : p_186521_ + 1);
        }

        public boolean canSpawn(MobCategory category) {
            return this.counts.getOrDefault(category, 0) < category.getMaxInstancesPerChunk();
        }
    }
}
