package net.minecraft.world.level.levelgen;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.ImmutableList.Builder;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.material.MaterialRuleList;

public class NoiseChunk implements DensityFunction.ContextProvider, DensityFunction.FunctionContext {
    private final NoiseSettings noiseSettings;
    final int cellCountXZ;
    final int cellCountY;
    final int cellNoiseMinY;
    private final int firstCellX;
    private final int firstCellZ;
    final int firstNoiseX;
    final int firstNoiseZ;
    final List<NoiseChunk.NoiseInterpolator> interpolators;
    final List<NoiseChunk.CacheAllInCell> cellCaches;
    private final Map<DensityFunction, DensityFunction> wrapped = new HashMap<>();
    private final Long2IntMap preliminarySurfaceLevel = new Long2IntOpenHashMap();
    private final Aquifer aquifer;
    private final DensityFunction initialDensityNoJaggedness;
    private final NoiseChunk.BlockStateFiller blockStateRule;
    private final Blender blender;
    private final NoiseChunk.FlatCache blendAlpha;
    private final NoiseChunk.FlatCache blendOffset;
    private final DensityFunctions.BeardifierOrMarker beardifier;
    private long lastBlendingDataPos = ChunkPos.INVALID_CHUNK_POS;
    private Blender.BlendingOutput lastBlendingOutput = new Blender.BlendingOutput(1.0, 0.0);
    final int noiseSizeXZ;
    final int cellWidth;
    final int cellHeight;
    boolean interpolating;
    boolean fillingCell;
    private int cellStartBlockX;
    int cellStartBlockY;
    private int cellStartBlockZ;
    int inCellX;
    int inCellY;
    int inCellZ;
    long interpolationCounter;
    long arrayInterpolationCounter;
    int arrayIndex;
    private final DensityFunction.ContextProvider sliceFillingContextProvider = new DensityFunction.ContextProvider() {
        @Override
        public DensityFunction.FunctionContext forIndex(int p_209253_) {
            NoiseChunk.this.cellStartBlockY = (p_209253_ + NoiseChunk.this.cellNoiseMinY) * NoiseChunk.this.cellHeight;
            NoiseChunk.this.interpolationCounter++;
            NoiseChunk.this.inCellY = 0;
            NoiseChunk.this.arrayIndex = p_209253_;
            return NoiseChunk.this;
        }

        @Override
        public void fillAllDirectly(double[] p_209255_, DensityFunction p_209256_) {
            for (int i2 = 0; i2 < NoiseChunk.this.cellCountY + 1; i2++) {
                NoiseChunk.this.cellStartBlockY = (i2 + NoiseChunk.this.cellNoiseMinY) * NoiseChunk.this.cellHeight;
                NoiseChunk.this.interpolationCounter++;
                NoiseChunk.this.inCellY = 0;
                NoiseChunk.this.arrayIndex = i2;
                p_209255_[i2] = p_209256_.compute(NoiseChunk.this);
            }
        }
    };

    public static NoiseChunk forChunk(
        ChunkAccess chunk,
        RandomState state,
        DensityFunctions.BeardifierOrMarker beardifierOrMarker,
        NoiseGeneratorSettings noiseGeneratorSettings,
        Aquifer.FluidPicker fluidPicke,
        Blender blender
    ) {
        NoiseSettings noisesettings = noiseGeneratorSettings.noiseSettings().clampToHeightAccessor(chunk);
        ChunkPos chunkpos = chunk.getPos();
        int i = 16 / noisesettings.getCellWidth();
        return new NoiseChunk(i, state, chunkpos.getMinBlockX(), chunkpos.getMinBlockZ(), noisesettings, beardifierOrMarker, noiseGeneratorSettings, fluidPicke, blender);
    }

