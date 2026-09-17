package net.minecraft.world.level.levelgen.synth;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.ints.IntBidirectionalIterator;
import it.unimi.dsi.fastutil.ints.IntRBTreeSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;

/**
 * This class generates multiple octaves of perlin noise. Each individual octave is an instance of {@link net.minecraft.world.level.levelgen.synth.ImprovedNoise}.
 * Mojang uses the term 'Perlin' to describe octaves or fBm (Fractal Brownian Motion) noise.
 */
public class PerlinNoise {
    private static final int ROUND_OFF = 33554432;
    private final ImprovedNoise[] noiseLevels;
    private final int firstOctave;
    private final DoubleList amplitudes;
    private final double lowestFreqValueFactor;
    private final double lowestFreqInputFactor;
    private final double maxValue;

    @Deprecated
    public static PerlinNoise createLegacyForBlendedNoise(RandomSource random, IntStream octaves) {
        return new PerlinNoise(random, makeAmplitudes(new IntRBTreeSet(octaves.boxed().collect(ImmutableList.toImmutableList()))), false);
    }

    @Deprecated
    public static PerlinNoise createLegacyForLegacyNetherBiome(RandomSource random, int firstOctave, DoubleList amplitudes) {
        return new PerlinNoise(random, Pair.of(firstOctave, amplitudes), false);
    }

    public static PerlinNoise create(RandomSource random, IntStream octaves) {
        return create(random, octaves.boxed().collect(ImmutableList.toImmutableList()));
    }

    public static PerlinNoise create(RandomSource random, List<Integer> octaves) {
        return new PerlinNoise(random, makeAmplitudes(new IntRBTreeSet(octaves)), true);
    }

    public static PerlinNoise create(RandomSource random, int firstOctave, double firstAmplitude, double... amplitudes) {
        DoubleArrayList doublearraylist = new DoubleArrayList(amplitudes);
        doublearraylist.add(0, firstAmplitude);
        return new PerlinNoise(random, Pair.of(firstOctave, doublearraylist), true);
    }

    public static PerlinNoise create(RandomSource random, int firstOctave, DoubleList amplitudes) {
        return new PerlinNoise(random, Pair.of(firstOctave, amplitudes), true);
    }

    private static Pair<Integer, DoubleList> makeAmplitudes(IntSortedSet octaves) {
        if (octaves.isEmpty()) {
            throw new IllegalArgumentException("Need some octaves!");
        } else {
            int i = -octaves.firstInt();
            int j = octaves.lastInt();
            int k = i + j + 1;
            if (k < 1) {
                throw new IllegalArgumentException("Total number of octaves needs to be >= 1");
            } else {
                DoubleList doublelist = new DoubleArrayList(new double[k]);
                IntBidirectionalIterator intbidirectionaliterator = octaves.iterator();

                while (intbidirectionaliterator.hasNext()) {
                    int l = intbidirectionaliterator.nextInt();
                    doublelist.set(l + i, 1.0);
                }

                return Pair.of(-i, doublelist);
            }
        }
    }

