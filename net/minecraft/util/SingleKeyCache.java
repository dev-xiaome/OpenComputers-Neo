package net.minecraft.util;

import java.util.Objects;
import java.util.function.Function;
import javax.annotation.Nullable;

public class SingleKeyCache<K, V> {
    private final Function<K, V> computeValue;
    @Nullable
    private K cacheKey = (K)null;
    @Nullable
    private V cachedValue;

    public SingleKeyCache(Function<K, V> computeValue) {
        this.computeValue = computeValue;
    }

    public V getValue(K cacheKey) {
        if (this.cachedValue == null || !Objects.equals(this.cacheKey, cacheKey)) {
            this.cachedValue = this.computeValue.apply(cacheKey);
            this.cacheKey = cacheKey;
        }

        return this.cachedValue;
    }
}
