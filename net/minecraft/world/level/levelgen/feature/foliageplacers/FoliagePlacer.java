package net.minecraft.world.level.levelgen.feature.foliageplacers;

import com.mojang.datafixers.Products.P2;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import com.mojang.serialization.codecs.RecordCodecBuilder.Mu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

public abstract class FoliagePlacer {
    public static final Codec<FoliagePlacer> CODEC = BuiltInRegistries.FOLIAGE_PLACER_TYPE
        .byNameCodec()
        .dispatch(FoliagePlacer::type, FoliagePlacerType::codec);
    protected final IntProvider radius;
    protected final IntProvider offset;

    protected static <P extends FoliagePlacer> P2<Mu<P>, IntProvider, IntProvider> foliagePlacerParts(Instance<P> instance) {
        return instance.group(
            IntProvider.codec(0, 16).fieldOf("radius").forGetter(p_161449_ -> p_161449_.radius),
            IntProvider.codec(0, 16).fieldOf("offset").forGetter(p_161447_ -> p_161447_.offset)
        );
    }

    public FoliagePlacer(IntProvider radius, IntProvider offset) {
        this.radius = radius;
        this.offset = offset;
    }

    protected abstract FoliagePlacerType<?> type();

    public void createFoliage(
        LevelSimulatedReader level,
        FoliagePlacer.FoliageSetter blockSetter,
        RandomSource random,
        TreeConfiguration config,
        int maxFreeTreeHeight,
        FoliagePlacer.FoliageAttachment attachment,
        int foliageHeight,
        int foliageRadius
    ) {
        this.createFoliage(level, blockSetter, random, config, maxFreeTreeHeight, attachment, foliageHeight, foliageRadius, this.offset(random));
    }

    protected abstract void createFoliage(
        LevelSimulatedReader level,
        FoliagePlacer.FoliageSetter blockSetter,
        RandomSource random,
        TreeConfiguration config,
        int maxFreeTreeHeight,
        FoliagePlacer.FoliageAttachment attachment,
        int foliageHeight,
        int foliageRadius,
        int offset
    );

    public abstract int foliageHeight(RandomSource random, int height, TreeConfiguration config);

    public int foliageRadius(RandomSource random, int radius) {
        return this.radius.sample(random);
    }

    private int offset(RandomSource random) {
        return this.offset.sample(random);
    }

    /**
     * Skips certain positions based on the provided shape, such as rounding corners randomly.
     * The coordinates are passed in as absolute value, and should be within [0, {@code range}].
     */
    protected abstract boolean shouldSkipLocation(RandomSource random, int localX, int localY, int localZ, int range, boolean large);

    protected boolean shouldSkipLocationSigned(RandomSource random, int localX, int localY, int localZ, int range, boolean large) {
        int i;
        int j;
        if (large) {
            i = Math.min(Math.abs(localX), Math.abs(localX - 1));
            j = Math.min(Math.abs(localZ), Math.abs(localZ - 1));
        } else {
            i = Math.abs(localX);
            j = Math.abs(localZ);
        }

        return this.shouldSkipLocation(random, i, localY, j, range, large);
    }

    protected void placeLeavesRow(
        LevelSimulatedReader level,
        FoliagePlacer.FoliageSetter foliageSetter,
        RandomSource random,
        TreeConfiguration treeConfiguration,
        BlockPos pos,
        int range,
        int localY,
        boolean large
    ) {
        int i = large ? 1 : 0;
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

        for (int j = -range; j <= range + i; j++) {
            for (int k = -range; k <= range + i; k++) {
                if (!this.shouldSkipLocationSigned(random, j, localY, k, range, large)) {
                    blockpos$mutableblockpos.setWithOffset(pos, j, localY, k);
                    tryPlaceLeaf(level, foliageSetter, random, treeConfiguration, blockpos$mutableblockpos);
                }
            }
        }
    }

    protected final void placeLeavesRowWithHangingLeavesBelow(
        LevelSimulatedReader level,
        FoliagePlacer.FoliageSetter foliageSetter,
        RandomSource random,
        TreeConfiguration treeConfiguration,
        BlockPos pos,
        int range,
        int localY,
        boolean large,
        float hangingLeavesChance,
        float hangingLeavesExtensionChance
    ) {
        this.placeLeavesRow(level, foliageSetter, random, treeConfiguration, pos, range, localY, large);
        int i = large ? 1 : 0;
        BlockPos blockpos = pos.below();
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            Direction direction1 = direction.getClockWise();
            int j = direction1.getAxisDirection() == Direction.AxisDirection.POSITIVE ? range + i : range;
            blockpos$mutableblockpos.setWithOffset(pos, 0, localY - 1, 0).move(direction1, j).move(direction, -range);
            int k = -range;

            while (k < range + i) {
                boolean flag = foliageSetter.isSet(blockpos$mutableblockpos.move(Direction.UP));
                blockpos$mutableblockpos.move(Direction.DOWN);
                if (flag && tryPlaceExtension(level, foliageSetter, random, treeConfiguration, hangingLeavesChance, blockpos, blockpos$mutableblockpos)) {
                    blockpos$mutableblockpos.move(Direction.DOWN);
                    tryPlaceExtension(level, foliageSetter, random, treeConfiguration, hangingLeavesExtensionChance, blockpos, blockpos$mutableblockpos);
                    blockpos$mutableblockpos.move(Direction.UP);
                }

                k++;
                blockpos$mutableblockpos.move(direction);
            }
        }
    }

    private static boolean tryPlaceExtension(
        LevelSimulatedReader level,
        FoliagePlacer.FoliageSetter foliageSetter,
        RandomSource random,
        TreeConfiguration treeConfiguration,
        float extensionChance,
        BlockPos logPos,
        BlockPos.MutableBlockPos pos
    ) {
        if (pos.distManhattan(logPos) >= 7) {
            return false;
        } else {
            return random.nextFloat() > extensionChance ? false : tryPlaceLeaf(level, foliageSetter, random, treeConfiguration, pos);
        }
    }

    protected static boolean tryPlaceLeaf(
        LevelSimulatedReader level, FoliagePlacer.FoliageSetter foliageSetter, RandomSource random, TreeConfiguration treeConfiguration, BlockPos pos
    ) {
        if (!TreeFeature.validTreePos(level, pos)) {
            return false;
        } else {
            BlockState blockstate = treeConfiguration.foliageProvider.getState(random, pos);
            if (blockstate.hasProperty(BlockStateProperties.WATERLOGGED)) {
                blockstate = blockstate.setValue(
                    BlockStateProperties.WATERLOGGED,
                    Boolean.valueOf(level.isFluidAtPosition(pos, p_225638_ -> p_225638_.isSourceOfType(Fluids.WATER)))
                );
            }

            foliageSetter.set(pos, blockstate);
            return true;
        }
    }

    public static final class FoliageAttachment {
        private final BlockPos pos;
        private final int radiusOffset;
        private final boolean doubleTrunk;

        public FoliageAttachment(BlockPos pos, int radiusOffset, boolean doubleTrunk) {
            this.pos = pos;
            this.radiusOffset = radiusOffset;
            this.doubleTrunk = doubleTrunk;
        }

        public BlockPos pos() {
            return this.pos;
        }

        public int radiusOffset() {
            return this.radiusOffset;
        }

        public boolean doubleTrunk() {
            return this.doubleTrunk;
        }
    }

    public interface FoliageSetter {
        void set(BlockPos pos, BlockState state);

        boolean isSet(BlockPos pos);
    }
}
