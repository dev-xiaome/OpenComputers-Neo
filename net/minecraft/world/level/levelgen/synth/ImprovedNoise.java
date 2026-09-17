package net.minecraft.world.level.levelgen.synth;

import com.google.common.annotations.VisibleForTesting;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Generates a single octave of Perlin noise.
 */
public final class ImprovedNoise {
    private static final float SHIFT_UP_EPSILON = 1.0E-7F;
    /**
     * A permutation array used in noise calculation.
     * This is populated with the values [0, 256) and shuffled per instance of {@code ImprovedNoise}.
     *
     * @see #p(int)
     */
    private final byte[] p;
    public final double xo;
    public final double yo;
    public final double zo;

    public ImprovedNoise(RandomSource random) {
        this.xo = random.nextDouble() * 256.0;
        this.yo = random.nextDouble() * 256.0;
        this.zo = random.nextDouble() * 256.0;
        this.p = new byte[256];

        for (int i = 0; i < 256; i++) {
            this.p[i] = (byte)i;
        }

        for (int k = 0; k < 256; k++) {
            int j = random.nextInt(256 - k);
            byte b0 = this.p[k];
            this.p[k] = this.p[k + j];
            this.p[k + j] = b0;
        }
    }

    public double noise(double x, double y, double z) {
        return this.noise(x, y, z, 0.0, 0.0);
    }

    @Deprecated
    public double noise(double x, double y, double z, double yScale, double yMax) {
        double d0 = x + this.xo;
        double d1 = y + this.yo;
        double d2 = z + this.zo;
        int i = Mth.floor(d0);
        int j = Mth.floor(d1);
        int k = Mth.floor(d2);
        double d3 = d0 - (double)i;
        double d4 = d1 - (double)j;
        double d5 = d2 - (double)k;
        double d6;
        if (yScale != 0.0) {
            double d7;
            if (yMax >= 0.0 && yMax < d4) {
                d7 = yMax;
            } else {
                d7 = d4;
            }

            d6 = (double)Mth.floor(d7 / yScale + 1.0E-7F) * yScale;
        } else {
            d6 = 0.0;
        }

        return this.sampleAndLerp(i, j, k, d3, d4 - d6, d5, d4);
    }

    public double noiseWithDerivative(double x, double y, double z, double[] values) {
        double d0 = x + this.xo;
        double d1 = y + this.yo;
        double d2 = z + this.zo;
        int i = Mth.floor(d0);
        int j = Mth.floor(d1);
        int k = Mth.floor(d2);
        double d3 = d0 - (double)i;
        double d4 = d1 - (double)j;
        double d5 = d2 - (double)k;
        return this.sampleWithDerivative(i, j, k, d3, d4, d5, values);
    }

    private static double gradDot(int gradIndex, double xFactor, double yFactor, double zFactor) {
        return SimplexNoise.dot(SimplexNoise.GRADIENT[gradIndex & 15], xFactor, yFactor, zFactor);
    }

    private int p(int index) {
        return this.p[index & 0xFF] & 0xFF;
    }

    private double sampleAndLerp(int gridX, int gridY, int gridZ, double deltaX, double weirdDeltaY, double deltaZ, double deltaY) {
        int i = this.p(gridX);
        int j = this.p(gridX + 1);
        int k = this.p(i + gridY);
        int l = this.p(i + gridY + 1);
        int i1 = this.p(j + gridY);
        int j1 = this.p(j + gridY + 1);
        double d0 = gradDot(this.p(k + gridZ), deltaX, weirdDeltaY, deltaZ);
        double d1 = gradDot(this.p(i1 + gridZ), deltaX - 1.0, weirdDeltaY, deltaZ);
        double d2 = gradDot(this.p(l + gridZ), deltaX, weirdDeltaY - 1.0, deltaZ);
        double d3 = gradDot(this.p(j1 + gridZ), deltaX - 1.0, weirdDeltaY - 1.0, deltaZ);
        double d4 = gradDot(this.p(k + gridZ + 1), deltaX, weirdDeltaY, deltaZ - 1.0);
        double d5 = gradDot(this.p(i1 + gridZ + 1), deltaX - 1.0, weirdDeltaY, deltaZ - 1.0);
        double d6 = gradDot(this.p(l + gridZ + 1), deltaX, weirdDeltaY - 1.0, deltaZ - 1.0);
        double d7 = gradDot(this.p(j1 + gridZ + 1), deltaX - 1.0, weirdDeltaY - 1.0, deltaZ - 1.0);
        double d8 = Mth.smoothstep(deltaX);
        double d9 = Mth.smoothstep(deltaY);
        double d10 = Mth.smoothstep(deltaZ);
        return Mth.lerp3(d8, d9, d10, d0, d1, d2, d3, d4, d5, d6, d7);
    }