    public NoiseChunk(
        int cellCountXZ,
        RandomState random,
        int firstNoiseX,
        int firstNoiseZ,
        NoiseSettings noiseSettings,
        DensityFunctions.BeardifierOrMarker beardifier,
        NoiseGeneratorSettings noiseGeneratorSettings,
        Aquifer.FluidPicker fluidPicker,
        Blender blendifier
    ) {
        this.noiseSettings = noiseSettings;
        this.cellWidth = noiseSettings.getCellWidth();
        this.cellHeight = noiseSettings.getCellHeight();
        this.cellCountXZ = cellCountXZ;
        this.cellCountY = Mth.floorDiv(noiseSettings.height(), this.cellHeight);
        this.cellNoiseMinY = Mth.floorDiv(noiseSettings.minY(), this.cellHeight);
        this.firstCellX = Math.floorDiv(firstNoiseX, this.cellWidth);
        this.firstCellZ = Math.floorDiv(firstNoiseZ, this.cellWidth);
        this.interpolators = Lists.newArrayList();
        this.cellCaches = Lists.newArrayList();
        this.firstNoiseX = QuartPos.fromBlock(firstNoiseX);
        this.firstNoiseZ = QuartPos.fromBlock(firstNoiseZ);
        this.noiseSizeXZ = QuartPos.fromBlock(cellCountXZ * this.cellWidth);
        this.blender = blendifier;
        this.beardifier = beardifier;
        this.blendAlpha = new NoiseChunk.FlatCache(new NoiseChunk.BlendAlpha(), false);
        this.blendOffset = new NoiseChunk.FlatCache(new NoiseChunk.BlendOffset(), false);

        for (int i = 0; i <= this.noiseSizeXZ; i++) {
            int j = this.firstNoiseX + i;
            int k = QuartPos.toBlock(j);

            for (int l = 0; l <= this.noiseSizeXZ; l++) {
                int i1 = this.firstNoiseZ + l;
                int j1 = QuartPos.toBlock(i1);
                Blender.BlendingOutput blender$blendingoutput = blendifier.blendOffsetAndFactor(k, j1);
                this.blendAlpha.values[i][l] = blender$blendingoutput.alpha();
                this.blendOffset.values[i][l] = blender$blendingoutput.blendingOffset();
            }
        }

        NoiseRouter noiserouter = random.router();
        NoiseRouter noiserouter1 = noiserouter.mapAll(this::wrap);
        if (!noiseGeneratorSettings.isAquifersEnabled()) {
            this.aquifer = Aquifer.createDisabled(fluidPicker);
        } else {
            int k1 = SectionPos.blockToSectionCoord(firstNoiseX);
            int l1 = SectionPos.blockToSectionCoord(firstNoiseZ);
            this.aquifer = Aquifer.create(this, new ChunkPos(k1, l1), noiserouter1, random.aquiferRandom(), noiseSettings.minY(), noiseSettings.height(), fluidPicker);
        }

        Builder<NoiseChunk.BlockStateFiller> builder = ImmutableList.builder();
        DensityFunction densityfunction = DensityFunctions.cacheAllInCell(
                DensityFunctions.add(noiserouter1.finalDensity(), DensityFunctions.BeardifierMarker.INSTANCE)
            )
            .mapAll(this::wrap);
        builder.add(p_209217_ -> this.aquifer.computeSubstance(p_209217_, densityfunction.compute(p_209217_)));
        if (noiseGeneratorSettings.oreVeinsEnabled()) {
            builder.add(OreVeinifier.create(noiserouter1.veinToggle(), noiserouter1.veinRidged(), noiserouter1.veinGap(), random.oreRandom()));
        }

        this.blockStateRule = new MaterialRuleList(builder.build());
        this.initialDensityNoJaggedness = noiserouter1.initialDensityWithoutJaggedness();
    }

    protected Climate.Sampler cachedClimateSampler(NoiseRouter noiseRouter, List<Climate.ParameterPoint> points) {
        return new Climate.Sampler(
            noiseRouter.temperature().mapAll(this::wrap),
            noiseRouter.vegetation().mapAll(this::wrap),
            noiseRouter.continents().mapAll(this::wrap),
            noiseRouter.erosion().mapAll(this::wrap),
            noiseRouter.depth().mapAll(this::wrap),
            noiseRouter.ridges().mapAll(this::wrap),
            points
        );
    }

