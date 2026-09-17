package net.minecraft.world.flag;

import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

public class FeatureFlagRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final FeatureFlagUniverse universe;
    private final Map<ResourceLocation, FeatureFlag> names;
    private final FeatureFlagSet allFlags;

    FeatureFlagRegistry(FeatureFlagUniverse universe, FeatureFlagSet allFlags, Map<ResourceLocation, FeatureFlag> names) {
        this.universe = universe;
        this.names = names;
        this.allFlags = allFlags;
    }

    public boolean isSubset(FeatureFlagSet set) {
        return set.isSubsetOf(this.allFlags);
    }

    public FeatureFlagSet allFlags() {
        return this.allFlags;
    }

    public FeatureFlagSet fromNames(Iterable<ResourceLocation> names) {
        return this.fromNames(names, p_251224_ -> LOGGER.warn("Unknown feature flag: {}", p_251224_));
    }

    public FeatureFlagSet subset(FeatureFlag... flags) {
        return FeatureFlagSet.create(this.universe, Arrays.asList(flags));
    }

    public FeatureFlagSet fromNames(Iterable<ResourceLocation> names, Consumer<ResourceLocation> onError) {
        Set<FeatureFlag> set = Sets.newIdentityHashSet();

        for (ResourceLocation resourcelocation : names) {
            FeatureFlag featureflag = this.names.get(resourcelocation);
            if (featureflag == null) {
                onError.accept(resourcelocation);
            } else {
                set.add(featureflag);
            }
        }

        return FeatureFlagSet.create(this.universe, set);
    }

    public Set<ResourceLocation> toNames(FeatureFlagSet p_set) {
        Set<ResourceLocation> set = new HashSet<>();
        this.names.forEach((p_252018_, p_250772_) -> {
            if (p_set.contains(p_250772_)) {
                set.add(p_252018_);
            }
        });
        return set;
    }

    public Codec<FeatureFlagSet> codec() {
        return ResourceLocation.CODEC.listOf().comapFlatMap(p_275144_ -> {
            Set<ResourceLocation> set = new HashSet<>();
            FeatureFlagSet featureflagset = this.fromNames(p_275144_, set::add);
            return !set.isEmpty() ? DataResult.error(() -> "Unknown feature ids: " + set, featureflagset) : DataResult.success(featureflagset);
        }, p_249796_ -> List.copyOf(this.toNames(p_249796_)));
    }

    public FeatureFlag getFlag(ResourceLocation name) {
        return com.google.common.base.Preconditions.checkNotNull(this.names.get(name), "Flag %s was not registered", name);
    }

    public Map<ResourceLocation, FeatureFlag> getAllFlags() {
        return this.names;
    }

    public boolean hasAnyModdedFlags() {
        return this.names.values().stream().anyMatch(FeatureFlag::isModded);
    }

    public static class Builder {
        private final FeatureFlagUniverse universe;
        private int id;
        private final Map<ResourceLocation, FeatureFlag> flags = new LinkedHashMap<>();

        public Builder(String id) {
            this.universe = new FeatureFlagUniverse(id);
        }

        public FeatureFlag createVanilla(String id) {
            return this.create(ResourceLocation.withDefaultNamespace(id));
        }

        /**
         * @deprecated Neo: use {@link #create(ResourceLocation, boolean)} instead
         */
        @Deprecated
        public FeatureFlag create(ResourceLocation location) {
            return create(location, false);
        }

        public FeatureFlag create(ResourceLocation location, boolean modded) {
            if (this.id >= 64 && false) {
                throw new IllegalStateException("Too many feature flags");
            } else {
                FeatureFlag featureflag = new FeatureFlag(this.universe, this.id % FeatureFlagSet.MAX_CONTAINER_SIZE, this.id / FeatureFlagSet.MAX_CONTAINER_SIZE, modded);
                this.id++;
                FeatureFlag featureflag1 = this.flags.put(location, featureflag);
                if (featureflag1 != null) {
                    throw new IllegalStateException("Duplicate feature flag " + location);
                } else {
                    return featureflag;
                }
            }
        }

        public FeatureFlagRegistry build() {
            FeatureFlagSet featureflagset = FeatureFlagSet.create(this.universe, this.flags.values());
            return new FeatureFlagRegistry(this.universe, featureflagset, Map.copyOf(this.flags));
        }
    }
}