    private double sampleWithDerivative(int gridX, int gridY, int gridZ, double deltaX, double deltaY, double deltaZ, double[] noiseValues) {
        int i = this.p(gridX);
        int j = this.p(gridX + 1);
        int k = this.p(i + gridY);
        int l = this.p(i + gridY + 1);
        int i1 = this.p(j + gridY);
        int j1 = this.p(j + gridY + 1);
        int k1 = this.p(k + gridZ);
        int l1 = this.p(i1 + gridZ);
        int i2 = this.p(l + gridZ);
        int j2 = this.p(j1 + gridZ);
        int k2 = this.p(k + gridZ + 1);
        int l2 = this.p(i1 + gridZ + 1);
        int i3 = this.p(l + gridZ + 1);
        int j3 = this.p(j1 + gridZ + 1);
        int[] aint = SimplexNoise.GRADIENT[k1 & 15];
        int[] aint1 = SimplexNoise.GRADIENT[l1 & 15];
        int[] aint2 = SimplexNoise.GRADIENT[i2 & 15];
        int[] aint3 = SimplexNoise.GRADIENT[j2 & 15];
        int[] aint4 = SimplexNoise.GRADIENT[k2 & 15];
        int[] aint5 = SimplexNoise.GRADIENT[l2 & 15];
        int[] aint6 = SimplexNoise.GRADIENT[i3 & 15];
        int[] aint7 = SimplexNoise.GRADIENT[j3 & 15];
        double d0 = SimplexNoise.dot(aint, deltaX, deltaY, deltaZ);
        double d1 = SimplexNoise.dot(aint1, deltaX - 1.0, deltaY, deltaZ);
        double d2 = SimplexNoise.dot(aint2, deltaX, deltaY - 1.0, deltaZ);
        double d3 = SimplexNoise.dot(aint3, deltaX - 1.0, deltaY - 1.0, deltaZ);
        double d4 = SimplexNoise.dot(aint4, deltaX, deltaY, deltaZ - 1.0);
        double d5 = SimplexNoise.dot(aint5, deltaX - 1.0, deltaY, deltaZ - 1.0);
        double d6 = SimplexNoise.dot(aint6, deltaX, deltaY - 1.0, deltaZ - 1.0);
        double d7 = SimplexNoise.dot(aint7, deltaX - 1.0, deltaY - 1.0, deltaZ - 1.0);
        double d8 = Mth.smoothstep(deltaX);
        double d9 = Mth.smoothstep(deltaY);
        double d10 = Mth.smoothstep(deltaZ);
        double d11 = Mth.lerp3(
            d8,
            d9,
            d10,
            (double)aint[0],
            (double)aint1[0],
            (double)aint2[0],
            (double)aint3[0],
            (double)aint4[0],
            (double)aint5[0],
            (double)aint6[0],
            (double)aint7[0]
        );
        double d12 = Mth.lerp3(
            d8,
            d9,
            d10,
            (double)aint[1],
            (double)aint1[1],
            (double)aint2[1],
            (double)aint3[1],
            (double)aint4[1],
            (double)aint5[1],
            (double)aint6[1],
            (double)aint7[1]
        );
        double d13 = Mth.lerp3(
            d8,
            d9,
            d10,
            (double)aint[2],
            (double)aint1[2],
            (double)aint2[2],
            (double)aint3[2],
            (double)aint4[2],
            (double)aint5[2],
            (double)aint6[2],
            (double)aint7[2]
        );
        double d14 = Mth.lerp2(d9, d10, d1 - d0, d3 - d2, d5 - d4, d7 - d6);
        double d15 = Mth.lerp2(d10, d8, d2 - d0, d6 - d4, d3 - d1, d7 - d5);
        double d16 = Mth.lerp2(d8, d9, d4 - d0, d5 - d1, d6 - d2, d7 - d3);
        double d17 = Mth.smoothstepDerivative(deltaX);
        double d18 = Mth.smoothstepDerivative(deltaY);
        double d19 = Mth.smoothstepDerivative(deltaZ);
        double d20 = d11 + d17 * d14;
        double d21 = d12 + d18 * d15;
        double d22 = d13 + d19 * d16;
        noiseValues[0] += d20;
        noiseValues[1] += d21;
        noiseValues[2] += d22;
        return Mth.lerp3(d8, d9, d10, d0, d1, d2, d3, d4, d5, d6, d7);
    }

    @VisibleForTesting
    public void parityConfigString(StringBuilder builder) {
        NoiseUtils.parityNoiseOctaveConfigString(builder, this.xo, this.yo, this.zo, this.p);
    }
}
