package net.minecraft.util.random;

import java.util.List;
import java.util.Optional;
import net.minecraft.Util;
import net.minecraft.util.RandomSource;

public class WeightedRandom {
    private WeightedRandom() {
    }

    public static int getTotalWeight(List<? extends WeightedEntry> entries) {
        long i = 0L;

        for (WeightedEntry weightedentry : entries) {
            i += (long)weightedentry.getWeight().asInt();
        }

        if (i > 2147483647L) {
            throw new IllegalArgumentException("Sum of weights must be <= 2147483647");
        } else {
            return (int)i;
        }
    }

    public static <T extends WeightedEntry> Optional<T> getRandomItem(RandomSource random, List<T> entries, int totalWeight) {
        if (totalWeight < 0) {
            throw (IllegalArgumentException)Util.pauseInIde(new IllegalArgumentException("Negative total weight in getRandomItem"));
        } else if (totalWeight == 0) {
            return Optional.empty();
        } else {
            int i = random.nextInt(totalWeight);
            return getWeightedItem(entries, i);
        }
    }

    public static <T extends WeightedEntry> Optional<T> getWeightedItem(List<T> entries, int weightedIndex) {
        for (T t : entries) {
            weightedIndex -= t.getWeight().asInt();
            if (weightedIndex < 0) {
                return Optional.of(t);
            }
        }

        return Optional.empty();
    }

    public static <T extends WeightedEntry> Optional<T> getRandomItem(RandomSource random, List<T> entries) {
        return getRandomItem(random, entries, getTotalWeight(entries));
    }
}