    @Nullable
    protected BlockState getInterpolatedState() {
        return this.blockStateRule.calculate(this);
    }

    @Override
    public int blockX() {
        return this.cellStartBlockX + this.inCellX;
    }

    @Override
    public int blockY() {
        return this.cellStartBlockY + this.inCellY;
    }

    @Override
    public int blockZ() {
        return this.cellStartBlockZ + this.inCellZ;
    }

    public int preliminarySurfaceLevel(int x, int z) {
        int i = QuartPos.toBlock(QuartPos.fromBlock(x));
        int j = QuartPos.toBlock(QuartPos.fromBlock(z));
        return this.preliminarySurfaceLevel.computeIfAbsent(ColumnPos.asLong(i, j), this::computePreliminarySurfaceLevel);
    }

    private int computePreliminarySurfaceLevel(long packedChunkPos) {
        int i = ColumnPos.getX(packedChunkPos);
        int j = ColumnPos.getZ(packedChunkPos);
        int k = this.noiseSettings.minY();

        for (int l = k + this.noiseSettings.height(); l >= k; l -= this.cellHeight) {
            if (this.initialDensityNoJaggedness.compute(new DensityFunction.SinglePointContext(i, l, j)) > 0.390625) {
                return l;
            }
        }

        return Integer.MAX_VALUE;
    }

    @Override
    public Blender getBlender() {
        return this.blender;
    }

    private void fillSlice(boolean isSlice0, int start) {
        this.cellStartBlockX = start * this.cellWidth;
        this.inCellX = 0;

        for (int i = 0; i < this.cellCountXZ + 1; i++) {
            int j = this.firstCellZ + i;
            this.cellStartBlockZ = j * this.cellWidth;
            this.inCellZ = 0;
            this.arrayInterpolationCounter++;

            for (NoiseChunk.NoiseInterpolator noisechunk$noiseinterpolator : this.interpolators) {
                double[] adouble = (isSlice0 ? noisechunk$noiseinterpolator.slice0 : noisechunk$noiseinterpolator.slice1)[i];
                noisechunk$noiseinterpolator.fillArray(adouble, this.sliceFillingContextProvider);
            }
        }

        this.arrayInterpolationCounter++;
    }

    public void initializeForFirstCellX() {
        if (this.interpolating) {
            throw new IllegalStateException("Staring interpolation twice");
        } else {
            this.interpolating = true;
            this.interpolationCounter = 0L;
            this.fillSlice(true, this.firstCellX);
        }
    }

    public void advanceCellX(int increment) {
        this.fillSlice(false, this.firstCellX + increment + 1);
        this.cellStartBlockX = (this.firstCellX + increment) * this.cellWidth;
    }

    public NoiseChunk forIndex(int arrayIndex) {
        int i = Math.floorMod(arrayIndex, this.cellWidth);
        int j = Math.floorDiv(arrayIndex, this.cellWidth);
        int k = Math.floorMod(j, this.cellWidth);
        int l = this.cellHeight - 1 - Math.floorDiv(j, this.cellWidth);
        this.inCellX = k;
        this.inCellY = l;
        this.inCellZ = i;
        this.arrayIndex = arrayIndex;
        return this;
    }

    @Override
    public void fillAllDirectly(double[] values, DensityFunction function) {
        this.arrayIndex = 0;

        for (int i = this.cellHeight - 1; i >= 0; i--) {
            this.inCellY = i;

            for (int j = 0; j < this.cellWidth; j++) {
                this.inCellX = j;

                for (int k = 0; k < this.cellWidth; k++) {
                    this.inCellZ = k;
                    values[this.arrayIndex++] = function.compute(this);
                }
            }
        }
    }