    protected PerlinNoise(RandomSource random, Pair<Integer, DoubleList> octavesAndAmplitudes, boolean useNewFactory) {
        this.firstOctave = octavesAndAmplitudes.getFirst();
        this.amplitudes = octavesAndAmplitudes.getSecond();
        int i = this.amplitudes.size();
        int j = -this.firstOctave;
        this.noiseLevels = new ImprovedNoise[i];
        if (useNewFactory) {
            PositionalRandomFactory positionalrandomfactory = random.forkPositional();

            for (int k = 0; k < i; k++) {
                if (this.amplitudes.getDouble(k) != 0.0) {
                    int l = this.firstOctave + k;
                    this.noiseLevels[k] = new ImprovedNoise(positionalrandomfactory.fromHashOf("octave_" + l));
                }
            }
        } else {
            ImprovedNoise improvednoise = new ImprovedNoise(random);
            if (j >= 0 && j < i) {
                double d0 = this.amplitudes.getDouble(j);
                if (d0 != 0.0) {
                    this.noiseLevels[j] = improvednoise;
                }
            }

            for (int i1 = j - 1; i1 >= 0; i1--) {
                if (i1 < i) {
                    double d1 = this.amplitudes.getDouble(i1);
                    if (d1 != 0.0) {
                        this.noiseLevels[i1] = new ImprovedNoise(random);
                    } else {
                        skipOctave(random);
                    }
                } else {
                    skipOctave(random);
                }
            }

            if (Arrays.stream(this.noiseLevels).filter(Objects::nonNull).count() != this.amplitudes.stream().filter(p_192897_ -> p_192897_ != 0.0).count()) {
                throw new IllegalStateException("Failed to create correct number of noise levels for given non-zero amplitudes");
            }

            if (j < i - 1) {
                throw new IllegalArgumentException("Positive octaves are temporarily disabled");
            }
        }

        this.lowestFreqInputFactor = Math.pow(2.0, (double)(-j));
        this.lowestFreqValueFactor = Math.pow(2.0, (double)(i - 1)) / (Math.pow(2.0, (double)i) - 1.0);
        this.maxValue = this.edgeValue(2.0);
    }

    protected double maxValue() {
        return this.maxValue;
    }

    private static void skipOctave(RandomSource random) {
        random.consumeCount(262);
    }

    public double getValue(double x, double y, double z) {
        return this.getValue(x, y, z, 0.0, 0.0, false);
    }

    @Deprecated
    public double getValue(double x, double y, double z, double yScale, double yMax, boolean useFixedY) {
        double d0 = 0.0;
        double d1 = this.lowestFreqInputFactor;
        double d2 = this.lowestFreqValueFactor;

        for (int i = 0; i < this.noiseLevels.length; i++) {
            ImprovedNoise improvednoise = this.noiseLevels[i];
            if (improvednoise != null) {
                double d3 = improvednoise.noise(
                    wrap(x * d1), useFixedY ? -improvednoise.yo : wrap(y * d1), wrap(z * d1), yScale * d1, yMax * d1
                );
                d0 += this.amplitudes.getDouble(i) * d3 * d2;
            }

            d1 *= 2.0;
            d2 /= 2.0;
        }

        return d0;
    }

    public double maxBrokenValue(double yMultiplier) {
        return this.edgeValue(yMultiplier + 2.0);
    }

    private double edgeValue(double multiplier) {
        double d0 = 0.0;
        double d1 = this.lowestFreqValueFactor;

        for (int i = 0; i < this.noiseLevels.length; i++) {
            ImprovedNoise improvednoise = this.noiseLevels[i];
            if (improvednoise != null) {
                d0 += this.amplitudes.getDouble(i) * multiplier * d1;
            }

            d1 /= 2.0;
        }

        return d0;
    }

    /**
     * @return A single octave of Perlin noise.
     */
    @Nullable
    public ImprovedNoise getOctaveNoise(int octave) {
        return this.noiseLevels[this.noiseLevels.length - 1 - octave];
    }

    public static double wrap(double value) {
        return value - (double)Mth.lfloor(value / 3.3554432E7 + 0.5) * 3.3554432E7;
    }

    protected int firstOctave() {
        return this.firstOctave;
    }

    protected DoubleList amplitudes() {
        return this.amplitudes;
    }

    @VisibleForTesting
    public void parityConfigString(StringBuilder builder) {
        builder.append("PerlinNoise{");
        List<String> list = this.amplitudes.stream().map(p_192889_ -> String.format(Locale.ROOT, "%.2f", p_192889_)).toList();
        builder.append("first octave: ").append(this.firstOctave).append(", amplitudes: ").append(list).append(", noise levels: [");

        for (int i = 0; i < this.noiseLevels.length; i++) {
            builder.append(i).append(": ");
            ImprovedNoise improvednoise = this.noiseLevels[i];
            if (improvednoise == null) {
                builder.append("null");
            } else {
                improvednoise.parityConfigString(builder);
            }

            builder.append(", ");
        }

        builder.append("]");
        builder.append("}");
    }
}
