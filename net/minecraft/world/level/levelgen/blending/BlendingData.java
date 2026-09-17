package net.minecraft.world.level.levelgen.blending;

import com.google.common.primitives.Doubles;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.DoubleStream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction8;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

public class BlendingData {
    private static final double BLENDING_DENSITY_FACTOR = 0.1;
    protected static final int CELL_WIDTH = 4;
    protected static final int CELL_HEIGHT = 8;
    protected static final int CELL_RATIO = 2;
    private static final double SOLID_DENSITY = 1.0;
    private static final double AIR_DENSITY = -1.0;
    private static final int CELLS_PER_SECTION_Y = 2;
    private static final int QUARTS_PER_SECTION = QuartPos.fromBlock(16);
    private static final int CELL_HORIZONTAL_MAX_INDEX_INSIDE = QUARTS_PER_SECTION - 1;
    private static final int CELL_HORIZONTAL_MAX_INDEX_OUTSIDE = QUARTS_PER_SECTION;
    private static final int CELL_COLUMN_INSIDE_COUNT = 2 * CELL_HORIZONTAL_MAX_INDEX_INSIDE + 1;
    private static final int CELL_COLUMN_OUTSIDE_COUNT = 2 * CELL_HORIZONTAL_MAX_INDEX_OUTSIDE + 1;
    private static final int CELL_COLUMN_COUNT = CELL_COLUMN_INSIDE_COUNT + CELL_COLUMN_OUTSIDE_COUNT;
    private final LevelHeightAccessor areaWithOldGeneration;
    private static final List<Block> SURFACE_BLOCKS = List.of(
        Blocks.PODZOL,
        Blocks.GRAVEL,
        Blocks.GRASS_BLOCK,
        Blocks.STONE,
        Blocks.COARSE_DIRT,
        Blocks.SAND,
        Blocks.RED_SAND,
        Blocks.MYCELIUM,
        Blocks.SNOW_BLOCK,
        Blocks.TERRACOTTA,
        Blocks.DIRT
    );
    protected static final double NO_VALUE = Double.MAX_VALUE;
    private boolean hasCalculatedData;
    private final double[] heights;
    private final List<List<Holder<Biome>>> biomes;
    private final transient double[][] densities;
    private static final Codec<double[]> DOUBLE_ARRAY_CODEC = Codec.DOUBLE.listOf().xmap(Doubles::toArray, Doubles::asList);
    public static final Codec<BlendingData> CODEC = RecordCodecBuilder.<BlendingData>create(
            p_338098_ -> p_338098_.group(
                        Codec.INT.fieldOf("min_section").forGetter(p_224767_ -> p_224767_.areaWithOldGeneration.getMinSection()),
                        Codec.INT.fieldOf("max_section").forGetter(p_224765_ -> p_224765_.areaWithOldGeneration.getMaxSection()),
                        DOUBLE_ARRAY_CODEC.lenientOptionalFieldOf("heights")
                            .forGetter(
                                p_224762_ -> DoubleStream.of(p_224762_.heights).anyMatch(p_224745_ -> p_224745_ != Double.MAX_VALUE)
                                        ? Optional.of(p_224762_.heights)
                                        : Optional.empty()
                            )
                    )
                    .apply(p_338098_, BlendingData::new)
        )
        .comapFlatMap(BlendingData::validateArraySize, Function.identity());

    private static DataResult<BlendingData> validateArraySize(BlendingData blendingData) {
        return blendingData.heights.length != CELL_COLUMN_COUNT
            ? DataResult.error(() -> "heights has to be of length " + CELL_COLUMN_COUNT)
            : DataResult.success(blendingData);
    }

    private BlendingData(int sectionX, int sectionZ, Optional<double[]> heights) {
        this.heights = heights.orElse(Util.make(new double[CELL_COLUMN_COUNT], p_224756_ -> Arrays.fill(p_224756_, Double.MAX_VALUE)));
        this.densities = new double[CELL_COLUMN_COUNT][];
        ObjectArrayList<List<Holder<Biome>>> objectarraylist = new ObjectArrayList<>(CELL_COLUMN_COUNT);
        objectarraylist.size(CELL_COLUMN_COUNT);
        this.biomes = objectarraylist;
        int i = SectionPos.sectionToBlockCoord(sectionX);
        int j = SectionPos.sectionToBlockCoord(sectionZ) - i;
        this.areaWithOldGeneration = LevelHeightAccessor.create(i, j);
    }

