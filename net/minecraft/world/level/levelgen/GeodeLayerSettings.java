package net.minecraft.world.level.levelgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;

public class GeodeLayerSettings {
    private static final Codec<Double> LAYER_RANGE = Codec.doubleRange(0.01, 50.0);
    public static final Codec<GeodeLayerSettings> CODEC = RecordCodecBuilder.create(
        p_158354_ -> p_158354_.group(
                    LAYER_RANGE.fieldOf("filling").orElse(1.7).forGetter(p_158362_ -> p_158362_.filling),
                    LAYER_RANGE.fieldOf("inner_layer").orElse(2.2).forGetter(p_158360_ -> p_158360_.innerLayer),
                    LAYER_RANGE.fieldOf("middle_layer").orElse(3.2).forGetter(p_158358_ -> p_158358_.middleLayer),
                    LAYER_RANGE.fieldOf("outer_layer").orElse(4.2).forGetter(p_158356_ -> p_158356_.outerLayer)
                )
                .apply(p_158354_, GeodeLayerSettings::new)
    );
    public final double filling;
    public final double innerLayer;
    public final double middleLayer;
    public final double outerLayer;

    public GeodeLayerSettings(double filling, double innerLayer, double middleLayer, double outerLayer) {
        this.filling = filling;
        this.innerLayer = innerLayer;
        this.middleLayer = middleLayer;
        this.outerLayer = outerLayer;
    }
}
