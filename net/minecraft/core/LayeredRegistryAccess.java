package net.minecraft.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.resources.ResourceKey;

public class LayeredRegistryAccess<T> {
    private final List<T> keys;
    private final List<RegistryAccess.Frozen> values;
    private final RegistryAccess.Frozen composite;

    public LayeredRegistryAccess(List<T> keys) {
        this(keys, Util.make(() -> {
            RegistryAccess.Frozen[] aregistryaccess$frozen = new RegistryAccess.Frozen[keys.size()];
            Arrays.fill(aregistryaccess$frozen, RegistryAccess.EMPTY);
            return Arrays.asList(aregistryaccess$frozen);
        }));
    }

    private LayeredRegistryAccess(List<T> keys, List<RegistryAccess.Frozen> values) {
        this.keys = List.copyOf(keys);
        this.values = List.copyOf(values);
        this.composite = new RegistryAccess.ImmutableRegistryAccess(collectRegistries(values.stream())).freeze();
    }

    private int getLayerIndexOrThrow(T key) {
        int i = this.keys.indexOf(key);
        if (i == -1) {
            throw new IllegalStateException("Can't find " + key + " inside " + this.keys);
        } else {
            return i;
        }
    }

    public RegistryAccess.Frozen getLayer(T key) {
        int i = this.getLayerIndexOrThrow(key);
        return this.values.get(i);
    }

    public RegistryAccess.Frozen getAccessForLoading(T key) {
        int i = this.getLayerIndexOrThrow(key);
        return this.getCompositeAccessForLayers(0, i);
    }

    public RegistryAccess.Frozen getAccessFrom(T key) {
        int i = this.getLayerIndexOrThrow(key);
        return this.getCompositeAccessForLayers(i, this.values.size());
    }

    private RegistryAccess.Frozen getCompositeAccessForLayers(int startIndex, int endIndex) {
        return new RegistryAccess.ImmutableRegistryAccess(collectRegistries(this.values.subList(startIndex, endIndex).stream())).freeze();
    }

    public LayeredRegistryAccess<T> replaceFrom(T key, RegistryAccess.Frozen... values) {
        return this.replaceFrom(key, Arrays.asList(values));
    }

    public LayeredRegistryAccess<T> replaceFrom(T key, List<RegistryAccess.Frozen> values) {
        int i = this.getLayerIndexOrThrow(key);
        if (values.size() > this.values.size() - i) {
            throw new IllegalStateException("Too many values to replace");
        } else {
            List<RegistryAccess.Frozen> list = new ArrayList<>();

            for (int j = 0; j < i; j++) {
                list.add(this.values.get(j));
            }

            list.addAll(values);

            while (list.size() < this.values.size()) {
                list.add(RegistryAccess.EMPTY);
            }

            return new LayeredRegistryAccess<>(this.keys, list);
        }
    }

    public RegistryAccess.Frozen compositeAccess() {
        return this.composite;
    }

    private static Map<ResourceKey<? extends Registry<?>>, Registry<?>> collectRegistries(Stream<? extends RegistryAccess> accesses) {
        Map<ResourceKey<? extends Registry<?>>, Registry<?>> map = new HashMap<>();
        accesses.forEach(p_252003_ -> p_252003_.registries().forEach(p_339330_ -> {
                if (map.put(p_339330_.key(), p_339330_.value()) != null) {
                    throw new IllegalStateException("Duplicated registry " + p_339330_.key());
                }
            }));
        return map;
    }
}