    public void selectCellYZ(int y, int z) {
        this.interpolators.forEach(p_209205_ -> p_209205_.selectCellYZ(y, z));
        this.fillingCell = true;
        this.cellStartBlockY = (y + this.cellNoiseMinY) * this.cellHeight;
        this.cellStartBlockZ = (this.firstCellZ + z) * this.cellWidth;
        this.arrayInterpolationCounter++;

        for (NoiseChunk.CacheAllInCell noisechunk$cacheallincell : this.cellCaches) {
            noisechunk$cacheallincell.noiseFiller.fillArray(noisechunk$cacheallincell.values, this);
        }

        this.arrayInterpolationCounter++;
        this.fillingCell = false;
    }

    public void updateForY(int cellEndBlockY, double y) {
        this.inCellY = cellEndBlockY - this.cellStartBlockY;
        this.interpolators.forEach(p_209238_ -> p_209238_.updateForY(y));
    }

    public void updateForX(int cellEndBlockX, double x) {
        this.inCellX = cellEndBlockX - this.cellStartBlockX;
        this.interpolators.forEach(p_209229_ -> p_209229_.updateForX(x));
    }

    public void updateForZ(int cellEndBlockZ, double z) {
        this.inCellZ = cellEndBlockZ - this.cellStartBlockZ;
        this.interpolationCounter++;
        this.interpolators.forEach(p_209188_ -> p_209188_.updateForZ(z));
    }

    public void stopInterpolation() {
        if (!this.interpolating) {
            throw new IllegalStateException("Staring interpolation twice");
        } else {
            this.interpolating = false;
        }
    }

    public void swapSlices() {
        this.interpolators.forEach(NoiseChunk.NoiseInterpolator::swapSlices);
    }

    public Aquifer aquifer() {
        return this.aquifer;
    }

    protected int cellWidth() {
        return this.cellWidth;
    }

    protected int cellHeight() {
        return this.cellHeight;
    }

    Blender.BlendingOutput getOrComputeBlendingOutput(int chunkX, int chunkZ) {
        long i = ChunkPos.asLong(chunkX, chunkZ);
        if (this.lastBlendingDataPos == i) {
            return this.lastBlendingOutput;
        } else {
            this.lastBlendingDataPos = i;
            Blender.BlendingOutput blender$blendingoutput = this.blender.blendOffsetAndFactor(chunkX, chunkZ);
            this.lastBlendingOutput = blender$blendingoutput;
            return blender$blendingoutput;
        }
    }

    protected DensityFunction wrap(DensityFunction densityFunction) {
        return this.wrapped.computeIfAbsent(densityFunction, this::wrapNew);
    }

    private DensityFunction wrapNew(DensityFunction densityFunction) {
        if (densityFunction instanceof DensityFunctions.Marker densityfunctions$marker) {
            return (DensityFunction)(switch (densityfunctions$marker.type()) {
                case Interpolated -> new NoiseChunk.NoiseInterpolator(densityfunctions$marker.wrapped());
                case FlatCache -> new NoiseChunk.FlatCache(densityfunctions$marker.wrapped(), true);
                case Cache2D -> new NoiseChunk.Cache2D(densityfunctions$marker.wrapped());
                case CacheOnce -> new NoiseChunk.CacheOnce(densityfunctions$marker.wrapped());
                case CacheAllInCell -> new NoiseChunk.CacheAllInCell(densityfunctions$marker.wrapped());
            });
        } else {
            if (this.blender != Blender.empty()) {
                if (densityFunction == DensityFunctions.BlendAlpha.INSTANCE) {
                    return this.blendAlpha;
                }

                if (densityFunction == DensityFunctions.BlendOffset.INSTANCE) {
                    return this.blendOffset;
                }
            }

            if (densityFunction == DensityFunctions.BeardifierMarker.INSTANCE) {
                return this.beardifier;
            } else {
                return densityFunction instanceof DensityFunctions.HolderHolder densityfunctions$holderholder
                    ? densityfunctions$holderholder.function().value()
                    : densityFunction;
            }
        }
    }

    class BlendAlpha implements NoiseChunk.NoiseChunkDensityFunction {
        @Override
        public DensityFunction wrapped() {
            return DensityFunctions.BlendAlpha.INSTANCE;
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor p_224365_) {
            return this.wrapped().mapAll(p_224365_);
        }

