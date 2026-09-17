package net.minecraft.world.flag;

import com.mojang.serialization.Codec;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;

public class FeatureFlags {
    public static final FeatureFlag VANILLA;
    public static final FeatureFlag BUNDLE;
    public static final FeatureFlag TRADE_REBALANCE;
    public static final FeatureFlagRegistry REGISTRY;
    public static final Codec<FeatureFlagSet> CODEC;
    public static final FeatureFlagSet VANILLA_SET;
    public static final FeatureFlagSet DEFAULT_FLAGS;

    public static String printMissingFlags(FeatureFlagSet enabledFeatures, FeatureFlagSet requestedFeatures) {
        return printMissingFlags(REGISTRY, enabledFeatures, requestedFeatures);
    }

    public static String printMissingFlags(FeatureFlagRegistry registry, FeatureFlagSet enabledFeatures, FeatureFlagSet requestedFeatures) {
        Set<ResourceLocation> set = registry.toNames(requestedFeatures);
        Set<ResourceLocation> set1 = registry.toNames(enabledFeatures);
        return set.stream().filter(p_251831_ -> !set1.contains(p_251831_)).map(ResourceLocation::toString).collect(Collectors.joining(", "));
    }

    public static boolean isExperimental(FeatureFlagSet set) {
        return !set.isSubsetOf(VANILLA_SET);
    }

    static {
        FeatureFlagRegistry.Builder featureflagregistry$builder = new FeatureFlagRegistry.Builder("main");
        VANILLA = featureflagregistry$builder.createVanilla("vanilla");
        BUNDLE = featureflagregistry$builder.createVanilla("bundle");
        TRADE_REBALANCE = featureflagregistry$builder.createVanilla("trade_rebalance");
        net.neoforged.neoforge.common.util.flag.FeatureFlagLoader.loadModdedFlags(featureflagregistry$builder);
        REGISTRY = featureflagregistry$builder.build();
        CODEC = REGISTRY.codec();
        VANILLA_SET = FeatureFlagSet.of(VANILLA);
        DEFAULT_FLAGS = VANILLA_SET;
    }
}
