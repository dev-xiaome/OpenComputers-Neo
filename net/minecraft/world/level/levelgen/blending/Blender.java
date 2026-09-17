package net.minecraft.world.level.levelgen.blending;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.ImmutableMap.Builder;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction8;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.data.worldgen.NoiseData;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.material.FluidState;
import org.apache.commons.lang3.mutable.MutableDouble;
import org.apache.commons.lang3.mutable.MutableObject;

public class Blender {
    private static final Blender EMPTY = new Blender(new Long2ObjectOpenHashMap(), new Long2ObjectOpenHashMap()) {
        @Override
        public Blender.BlendingOutput blendOffsetAndFactor(int p_209724_, int p_209725_) {
            return new Blender.BlendingOutput(1.0, 0.0);
        }

        @Override
        public double blendDensity(DensityFunction.FunctionContext p_209727_, double p_209728_) {
            return p_209728_;
        }

        @Override
        public BiomeResolver getBiomeResolver(BiomeResolver p_190232_) {
            return p_190232_;
        }
    };
    private static final NormalNoise SHIFT_NOISE = NormalNoise.create(new XoroshiroRandomSource(42L), NoiseData.DEFAULT_SHIFT);
    private static final int HEIGHT_BLENDING_RANGE_CELLS = QuartPos.fromSection(7) - 1;
    private static final int HEIGHT_BLENDING_RANGE_CHUNKS = QuartPos.toSection(HEIGHT_BLENDING_RANGE_CELLS + 3);
    private static final int DENSITY_BLENDING_RANGE_CELLS = 2;
    private static final int DENSITY_BLENDING_RANGE_CHUNKS = QuartPos.toSection(5);
    private static final double OLD_CHUNK_XZ_RADIUS = 8.0;
    private final Long2ObjectOpenHashMap<BlendingData> heightAndBiomeBlendingData;
    private final Long2ObjectOpenHashMap<BlendingData> densityBlendingData;

    public static Blender empty() {
        return EMPTY;
    }

    public static Blender of(@Nullable WorldGenRegion region) {
        if (region == null) {
            return EMPTY;
        } else {
            ChunkPos chunkpos = region.getCenter();
            if (!region.isOldChunkAround(chunkpos, HEIGHT_BLENDING_RANGE_CHUNKS)) {
                return EMPTY;
            } else {
                Long2ObjectOpenHashMap<BlendingData> long2objectopenhashmap = new Long2ObjectOpenHashMap<>();
                Long2ObjectOpenHashMap<BlendingData> long2objectopenhashmap1 = new Long2ObjectOpenHashMap<>();
                int i = Mth.square(HEIGHT_BLENDING_RANGE_CHUNKS + 1);

                for (int j = -HEIGHT_BLENDING_RANGE_CHUNKS; j <= HEIGHT_BLENDING_RANGE_CHUNKS; j++) {
                    for (int k = -HEIGHT_BLENDING_RANGE_CHUNKS; k <= HEIGHT_BLENDING_RANGE_CHUNKS; k++) {
                        if (j * j + k * k <= i) {
                            int l = chunkpos.x + j;
                            int i1 = chunkpos.z + k;
                            BlendingData blendingdata = BlendingData.getOrUpdateBlendingData(region, l, i1);
                            if (blendingdata != null) {
                                long2objectopenhashmap.put(ChunkPos.asLong(l, i1), blendingdata);
                                if (j >= -DENSITY_BLENDING_RANGE_CHUNKS
                                    && j <= DENSITY_BLENDING_RANGE_CHUNKS
                                    && k >= -DENSITY_BLENDING_RANGE_CHUNKS
                                    && k <= DENSITY_BLENDING_RANGE_CHUNKS) {
                                    long2objectopenhashmap1.put(ChunkPos.asLong(l, i1), blendingdata);
                                }
                            }
                        }
                    }
                }

                return long2objectopenhashmap.isEmpty() && long2objectopenhashmap1.isEmpty()
                    ? EMPTY
                    : new Blender(long2objectopenhashmap, long2objectopenhashmap1);
            }
        }
    }

    Blender(Long2ObjectOpenHashMap<BlendingData> heightAndBiomeBlendingData, Long2ObjectOpenHashMap<BlendingData> densityBlendingData) {
        this.heightAndBiomeBlendingData = heightAndBiomeBlendingData;
        this.densityBlendingData = densityBlendingData;
    }

