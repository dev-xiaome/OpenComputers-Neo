package net.minecraft.world.level.levelgen.synth;

import java.util.Locale;

public class NoiseUtils {
    /**
     * Takes an input value and biases it using a sine function towards two larger magnitude values.
     *
     * @param value A value in the range [-1, 1]
     * @param bias  The effect of the bias. At {@code 0.0}, there will be no bias.
     *              Mojang only uses {@code 1.0} here.
     */
    public static double biasTowardsExtreme(double value, double bias) {
        return value + Math.sin(Math.PI * value) * bias / Math.PI;
    }

    public static void parityNoiseOctaveConfigString(StringBuilder builder, double xo, double yo, double zo, byte[] p) {
        builder.append(
            String.format(
                Locale.ROOT, "xo=%.3f, yo=%.3f, zo=%.3f, p0=%d, p255=%d", (float)xo, (float)yo, (float)zo, p[0], p[255]
            )
        );
    }

    public static void parityNoiseOctaveConfigString(StringBuilder builder, double xo, double yo, double zo, int[] p) {
        builder.append(
            String.format(
                Locale.ROOT, "xo=%.3f, yo=%.3f, zo=%.3f, p0=%d, p255=%d", (float)xo, (float)yo, (float)zo, p[0], p[255]
            )
        );
    }
}
