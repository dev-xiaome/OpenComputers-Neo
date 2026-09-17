package net.minecraft.client.gui.font;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Arrays;
import java.util.function.IntFunction;
import javax.annotation.Nullable;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class CodepointMap<T> {
    private static final int BLOCK_BITS = 8;
    private static final int BLOCK_SIZE = 256;
    private static final int IN_BLOCK_MASK = 255;
    private static final int MAX_BLOCK = 4351;
    private static final int BLOCK_COUNT = 4352;
    private final T[] empty;
    private final T[][] blockMap;
    private final IntFunction<T[]> blockConstructor;

    public CodepointMap(IntFunction<T[]> blockConstructor, IntFunction<T[][]> blockMapConstructor) {
        this.empty = (T[])((Object[])blockConstructor.apply(256));
        this.blockMap = (T[][])((Object[][])blockMapConstructor.apply(4352));
        Arrays.fill(this.blockMap, this.empty);
        this.blockConstructor = blockConstructor;
    }

    public void clear() {
        Arrays.fill(this.blockMap, this.empty);
    }

    @Nullable
    public T get(int index) {
        int i = index >> 8;
        int j = index & 0xFF;
        return this.blockMap[i][j];
    }

    @Nullable
    public T put(int index, T value) {
        int i = index >> 8;
        int j = index & 0xFF;
        T[] at = this.blockMap[i];
        if (at == this.empty) {
            at = (T[])((Object[])this.blockConstructor.apply(256));
            this.blockMap[i] = at;
            at[j] = value;
            return null;
        } else {
            T t = at[j];
            at[j] = value;
            return t;
        }
    }

    public T computeIfAbsent(int index, IntFunction<T> valueIfAbsentGetter) {
        int i = index >> 8;
        int j = index & 0xFF;
        T[] at = this.blockMap[i];
        T t = at[j];
        if (t != null) {
            return t;
        } else {
            if (at == this.empty) {
                at = (T[])((Object[])this.blockConstructor.apply(256));
                this.blockMap[i] = at;
            }

            T t1 = valueIfAbsentGetter.apply(index);
            at[j] = t1;
            return t1;
        }
    }

    @Nullable
    public T remove(int p_285488_) {
        int i = p_285488_ >> 8;
        int j = p_285488_ & 0xFF;
        T[] at = this.blockMap[i];
        if (at == this.empty) {
            return null;
        } else {
            T t = at[j];
            at[j] = null;
            return t;
        }
    }

    public void forEach(CodepointMap.Output<T> output) {
        for (int i = 0; i < this.blockMap.length; i++) {
            T[] at = this.blockMap[i];
            if (at != this.empty) {
                for (int j = 0; j < at.length; j++) {
                    T t = at[j];
                    if (t != null) {
                        int k = i << 8 | j;
                        output.accept(k, t);
                    }
                }
            }
        }
    }

    public IntSet keySet() {
        IntOpenHashSet intopenhashset = new IntOpenHashSet();
        this.forEach((p_285165_, p_285389_) -> intopenhashset.add(p_285165_));
        return intopenhashset;
    }

    @FunctionalInterface
    @OnlyIn(Dist.CLIENT)
    public interface Output<T> {
        void accept(int index, T object);
    }
}
