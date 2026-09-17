package net.minecraft.world.level.block.state.properties;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.util.StringRepresentable;

public class EnumProperty<T extends Enum<T> & StringRepresentable> extends Property<T> {
    private final ImmutableSet<T> values;
    /**
     * Map of names to Enum values
     */
    private final Map<String, T> names = Maps.newHashMap();

    protected EnumProperty(String name, Class<T> clazz, Collection<T> values) {
        super(name, clazz);
        this.values = ImmutableSet.copyOf(values);

        for (T t : values) {
            String s = t.getSerializedName();
            if (this.names.containsKey(s)) {
                throw new IllegalArgumentException("Multiple values have the same name '" + s + "'");
            }

            this.names.put(s, t);
        }
    }

    @Override
    public Collection<T> getPossibleValues() {
        return this.values;
    }

    @Override
    public Optional<T> getValue(String value) {
        return Optional.ofNullable(this.names.get(value));
    }

    /**
     * @return the name for the given value.
     */
    public String getName(T value) {
        return value.getSerializedName();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else {
            if (other instanceof EnumProperty<?> enumproperty && super.equals(other)) {
                return this.values.equals(enumproperty.values) && this.names.equals(enumproperty.names);
            }

            return false;
        }
    }

    @Override
    public int generateHashCode() {
        int i = super.generateHashCode();
        i = 31 * i + this.values.hashCode();
        return 31 * i + this.names.hashCode();
    }

    /**
     * Create a new EnumProperty with all Enum constants of the given class.
     */
    public static <T extends Enum<T> & StringRepresentable> EnumProperty<T> create(String name, Class<T> clazz) {
        return create(name, clazz, p_187560_ -> true);
    }

    /**
     * Create a new EnumProperty with all Enum constants of the given class that match the given Predicate.
     */
    public static <T extends Enum<T> & StringRepresentable> EnumProperty<T> create(String name, Class<T> clazz, Predicate<T> filter) {
        return create(name, clazz, Arrays.<T>stream(clazz.getEnumConstants()).filter(filter).collect(Collectors.toList()));
    }

    /**
     * Create a new EnumProperty with the specified values
     */
    public static <T extends Enum<T> & StringRepresentable> EnumProperty<T> create(String name, Class<T> clazz, T... values) {
        return create(name, clazz, Lists.newArrayList(values));
    }

    /**
     * Create a new EnumProperty with the specified values
     */
    public static <T extends Enum<T> & StringRepresentable> EnumProperty<T> create(String name, Class<T> clazz, Collection<T> values) {
        return new EnumProperty<>(name, clazz, values);
    }
}