    public Blender.BlendingOutput blendOffsetAndFactor(int x, int z) {
        int i = QuartPos.fromBlock(x);
        int j = QuartPos.fromBlock(z);
        double d0 = this.getBlendingDataValue(i, 0, j, BlendingData::getHeight);
        if (d0 != Double.MAX_VALUE) {
            return new Blender.BlendingOutput(0.0, heightToOffset(d0));
        } else {
            MutableDouble mutabledouble = new MutableDouble(0.0);
            MutableDouble mutabledouble1 = new MutableDouble(0.0);
            MutableDouble mutabledouble2 = new MutableDouble(Double.POSITIVE_INFINITY);
            this.heightAndBiomeBlendingData
                .forEach(
                    (p_202249_, p_202250_) -> p_202250_.iterateHeights(
                            QuartPos.fromSection(ChunkPos.getX(p_202249_)),
                            QuartPos.fromSection(ChunkPos.getZ(p_202249_)),
                            (p_190199_, p_190200_, p_190201_) -> {
                                double d3 = Mth.length((double)(i - p_190199_), (double)(j - p_190200_));
                                if (!(d3 > (double)HEIGHT_BLENDING_RANGE_CELLS)) {
                                    if (d3 < mutabledouble2.doubleValue()) {
                                        mutabledouble2.setValue(d3);
                                    }

                                    double d4 = 1.0 / (d3 * d3 * d3 * d3);
                                    mutabledouble1.add(p_190201_ * d4);
                                    mutabledouble.add(d4);
                                }
                            }
                        )
                );
            if (mutabledouble2.doubleValue() == Double.POSITIVE_INFINITY) {
                return new Blender.BlendingOutput(1.0, 0.0);
            } else {
                double d1 = mutabledouble1.doubleValue() / mutabledouble.doubleValue();
                double d2 = Mth.clamp(mutabledouble2.doubleValue() / (double)(HEIGHT_BLENDING_RANGE_CELLS + 1), 0.0, 1.0);
                d2 = 3.0 * d2 * d2 - 2.0 * d2 * d2 * d2;
                return new Blender.BlendingOutput(d2, heightToOffset(d1));
            }
        }
    }

    private static double heightToOffset(double height) {
        double d0 = 1.0;
        double d1 = height + 0.5;
        double d2 = Mth.positiveModulo(d1, 8.0);
        return 1.0 * (32.0 * (d1 - 128.0) - 3.0 * (d1 - 120.0) * d2 + 3.0 * d2 * d2) / (128.0 * (32.0 - 3.0 * d2));
    }

    public double blendDensity(DensityFunction.FunctionContext context, double density) {
        int i = QuartPos.fromBlock(context.blockX());
        int j = context.blockY() / 8;
        int k = QuartPos.fromBlock(context.blockZ());
        double d0 = this.getBlendingDataValue(i, j, k, BlendingData::getDensity);
        if (d0 != Double.MAX_VALUE) {
            return d0;
        } else {
            MutableDouble mutabledouble = new MutableDouble(0.0);
            MutableDouble mutabledouble1 = new MutableDouble(0.0);
            MutableDouble mutabledouble2 = new MutableDouble(Double.POSITIVE_INFINITY);
            this.densityBlendingData
                .forEach(
                    (p_202241_, p_202242_) -> p_202242_.iterateDensities(
                            QuartPos.fromSection(ChunkPos.getX(p_202241_)),
                            QuartPos.fromSection(ChunkPos.getZ(p_202241_)),
                            j - 1,
                            j + 1,
                            (p_202230_, p_202231_, p_202232_, p_202233_) -> {
                                double d3 = Mth.length((double)(i - p_202230_), (double)((j - p_202231_) * 2), (double)(k - p_202232_));
                                if (!(d3 > 2.0)) {
                                    if (d3 < mutabledouble2.doubleValue()) {
                                        mutabledouble2.setValue(d3);
                                    }

                                    double d4 = 1.0 / (d3 * d3 * d3 * d3);
                                    mutabledouble1.add(p_202233_ * d4);
                                    mutabledouble.add(d4);
                                }
                            }
                        )
                );
            if (mutabledouble2.doubleValue() == Double.POSITIVE_INFINITY) {
                return density;
            } else {
                double d1 = mutabledouble1.doubleValue() / mutabledouble.doubleValue();
                double d2 = Mth.clamp(mutabledouble2.doubleValue() / 3.0, 0.0, 1.0);
                return Mth.lerp(d2, d1, density);
            }
        }
    }

    private double getBlendingDataValue(int x, int y, int z, Blender.CellValueGetter getter) {
        int i = QuartPos.toSection(x);
        int j = QuartPos.toSection(z);
        boolean flag = (x & 3) == 0;
        boolean flag1 = (z & 3) == 0;
        double d0 = this.getBlendingDataValue(getter, i, j, x, y, z);
        if (d0 == Double.MAX_VALUE) {
            if (flag && flag1) {
                d0 = this.getBlendingDataValue(getter, i - 1, j - 1, x, y, z);
            }

            if (d0 == Double.MAX_VALUE) {
                if (flag) {
                    d0 = this.getBlendingDataValue(getter, i - 1, j, x, y, z);
                }

                if (d0 == Double.MAX_VALUE && flag1) {
                    d0 = this.getBlendingDataValue(getter, i, j - 1, x, y, z);
                }
            }
        }

        return d0;
    }

