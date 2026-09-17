package net.minecraft.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

public class ByIdMap {
    private static <T> IntFunction<T> createMap(ToIntFunction<T> keyExtractor, T[] values) {
        if (values.length == 0) {
            throw new IllegalArgumentException("Empty value list");
        } else {
            Int2ObjectMap<T> int2objectmap = new Int2ObjectOpenHashMap<>();

            for (T t : values) {
                int i = keyExtractor.applyAsInt(t);
                T t1 = int2objectmap.put(i, t);
                if (t1 != null) {
                    throw new IllegalArgumentException("Duplicate entry on id " + i + ": current=" + t + ", previous=" + t1);
                }
            }

            return int2objectmap;
        }
    }

    public static <T> IntFunction<T> sparse(ToIntFunction<T> keyExtractor, T[] values, T fallback) {
        IntFunction<T> intfunction = createMap(keyExtractor, values);
        return p_262932_ -> Objects.requireNonNullElse(intfunction.apply(p_262932_), fallback);
    }

    private static <T> T[] createSortedArray(ToIntFunction<T> keyExtractor, T[] values) {
        int i = values.length;
        if (i == 0) {
            throw new IllegalArgumentException("Empty value list");
        } else {
            T[] at = (T[])values.clone();
            Arrays.fill(at, null);

            for (T t : values) {
                int j = keyExtractor.applyAsInt(t);
                if (j < 0 || j >= i) {
                    throw new IllegalArgumentException("Values are not continous, found index " + j + " for value " + t);
                }

                T t1 = at[j];
                if (t1 != null) {
                    throw new IllegalArgumentException("Duplicate entry on id " + j + ": current=" + t + ", previous=" + t1);
                }

                at[j] = t;
            }

            for (int k = 0; k < i; k++) {
                if (at[k] == null) {
                    throw new IllegalArgumentException("Missing value at index: " + k);
                }
            }

            return at;
        }
    }

    public static <T> IntFunction<T> continuous(ToIntFunction<T> keyExtractor, T[] values, ByIdMap.OutOfBoundsStrategy outOfBoundsStrategy) {
        T[] at = createSortedArray(keyExtractor, values);
        int i = at.length;

        return switch (outOfBoundsStrategy) {
            case ZERO -> {
                T t = at[0];
                yield p_262927_ -> p_262927_ >= 0 && p_262927_ < i ? at[p_262927_] : t;
            }
            case WRAP -> p_262977_ -> at[Mth.positiveModulo(p_262977_, i)];
            case CLAMP -> p_263013_ -> at[Mth.clamp(p_263013_, 0, i - 1)];
        };
    }

    public static enum OutOfBoundsStrategy {
        ZERO,
        WRAP,
        CLAMP;
    }
}
