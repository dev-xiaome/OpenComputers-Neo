package net.minecraft.world.level.block.state;

import com.google.common.collect.ArrayTable;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.world.level.block.state.properties.Property;

public abstract class StateHolder<O, S> {
    public static final String NAME_TAG = "Name";
    public static final String PROPERTIES_TAG = "Properties";
    private static final Function<Entry<Property<?>, Comparable<?>>, String> PROPERTY_ENTRY_TO_STRING_FUNCTION = new Function<Entry<Property<?>, Comparable<?>>, String>() {
        public String apply(@Nullable Entry<Property<?>, Comparable<?>> propertyEntry) {
            if (propertyEntry == null) {
                return "<NULL>";
            } else {
                Property<?> property = propertyEntry.getKey();
                return property.getName() + "=" + this.getName(property, propertyEntry.getValue());
            }
        }

        private <T extends Comparable<T>> String getName(Property<T> property, Comparable<?> value) {
            return property.getName((T)value);
        }
    };
    protected final O owner;
    private final Reference2ObjectArrayMap<Property<?>, Comparable<?>> values;
    private Table<Property<?>, Comparable<?>, S> neighbours;
    protected final MapCodec<S> propertiesCodec;

    protected StateHolder(O owner, Reference2ObjectArrayMap<Property<?>, Comparable<?>> values, MapCodec<S> propertiesCodec) {
        this.owner = owner;
        this.values = values;
        this.propertiesCodec = propertiesCodec;
    }

    public <T extends Comparable<T>> S cycle(Property<T> property) {
        return this.setValue(property, findNextInCollection(property.getPossibleValues(), this.getValue(property)));
    }

    protected static <T> T findNextInCollection(Collection<T> collection, T value) {
        Iterator<T> iterator = collection.iterator();

        while (iterator.hasNext()) {
            if (iterator.next().equals(value)) {
                if (iterator.hasNext()) {
                    return iterator.next();
                }

                return collection.iterator().next();
            }
        }

        return iterator.next();
    }

    @Override
    public String toString() {
        StringBuilder stringbuilder = new StringBuilder();
        stringbuilder.append(this.owner);
        if (!this.getValues().isEmpty()) {
            stringbuilder.append('[');
            stringbuilder.append(this.getValues().entrySet().stream().map(PROPERTY_ENTRY_TO_STRING_FUNCTION).collect(Collectors.joining(",")));
            stringbuilder.append(']');
        }

        return stringbuilder.toString();
    }

    public Collection<Property<?>> getProperties() {
        return Collections.unmodifiableCollection(this.values.keySet());
    }

    public <T extends Comparable<T>> boolean hasProperty(Property<T> property) {
        return this.values.containsKey(property);
    }

    /**
     * @return the value of the given Property for this state
     */
    public <T extends Comparable<T>> T getValue(Property<T> property) {
        Comparable<?> comparable = this.values.get(property);
        if (comparable == null) {
            throw new IllegalArgumentException("Cannot get property " + property + " as it does not exist in " + this.owner);
        } else {
            return property.getValueClass().cast(comparable);
        }
    }

    public <T extends Comparable<T>> Optional<T> getOptionalValue(Property<T> property) {
        Comparable<?> comparable = this.values.get(property);
        return comparable == null ? Optional.empty() : Optional.of(property.getValueClass().cast(comparable));
    }

    public <T extends Comparable<T>, V extends T> S setValue(Property<T> property, V value) {
        Comparable<?> comparable = this.values.get(property);
        if (comparable == null) {
            throw new IllegalArgumentException("Cannot set property " + property + " as it does not exist in " + this.owner);
        } else if (comparable.equals(value)) {
            return (S)this;
        } else {
            S s = this.neighbours.get(property, value);
            if (s == null) {
                throw new IllegalArgumentException("Cannot set property " + property + " to " + value + " on " + this.owner + ", it is not an allowed value");
            } else {
                return s;
            }
        }
    }

    public <T extends Comparable<T>, V extends T> S trySetValue(Property<T> property, V value) {
        Comparable<?> comparable = this.values.get(property);
        if (comparable != null && !comparable.equals(value)) {
            S s = this.neighbours.get(property, value);
            if (s == null) {
                throw new IllegalArgumentException(
                    "Cannot set property " + property + " to " + value + " on " + this.owner + ", it is not an allowed value"
                );
            } else {
                return s;
            }
        } else {
            return (S)this;
        }
    }

    public void populateNeighbours(Map<Map<Property<?>, Comparable<?>>, S> possibleStateMap) {
        if (this.neighbours != null) {
            throw new IllegalStateException();
        } else {
            Table<Property<?>, Comparable<?>, S> table = HashBasedTable.create();

            for (Entry<Property<?>, Comparable<?>> entry : this.values.entrySet()) {
                Property<?> property = entry.getKey();

                for (Comparable<?> comparable : property.getPossibleValues()) {
                    if (!comparable.equals(entry.getValue())) {
                        table.put(property, comparable, possibleStateMap.get(this.makeNeighbourValues(property, comparable)));
                    }
                }
            }

            this.neighbours = (Table<Property<?>, Comparable<?>, S>)(table.isEmpty() ? table : ArrayTable.create(table));
        }
    }

    private Map<Property<?>, Comparable<?>> makeNeighbourValues(Property<?> property, Comparable<?> value) {
        Map<Property<?>, Comparable<?>> map = new Reference2ObjectArrayMap<>(this.values);
        map.put(property, value);
        return map;
    }

    public Map<Property<?>, Comparable<?>> getValues() {
        return this.values;
    }

    protected static <O, S extends StateHolder<O, S>> Codec<S> codec(Codec<O> propertyMap, Function<O, S> holderFunction) {
        return propertyMap.dispatch(
            "Name",
            p_61121_ -> p_61121_.owner,
            p_338076_ -> {
                S s = holderFunction.apply((O)p_338076_);
                return s.getValues().isEmpty()
                    ? MapCodec.unit(s)
                    : s.propertiesCodec.codec().lenientOptionalFieldOf("Properties").xmap(p_187544_ -> p_187544_.orElse(s), Optional::of);
            }
        );
    }
}
