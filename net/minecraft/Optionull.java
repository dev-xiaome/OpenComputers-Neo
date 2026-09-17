package net.minecraft;

import java.util.Collection;
import java.util.Iterator;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

public class Optionull {
    @Nullable
    public static <T, R> R map(@Nullable T value, Function<T, R> mapper) {
        return value == null ? null : mapper.apply(value);
    }

    public static <T, R> R mapOrDefault(@Nullable T value, Function<T, R> mapper, R defaultValue) {
        return value == null ? defaultValue : mapper.apply(value);
    }

    public static <T, R> R mapOrElse(@Nullable T value, Function<T, R> mapper, Supplier<R> supplier) {
        return value == null ? supplier.get() : mapper.apply(value);
    }

    @Nullable
    public static <T> T first(Collection<T> collection) {
        Iterator<T> iterator = collection.iterator();
        return iterator.hasNext() ? iterator.next() : null;
    }

    public static <T> T firstOrDefault(Collection<T> collection, T defaultValue) {
        Iterator<T> iterator = collection.iterator();
        return iterator.hasNext() ? iterator.next() : defaultValue;
    }

    public static <T> T firstOrElse(Collection<T> collection, Supplier<T> supplier) {
        Iterator<T> iterator = collection.iterator();
        return iterator.hasNext() ? iterator.next() : supplier.get();
    }

    public static <T> boolean isNullOrEmpty(@Nullable T[] array) {
        return array == null || array.length == 0;
    }

    public static boolean isNullOrEmpty(@Nullable boolean[] array) {
        return array == null || array.length == 0;
    }

    public static boolean isNullOrEmpty(@Nullable byte[] array) {
        return array == null || array.length == 0;
    }

    public static boolean isNullOrEmpty(@Nullable char[] array) {
        return array == null || array.length == 0;
    }

    public static boolean isNullOrEmpty(@Nullable short[] array) {
        return array == null || array.length == 0;
    }

    public static boolean isNullOrEmpty(@Nullable int[] array) {
        return array == null || array.length == 0;
    }

    public static boolean isNullOrEmpty(@Nullable long[] array) {
        return array == null || array.length == 0;
    }

    public static boolean isNullOrEmpty(@Nullable float[] array) {
        return array == null || array.length == 0;
    }

    public static boolean isNullOrEmpty(@Nullable double[] array) {
        return array == null || array.length == 0;
    }
}