    @Nullable
    public static BlendingData getOrUpdateBlendingData(WorldGenRegion region, int chunkX, int chunkZ) {
        ChunkAccess chunkaccess = region.getChunk(chunkX, chunkZ);
        BlendingData blendingdata = chunkaccess.getBlendingData();
        if (blendingdata != null && !chunkaccess.getHighestGeneratedStatus().isBefore(ChunkStatus.BIOMES)) {
            blendingdata.calculateData(chunkaccess, sideByGenerationAge(region, chunkX, chunkZ, false));
            return blendingdata;
        } else {
            return null;
        }
    }

    public static Set<Direction8> sideByGenerationAge(WorldGenLevel level, int chunkX, int chunkZ, boolean oldNoiseGeneration) {
        Set<Direction8> set = EnumSet.noneOf(Direction8.class);

        for (Direction8 direction8 : Direction8.values()) {
            int i = chunkX + direction8.getStepX();
            int j = chunkZ + direction8.getStepZ();
            if (level.getChunk(i, j).isOldNoiseGeneration() == oldNoiseGeneration) {
                set.add(direction8);
            }
        }

        return set;
    }

    private void calculateData(ChunkAccess chunk, Set<Direction8> directions) {
        if (!this.hasCalculatedData) {
            if (directions.contains(Direction8.NORTH) || directions.contains(Direction8.WEST) || directions.contains(Direction8.NORTH_WEST)) {
                this.addValuesForColumn(getInsideIndex(0, 0), chunk, 0, 0);
            }

            if (directions.contains(Direction8.NORTH)) {
                for (int i = 1; i < QUARTS_PER_SECTION; i++) {
                    this.addValuesForColumn(getInsideIndex(i, 0), chunk, 4 * i, 0);
                }
            }

            if (directions.contains(Direction8.WEST)) {
                for (int j = 1; j < QUARTS_PER_SECTION; j++) {
                    this.addValuesForColumn(getInsideIndex(0, j), chunk, 0, 4 * j);
                }
            }

            if (directions.contains(Direction8.EAST)) {
                for (int k = 1; k < QUARTS_PER_SECTION; k++) {
                    this.addValuesForColumn(getOutsideIndex(CELL_HORIZONTAL_MAX_INDEX_OUTSIDE, k), chunk, 15, 4 * k);
                }
            }

            if (directions.contains(Direction8.SOUTH)) {
                for (int l = 0; l < QUARTS_PER_SECTION; l++) {
                    this.addValuesForColumn(getOutsideIndex(l, CELL_HORIZONTAL_MAX_INDEX_OUTSIDE), chunk, 4 * l, 15);
                }
            }

            if (directions.contains(Direction8.EAST) && directions.contains(Direction8.NORTH_EAST)) {
                this.addValuesForColumn(getOutsideIndex(CELL_HORIZONTAL_MAX_INDEX_OUTSIDE, 0), chunk, 15, 0);
            }

            if (directions.contains(Direction8.EAST) && directions.contains(Direction8.SOUTH) && directions.contains(Direction8.SOUTH_EAST)) {
                this.addValuesForColumn(getOutsideIndex(CELL_HORIZONTAL_MAX_INDEX_OUTSIDE, CELL_HORIZONTAL_MAX_INDEX_OUTSIDE), chunk, 15, 15);
            }

            this.hasCalculatedData = true;
        }
    }

    private void addValuesForColumn(int index, ChunkAccess chunk, int x, int z) {
        if (this.heights[index] == Double.MAX_VALUE) {
            this.heights[index] = (double)this.getHeightAtXZ(chunk, x, z);
        }

        this.densities[index] = this.getDensityColumn(chunk, x, z, Mth.floor(this.heights[index]));
        this.biomes.set(index, this.getBiomeColumn(chunk, x, z));
    }

    private int getHeightAtXZ(ChunkAccess chunk, int x, int z) {
        int i;
        if (chunk.hasPrimedHeightmap(Heightmap.Types.WORLD_SURFACE_WG)) {
            i = Math.min(chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) + 1, this.areaWithOldGeneration.getMaxBuildHeight());
        } else {
            i = this.areaWithOldGeneration.getMaxBuildHeight();
        }

