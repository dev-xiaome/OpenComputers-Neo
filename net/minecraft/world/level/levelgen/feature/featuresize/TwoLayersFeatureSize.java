package net.minecraft.world.level.levelgen.feature.featuresize;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.OptionalInt;

public class TwoLayersFeatureSize extends FeatureSize {
    public static final MapCodec<TwoLayersFeatureSize> CODEC = RecordCodecBuilder.mapCodec(
        p_68356_ -> p_68356_.group(
                    Codec.intRange(0, 81).fieldOf("limit").orElse(1).forGetter(p_161341_ -> p_161341_.limit),
                    Codec.intRange(0, 16).fieldOf("lower_size").orElse(0).forGetter(p_161339_ -> p_161339_.lowerSize),
                    Codec.intRange(0, 16).fieldOf("upper_size").orElse(1).forGetter(p_161337_ -> p_161337_.upperSize),
                    minClippedHeightCodec()
                )
                .apply(p_68356_, TwoLayersFeatureSize::new)
    );
    private final int limit;
    private final int lowerSize;
    private final int upperSize;

    public TwoLayersFeatureSize(int limit, int lowerSize, int upperSize) {
        this(limit, lowerSize, upperSize, OptionalInt.empty());
    }

    public TwoLayersFeatureSize(int limit, int lowerSize, int upperSize, OptionalInt minClippedHeight) {
        super(minClippedHeight);
        this.limit = limit;
        this.lowerSize = lowerSize;
        this.upperSize = upperSize;
    }

    @Override
    protected FeatureSizeType<?> type() {
        return FeatureSizeType.TWO_LAYERS_FEATURE_SIZE;
    }

    @Override
    public int getSizeAtHeight(int height, int midpoint) {
        return midpoint < this.limit ? this.lowerSize : this.upperSize;
    }
}