        @Override
        public double compute(DensityFunction.FunctionContext p_209264_) {
            return NoiseChunk.this.getOrComputeBlendingOutput(p_209264_.blockX(), p_209264_.blockZ()).alpha();
        }

        @Override
        public void fillArray(double[] p_209266_, DensityFunction.ContextProvider p_209267_) {
            p_209267_.fillAllDirectly(p_209266_, this);
        }

        @Override
        public double minValue() {
            return 0.0;
        }

        @Override
        public double maxValue() {
            return 1.0;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return DensityFunctions.BlendAlpha.CODEC;
        }
    }

    class BlendOffset implements NoiseChunk.NoiseChunkDensityFunction {
        @Override
        public DensityFunction wrapped() {
            return DensityFunctions.BlendOffset.INSTANCE;
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor p_224368_) {
            return this.wrapped().mapAll(p_224368_);
        }

        @Override
        public double compute(DensityFunction.FunctionContext p_209276_) {
            return NoiseChunk.this.getOrComputeBlendingOutput(p_209276_.blockX(), p_209276_.blockZ()).blendingOffset();
        }

        @Override
        public void fillArray(double[] p_209278_, DensityFunction.ContextProvider p_209279_) {
            p_209279_.fillAllDirectly(p_209278_, this);
        }

        @Override
        public double minValue() {
            return Double.NEGATIVE_INFINITY;
        }

        @Override
        public double maxValue() {
            return Double.POSITIVE_INFINITY;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return DensityFunctions.BlendOffset.CODEC;
        }
    }

    @FunctionalInterface
    public interface BlockStateFiller {
        @Nullable
        BlockState calculate(DensityFunction.FunctionContext context);
    }

    static class Cache2D implements DensityFunctions.MarkerOrMarked, NoiseChunk.NoiseChunkDensityFunction {
        private final DensityFunction function;
        private long lastPos2D = ChunkPos.INVALID_CHUNK_POS;
        private double lastValue;

        Cache2D(DensityFunction function) {
            this.function = function;
        }

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            int i = context.blockX();
            int j = context.blockZ();
            long k = ChunkPos.asLong(i, j);
            if (this.lastPos2D == k) {
                return this.lastValue;
            } else {
                this.lastPos2D = k;
                double d0 = this.function.compute(context);
                this.lastValue = d0;
                return d0;
            }
        }

