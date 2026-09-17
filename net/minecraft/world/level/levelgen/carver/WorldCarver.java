package net.minecraft.world.level.levelgen.carver;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.apache.commons.lang3.mutable.MutableBoolean;

public abstract class WorldCarver<C extends CarverConfiguration> {
    public static final WorldCarver<CaveCarverConfiguration> CAVE = register("cave", new CaveWorldCarver(CaveCarverConfiguration.CODEC));
    public static final WorldCarver<CaveCarverConfiguration> NETHER_CAVE = register("nether_cave", new NetherWorldCarver(CaveCarverConfiguration.CODEC));
    public static final WorldCarver<CanyonCarverConfiguration> CANYON = register("canyon", new CanyonWorldCarver(CanyonCarverConfiguration.CODEC));
    protected static final BlockState AIR = Blocks.AIR.defaultBlockState();
    protected static final BlockState CAVE_AIR = Blocks.CAVE_AIR.defaultBlockState();
    protected static final FluidState WATER = Fluids.WATER.defaultFluidState();
    protected static final FluidState LAVA = Fluids.LAVA.defaultFluidState();
    protected Set<Fluid> liquids = ImmutableSet.of(Fluids.WATER);
    private final MapCodec<ConfiguredWorldCarver<C>> configuredCodec;

    private static <C extends CarverConfiguration, F extends WorldCarver<C>> F register(String key, F carver) {
        return Registry.register(BuiltInRegistries.CARVER, key, carver);
    }

    public WorldCarver(Codec<C> codec) {
        this.configuredCodec = codec.fieldOf("config").xmap(this::configured, ConfiguredWorldCarver::config);
    }

    public ConfiguredWorldCarver<C> configured(C config) {
        return new ConfiguredWorldCarver<>(this, config);
    }

    public MapCodec<ConfiguredWorldCarver<C>> configuredCodec() {
        return this.configuredCodec;
    }

    public int getRange() {
        return 4;
    }

    /**
     * Carves blocks in an ellipsoid (more accurately a spheroid), defined by a center (x, y, z) position, with a horizontal and vertical radius (the semi-axes)
     *
     * @param skipChecker Used to skip certain blocks within the carved region.
     */
    protected boolean carveEllipsoid(
        CarvingContext context,
        C config,
        ChunkAccess chunk,
        Function<BlockPos, Holder<Biome>> biomeAccessor,
        Aquifer aquifer,
        double x,
        double y,
        double z,
        double horizontalRadius,
        double verticalRadius,
        CarvingMask carvingMask,
        WorldCarver.CarveSkipChecker skipChecker
    ) {
        ChunkPos chunkpos = chunk.getPos();
        double d0 = (double)chunkpos.getMiddleBlockX();
        double d1 = (double)chunkpos.getMiddleBlockZ();
        double d2 = 16.0 + horizontalRadius * 2.0;
        if (!(Math.abs(x - d0) > d2) && !(Math.abs(z - d1) > d2)) {
            int i = chunkpos.getMinBlockX();
            int j = chunkpos.getMinBlockZ();
            int k = Math.max(Mth.floor(x - horizontalRadius) - i - 1, 0);
            int l = Math.min(Mth.floor(x + horizontalRadius) - i, 15);
            int i1 = Math.max(Mth.floor(y - verticalRadius) - 1, context.getMinGenY() + 1);
            int j1 = chunk.isUpgrading() ? 0 : 7;
            int k1 = Math.min(Mth.floor(y + verticalRadius) + 1, context.getMinGenY() + context.getGenDepth() - 1 - j1);
            int l1 = Math.max(Mth.floor(z - horizontalRadius) - j - 1, 0);
            int i2 = Math.min(Mth.floor(z + horizontalRadius) - j, 15);
            boolean flag = false;
            BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();
            BlockPos.MutableBlockPos blockpos$mutableblockpos1 = new BlockPos.MutableBlockPos();

            for (int j2 = k; j2 <= l; j2++) {
                int k2 = chunkpos.getBlockX(j2);
                double d3 = ((double)k2 + 0.5 - x) / horizontalRadius;

                for (int l2 = l1; l2 <= i2; l2++) {
                    int i3 = chunkpos.getBlockZ(l2);
                    double d4 = ((double)i3 + 0.5 - z) / horizontalRadius;
                    if (!(d3 * d3 + d4 * d4 >= 1.0)) {
                        MutableBoolean mutableboolean = new MutableBoolean(false);

                        for (int j3 = k1; j3 > i1; j3--) {
                            double d5 = ((double)j3 - 0.5 - y) / verticalRadius;
                            if (!skipChecker.shouldSkip(context, d3, d5, d4, j3) && (!carvingMask.get(j2, j3, l2) || isDebugEnabled(config))) {
                                carvingMask.set(j2, j3, l2);
                                blockpos$mutableblockpos.set(k2, j3, i3);
                                flag |= this.carveBlock(
                                    context,
                                    config,
                                    chunk,
                                    biomeAccessor,
                                    carvingMask,
                                    blockpos$mutableblockpos,
                                    blockpos$mutableblockpos1,
                                    aquifer,
                                    mutableboolean
                                );
                            }
                        }
                    }
                }
            }

            return flag;
        } else {
            return false;
        }
    }

