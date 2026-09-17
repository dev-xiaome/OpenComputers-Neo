package net.minecraft.util.random;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.util.RandomSource;

public class WeightedRandomList<E extends WeightedEntry> {
    private final int totalWeight;
    private final ImmutableList<E> items;

    WeightedRandomList(List<? extends E> items) {
        this.items = ImmutableList.copyOf(items);
        this.totalWeight = WeightedRandom.getTotalWeight(items);
    }

    public static <E extends WeightedEntry> WeightedRandomList<E> create() {
        return new WeightedRandomList<>(ImmutableList.of());
    }

    @SafeVarargs
    public static <E extends WeightedEntry> WeightedRandomList<E> create(E... items) {
        return new WeightedRandomList<>(ImmutableList.copyOf(items));
    }

    public static <E extends WeightedEntry> WeightedRandomList<E> create(List<E> items) {
        return new WeightedRandomList<>(items);
    }

    public boolean isEmpty() {
        return this.items.isEmpty();
    }

    public Optional<E> getRandom(RandomSource random) {
        if (this.totalWeight == 0) {
            return Optional.empty();
        } else {
            int i = random.nextInt(this.totalWeight);
            return WeightedRandom.getWeightedItem(this.items, i);
        }
    }

    public List<E> unwrap() {
        return this.items;
    }

    public static <E extends WeightedEntry> Codec<WeightedRandomList<E>> codec(Codec<E> elementCodec) {
        return elementCodec.listOf().xmap(WeightedRandomList::create, WeightedRandomList::unwrap);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        } else if (other != null && this.getClass() == other.getClass()) {
            WeightedRandomList<?> weightedrandomlist = (WeightedRandomList<?>)other;
            return this.totalWeight == weightedrandomlist.totalWeight && Objects.equals(this.items, weightedrandomlist.items);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.totalWeight, this.items);
    }
}