        @Override
        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            this.function.fillArray(array, contextProvider);
        }

        @Override
        public DensityFunction wrapped() {
            return this.function;
        }

        @Override
        public DensityFunctions.Marker.Type type() {
            return DensityFunctions.Marker.Type.Cache2D;
        }
    }

    class CacheAllInCell implements DensityFunctions.MarkerOrMarked, NoiseChunk.NoiseChunkDensityFunction {
        final DensityFunction noiseFiller;
        final double[] values;

        CacheAllInCell(DensityFunction noiseFilter) {
            this.noiseFiller = noiseFilter;
            this.values = new double[NoiseChunk.this.cellWidth * NoiseChunk.this.cellWidth * NoiseChunk.this.cellHeight];
            NoiseChunk.this.cellCaches.add(this);
        }

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            if (context != NoiseChunk.this) {
                return this.noiseFiller.compute(context);
            } else if (!NoiseChunk.this.interpolating) {
                throw new IllegalStateException("Trying to sample interpolator outside the interpolation loop");
            } else {
                int i = NoiseChunk.this.inCellX;
                int j = NoiseChunk.this.inCellY;
                int k = NoiseChunk.this.inCellZ;
                return i >= 0 && j >= 0 && k >= 0 && i < NoiseChunk.this.cellWidth && j < NoiseChunk.this.cellHeight && k < NoiseChunk.this.cellWidth
                    ? this.values[((NoiseChunk.this.cellHeight - 1 - j) * NoiseChunk.this.cellWidth + i) * NoiseChunk.this.cellWidth + k]
                    : this.noiseFiller.compute(context);
            }
        }

        @Override
        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            contextProvider.fillAllDirectly(array, this);
        }

        @Override
        public DensityFunction wrapped() {
            return this.noiseFiller;
        }

        @Override
        public DensityFunctions.Marker.Type type() {
            return DensityFunctions.Marker.Type.CacheAllInCell;
        }
    }

    class CacheOnce implements DensityFunctions.MarkerOrMarked, NoiseChunk.NoiseChunkDensityFunction {
        private final DensityFunction function;
        private long lastCounter;
        private long lastArrayCounter;
        private double lastValue;
        @Nullable
        private double[] lastArray;

        CacheOnce(DensityFunction function) {
            this.function = function;
        }

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            if (context != NoiseChunk.this) {
                return this.function.compute(context);
            } else if (this.lastArray != null && this.lastArrayCounter == NoiseChunk.this.arrayInterpolationCounter) {
                return this.lastArray[NoiseChunk.this.arrayIndex];
            } else if (this.lastCounter == NoiseChunk.this.interpolationCounter) {
                return this.lastValue;
            } else {
                this.lastCounter = NoiseChunk.this.interpolationCounter;
                double d0 = this.function.compute(context);
                this.lastValue = d0;
                return d0;
            }
        }

        @Override
        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            if (this.lastArray != null && this.lastArrayCounter == NoiseChunk.this.arrayInterpolationCounter) {
                System.arraycopy(this.lastArray, 0, array, 0, array.length);
            } else {
                this.wrapped().fillArray(array, contextProvider);
                if (this.lastArray != null && this.lastArray.length == array.length) {
                    System.arraycopy(array, 0, this.lastArray, 0, array.length);
                } else {
                    this.lastArray = (double[])array.clone();
                }

                this.lastArrayCounter = NoiseChunk.this.arrayInterpolationCounter;
            }
        }

        @Override
        public DensityFunction wrapped() {
            return this.function;
        }

        @Override
        public DensityFunctions.Marker.Type type() {
            return DensityFunctions.Marker.Type.CacheOnce;
        }
    }

    class FlatCache implements DensityFunctions.MarkerOrMarked, NoiseChunk.NoiseChunkDensityFunction {
        private final DensityFunction noiseFiller;
        final double[][] values;

        FlatCache(DensityFunction noiseFiller, boolean computeValues) {
            this.noiseFiller = noiseFiller;
            this.values = new double[NoiseChunk.this.noiseSizeXZ + 1][NoiseChunk.this.noiseSizeXZ + 1];
            if (computeValues) {
                for (int i = 0; i <= NoiseChunk.this.noiseSizeXZ; i++) {
                    int j = NoiseChunk.this.firstNoiseX + i;
                    int k = QuartPos.toBlock(j);

                    for (int l = 0; l <= NoiseChunk.this.noiseSizeXZ; l++) {
                        int i1 = NoiseChunk.this.firstNoiseZ + l;
                        int j1 = QuartPos.toBlock(i1);
                        this.values[i][l] = noiseFiller.compute(new DensityFunction.SinglePointContext(k, 0, j1));
                    }
                }
            }
        }

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            int i = QuartPos.fromBlock(context.blockX());
            int j = QuartPos.fromBlock(context.blockZ());
            int k = i - NoiseChunk.this.firstNoiseX;
            int l = j - NoiseChunk.this.firstNoiseZ;
            int i1 = this.values.length;
            return k >= 0 && l >= 0 && k < i1 && l < i1 ? this.values[k][l] : this.noiseFiller.compute(context);
        }

        @Override
        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            contextProvider.fillAllDirectly(array, this);
        }

        @Override
        public DensityFunction wrapped() {
            return this.noiseFiller;
        }

        @Override
        public DensityFunctions.Marker.Type type() {
            return DensityFunctions.Marker.Type.FlatCache;
        }
    }

    interface NoiseChunkDensityFunction extends DensityFunction {
        DensityFunction wrapped();

        @Override
        default double minValue() {
            return this.wrapped().minValue();
        }

        @Override
        default double maxValue() {
            return this.wrapped().maxValue();
        }
    }

    public class NoiseInterpolator implements DensityFunctions.MarkerOrMarked, NoiseChunk.NoiseChunkDensityFunction {
        double[][] slice0;
        double[][] slice1;
        private final DensityFunction noiseFiller;
        private double noise000;
        private double noise001;
        private double noise100;
        private double noise101;
        private double noise010;
        private double noise011;
        private double noise110;
        private double noise111;
        private double valueXZ00;
        private double valueXZ10;
        private double valueXZ01;
        private double valueXZ11;
        private double valueZ0;
        private double valueZ1;
        private double value;

        NoiseInterpolator(DensityFunction noiseFilter) {
            this.noiseFiller = noiseFilter;
            this.slice0 = this.allocateSlice(NoiseChunk.this.cellCountY, NoiseChunk.this.cellCountXZ);
            this.slice1 = this.allocateSlice(NoiseChunk.this.cellCountY, NoiseChunk.this.cellCountXZ);
            NoiseChunk.this.interpolators.add(this);
        }

        private double[][] allocateSlice(int cellCountY, int cellCountXZ) {
            int i = cellCountXZ + 1;
            int j = cellCountY + 1;
            double[][] adouble = new double[i][j];

            for (int k = 0; k < i; k++) {
                adouble[k] = new double[j];
            }

            return adouble;
        }

        void selectCellYZ(int y, int z) {
            this.noise000 = this.slice0[z][y];
            this.noise001 = this.slice0[z + 1][y];
            this.noise100 = this.slice1[z][y];
            this.noise101 = this.slice1[z + 1][y];
            this.noise010 = this.slice0[z][y + 1];
            this.noise011 = this.slice0[z + 1][y + 1];
            this.noise110 = this.slice1[z][y + 1];
            this.noise111 = this.slice1[z + 1][y + 1];
        }

        void updateForY(double y) {
            this.valueXZ00 = Mth.lerp(y, this.noise000, this.noise010);
            this.valueXZ10 = Mth.lerp(y, this.noise100, this.noise110);
            this.valueXZ01 = Mth.lerp(y, this.noise001, this.noise011);
            this.valueXZ11 = Mth.lerp(y, this.noise101, this.noise111);
        }

        void updateForX(double x) {
            this.valueZ0 = Mth.lerp(x, this.valueXZ00, this.valueXZ10);
            this.valueZ1 = Mth.lerp(x, this.valueXZ01, this.valueXZ11);
        }

        void updateForZ(double z) {
            this.value = Mth.lerp(z, this.valueZ0, this.valueZ1);
        }

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            if (context != NoiseChunk.this) {
                return this.noiseFiller.compute(context);
            } else if (!NoiseChunk.this.interpolating) {
                throw new IllegalStateException("Trying to sample interpolator outside the interpolation loop");
            } else {
                return NoiseChunk.this.fillingCell
                    ? Mth.lerp3(
                        (double)NoiseChunk.this.inCellX / (double)NoiseChunk.this.cellWidth,
                        (double)NoiseChunk.this.inCellY / (double)NoiseChunk.this.cellHeight,
                        (double)NoiseChunk.this.inCellZ / (double)NoiseChunk.this.cellWidth,
                        this.noise000,
                        this.noise100,
                        this.noise010,
                        this.noise110,
                        this.noise001,
                        this.noise101,
                        this.noise011,
                        this.noise111
                    )
                    : this.value;
            }
        }

        @Override
        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            if (NoiseChunk.this.fillingCell) {
                contextProvider.fillAllDirectly(array, this);
            } else {
                this.wrapped().fillArray(array, contextProvider);
            }
        }

        @Override
        public DensityFunction wrapped() {
            return this.noiseFiller;
        }

        private void swapSlices() {
            double[][] adouble = this.slice0;
            this.slice0 = this.slice1;
            this.slice1 = adouble;
        }

        @Override
        public DensityFunctions.Marker.Type type() {
            return DensityFunctions.Marker.Type.Interpolated;
        }
    }
}
