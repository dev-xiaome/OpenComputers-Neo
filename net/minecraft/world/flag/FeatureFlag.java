package net.minecraft.world.flag;

public class FeatureFlag {
    final FeatureFlagUniverse universe;
    final long mask;
    final int extMaskIndex;
    final boolean modded;

    /**
     * @deprecated Neo: use {@link #FeatureFlag(FeatureFlagUniverse, int, int, boolean
     *             )} instead
     */
    @Deprecated
    FeatureFlag(FeatureFlagUniverse universe, int maskBit) {
        this(universe, maskBit, 0, false);
    }

    FeatureFlag(FeatureFlagUniverse universe, int maskBit, int offset, boolean modded) {
        this.universe = universe;
        this.mask = 1L << maskBit;
        this.extMaskIndex = offset - 1;
        this.modded = modded;
    }

    public boolean isModded() {
        return modded;
    }
}
