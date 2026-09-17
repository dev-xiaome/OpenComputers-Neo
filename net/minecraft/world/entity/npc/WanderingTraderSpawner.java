package net.minecraft.world.entity.npc;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.horse.TraderLlama;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ServerLevelData;

public class WanderingTraderSpawner implements CustomSpawner {
    private static final int DEFAULT_TICK_DELAY = 1200;
    public static final int DEFAULT_SPAWN_DELAY = 24000;
    private static final int MIN_SPAWN_CHANCE = 25;
    private static final int MAX_SPAWN_CHANCE = 75;
    private static final int SPAWN_CHANCE_INCREASE = 25;
    private static final int SPAWN_ONE_IN_X_CHANCE = 10;
    private static final int NUMBER_OF_SPAWN_ATTEMPTS = 10;
    private final RandomSource random = RandomSource.create();
    private final ServerLevelData serverLevelData;
    private int tickDelay;
    private int spawnDelay;
    private int spawnChance;

    public WanderingTraderSpawner(ServerLevelData serverLevelData) {
        this.serverLevelData = serverLevelData;
        this.tickDelay = 1200;
        this.spawnDelay = serverLevelData.getWanderingTraderSpawnDelay();
        this.spawnChance = serverLevelData.getWanderingTraderSpawnChance();
        if (this.spawnDelay == 0 && this.spawnChance == 0) {
            this.spawnDelay = 24000;
            serverLevelData.setWanderingTraderSpawnDelay(this.spawnDelay);
            this.spawnChance = 25;
            serverLevelData.setWanderingTraderSpawnChance(this.spawnChance);
        }
    }

    @Override
    public int tick(ServerLevel level, boolean spawnHostiles, boolean spawnPassives) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_DO_TRADER_SPAWNING)) {
            return 0;
        } else if (--this.tickDelay > 0) {
            return 0;
        } else {
            this.tickDelay = 1200;
            this.spawnDelay -= 1200;
            this.serverLevelData.setWanderingTraderSpawnDelay(this.spawnDelay);
            if (this.spawnDelay > 0) {
                return 0;
            } else {
                this.spawnDelay = 24000;
                if (!level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
                    return 0;
                } else {
                    int i = this.spawnChance;
                    this.spawnChance = Mth.clamp(this.spawnChance + 25, 25, 75);
                    this.serverLevelData.setWanderingTraderSpawnChance(this.spawnChance);
                    if (this.random.nextInt(100) > i) {
                        return 0;
                    } else if (this.spawn(level)) {
                        this.spawnChance = 25;
                        return 1;
                    } else {
                        return 0;
                    }
                }
            }
        }
    }

    private boolean spawn(ServerLevel serverLevel) {
        Player player = serverLevel.getRandomPlayer();
        if (player == null) {
            return true;
        } else if (this.random.nextInt(10) != 0) {
            return false;
        } else {
            BlockPos blockpos = player.blockPosition();
            int i = 48;
            PoiManager poimanager = serverLevel.getPoiManager();
            Optional<BlockPos> optional = poimanager.find(
                p_219713_ -> p_219713_.is(PoiTypes.MEETING), p_219711_ -> true, blockpos, 48, PoiManager.Occupancy.ANY
            );
            BlockPos blockpos1 = optional.orElse(blockpos);
            BlockPos blockpos2 = this.findSpawnPositionNear(serverLevel, blockpos1, 48);
            if (blockpos2 != null && this.hasEnoughSpace(serverLevel, blockpos2)) {
                if (serverLevel.getBiome(blockpos2).is(BiomeTags.WITHOUT_WANDERING_TRADER_SPAWNS)) {
                    return false;
                }

                WanderingTrader wanderingtrader = EntityType.WANDERING_TRADER.spawn(serverLevel, blockpos2, MobSpawnType.EVENT);
                if (wanderingtrader != null) {
                    for (int j = 0; j < 2; j++) {
                        this.tryToSpawnLlamaFor(serverLevel, wanderingtrader, 4);
                    }

                    this.serverLevelData.setWanderingTraderId(wanderingtrader.getUUID());
                    wanderingtrader.setDespawnDelay(48000);
                    wanderingtrader.setWanderTarget(blockpos1);
                    wanderingtrader.restrictTo(blockpos1, 16);
                    return true;
                }
            }

            return false;
        }
    }

    private void tryToSpawnLlamaFor(ServerLevel serverLevel, WanderingTrader trader, int maxDistance) {
        BlockPos blockpos = this.findSpawnPositionNear(serverLevel, trader.blockPosition(), maxDistance);
        if (blockpos != null) {
            TraderLlama traderllama = EntityType.TRADER_LLAMA.spawn(serverLevel, blockpos, MobSpawnType.EVENT);
            if (traderllama != null) {
                traderllama.setLeashedTo(trader, true);
            }
        }
    }

    @Nullable
    private BlockPos findSpawnPositionNear(LevelReader level, BlockPos pos, int maxDistance) {
        BlockPos blockpos = null;
        SpawnPlacementType spawnplacementtype = SpawnPlacements.getPlacementType(EntityType.WANDERING_TRADER);

        for (int i = 0; i < 10; i++) {
            int j = pos.getX() + this.random.nextInt(maxDistance * 2) - maxDistance;
            int k = pos.getZ() + this.random.nextInt(maxDistance * 2) - maxDistance;
            int l = level.getHeight(Heightmap.Types.WORLD_SURFACE, j, k);
            BlockPos blockpos1 = new BlockPos(j, l, k);
            if (spawnplacementtype.isSpawnPositionOk(level, blockpos1, EntityType.WANDERING_TRADER)) {
                blockpos = blockpos1;
                break;
            }
        }

        return blockpos;
    }

    private boolean hasEnoughSpace(BlockGetter level, BlockPos pos) {
        for (BlockPos blockpos : BlockPos.betweenClosed(pos, pos.offset(1, 2, 1))) {
            if (!level.getBlockState(blockpos).getCollisionShape(level, blockpos).isEmpty()) {
                return false;
            }
        }

        return true;
    }
}
