package net.minecraft.world.level.block.state;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.Encoder;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.world.level.block.state.properties.Property;

public class StateDefinition<O, S extends StateHolder<O, S>> {
    static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9_]+$");
    private final O owner;
    private final ImmutableSortedMap<String, Property<?>> propertiesByName;
    private final ImmutableList<S> states;

    protected StateDefinition(Function<O, S> stateValueFunction, O owner, StateDefinition.Factory<O, S> valueFunction, Map<String, Property<?>> propertiesByName) {
        this.owner = owner;
        this.propertiesByName = ImmutableSortedMap.copyOf(propertiesByName);
        Supplier<S> supplier = () -> stateValueFunction.apply(owner);
        MapCodec<S> mapcodec = MapCodec.of(Encoder.empty(), Decoder.unit(supplier));

        for (Entry<String, Property<?>> entry : this.propertiesByName.entrySet()) {
            mapcodec = appendPropertyCodec(mapcodec, supplier, entry.getKey(), entry.getValue());
        }

        MapCodec<S> mapcodec1 = mapcodec;
        Map<Map<Property<?>, Comparable<?>>, S> map = Maps.newLinkedHashMap();
        List<S> list = Lists.newArrayList();
        Stream<List<Pair<Property<?>, Comparable<?>>>> stream = Stream.of(Collections.emptyList());

        for (Property<?> property : this.propertiesByName.values()) {
            stream = stream.flatMap(p_61072_ -> property.getPossibleValues().stream().map(p_155961_ -> {
                    List<Pair<Property<?>, Comparable<?>>> list1 = Lists.newArrayList(p_61072_);
                    list1.add(Pair.of(property, p_155961_));
                    return list1;
                }));
        }

        stream.forEach(p_325887_ -> {
            Reference2ObjectArrayMap<Property<?>, Comparable<?>> reference2objectarraymap = new Reference2ObjectArrayMap<>(p_325887_.size());

            for (Pair<Property<?>, Comparable<?>> pair : p_325887_) {
                reference2objectarraymap.put(pair.getFirst(), pair.getSecond());
            }

            S s1 = valueFunction.create(owner, reference2objectarraymap, mapcodec1);
            map.put(reference2objectarraymap, s1);
            list.add(s1);
        });

        for (S s : list) {
            s.populateNeighbours(map);
        }

        this.states = ImmutableList.copyOf(list);
    }

    private static <S extends StateHolder<?, S>, T extends Comparable<T>> MapCodec<S> appendPropertyCodec(
        MapCodec<S> propertyCodec, Supplier<S> holderSupplier, String value, Property<T> property
    ) {
        return Codec.mapPair(propertyCodec, property.valueCodec().fieldOf(value).orElseGet(p_187541_ -> {
            }, () -> property.value(holderSupplier.get())))
            .xmap(
                p_187536_ -> p_187536_.getFirst().setValue(property, p_187536_.getSecond().value()),
                p_187533_ -> Pair.of((S)p_187533_, property.value(p_187533_))
            );
    }

    public ImmutableList<S> getPossibleStates() {
        return this.states;
    }

    public S any() {
        return this.states.get(0);
    }

    public O getOwner() {
        return this.owner;
    }

    public Collection<Property<?>> getProperties() {
        return this.propertiesByName.values();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("block", this.owner)
            .add("properties", this.propertiesByName.values().stream().map(Property::getName).collect(Collectors.toList()))
            .toString();
    }

    @Nullable
    public Property<?> getProperty(String propertyName) {
        return this.propertiesByName.get(propertyName);
    }

    public static class Builder<O, S extends StateHolder<O, S>> {
        private final O owner;
        private final Map<String, Property<?>> properties = Maps.newHashMap();

        public Builder(O owner) {
            this.owner = owner;
        }

        public StateDefinition.Builder<O, S> add(Property<?>... properties) {
            for (Property<?> property : properties) {
                this.validateProperty(property);
                this.properties.put(property.getName(), property);
            }

            return this;
        }

        private <T extends Comparable<T>> void validateProperty(Property<T> property) {
            String s = property.getName();
            if (!StateDefinition.NAME_PATTERN.matcher(s).matches()) {
                throw new IllegalArgumentException(this.owner + " has invalidly named property: " + s);
            } else {
                Collection<T> collection = property.getPossibleValues();
                if (collection.size() <= 1) {
                    throw new IllegalArgumentException(this.owner + " attempted use property " + s + " with <= 1 possible values");
                } else {
                    for (T t : collection) {
                        String s1 = property.getName(t);
                        if (!StateDefinition.NAME_PATTERN.matcher(s1).matches()) {
                            throw new IllegalArgumentException(this.owner + " has property: " + s + " with invalidly named value: " + s1);
                        }
                    }

                    if (this.properties.containsKey(s)) {
                        throw new IllegalArgumentException(this.owner + " has duplicate property: " + s);
                    }
                }
            }
        }

        public StateDefinition<O, S> create(Function<O, S> stateValueFunction, StateDefinition.Factory<O, S> stateFunction) {
            return new StateDefinition<>(stateValueFunction, this.owner, stateFunction, this.properties);
        }
    }

    public interface Factory<O, S> {
        S create(O owner, Reference2ObjectArrayMap<Property<?>, Comparable<?>> values, MapCodec<S> propertiesCodec);
    }
}