    /**
     * Carves a single block, replacing it with the appropriate state if possible, and handles replacing exposed dirt with grass.
     *
     * @param pos            The position to carve at. The method does not mutate this
     *                       position.
     * @param checkPos       An additional mutable block position object to be used
     *                       and modified by the method
     * @param reachedSurface Set to true if the block carved was the surface, which is
     *                       checked as being either grass or mycelium
     */
    protected boolean carveBlock(
        CarvingContext context,
        C config,
        ChunkAccess chunk,
        Function<BlockPos, Holder<Biome>> biomeGetter,
        CarvingMask carvingMask,
        BlockPos.MutableBlockPos pos,
        BlockPos.MutableBlockPos checkPos,
        Aquifer aquifer,
        MutableBoolean reachedSurface
    ) {
        BlockState blockstate = chunk.getBlockState(pos);
        if (blockstate.is(Blocks.GRASS_BLOCK) || blockstate.is(Blocks.MYCELIUM)) {
            reachedSurface.setTrue();
        }

        if (!this.canReplaceBlock(config, blockstate) && !isDebugEnabled(config)) {
            return false;
        } else {
            BlockState blockstate1 = this.getCarveState(context, config, pos, aquifer);
            if (blockstate1 == null) {
                return false;
            } else {
                chunk.setBlockState(pos, blockstate1, false);
                if (aquifer.shouldScheduleFluidUpdate() && !blockstate1.getFluidState().isEmpty()) {
                    chunk.markPosForPostprocessing(pos);
                }

                if (reachedSurface.isTrue()) {
                    checkPos.setWithOffset(pos, Direction.DOWN);
                    if (chunk.getBlockState(checkPos).is(Blocks.DIRT)) {
                        context.topMaterial(biomeGetter, chunk, checkPos, !blockstate1.getFluidState().isEmpty()).ifPresent(p_284918_ -> {
                            chunk.setBlockState(checkPos, p_284918_, false);
                            if (!p_284918_.getFluidState().isEmpty()) {
                                chunk.markPosForPostprocessing(checkPos);
                            }
                        });
                    }
                }

                return true;
            }
        }
    }

    @Nullable
    private BlockState getCarveState(CarvingContext context, C config, BlockPos pos, Aquifer aquifer) {
        if (pos.getY() <= config.lavaLevel.resolveY(context)) {
            return LAVA.createLegacyBlock();
        } else {
            BlockState blockstate = aquifer.computeSubstance(
                new DensityFunction.SinglePointContext(pos.getX(), pos.getY(), pos.getZ()), 0.0
            );
            if (blockstate == null) {
                return isDebugEnabled(config) ? config.debugSettings.getBarrierState() : null;
            } else {
                return isDebugEnabled(config) ? getDebugState(config, blockstate) : blockstate;
            }
        }
    }

    private static BlockState getDebugState(CarverConfiguration config, BlockState state) {
        if (state.is(Blocks.AIR)) {
            return config.debugSettings.getAirState();
        } else if (state.is(Blocks.WATER)) {
            BlockState blockstate = config.debugSettings.getWaterState();
            return blockstate.hasProperty(BlockStateProperties.WATERLOGGED)
                ? blockstate.setValue(BlockStateProperties.WATERLOGGED, Boolean.valueOf(true))
                : blockstate;
        } else {
            return state.is(Blocks.LAVA) ? config.debugSettings.getLavaState() : state;
        }
    }

    /**
     * Carves the given chunk with caves that originate from the given {@code chunkPos}.
     * This method is invoked 289 times in order to generate each chunk (once for every position in an 8 chunk radius, or 17x17 chunk area, centered around the target chunk).
     *
     * @see net.minecraft.world.level.chunk.ChunkGenerator#applyCarvers
     *
     * @param chunk    The chunk to be carved
     * @param chunkPos The chunk position this carver is being called from
     */
    public abstract boolean carve(
        CarvingContext context,
        C config,
        ChunkAccess chunk,
        Function<BlockPos, Holder<Biome>> biomeAccessor,
        RandomSource random,
        Aquifer aquifer,
        ChunkPos chunkPos,
        CarvingMask carvingMask
    );

    public abstract boolean isStartChunk(C config, RandomSource random);

    protected boolean canReplaceBlock(C config, BlockState state) {
        return state.is(config.replaceable);
    }

    protected static boolean canReach(ChunkPos chunkPos, double x, double z, int branchIndex, int branchCount, float width) {
        double d0 = (double)chunkPos.getMiddleBlockX();
        double d1 = (double)chunkPos.getMiddleBlockZ();
        double d2 = x - d0;
        double d3 = z - d1;
        double d4 = (double)(branchCount - branchIndex);
        double d5 = (double)(width + 2.0F + 16.0F);
        return d2 * d2 + d3 * d3 - d4 * d4 <= d5 * d5;
    }

    private static boolean isDebugEnabled(CarverConfiguration config) {
        return config.debugSettings.isDebugMode();
    }

    /**
     * Used to define certain positions to skip or ignore when carving.
     */
    public interface CarveSkipChecker {
        boolean shouldSkip(CarvingContext context, double relativeX, double relativeY, double relativeZ, int y);
    }
}