    private double getBlendingDataValue(Blender.CellValueGetter getter, int sectionX, int sectionZ, int x, int y, int z) {
        BlendingData blendingdata = this.heightAndBiomeBlendingData.get(ChunkPos.asLong(sectionX, sectionZ));
        return blendingdata != null
            ? getter.get(blendingdata, x - QuartPos.fromSection(sectionX), y, z - QuartPos.fromSection(sectionZ))
            : Double.MAX_VALUE;
    }

    public BiomeResolver getBiomeResolver(BiomeResolver resolver) {
        return (p_204669_, p_204670_, p_204671_, p_204672_) -> {
            Holder<Biome> holder = this.blendBiome(p_204669_, p_204670_, p_204671_);
            return holder == null ? resolver.getNoiseBiome(p_204669_, p_204670_, p_204671_, p_204672_) : holder;
        };
    }

    @Nullable
    private Holder<Biome> blendBiome(int x, int y, int z) {
        MutableDouble mutabledouble = new MutableDouble(Double.POSITIVE_INFINITY);
        MutableObject<Holder<Biome>> mutableobject = new MutableObject<>();
        this.heightAndBiomeBlendingData
            .forEach(
                (p_224716_, p_224717_) -> p_224717_.iterateBiomes(
                        QuartPos.fromSection(ChunkPos.getX(p_224716_)),
                        y,
                        QuartPos.fromSection(ChunkPos.getZ(p_224716_)),
                        (p_224723_, p_224724_, p_224725_) -> {
                            double d2 = Mth.length((double)(x - p_224723_), (double)(z - p_224724_));
                            if (!(d2 > (double)HEIGHT_BLENDING_RANGE_CELLS)) {
                                if (d2 < mutabledouble.doubleValue()) {
                                    mutableobject.setValue(p_224725_);
                                    mutabledouble.setValue(d2);
                                }
                            }
                        }
                    )
            );
        if (mutabledouble.doubleValue() == Double.POSITIVE_INFINITY) {
            return null;
        } else {
            double d0 = SHIFT_NOISE.getValue((double)x, 0.0, (double)z) * 12.0;
            double d1 = Mth.clamp((mutabledouble.doubleValue() + d0) / (double)(HEIGHT_BLENDING_RANGE_CELLS + 1), 0.0, 1.0);
            return d1 > 0.5 ? null : mutableobject.getValue();
        }
    }

