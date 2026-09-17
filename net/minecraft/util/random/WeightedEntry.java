package net.minecraft.util.random;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.function.BiFunction;
import java.util.function.Function;

public interface WeightedEntry {
    Weight getWeight();

    static <T> WeightedEntry.Wrapper<T> wrap(T data, int weight) {
        return new WeightedEntry.Wrapper<>(data, Weight.of(weight));
    }

    public static class IntrusiveBase implements WeightedEntry {
        private final Weight weight;

        public IntrusiveBase(int weight) {
            this.weight = Weight.of(weight);
        }

        public IntrusiveBase(Weight weight) {
            this.weight = weight;
        }

        @Override
        public Weight getWeight() {
            return this.weight;
        }
    }

    public static record Wrapper<T>(T data, Weight weight) implements WeightedEntry {
        @Override
        public Weight getWeight() {
            return this.weight;
        }

        public static <E> Codec<WeightedEntry.Wrapper<E>> codec(Codec<E> elementCodec) {
            return RecordCodecBuilder.create(
                p_146309_ -> p_146309_.group(
                            elementCodec.fieldOf("data").forGetter((Function<WeightedEntry.Wrapper<E>, E>)(WeightedEntry.Wrapper::data)),
                            Weight.CODEC.fieldOf("weight").forGetter(WeightedEntry.Wrapper::weight)
                        )
                        .apply(p_146309_, (BiFunction<E, Weight, WeightedEntry.Wrapper<E>>)(WeightedEntry.Wrapper::new))
            );
        }
    }
}
