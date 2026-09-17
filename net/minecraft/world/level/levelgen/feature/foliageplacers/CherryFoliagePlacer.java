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

public class CherryFoliagePlacer extends FoliagePlacer {
    public static final MapCodec<CherryFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec(
        p_273246_ -> foliagePlacerParts(p_273246_)
                .and(
                    p_273246_.group(
                        IntProvider.codec(4, 16).fieldOf("height").forGetter(p_273527_ -> p_273527_.height),
                        Codec.floatRange(0.0F, 1.0F).fieldOf("wide_bottom_layer_hole_chance").forGetter(p_273760_ -> p_273760_.wideBottomLayerHoleChance),
                        Codec.floatRange(0.0F, 1.0F).fieldOf("corner_hole_chance").forGetter(p_273020_ -> p_273020_.wideBottomLayerHoleChance),
                        Codec.floatRange(0.0F, 1.0F).fieldOf("hanging_leaves_chance").forGetter(p_273148_ -> p_273148_.hangingLeavesChance),
                        Codec.floatRange(0.0F, 1.0F).fieldOf("hanging_leaves_extension_chance").forGetter(p_273098_ -> p_273098_.hangingLeavesExtensionChance)
                    )
                )
                .apply(p_273246_, CherryFoliagePlacer::new)
    );
    private final IntProvider height;
    private final float wideBottomLayerHoleChance;
    private final float cornerHoleChance;
    private final float hangingLeavesChance;
    private final float hangingLeavesExtensionChance;

    public CherryFoliagePlacer(
        IntProvider radius, IntProvider offset, IntProvider height, float wideBottomLayerHoleChance, float cornerHoleChance, float hangingLeavesChance, float hangingLeavesExtensionChance
    ) {
        super(radius, offset);
        this.height = height;
        this.wideBottomLayerHoleChance = wideBottomLayerHoleChance;
        this.cornerHoleChance = cornerHoleChance;
        this.hangingLeavesChance = hangingLeavesChance;
        this.hangingLeavesExtensionChance = hangingLeavesExtensionChance;
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return FoliagePlacerType.CHERRY_FOLIAGE_PLACER;
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
        boolean flag = attachment.doubleTrunk();
        BlockPos blockpos = attachment.pos().above(offset);
        int i = foliageRadius + attachment.radiusOffset() - 1;
        this.placeLeavesRow(level, blockSetter, random, config, blockpos, i - 2, foliageHeight - 3, flag);
        this.placeLeavesRow(level, blockSetter, random, config, blockpos, i - 1, foliageHeight - 4, flag);

        for (int j = foliageHeight - 5; j >= 0; j--) {
            this.placeLeavesRow(level, blockSetter, random, config, blockpos, i, j, flag);
        }

        this.placeLeavesRowWithHangingLeavesBelow(
            level, blockSetter, random, config, blockpos, i, -1, flag, this.hangingLeavesChance, this.hangingLeavesExtensionChance
        );
        this.placeLeavesRowWithHangingLeavesBelow(
            level, blockSetter, random, config, blockpos, i - 1, -2, flag, this.hangingLeavesChance, this.hangingLeavesExtensionChance
        );
    }

    @Override
    public int foliageHeight(RandomSource random, int height, TreeConfiguration config) {
        return this.height.sample(random);
    }

    /**
     * Skips certain positions based on the provided shape, such as rounding corners randomly.
     * The coordinates are passed in as absolute value, and should be within [0, {@code range}].
     */
    @Override
    protected boolean shouldSkipLocation(RandomSource random, int localX, int localY, int localZ, int range, boolean large) {
        if (localY == -1 && (localX == range || localZ == range) && random.nextFloat() < this.wideBottomLayerHoleChance) {
            return true;
        } else {
            boolean flag = localX == range && localZ == range;
            boolean flag1 = range > 2;
            return flag1
                ? flag || localX + localZ > range * 2 - 2 && random.nextFloat() < this.cornerHoleChance
                : flag && random.nextFloat() < this.cornerHoleChance;
        }
    }
}