    public static void generateBorderTicks(WorldGenRegion region, ChunkAccess chunk) {
        ChunkPos chunkpos = chunk.getPos();
        boolean flag = chunk.isOldNoiseGeneration();
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();
        BlockPos blockpos = new BlockPos(chunkpos.getMinBlockX(), 0, chunkpos.getMinBlockZ());
        BlendingData blendingdata = chunk.getBlendingData();
        if (blendingdata != null) {
            int i = blendingdata.getAreaWithOldGeneration().getMinBuildHeight();
            int j = blendingdata.getAreaWithOldGeneration().getMaxBuildHeight() - 1;
            if (flag) {
                for (int k = 0; k < 16; k++) {
                    for (int l = 0; l < 16; l++) {
                        generateBorderTick(chunk, blockpos$mutableblockpos.setWithOffset(blockpos, k, i - 1, l));
                        generateBorderTick(chunk, blockpos$mutableblockpos.setWithOffset(blockpos, k, i, l));
                        generateBorderTick(chunk, blockpos$mutableblockpos.setWithOffset(blockpos, k, j, l));
                        generateBorderTick(chunk, blockpos$mutableblockpos.setWithOffset(blockpos, k, j + 1, l));
                    }
                }
            }

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if (region.getChunk(chunkpos.x + direction.getStepX(), chunkpos.z + direction.getStepZ()).isOldNoiseGeneration() != flag) {
                    int i1 = direction == Direction.EAST ? 15 : 0;
                    int j1 = direction == Direction.WEST ? 0 : 15;
                    int k1 = direction == Direction.SOUTH ? 15 : 0;
                    int l1 = direction == Direction.NORTH ? 0 : 15;

                    for (int i2 = i1; i2 <= j1; i2++) {
                        for (int j2 = k1; j2 <= l1; j2++) {
                            int k2 = Math.min(j, chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, i2, j2)) + 1;

                            for (int l2 = i; l2 < k2; l2++) {
                                generateBorderTick(chunk, blockpos$mutableblockpos.setWithOffset(blockpos, i2, l2, j2));
                            }
                        }
                    }
                }
            }
        }
    }

    private static void generateBorderTick(ChunkAccess chunk, BlockPos pos) {
        BlockState blockstate = chunk.getBlockState(pos);
        if (blockstate.is(BlockTags.LEAVES)) {
            chunk.markPosForPostprocessing(pos);
        }

        FluidState fluidstate = chunk.getFluidState(pos);
        if (!fluidstate.isEmpty()) {
            chunk.markPosForPostprocessing(pos);
        }
    }

    public static void addAroundOldChunksCarvingMaskFilter(WorldGenLevel level, ProtoChunk chunk) {
        ChunkPos chunkpos = chunk.getPos();
        Builder<Direction8, BlendingData> builder = ImmutableMap.builder();

        for (Direction8 direction8 : Direction8.values()) {
            int i = chunkpos.x + direction8.getStepX();
            int j = chunkpos.z + direction8.getStepZ();
            BlendingData blendingdata = level.getChunk(i, j).getBlendingData();
            if (blendingdata != null) {
                builder.put(direction8, blendingdata);
            }
        }

        ImmutableMap<Direction8, BlendingData> immutablemap = builder.build();
        if (chunk.isOldNoiseGeneration() || !immutablemap.isEmpty()) {
            Blender.DistanceGetter blender$distancegetter = makeOldChunkDistanceGetter(chunk.getBlendingData(), immutablemap);
            CarvingMask.Mask carvingmask$mask = (p_202262_, p_202263_, p_202264_) -> {
                double d0 = (double)p_202262_ + 0.5 + SHIFT_NOISE.getValue((double)p_202262_, (double)p_202263_, (double)p_202264_) * 4.0;
                double d1 = (double)p_202263_ + 0.5 + SHIFT_NOISE.getValue((double)p_202263_, (double)p_202264_, (double)p_202262_) * 4.0;
                double d2 = (double)p_202264_ + 0.5 + SHIFT_NOISE.getValue((double)p_202264_, (double)p_202262_, (double)p_202263_) * 4.0;
                return blender$distancegetter.getDistance(d0, d1, d2) < 4.0;
            };
            Stream.of(GenerationStep.Carving.values())
                .map(chunk::getOrCreateCarvingMask)
                .forEach(p_202259_ -> p_202259_.setAdditionalMask(carvingmask$mask));
        }
    }

    public static Blender.DistanceGetter makeOldChunkDistanceGetter(@Nullable BlendingData blendingData, Map<Direction8, BlendingData> surroundingBlendingData) {
        List<Blender.DistanceGetter> list = Lists.newArrayList();
        if (blendingData != null) {
            list.add(makeOffsetOldChunkDistanceGetter(null, blendingData));
        }

        surroundingBlendingData.forEach((p_224734_, p_224735_) -> list.add(makeOffsetOldChunkDistanceGetter(p_224734_, p_224735_)));
        return (p_202267_, p_202268_, p_202269_) -> {
            double d0 = Double.POSITIVE_INFINITY;

            for (Blender.DistanceGetter blender$distancegetter : list) {
                double d1 = blender$distancegetter.getDistance(p_202267_, p_202268_, p_202269_);
                if (d1 < d0) {
                    d0 = d1;
                }
            }

            return d0;
        };
    }

    private static Blender.DistanceGetter makeOffsetOldChunkDistanceGetter(@Nullable Direction8 p_direction, BlendingData blendingData) {
        double d0 = 0.0;
        double d1 = 0.0;
        if (p_direction != null) {
            for (Direction direction : p_direction.getDirections()) {
                d0 += (double)(direction.getStepX() * 16);
                d1 += (double)(direction.getStepZ() * 16);
            }
        }

        double d5 = d0;
        double d2 = d1;
        double d3 = (double)blendingData.getAreaWithOldGeneration().getHeight() / 2.0;
        double d4 = (double)blendingData.getAreaWithOldGeneration().getMinBuildHeight() + d3;
        return (p_224703_, p_224704_, p_224705_) -> distanceToCube(p_224703_ - 8.0 - d5, p_224704_ - d4, p_224705_ - 8.0 - d2, 8.0, d3, 8.0);
    }

    private static double distanceToCube(double x1, double y1, double z1, double x2, double y2, double z2) {
        double d0 = Math.abs(x1) - x2;
        double d1 = Math.abs(y1) - y2;
        double d2 = Math.abs(z1) - z2;
        return Mth.length(Math.max(0.0, d0), Math.max(0.0, d1), Math.max(0.0, d2));
    }

    public static record BlendingOutput(double alpha, double blendingOffset) {
    }

    interface CellValueGetter {
        double get(BlendingData blendingData, int x, int y, int z);
    }

    public interface DistanceGetter {
        double getDistance(double x, double y, double z);
    }
}