        int j = this.areaWithOldGeneration.getMinBuildHeight();
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos(x, i, z);

        while (blockpos$mutableblockpos.getY() > j) {
            blockpos$mutableblockpos.move(Direction.DOWN);
            if (SURFACE_BLOCKS.contains(chunk.getBlockState(blockpos$mutableblockpos).getBlock())) {
                return blockpos$mutableblockpos.getY();
            }
        }

        return j;
    }

    private static double read1(ChunkAccess chunk, BlockPos.MutableBlockPos pos) {
        return isGround(chunk, pos.move(Direction.DOWN)) ? 1.0 : -1.0;
    }

    private static double read7(ChunkAccess chunk, BlockPos.MutableBlockPos pos) {
        double d0 = 0.0;

        for (int i = 0; i < 7; i++) {
            d0 += read1(chunk, pos);
        }

        return d0;
    }

    private double[] getDensityColumn(ChunkAccess chunk, int x, int z, int height) {
        double[] adouble = new double[this.cellCountPerColumn()];
        Arrays.fill(adouble, -1.0);
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos(x, this.areaWithOldGeneration.getMaxBuildHeight(), z);
        double d0 = read7(chunk, blockpos$mutableblockpos);

        for (int i = adouble.length - 2; i >= 0; i--) {
            double d1 = read1(chunk, blockpos$mutableblockpos);
            double d2 = read7(chunk, blockpos$mutableblockpos);
            adouble[i] = (d0 + d1 + d2) / 15.0;
            d0 = d2;
        }

        int j = this.getCellYIndex(Mth.floorDiv(height, 8));
        if (j >= 0 && j < adouble.length - 1) {
            double d4 = ((double)height + 0.5) % 8.0 / 8.0;
            double d5 = (1.0 - d4) / d4;
            double d3 = Math.max(d5, 1.0) * 0.25;
            adouble[j + 1] = -d5 / d3;
            adouble[j] = 1.0 / d3;
        }

        return adouble;
    }

    private List<Holder<Biome>> getBiomeColumn(ChunkAccess chunk, int x, int z) {
        ObjectArrayList<Holder<Biome>> objectarraylist = new ObjectArrayList<>(this.quartCountPerColumn());
        objectarraylist.size(this.quartCountPerColumn());

        for (int i = 0; i < objectarraylist.size(); i++) {
            int j = i + QuartPos.fromBlock(this.areaWithOldGeneration.getMinBuildHeight());
            objectarraylist.set(i, chunk.getNoiseBiome(QuartPos.fromBlock(x), j, QuartPos.fromBlock(z)));
        }

        return objectarraylist;
    }

    private static boolean isGround(ChunkAccess chunk, BlockPos pos) {
        BlockState blockstate = chunk.getBlockState(pos);
        if (blockstate.isAir()) {
            return false;
        } else if (blockstate.is(BlockTags.LEAVES)) {
            return false;
        } else if (blockstate.is(BlockTags.LOGS)) {
            return false;
        } else {
            return blockstate.is(Blocks.BROWN_MUSHROOM_BLOCK) || blockstate.is(Blocks.RED_MUSHROOM_BLOCK)
                ? false
                : !blockstate.getCollisionShape(chunk, pos).isEmpty();
        }
    }

    protected double getHeight(int x, int y, int z) {
        if (x == CELL_HORIZONTAL_MAX_INDEX_OUTSIDE || z == CELL_HORIZONTAL_MAX_INDEX_OUTSIDE) {
            return this.heights[getOutsideIndex(x, z)];
        } else {
            return x != 0 && z != 0 ? Double.MAX_VALUE : this.heights[getInsideIndex(x, z)];
        }
    }

    private double getDensity(@Nullable double[] heights, int y) {
        if (heights == null) {
            return Double.MAX_VALUE;
        } else {
            int i = this.getCellYIndex(y);
            return i >= 0 && i < heights.length ? heights[i] * 0.1 : Double.MAX_VALUE;
        }
    }

    protected double getDensity(int x, int y, int z) {
        if (y == this.getMinY()) {
            return 0.1;
        } else if (x == CELL_HORIZONTAL_MAX_INDEX_OUTSIDE || z == CELL_HORIZONTAL_MAX_INDEX_OUTSIDE) {
            return this.getDensity(this.densities[getOutsideIndex(x, z)], y);
        } else {
            return x != 0 && z != 0 ? Double.MAX_VALUE : this.getDensity(this.densities[getInsideIndex(x, z)], y);
        }
    }

    protected void iterateBiomes(int x, int y, int z, BlendingData.BiomeConsumer consumer) {
        if (y >= QuartPos.fromBlock(this.areaWithOldGeneration.getMinBuildHeight())
            && y < QuartPos.fromBlock(this.areaWithOldGeneration.getMaxBuildHeight())) {
            int i = y - QuartPos.fromBlock(this.areaWithOldGeneration.getMinBuildHeight());

            for (int j = 0; j < this.biomes.size(); j++) {
                if (this.biomes.get(j) != null) {
                    Holder<Biome> holder = this.biomes.get(j).get(i);
                    if (holder != null) {
                        consumer.consume(x + getX(j), z + getZ(j), holder);
                    }
                }
            }
        }
    }

    protected void iterateHeights(int x, int z, BlendingData.HeightConsumer consumer) {
        for (int i = 0; i < this.heights.length; i++) {
            double d0 = this.heights[i];
            if (d0 != Double.MAX_VALUE) {
                consumer.consume(x + getX(i), z + getZ(i), d0);
            }
        }
    }

    protected void iterateDensities(int x, int z, int minY, int maxY, BlendingData.DensityConsumer consumer) {
        int i = this.getColumnMinY();
        int j = Math.max(0, minY - i);
        int k = Math.min(this.cellCountPerColumn(), maxY - i);

        for (int l = 0; l < this.densities.length; l++) {
            double[] adouble = this.densities[l];
            if (adouble != null) {
                int i1 = x + getX(l);
                int j1 = z + getZ(l);

                for (int k1 = j; k1 < k; k1++) {
                    consumer.consume(i1, k1 + i, j1, adouble[k1] * 0.1);
                }
            }
        }
    }

    private int cellCountPerColumn() {
        return this.areaWithOldGeneration.getSectionsCount() * 2;
    }

    private int quartCountPerColumn() {
        return QuartPos.fromSection(this.areaWithOldGeneration.getSectionsCount());
    }

    private int getColumnMinY() {
        return this.getMinY() + 1;
    }

    private int getMinY() {
        return this.areaWithOldGeneration.getMinSection() * 2;
    }

    private int getCellYIndex(int y) {
        return y - this.getColumnMinY();
    }

    private static int getInsideIndex(int x, int z) {
        return CELL_HORIZONTAL_MAX_INDEX_INSIDE - x + z;
    }

    private static int getOutsideIndex(int x, int z) {
        return CELL_COLUMN_INSIDE_COUNT + x + CELL_HORIZONTAL_MAX_INDEX_OUTSIDE - z;
    }

    private static int getX(int index) {
        if (index < CELL_COLUMN_INSIDE_COUNT) {
            return zeroIfNegative(CELL_HORIZONTAL_MAX_INDEX_INSIDE - index);
        } else {
            int i = index - CELL_COLUMN_INSIDE_COUNT;
            return CELL_HORIZONTAL_MAX_INDEX_OUTSIDE - zeroIfNegative(CELL_HORIZONTAL_MAX_INDEX_OUTSIDE - i);
        }
    }

    private static int getZ(int index) {
        if (index < CELL_COLUMN_INSIDE_COUNT) {
            return zeroIfNegative(index - CELL_HORIZONTAL_MAX_INDEX_INSIDE);
        } else {
            int i = index - CELL_COLUMN_INSIDE_COUNT;
            return CELL_HORIZONTAL_MAX_INDEX_OUTSIDE - zeroIfNegative(i - CELL_HORIZONTAL_MAX_INDEX_OUTSIDE);
        }
    }

    private static int zeroIfNegative(int value) {
        return value & ~(value >> 31);
    }

    public LevelHeightAccessor getAreaWithOldGeneration() {
        return this.areaWithOldGeneration;
    }

    protected interface BiomeConsumer {
        void consume(int x, int z, Holder<Biome> biome);
    }

    protected interface DensityConsumer {
        void consume(int x, int y, int z, double density);
    }

    protected interface HeightConsumer {
        void consume(int x, int z, double height);
    }
}
