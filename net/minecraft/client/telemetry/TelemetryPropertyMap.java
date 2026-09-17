package net.minecraft.client.telemetry;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class TelemetryPropertyMap {
    final Map<TelemetryProperty<?>, Object> entries;

    TelemetryPropertyMap(Map<TelemetryProperty<?>, Object> entries) {
        this.entries = entries;
    }

    public static TelemetryPropertyMap.Builder builder() {
        return new TelemetryPropertyMap.Builder();
    }

    public static MapCodec<TelemetryPropertyMap> createCodec(final List<TelemetryProperty<?>> properties) {
        return new MapCodec<TelemetryPropertyMap>() {
            public <T> RecordBuilder<T> encode(TelemetryPropertyMap map, DynamicOps<T> ops, RecordBuilder<T> builder) {
                RecordBuilder<T> recordbuilder = builder;

                for (TelemetryProperty<?> telemetryproperty : properties) {
                    recordbuilder = this.encodeProperty(map, recordbuilder, telemetryproperty);
                }

                return recordbuilder;
            }

            private <T, V> RecordBuilder<T> encodeProperty(TelemetryPropertyMap map, RecordBuilder<T> builder, TelemetryProperty<V> key) {
                V v = map.get(key);
                return v != null ? builder.add(key.id(), v, key.codec()) : builder;
            }

            @Override
            public <T> DataResult<TelemetryPropertyMap> decode(DynamicOps<T> ops, MapLike<T> value) {
                DataResult<TelemetryPropertyMap.Builder> dataresult = DataResult.success(new TelemetryPropertyMap.Builder());

                for (TelemetryProperty<?> telemetryproperty : properties) {
                    dataresult = this.decodeProperty(dataresult, ops, value, telemetryproperty);
                }

                return dataresult.map(TelemetryPropertyMap.Builder::build);
            }

            private <T, V> DataResult<TelemetryPropertyMap.Builder> decodeProperty(
                DataResult<TelemetryPropertyMap.Builder> result, DynamicOps<T> ops, MapLike<T> value, TelemetryProperty<V> property
            ) {
                T t = value.get(property.id());
                if (t != null) {
                    DataResult<V> dataresult = property.codec().parse(ops, t);
                    return result.apply2stable((p_262028_, p_261796_) -> p_262028_.put(property, (V)p_261796_), dataresult);
                } else {
                    return result;
                }
            }

            @Override
            public <T> Stream<T> keys(DynamicOps<T> p_261746_) {
                return properties.stream().map(TelemetryProperty::id).map(p_261746_::createString);
            }
        };
    }

    @Nullable
    public <T> T get(TelemetryProperty<T> key) {
        return (T)this.entries.get(key);
    }

    @Override
    public String toString() {
        return this.entries.toString();
    }

    public Set<TelemetryProperty<?>> propertySet() {
        return this.entries.keySet();
    }

    @OnlyIn(Dist.CLIENT)
    public static class Builder {
        private final Map<TelemetryProperty<?>, Object> entries = new Reference2ObjectOpenHashMap<>();

        Builder() {
        }

        public <T> TelemetryPropertyMap.Builder put(TelemetryProperty<T> key, T value) {
            this.entries.put(key, value);
            return this;
        }

        public <T> TelemetryPropertyMap.Builder putIfNotNull(TelemetryProperty<T> key, @Nullable T value) {
            if (value != null) {
                this.entries.put(key, value);
            }

            return this;
        }

        public TelemetryPropertyMap.Builder putAll(TelemetryPropertyMap propertyMap) {
            this.entries.putAll(propertyMap.entries);
            return this;
        }

        public TelemetryPropertyMap build() {
            return new TelemetryPropertyMap(this.entries);
        }
    }
}
