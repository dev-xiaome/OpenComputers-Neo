package net.minecraft.stats;

import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/**
 * An immutable statistic to be counted for a particular entry in the {@linkplain #type}'s registry. This is used as a key in a {@link net.minecraft.stats.StatsCounter} for a corresponding count.
 * <p>
 * By default, the statistic's {@linkplain #getName() name} is formatted {@code <stat type namespace>.<stat type path>:<value namespace>.<value path>}, as created by {@link #buildName(StatType, Object)}.
 *
 * @param <T> the type of the registry entry for this statistic
 * @see net.minecraft.stats.StatType
 * @see net.minecraft.stats.Stats
 */
public class Stat<T> extends ObjectiveCriteria {
    public static final StreamCodec<RegistryFriendlyByteBuf, Stat<?>> STREAM_CODEC = ByteBufCodecs.registry(Registries.STAT_TYPE)
        .dispatch(Stat::getType, StatType::streamCodec);
    private final StatFormatter formatter;
    /**
     * The registry entry for this statistic.
     */
    private final T value;
    /**
     * The parent statistic type.
     */
    private final StatType<T> type;

    protected Stat(StatType<T> type, T value, StatFormatter formatter) {
        super(buildName(type, value));
        this.type = type;
        this.formatter = formatter;
        this.value = value;
    }

    /**
     * @return the name for the specified {@code type} and {@code value} in the form {@code <stat type namespace>.<stat type path>:<value namespace>.<value path>}
     */
    public static <T> String buildName(StatType<T> type, T value) {
        return locationToKey(BuiltInRegistries.STAT_TYPE.getKey(type)) + ":" + locationToKey(type.getRegistry().getKey(value));
    }

    /**
     * @return the specified {@code location} as a string with {@code .} as the separator character
     */
    private static <T> String locationToKey(@Nullable ResourceLocation location) {
        return location.toString().replace(':', '.');
    }

    public StatType<T> getType() {
        return this.type;
    }

    public T getValue() {
        return this.value;
    }

    public String format(int value) {
        return this.formatter.format(value);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Stat && Objects.equals(this.getName(), ((Stat)other).getName());
    }

    @Override
    public int hashCode() {
        return this.getName().hashCode();
    }

    @Override
    public String toString() {
        return "Stat{name=" + this.getName() + ", formatter=" + this.formatter + "}";
    }
}
