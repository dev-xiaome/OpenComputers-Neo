package net.minecraft.core.component;

import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterators;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;

public interface DataComponentMap extends Iterable<TypedDataComponent<?>> {
    DataComponentMap EMPTY = new DataComponentMap() {
        @Nullable
        @Override
        public <T> T get(DataComponentType<? extends T> p_331168_) {
            return null;
        }

        @Override
        public Set<DataComponentType<?>> keySet() {
            return Set.of();
        }

        @Override
        public Iterator<TypedDataComponent<?>> iterator() {
            return Collections.emptyIterator();
        }
    };
    Codec<DataComponentMap> CODEC = makeCodecFromMap(DataComponentType.VALUE_MAP_CODEC);

    static Codec<DataComponentMap> makeCodec(Codec<DataComponentType<?>> codec) {
        return makeCodecFromMap(Codec.dispatchedMap(codec, DataComponentType::codecOrThrow));
    }

    static Codec<DataComponentMap> makeCodecFromMap(Codec<Map<DataComponentType<?>, Object>> codec) {
        return codec.flatComapMap(DataComponentMap.Builder::buildFromMapTrusted, p_337448_ -> {
            int i = p_337448_.size();
            if (i == 0) {
                return DataResult.success(Reference2ObjectMaps.emptyMap());
            } else {
                Reference2ObjectMap<DataComponentType<?>, Object> reference2objectmap = new Reference2ObjectArrayMap<>(i);

                for (TypedDataComponent<?> typeddatacomponent : p_337448_) {
                    if (!typeddatacomponent.type().isTransient()) {
                        reference2objectmap.put(typeddatacomponent.type(), typeddatacomponent.value());
                    }
                }

                return DataResult.success(reference2objectmap);
            }
        });
    }

    static DataComponentMap composite(final DataComponentMap map1, final DataComponentMap map2) {
        return new DataComponentMap() {
            @Nullable
            @Override
            public <T> T get(DataComponentType<? extends T> p_330291_) {
                T t = map2.get(p_330291_);
                return t != null ? t : map1.get(p_330291_);
            }

            @Override
            public Set<DataComponentType<?>> keySet() {
                return Sets.union(map1.keySet(), map2.keySet());
            }
        };
    }

    static DataComponentMap.Builder builder() {
        return new DataComponentMap.Builder();
    }

    @Nullable
    <T> T get(DataComponentType<? extends T> component);

    Set<DataComponentType<?>> keySet();

    default boolean has(DataComponentType<?> component) {
        return this.get(component) != null;
    }

    default <T> T getOrDefault(DataComponentType<? extends T> component, T defaultValue) {
        T t = this.get(component);
        return t != null ? t : defaultValue;
    }

    @Nullable
    default <T> TypedDataComponent<T> getTyped(DataComponentType<T> component) {
        T t = this.get(component);
        return t != null ? new TypedDataComponent<>(component, t) : null;
    }

    @Override
    default Iterator<TypedDataComponent<?>> iterator() {
        return Iterators.transform(this.keySet().iterator(), p_330954_ -> Objects.requireNonNull(this.getTyped((DataComponentType<?>)p_330954_)));
    }

    default Stream<TypedDataComponent<?>> stream() {
        return StreamSupport.stream(Spliterators.spliterator(this.iterator(), (long)this.size(), 1345), false);
    }

    default int size() {
        return this.keySet().size();
    }

    default boolean isEmpty() {
        return this.size() == 0;
    }

    default DataComponentMap filter(final Predicate<DataComponentType<?>> predicate) {
        return new DataComponentMap() {
            @Nullable
            @Override
            public <T> T get(DataComponentType<? extends T> p_341052_) {
                return predicate.test(p_341052_) ? DataComponentMap.this.get(p_341052_) : null;
            }

            @Override
            public Set<DataComponentType<?>> keySet() {
                return Sets.filter(DataComponentMap.this.keySet(), predicate::test);
            }
        };
    }

    public static class Builder implements net.neoforged.neoforge.common.extensions.IDataComponentMapBuilderExtensions {
        private final Reference2ObjectMap<DataComponentType<?>, Object> map = new Reference2ObjectArrayMap<>();

        Builder() {
        }

        public <T> DataComponentMap.Builder set(DataComponentType<T> component, @Nullable T value) {
            this.setUnchecked(component, value);
            return this;
        }

        <T> void setUnchecked(DataComponentType<T> component, @Nullable Object value) {
            if (value != null) {
                this.map.put(component, value);
            } else {
                this.map.remove(component);
            }
        }

        public DataComponentMap.Builder addAll(DataComponentMap components) {
            for (TypedDataComponent<?> typeddatacomponent : components) {
                this.map.put(typeddatacomponent.type(), typeddatacomponent.value());
            }

            return this;
        }

        public DataComponentMap build() {
            return buildFromMapTrusted(this.map);
        }

        private static DataComponentMap buildFromMapTrusted(Map<DataComponentType<?>, Object> map) {
            if (map.isEmpty()) {
                return DataComponentMap.EMPTY;
            } else {
                return map.size() < 8
                    ? new DataComponentMap.Builder.SimpleMap(new Reference2ObjectArrayMap<>(map))
                    : new DataComponentMap.Builder.SimpleMap(new Reference2ObjectOpenHashMap<>(map));
            }
        }

        static record SimpleMap(Reference2ObjectMap<DataComponentType<?>, Object> map) implements DataComponentMap {
            @Nullable
            @Override
            public <T> T get(DataComponentType<? extends T> p_331063_) {
                return (T)this.map.get(p_331063_);
            }

            @Override
            public boolean has(DataComponentType<?> p_331343_) {
                return this.map.containsKey(p_331343_);
            }

            @Override
            public Set<DataComponentType<?>> keySet() {
                return this.map.keySet();
            }

            @Override
            public Iterator<TypedDataComponent<?>> iterator() {
                return Iterators.transform(Reference2ObjectMaps.fastIterator(this.map), TypedDataComponent::fromEntryUnchecked);
            }

            @Override
            public int size() {
                return this.map.size();
            }

            @Override
            public String toString() {
                return this.map.toString();
            }
        }
    }
}
