package net.minecraft.world.level.levelgen.heightproviders;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import org.slf4j.Logger;

public class TrapezoidHeight extends HeightProvider {
    public static final MapCodec<TrapezoidHeight> CODEC = RecordCodecBuilder.mapCodec(
        p_162005_ -> p_162005_.group(
                    VerticalAnchor.CODEC.fieldOf("min_inclusive").forGetter(p_162021_ -> p_162021_.minInclusive),
                    VerticalAnchor.CODEC.fieldOf("max_inclusive").forGetter(p_162019_ -> p_162019_.maxInclusive),
                    Codec.INT.optionalFieldOf("plateau", Integer.valueOf(0)).forGetter(p_162014_ -> p_162014_.plateau)
                )
                .apply(p_162005_, TrapezoidHeight::new)
    );
    private static final Logger LOGGER = LogUtils.getLogger();
    private final VerticalAnchor minInclusive;
    private final VerticalAnchor maxInclusive;
    private final int plateau;

    private TrapezoidHeight(VerticalAnchor minInclusive, VerticalAnchor maxInclusive, int plateau) {
        this.minInclusive = minInclusive;
        this.maxInclusive = maxInclusive;
        this.plateau = plateau;
    }

    public static TrapezoidHeight of(VerticalAnchor minInclusive, VerticalAnchor maxInclusive, int plateau) {
        return new TrapezoidHeight(minInclusive, maxInclusive, plateau);
    }

    public static TrapezoidHeight of(VerticalAnchor minInclusive, VerticalAnchor maxInclusive) {
        return of(minInclusive, maxInclusive, 0);
    }

    @Override
    public int sample(RandomSource random, WorldGenerationContext context) {
        int i = this.minInclusive.resolveY(context);
        int j = this.maxInclusive.resolveY(context);
        if (i > j) {
            LOGGER.warn("Empty height range: {}", this);
            return i;
        } else {
            int k = j - i;
            if (this.plateau >= k) {
                return Mth.randomBetweenInclusive(random, i, j);
            } else {
                int l = (k - this.plateau) / 2;
                int i1 = k - l;
                return i + Mth.randomBetweenInclusive(random, 0, i1) + Mth.randomBetweenInclusive(random, 0, l);
            }
        }
    }

    @Override
    public HeightProviderType<?> getType() {
        return HeightProviderType.TRAPEZOID;
    }

    @Override
    public String toString() {
        return this.plateau == 0
            ? "triangle (" + this.minInclusive + "-" + this.maxInclusive + ")"
            : "trapezoid(" + this.plateau + ") in [" + this.minInclusive + "-" + this.maxInclusive + "]";
    }
}
