package net.minecraft.world.entity.npc;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;

public class CatSpawner implements CustomSpawner {
    private static final int TICK_DELAY = 1200;
    private int nextTick;

    @Override
    public int tick(ServerLevel level, boolean spawnHostiles, boolean spawnPassives) {
        if (spawnPassives && level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
            this.nextTick--;
            if (this.nextTick > 0) {
                return 0;
            } else {
                this.nextTick = 1200;
                Player player = level.getRandomPlayer();
                if (player == null) {
                    return 0;
                } else {
                    RandomSource randomsource = level.random;
                    int i = (8 + randomsource.nextInt(24)) * (randomsource.nextBoolean() ? -1 : 1);
                    int j = (8 + randomsource.nextInt(24)) * (randomsource.nextBoolean() ? -1 : 1);
                    BlockPos blockpos = player.blockPosition().offset(i, 0, j);
                    int k = 10;
                    if (!level.hasChunksAt(blockpos.getX() - 10, blockpos.getZ() - 10, blockpos.getX() + 10, blockpos.getZ() + 10)) {
                        return 0;
                    } else {
                        if (SpawnPlacements.isSpawnPositionOk(EntityType.CAT, level, blockpos)) {
                            if (level.isCloseToVillage(blockpos, 2)) {
                                return this.spawnInVillage(level, blockpos);
                            }

                            if (level.structureManager().getStructureWithPieceAt(blockpos, StructureTags.CATS_SPAWN_IN).isValid()) {
                                return this.spawnInHut(level, blockpos);
                            }
                        }

                        return 0;
                    }
                }
            }
        } else {
            return 0;
        }
    }

    private int spawnInVillage(ServerLevel serverLevel, BlockPos pos) {
        int i = 48;
        if (serverLevel.getPoiManager().getCountInRange(p_219610_ -> p_219610_.is(PoiTypes.HOME), pos, 48, PoiManager.Occupancy.IS_OCCUPIED) > 4L) {
            List<Cat> list = serverLevel.getEntitiesOfClass(Cat.class, new AABB(pos).inflate(48.0, 8.0, 48.0));
            if (list.size() < 5) {
                return this.spawnCat(pos, serverLevel);
            }
        }

        return 0;
    }

    private int spawnInHut(ServerLevel serverLevel, BlockPos pos) {
        int i = 16;
        List<Cat> list = serverLevel.getEntitiesOfClass(Cat.class, new AABB(pos).inflate(16.0, 8.0, 16.0));
        return list.size() < 1 ? this.spawnCat(pos, serverLevel) : 0;
    }

    private int spawnCat(BlockPos pos, ServerLevel serverLevel) {
        Cat cat = EntityType.CAT.create(serverLevel);
        if (cat == null) {
            return 0;
        } else {
            cat.moveTo(pos, 0.0F, 0.0F); // Fix MC-147659: Some witch huts spawn the incorrect cat
            cat.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null);
            serverLevel.addFreshEntityWithPassengers(cat);
            return 1;
        }
    }
}
