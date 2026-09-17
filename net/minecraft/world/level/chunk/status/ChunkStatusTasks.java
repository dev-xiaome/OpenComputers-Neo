package net.minecraft.world.level.chunk.status;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ChunkTaskPriorityQueueSorter;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.Blender;

public class ChunkStatusTasks {
    private static boolean isLighted(ChunkAccess chunk) {
        return chunk.getPersistedStatus().isOrAfter(ChunkStatus.LIGHT) && chunk.isLightCorrect();
    }

    static CompletableFuture<ChunkAccess> passThrough(
        WorldGenContext worldGenContext, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk
    ) {
        return CompletableFuture.completedFuture(chunk);
    }

    static CompletableFuture<ChunkAccess> generateStructureStarts(
        WorldGenContext worldGenContext, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk
    ) {
        ServerLevel serverlevel = worldGenContext.level();
        if (serverlevel.getServer().getWorldData().worldGenOptions().generateStructures()) {
            worldGenContext.generator()
                .createStructures(
                    serverlevel.registryAccess(),
                    serverlevel.getChunkSource().getGeneratorState(),
                    serverlevel.structureManager(),
                    chunk,
                    worldGenContext.structureManager()
                );
        }

        serverlevel.onStructureStartsAvailable(chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    static CompletableFuture<ChunkAccess> loadStructureStarts(
        WorldGenContext worldGenContext, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk
    ) {
        worldGenContext.level().onStructureStartsAvailable(chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    static CompletableFuture<ChunkAccess> generateStructureReferences(
        WorldGenContext worldGenContext, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk
    ) {
        ServerLevel serverlevel = worldGenContext.level();
        WorldGenRegion worldgenregion = new WorldGenRegion(serverlevel, cache, step, chunk);
        worldGenContext.generator().createReferences(worldgenregion, serverlevel.structureManager().forWorldGenRegion(worldgenregion), chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    static CompletableFuture<ChunkAccess> generateBiomes(
        WorldGenContext worldGenContext, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk
    ) {
        ServerLevel serverlevel = worldGenContext.level();
        WorldGenRegion worldgenregion = new WorldGenRegion(serverlevel, cache, step, chunk);
        return worldGenContext.generator()
            .createBiomes(
                serverlevel.getChunkSource().randomState(),
                Blender.of(worldgenregion),
                serverlevel.structureManager().forWorldGenRegion(worldgenregion),
                chunk
            );
    }

    static CompletableFuture<ChunkAccess> generateNoise(
        WorldGenContext worldGenContext, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk
    ) {
        ServerLevel serverlevel = worldGenContext.level();
        WorldGenRegion worldgenregion = new WorldGenRegion(serverlevel, cache, step, chunk);
        return worldGenContext.generator()
            .fillFromNoise(
                Blender.of(worldgenregion),
                serverlevel.getChunkSource().randomState(),
                serverlevel.structureManager().forWorldGenRegion(worldgenregion),
                chunk
            )
            .thenApply(p_330442_ -> {
                if (p_330442_ instanceof ProtoChunk protochunk) {
                    BelowZeroRetrogen belowzeroretrogen = protochunk.getBelowZeroRetrogen();
                    if (belowzeroretrogen != null) {
                        BelowZeroRetrogen.replaceOldBedrock(protochunk);
                        if (belowzeroretrogen.hasBedrockHoles()) {
                            belowzeroretrogen.applyBedrockMask(protochunk);
                        }
                    }
                }

                return (ChunkAccess)p_330442_;
            });
    }

    static CompletableFuture<ChunkAccess> generateSurface(
        WorldGenContext worldGenContext, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk
    ) {
        ServerLevel serverlevel = worldGenContext.level();
        WorldGenRegion worldgenregion = new WorldGenRegion(serverlevel, cache, step, chunk);
        worldGenContext.generator()
            .buildSurface(
                worldgenregion, serverlevel.structureManager().forWorldGenRegion(worldgenregion), serverlevel.getChunkSource().randomState(), chunk
            );
        return CompletableFuture.completedFuture(chunk);
    }

    static CompletableFuture<ChunkAccess> generateCarvers(
        WorldGenContext worldGenContext, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk
    ) {
        ServerLevel serverlevel = worldGenContext.level();
        WorldGenRegion worldgenregion = new WorldGenRegion(serverlevel, cache, step, chunk);
        if (chunk instanceof ProtoChunk protochunk) {
            Blender.addAroundOldChunksCarvingMaskFilter(worldgenregion, protochunk);
        }

        worldGenContext.generator()
            .applyCarvers(
                worldgenregion,
                serverlevel.getSeed(),
                serverlevel.getChunkSource().randomState(),
                serverlevel.getBiomeManager(),
                serverlevel.structureManager().forWorldGenRegion(worldgenregion),
                chunk,
                GenerationStep.Carving.AIR
            );
        return CompletableFuture.completedFuture(chunk);
    }

    static CompletableFuture<ChunkAccess> generateFeatures(
        WorldGenContext worldGenContext, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk
    ) {
        ServerLevel serverlevel = worldGenContext.level();
        Heightmap.primeHeightmaps(
            chunk,
            EnumSet.of(Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Heightmap.Types.OCEAN_FLOOR, Heightmap.Types.WORLD_SURFACE)
        );
        WorldGenRegion worldgenregion = new WorldGenRegion(serverlevel, cache, step, chunk);
        worldGenContext.generator().applyBiomeDecoration(worldgenregion, chunk, serverlevel.structureManager().forWorldGenRegion(worldgenregion));
        Blender.generateBorderTicks(worldgenregion, chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    static CompletableFuture<ChunkAccess> initializeLight(
        WorldGenContext worldGenContext, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk
    ) {
        ThreadedLevelLightEngine threadedlevellightengine = worldGenContext.lightEngine();
        chunk.initializeLightSources();
        ((ProtoChunk)chunk).setLightEngine(threadedlevellightengine);
        boolean flag = isLighted(chunk);
        return threadedlevellightengine.initializeLight(chunk, flag);
    }

    static CompletableFuture<ChunkAccess> light(
        WorldGenContext worldGenContext, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk
    ) {
        boolean flag = isLighted(chunk);
        return worldGenContext.lightEngine().lightChunk(chunk, flag);
    }

    static CompletableFuture<ChunkAccess> generateSpawn(
        WorldGenContext worldGenContext, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk
    ) {
        if (!chunk.isUpgrading()) {
            worldGenContext.generator().spawnOriginalMobs(new WorldGenRegion(worldGenContext.level(), cache, step, chunk));
        }

        return CompletableFuture.completedFuture(chunk);
    }

    static CompletableFuture<ChunkAccess> full(
        WorldGenContext worldGenContext, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk
    ) {
        ChunkPos chunkpos = chunk.getPos();
        GenerationChunkHolder generationchunkholder = cache.get(chunkpos.x, chunkpos.z);
        return CompletableFuture.supplyAsync(
            () -> {
                ProtoChunk protochunk = (ProtoChunk)chunk;
                ServerLevel serverlevel = worldGenContext.level();
                LevelChunk levelchunk;
                if (protochunk instanceof ImposterProtoChunk) {
                    levelchunk = ((ImposterProtoChunk)protochunk).getWrapped();
                } else {
                    levelchunk = new LevelChunk(serverlevel, protochunk, p_347400_ -> postLoadProtoChunk(serverlevel, protochunk.getEntities()));
                    generationchunkholder.replaceProtoChunk(new ImposterProtoChunk(levelchunk, false));
                }

                levelchunk.setFullStatus(generationchunkholder::getFullStatus);
                try {
                generationchunkholder.currentlyLoading = levelchunk; // Neo: bypass the future chain when getChunk is called, this prevents deadlocks.
                levelchunk.runPostLoad();
                } finally {
                    generationchunkholder.currentlyLoading = null; // Neo: Stop bypassing the future chain.
                }
                levelchunk.setLoaded(true);
                try {
                generationchunkholder.currentlyLoading = levelchunk; // Neo: bypass the future chain when getChunk is called, this prevents deadlocks.
                levelchunk.registerAllBlockEntitiesAfterLevelLoad();
                levelchunk.registerTickContainerInLevel(serverlevel);
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.ChunkEvent.Load(levelchunk, !(protochunk instanceof ImposterProtoChunk)));
                } finally {
                    generationchunkholder.currentlyLoading = null; // Neo: Stop bypassing the future chain.
                }
                return levelchunk;
            },
            p_347404_ -> worldGenContext.mainThreadMailBox()
                    .tell(ChunkTaskPriorityQueueSorter.message(p_347404_, chunkpos.toLong(), generationchunkholder::getTicketLevel))
        );
    }

    private static void postLoadProtoChunk(ServerLevel level, List<CompoundTag> entityTags) {
        if (!entityTags.isEmpty()) {
            level.addWorldGenChunkEntities(EntityType.loadEntitiesRecursive(entityTags, level));
        }
    }
}
