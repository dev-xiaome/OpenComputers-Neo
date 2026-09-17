package net.minecraft.world.level.levelgen;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.BlockColumn;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public class SurfaceSystem {
    private static final BlockState WHITE_TERRACOTTA = Blocks.WHITE_TERRACOTTA.defaultBlockState();
    private static final BlockState ORANGE_TERRACOTTA = Blocks.ORANGE_TERRACOTTA.defaultBlockState();
    private static final BlockState TERRACOTTA = Blocks.TERRACOTTA.defaultBlockState();
    private static final BlockState YELLOW_TERRACOTTA = Blocks.YELLOW_TERRACOTTA.defaultBlockState();
    private static final BlockState BROWN_TERRACOTTA = Blocks.BROWN_TERRACOTTA.defaultBlockState();
    private static final BlockState RED_TERRACOTTA = Blocks.RED_TERRACOTTA.defaultBlockState();
    private static final BlockState LIGHT_GRAY_TERRACOTTA = Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState();
    private static final BlockState PACKED_ICE = Blocks.PACKED_ICE.defaultBlockState();
    private static final BlockState SNOW_BLOCK = Blocks.SNOW_BLOCK.defaultBlockState();
    private final BlockState defaultBlock;
    private final int seaLevel;
    private final BlockState[] clayBands;
    private final NormalNoise clayBandsOffsetNoise;
    private final NormalNoise badlandsPillarNoise;
    private final NormalNoise badlandsPillarRoofNoise;
    private final NormalNoise badlandsSurfaceNoise;
    private final NormalNoise icebergPillarNoise;
    private final NormalNoise icebergPillarRoofNoise;
    private final NormalNoise icebergSurfaceNoise;
    private final PositionalRandomFactory noiseRandom;
    private final NormalNoise surfaceNoise;
    private final NormalNoise surfaceSecondaryNoise;

    public SurfaceSystem(RandomState randomState, BlockState defaultBlock, int seaLevel, PositionalRandomFactory noiseRandom) {
        this.defaultBlock = defaultBlock;
        this.seaLevel = seaLevel;
        this.noiseRandom = noiseRandom;
        this.clayBandsOffsetNoise = randomState.getOrCreateNoise(Noises.CLAY_BANDS_OFFSET);
        this.clayBands = generateBands(noiseRandom.fromHashOf(ResourceLocation.withDefaultNamespace("clay_bands")));
        this.surfaceNoise = randomState.getOrCreateNoise(Noises.SURFACE);
        this.surfaceSecondaryNoise = randomState.getOrCreateNoise(Noises.SURFACE_SECONDARY);
        this.badlandsPillarNoise = randomState.getOrCreateNoise(Noises.BADLANDS_PILLAR);
        this.badlandsPillarRoofNoise = randomState.getOrCreateNoise(Noises.BADLANDS_PILLAR_ROOF);
        this.badlandsSurfaceNoise = randomState.getOrCreateNoise(Noises.BADLANDS_SURFACE);
        this.icebergPillarNoise = randomState.getOrCreateNoise(Noises.ICEBERG_PILLAR);
        this.icebergPillarRoofNoise = randomState.getOrCreateNoise(Noises.ICEBERG_PILLAR_ROOF);
        this.icebergSurfaceNoise = randomState.getOrCreateNoise(Noises.ICEBERG_SURFACE);
    }

    public void buildSurface(
        RandomState randomState,
        BiomeManager biomeManager,
        Registry<Biome> biomes,
        boolean useLegacyRandomSource,
        WorldGenerationContext context,
        final ChunkAccess chunk,
        NoiseChunk noiseChunk,
        SurfaceRules.RuleSource ruleSource
    ) {
        final BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();
        final ChunkPos chunkpos = chunk.getPos();
        int i = chunkpos.getMinBlockX();
        int j = chunkpos.getMinBlockZ();
        BlockColumn blockcolumn = new BlockColumn() {
            @Override
            public BlockState getBlock(int p_190006_) {
                return chunk.getBlockState(blockpos$mutableblockpos.setY(p_190006_));
            }

            @Override
            public void setBlock(int p_190008_, BlockState p_190009_) {
                LevelHeightAccessor levelheightaccessor = chunk.getHeightAccessorForGeneration();
                if (p_190008_ >= levelheightaccessor.getMinBuildHeight() && p_190008_ < levelheightaccessor.getMaxBuildHeight()) {
                    chunk.setBlockState(blockpos$mutableblockpos.setY(p_190008_), p_190009_, false);
                    if (!p_190009_.getFluidState().isEmpty()) {
                        chunk.markPosForPostprocessing(blockpos$mutableblockpos);
                    }
                }
            }

            @Override
            public String toString() {
                return "ChunkBlockColumn " + chunkpos;
            }
        };
        SurfaceRules.Context surfacerules$context = new SurfaceRules.Context(this, randomState, chunk, noiseChunk, biomeManager::getBiome, biomes, context);
        SurfaceRules.SurfaceRule surfacerules$surfacerule = ruleSource.apply(surfacerules$context);
        BlockPos.MutableBlockPos blockpos$mutableblockpos1 = new BlockPos.MutableBlockPos();

        for (int k = 0; k < 16; k++) {
            for (int l = 0; l < 16; l++) {
                int i1 = i + k;
                int j1 = j + l;
                int k1 = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, k, l) + 1;
                blockpos$mutableblockpos.setX(i1).setZ(j1);
                Holder<Biome> holder = biomeManager.getBiome(blockpos$mutableblockpos1.set(i1, useLegacyRandomSource ? 0 : k1, j1));
                if (holder.is(Biomes.ERODED_BADLANDS)) {
                    this.erodedBadlandsExtension(blockcolumn, i1, j1, k1, chunk);
                }

                int l1 = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, k, l) + 1;
                surfacerules$context.updateXZ(i1, j1);
                int i2 = 0;
                int j2 = Integer.MIN_VALUE;
                int k2 = Integer.MAX_VALUE;
                int l2 = chunk.getMinBuildHeight();

                for (int i3 = l1; i3 >= l2; i3--) {
                    BlockState blockstate = blockcolumn.getBlock(i3);
                    if (blockstate.isAir()) {
                        i2 = 0;
                        j2 = Integer.MIN_VALUE;
                    } else if (!blockstate.getFluidState().isEmpty()) {
                        if (j2 == Integer.MIN_VALUE) {
                            j2 = i3 + 1;
                        }
                    } else {
                        if (k2 >= i3) {
                            k2 = DimensionType.WAY_BELOW_MIN_Y;

                            for (int j3 = i3 - 1; j3 >= l2 - 1; j3--) {
                                BlockState blockstate1 = blockcolumn.getBlock(j3);
                                if (!this.isStone(blockstate1)) {
                                    k2 = j3 + 1;
                                    break;
                                }
                            }
                        }

                        i2++;
                        int k3 = i3 - k2 + 1;
                        surfacerules$context.updateY(i2, k3, j2, i1, i3, j1);
                        if (blockstate == this.defaultBlock) {
                            BlockState blockstate2 = surfacerules$surfacerule.tryApply(i1, i3, j1);
                            if (blockstate2 != null) {
                                blockcolumn.setBlock(i3, blockstate2);
                            }
                        }
                    }
                }

                if (holder.is(Biomes.FROZEN_OCEAN) || holder.is(Biomes.DEEP_FROZEN_OCEAN)) {
                    this.frozenOceanExtension(surfacerules$context.getMinSurfaceLevel(), holder.value(), blockcolumn, blockpos$mutableblockpos1, i1, j1, k1);
                }
            }
        }
    }

    protected int getSurfaceDepth(int x, int z) {
        double d0 = this.surfaceNoise.getValue((double)x, 0.0, (double)z);
        return (int)(d0 * 2.75 + 3.0 + this.noiseRandom.at(x, 0, z).nextDouble() * 0.25);
    }

    protected double getSurfaceSecondary(int x, int z) {
        return this.surfaceSecondaryNoise.getValue((double)x, 0.0, (double)z);
    }

    private boolean isStone(BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty();
    }

    @Deprecated
    public Optional<BlockState> topMaterial(
        SurfaceRules.RuleSource rule,
        CarvingContext context,
        Function<BlockPos, Holder<Biome>> biomeGetter,
        ChunkAccess chunk,
        NoiseChunk noiseChunk,
        BlockPos pos,
        boolean hasFluid
    ) {
        SurfaceRules.Context surfacerules$context = new SurfaceRules.Context(
            this, context.randomState(), chunk, noiseChunk, biomeGetter, context.registryAccess().registryOrThrow(Registries.BIOME), context
        );
        SurfaceRules.SurfaceRule surfacerules$surfacerule = rule.apply(surfacerules$context);
        int i = pos.getX();
        int j = pos.getY();
        int k = pos.getZ();
        surfacerules$context.updateXZ(i, k);
        surfacerules$context.updateY(1, 1, hasFluid ? j + 1 : Integer.MIN_VALUE, i, j, k);
        BlockState blockstate = surfacerules$surfacerule.tryApply(i, j, k);
        return Optional.ofNullable(blockstate);
    }

    private void erodedBadlandsExtension(BlockColumn blockColumn, int x, int z, int height, LevelHeightAccessor level) {
        double d0 = 0.2;
        double d1 = Math.min(
            Math.abs(this.badlandsSurfaceNoise.getValue((double)x, 0.0, (double)z) * 8.25),
            this.badlandsPillarNoise.getValue((double)x * 0.2, 0.0, (double)z * 0.2) * 15.0
        );
        if (!(d1 <= 0.0)) {
            double d2 = 0.75;
            double d3 = 1.5;
            double d4 = Math.abs(this.badlandsPillarRoofNoise.getValue((double)x * 0.75, 0.0, (double)z * 0.75) * 1.5);
            double d5 = 64.0 + Math.min(d1 * d1 * 2.5, Math.ceil(d4 * 50.0) + 24.0);
            int i = Mth.floor(d5);
            if (height <= i) {
                for (int j = i; j >= level.getMinBuildHeight(); j--) {
                    BlockState blockstate = blockColumn.getBlock(j);
                    if (blockstate.is(this.defaultBlock.getBlock())) {
                        break;
                    }

                    if (blockstate.is(Blocks.WATER)) {
                        return;
                    }
                }

                for (int k = i; k >= level.getMinBuildHeight() && blockColumn.getBlock(k).isAir(); k--) {
                    blockColumn.setBlock(k, this.defaultBlock);
                }
            }
        }
    }

    private void frozenOceanExtension(
        int minSurfaceLevel, Biome biome, BlockColumn blockColumn, BlockPos.MutableBlockPos topWaterPos, int x, int z, int height
    ) {
        double d0 = 1.28;
        double d1 = Math.min(
            Math.abs(this.icebergSurfaceNoise.getValue((double)x, 0.0, (double)z) * 8.25),
            this.icebergPillarNoise.getValue((double)x * 1.28, 0.0, (double)z * 1.28) * 15.0
        );
        if (!(d1 <= 1.8)) {
            double d3 = 1.17;
            double d4 = 1.5;
            double d5 = Math.abs(this.icebergPillarRoofNoise.getValue((double)x * 1.17, 0.0, (double)z * 1.17) * 1.5);
            double d6 = Math.min(d1 * d1 * 1.2, Math.ceil(d5 * 40.0) + 14.0);
            if (biome.shouldMeltFrozenOceanIcebergSlightly(topWaterPos.set(x, 63, z))) {
                d6 -= 2.0;
            }

            double d2;
            if (d6 > 2.0) {
                d2 = (double)this.seaLevel - d6 - 7.0;
                d6 += (double)this.seaLevel;
            } else {
                d6 = 0.0;
                d2 = 0.0;
            }

            double d7 = d6;
            RandomSource randomsource = this.noiseRandom.at(x, 0, z);
            int i = 2 + randomsource.nextInt(4);
            int j = this.seaLevel + 18 + randomsource.nextInt(10);
            int k = 0;

            for (int l = Math.max(height, (int)d6 + 1); l >= minSurfaceLevel; l--) {
                if (blockColumn.getBlock(l).isAir() && l < (int)d7 && randomsource.nextDouble() > 0.01
                    || blockColumn.getBlock(l).is(Blocks.WATER) && l > (int)d2 && l < this.seaLevel && d2 != 0.0 && randomsource.nextDouble() > 0.15) {
                    if (k <= i && l > j) {
                        blockColumn.setBlock(l, SNOW_BLOCK);
                        k++;
                    } else {
                        blockColumn.setBlock(l, PACKED_ICE);
                    }
                }
            }
        }
    }

    private static BlockState[] generateBands(RandomSource random) {
        BlockState[] ablockstate = new BlockState[192];
        Arrays.fill(ablockstate, TERRACOTTA);

        for (int k = 0; k < ablockstate.length; k++) {
            k += random.nextInt(5) + 1;
            if (k < ablockstate.length) {
                ablockstate[k] = ORANGE_TERRACOTTA;
            }
        }

        makeBands(random, ablockstate, 1, YELLOW_TERRACOTTA);
        makeBands(random, ablockstate, 2, BROWN_TERRACOTTA);
        makeBands(random, ablockstate, 1, RED_TERRACOTTA);
        int l = random.nextIntBetweenInclusive(9, 15);
        int i = 0;

        for (int j = 0; i < l && j < ablockstate.length; j += random.nextInt(16) + 4) {
            ablockstate[j] = WHITE_TERRACOTTA;
            if (j - 1 > 0 && random.nextBoolean()) {
                ablockstate[j - 1] = LIGHT_GRAY_TERRACOTTA;
            }

            if (j + 1 < ablockstate.length && random.nextBoolean()) {
                ablockstate[j + 1] = LIGHT_GRAY_TERRACOTTA;
            }

            i++;
        }

        return ablockstate;
    }

    private static void makeBands(RandomSource random, BlockState[] output, int minSize, BlockState state) {
        int i = random.nextIntBetweenInclusive(6, 15);

        for (int j = 0; j < i; j++) {
            int k = minSize + random.nextInt(3);
            int l = random.nextInt(output.length);

            for (int i1 = 0; l + i1 < output.length && i1 < k; i1++) {
                output[l + i1] = state;
            }
        }
    }

    protected BlockState getBand(int x, int y, int z) {
        int i = (int)Math.round(this.clayBandsOffsetNoise.getValue((double)x, 0.0, (double)z) * 4.0);
        return this.clayBands[(y + i + this.clayBands.length) % this.clayBands.length];
    }
}
