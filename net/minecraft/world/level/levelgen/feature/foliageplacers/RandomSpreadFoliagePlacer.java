package net.minecraft.world.level.levelgen.feature.foliageplacers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;

public class RandomSpreadFoliagePlacer extends FoliagePlacer {
    public static final MapCodec<RandomSpreadFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec(
        p_161522_ -> foliagePlacerParts(p_161522_)
                .and(
                    p_161522_.group(
                        IntProvider.codec(1, 512).fieldOf("foliage_height").forGetter(p_161537_ -> p_161537_.foliageHeight),
                        Codec.intRange(0, 256).fieldOf("leaf_placement_attempts").forGetter(p_161524_ -> p_161524_.leafPlacementAttempts)
                    )
                )
                .apply(p_161522_, RandomSpreadFoliagePlacer::new)
    );
    private final IntProvider foliageHeight;
    private final int leafPlacementAttempts;

    public RandomSpreadFoliagePlacer(IntProvider radius, IntProvider offset, IntProvider foliageHeight, int leafPlacementAttempts) {
        super(radius, offset);
        this.foliageHeight = foliageHeight;
        this.leafPlacementAttempts = leafPlacementAttempts;
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return FoliagePlacerType.RANDOM_SPREAD_FOLIAGE_PLACER;
    }

    @Override
    protected void createFoliage(
        LevelSimulatedReader level,
        FoliagePlacer.FoliageSetter blockSetter,
        RandomSource random,
        TreeConfiguration config,
        int maxFreeTreeHeight,
        FoliagePlacer.FoliageAttachment attachment,
        int foliageHeight,
        int foliageRadius,
        int offset
    ) {
        BlockPos blockpos = attachment.pos();
        BlockPos.MutableBlockPos blockpos$mutableblockpos = blockpos.mutable();

        for (int i = 0; i < this.leafPlacementAttempts; i++) {
            blockpos$mutableblockpos.setWithOffset(
                blockpos,
                random.nextInt(foliageRadius) - random.nextInt(foliageRadius),
                random.nextInt(foliageHeight) - random.nextInt(foliageHeight),
                random.nextInt(foliageRadius) - random.nextInt(foliageRadius)
            );
            tryPlaceLeaf(level, blockSetter, random, config, blockpos$mutableblockpos);
        }
    }

    @Override
    public int foliageHeight(RandomSource random, int height, TreeConfiguration config) {
        return this.foliageHeight.sample(random);
    }

    /**
     * Skips certain positions based on the provided shape, such as rounding corners randomly.
     * The coordinates are passed in as absolute value, and should be within [0, {@code range}].
     */
    @Override
    protected boolean shouldSkipLocation(RandomSource random, int localX, int localY, int localZ, int range, boolean large) {
        return false;
    }
}
