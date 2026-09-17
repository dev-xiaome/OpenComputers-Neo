package net.minecraft.util.valueproviders;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.util.random.WeightedEntry;

public class WeightedListInt extends IntProvider {
    public static final MapCodec<WeightedListInt> CODEC = RecordCodecBuilder.mapCodec(
        p_185920_ -> p_185920_.group(
                    SimpleWeightedRandomList.wrappedCodec(IntProvider.CODEC).fieldOf("distribution").forGetter(p_185918_ -> p_185918_.distribution)
                )
                .apply(p_185920_, WeightedListInt::new)
    );
    private final SimpleWeightedRandomList<IntProvider> distribution;
    private final int minValue;
    private final int maxValue;

    public WeightedListInt(SimpleWeightedRandomList<IntProvider> distribution) {
        this.distribution = distribution;
        List<WeightedEntry.Wrapper<IntProvider>> list = distribution.unwrap();
        int i = Integer.MAX_VALUE;
        int j = Integer.MIN_VALUE;

        for (WeightedEntry.Wrapper<IntProvider> wrapper : list) {
            int k = wrapper.data().getMinValue();
            int l = wrapper.data().getMaxValue();
            i = Math.min(i, k);
            j = Math.max(j, l);
        }

        this.minValue = i;
        this.maxValue = j;
    }

    @Override
    public int sample(RandomSource random) {
        return this.distribution.getRandomValue(random).orElseThrow(IllegalStateException::new).sample(random);
    }

    @Override
    public int getMinValue() {
        return this.minValue;
    }

    @Override
    public int getMaxValue() {
        return this.maxValue;
    }

    @Override
    public IntProviderType<?> getType() {
        return IntProviderType.WEIGHTED_LIST;
    }
}
