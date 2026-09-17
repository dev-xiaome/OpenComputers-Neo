package net.minecraft.util;

import java.util.Locale;
import java.util.UUID;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;
import net.minecraft.Util;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.math.Fraction;
import org.apache.commons.lang3.math.NumberUtils;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class Mth {
    private static final long UUID_VERSION = 61440L;
    private static final long UUID_VERSION_TYPE_4 = 16384L;
    private static final long UUID_VARIANT = -4611686018427387904L;
    private static final long UUID_VARIANT_2 = Long.MIN_VALUE;
    public static final float PI = (float) Math.PI;
    public static final float HALF_PI = (float) (Math.PI / 2);
    public static final float TWO_PI = (float) (Math.PI * 2);
    public static final float DEG_TO_RAD = (float) (Math.PI / 180.0);
    public static final float RAD_TO_DEG = 180.0F / (float)Math.PI;
    public static final float EPSILON = 1.0E-5F;
    public static final float SQRT_OF_TWO = sqrt(2.0F);
    private static final float SIN_SCALE = 10430.378F;
    public static final Vector3f Y_AXIS = new Vector3f(0.0F, 1.0F, 0.0F);
    public static final Vector3f X_AXIS = new Vector3f(1.0F, 0.0F, 0.0F);
    public static final Vector3f Z_AXIS = new Vector3f(0.0F, 0.0F, 1.0F);
    private static final float[] SIN = Util.make(new float[65536], p_14077_ -> {
        for (int i = 0; i < p_14077_.length; i++) {
            p_14077_[i] = (float)Math.sin((double)i * Math.PI * 2.0 / 65536.0);
        }
    });
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();
    /**
     * Though it looks like an array, this is really more like a mapping. Key (index of this array) is the upper 5 bits of the result of multiplying a 32-bit unsigned integer by the B(2, 5) De Bruijn sequence 0x077CB531. Value (value stored in the array) is the unique index (from the right) of the leftmo
     */
    private static final int[] MULTIPLY_DE_BRUIJN_BIT_POSITION = new int[]{
        0, 1, 28, 2, 29, 14, 24, 3, 30, 22, 20, 15, 25, 17, 4, 8, 31, 27, 13, 23, 21, 19, 16, 7, 26, 12, 18, 6, 11, 5, 10, 9
    };
    private static final double ONE_SIXTH = 0.16666666666666666;
    private static final int FRAC_EXP = 8;
    private static final int LUT_SIZE = 257;
    private static final double FRAC_BIAS = Double.longBitsToDouble(4805340802404319232L);
    private static final double[] ASIN_TAB = new double[257];
    private static final double[] COS_TAB = new double[257];

    /**
     * sin looked up in a table
     */
    public static float sin(float value) {
        return SIN[(int)(value * 10430.378F) & 65535];
    }

    /**
     * cos looked up in the sin table with the appropriate offset
     */
    public static float cos(float value) {
        return SIN[(int)(value * 10430.378F + 16384.0F) & 65535];
    }

    public static float sqrt(float value) {
        return (float)Math.sqrt((double)value);
    }

    /**
     * {@return the greatest integer less than or equal to the float argument}
     */
    public static int floor(float value) {
        int i = (int)value;
        return value < (float)i ? i - 1 : i;
    }

    /**
     * {@return the greatest integer less than or equal to the double argument}
     */
    public static int floor(double value) {
        int i = (int)value;
        return value < (double)i ? i - 1 : i;
    }

    /**
     * Long version of floor()
     */
    public static long lfloor(double value) {
        long i = (long)value;
        return value < (double)i ? i - 1L : i;
    }

    public static float abs(float value) {
        return Math.abs(value);
    }

    /**
     * {@return the unsigned value of an int}
     */
    public static int abs(int value) {
        return Math.abs(value);
    }

    public static int ceil(float value) {
        int i = (int)value;
        return value > (float)i ? i + 1 : i;
    }

    public static int ceil(double value) {
        int i = (int)value;
        return value > (double)i ? i + 1 : i;
    }

    /**
     * {@return the given value if between the lower and the upper bound. If the value is less than the lower bound, returns the lower bound} If the value is greater than the upper bound, returns the upper bound.
     *
     * @param value The value that is clamped.
     * @param min   The lower bound for the clamp.
     * @param max   The upper bound for the clamp.
     */
    public static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    public static long clamp(long value, long min, long max) {
        return Math.min(Math.max(value, min), max);
    }

    /**
     * {@return the given value if between the lower and the upper bound. If the value is less than the lower bound, returns the lower bound} If the value is greater than the upper bound, returns the upper bound.
     *
     * @param value The value that is clamped.
     * @param min   The lower bound for the clamp.
     * @param max   The upper bound for the clamp.
     */
    public static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    /**
     * {@return the given value if between the lower and the upper bound. If the value is less than the lower bound, returns the lower bound} If the value is greater than the upper bound, returns the upper bound.
     *
     * @param value The value that is clamped.
     * @param min   The lower bound for the clamp.
     * @param max   The upper bound for the clamp.
     */
    public static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    /**
     * Method for linear interpolation of doubles.
     *
     * @param start Start value for the lerp.
     * @param end   End value for the lerp.
     * @param delta A value between 0 and 1 that indicates the percentage of the lerp.
     *              (0 will give the start value and 1 will give the end value) If
     *              the value is not between 0 and 1, it is clamped.
     */
    public static double clampedLerp(double start, double end, double delta) {
        if (delta < 0.0) {
            return start;
        } else {
            return delta > 1.0 ? end : lerp(delta, start, end);
        }
    }

    /**
     * Method for linear interpolation of floats.
     *
     * @param start Start value for the lerp.
     * @param end   End value for the lerp.
     * @param delta A value between 0 and 1 that indicates the percentage of the lerp.
     *              (0 will give the start value and 1 will give the end value) If
     *              the value is not between 0 and 1, it is clamped.
     */
    public static float clampedLerp(float start, float end, float delta) {
        if (delta < 0.0F) {
            return start;
        } else {
            return delta > 1.0F ? end : lerp(delta, start, end);
        }
    }

    /**
     * {@return the maximum of the absolute value of two numbers}
     */
    public static double absMax(double x, double y) {
        if (x < 0.0) {
            x = -x;
        }

        if (y < 0.0) {
            y = -y;
        }

        return Math.max(x, y);
    }

    public static int floorDiv(int dividend, int divisor) {
        return Math.floorDiv(dividend, divisor);
    }

    public static int nextInt(RandomSource random, int minimum, int maximum) {
        return minimum >= maximum ? minimum : random.nextInt(maximum - minimum + 1) + minimum;
    }

    public static float nextFloat(RandomSource random, float minimum, float maximum) {
        return minimum >= maximum ? minimum : random.nextFloat() * (maximum - minimum) + minimum;
    }

    public static double nextDouble(RandomSource random, double minimum, double maximum) {
        return minimum >= maximum ? minimum : random.nextDouble() * (maximum - minimum) + minimum;
    }

    public static boolean equal(float x, float y) {
        return Math.abs(y - x) < 1.0E-5F;
    }

    public static boolean equal(double x, double y) {
        return Math.abs(y - x) < 1.0E-5F;
    }

    public static int positiveModulo(int x, int y) {
        return Math.floorMod(x, y);
    }

    public static float positiveModulo(float numerator, float denominator) {
        return (numerator % denominator + denominator) % denominator;
    }

    public static double positiveModulo(double numerator, double denominator) {
        return (numerator % denominator + denominator) % denominator;
    }

    public static boolean isMultipleOf(int number, int multiple) {
        return number % multiple == 0;
    }

    /**
     * Adjust the angle so that its value is in the range [-180;180)
     */
    public static int wrapDegrees(int angle) {
        int i = angle % 360;
        if (i >= 180) {
            i -= 360;
        }

        if (i < -180) {
            i += 360;
        }

        return i;
    }

    /**
     * The angle is reduced to an angle between -180 and +180 by mod, and a 360 check.
     */
    public static float wrapDegrees(float value) {
        float f = value % 360.0F;
        if (f >= 180.0F) {
            f -= 360.0F;
        }

        if (f < -180.0F) {
            f += 360.0F;
        }

        return f;
    }

    /**
     * The angle is reduced to an angle between -180 and +180 by mod, and a 360 check.
     */
    public static double wrapDegrees(double value) {
        double d0 = value % 360.0;
        if (d0 >= 180.0) {
            d0 -= 360.0;
        }

        if (d0 < -180.0) {
            d0 += 360.0;
        }

        return d0;
    }

    /**
     * {@return the difference between two angles in degrees}
     */
    public static float degreesDifference(float start, float end) {
        return wrapDegrees(end - start);
    }

    /**
     * {@return the absolute of the difference between two angles in degrees}
     */
    public static float degreesDifferenceAbs(float start, float end) {
        return abs(degreesDifference(start, end));
    }

    /**
     * Takes a rotation and compares it to another rotation.
     * If the difference is greater than a given maximum, clamps the original rotation between to have at most the given difference to the actual rotation.
     * This is used to match the body rotation of entities to their head rotation.
     * @return The new value for the rotation that was adjusted
     */
    public static float rotateIfNecessary(float rotationToAdjust, float actualRotation, float maxDifference) {
        float f = degreesDifference(rotationToAdjust, actualRotation);
        float f1 = clamp(f, -maxDifference, maxDifference);
        return actualRotation - f1;
    }

    /**
     * Changes value by stepSize towards the limit and returns the result.
     * If value is smaller than limit, the result will never be bigger than limit.
     * If value is bigger than limit, the result will never be smaller than limit.
     */
    public static float approach(float value, float limit, float stepSize) {
        stepSize = abs(stepSize);
        return value < limit ? clamp(value + stepSize, value, limit) : clamp(value - stepSize, limit, value);
    }

    /**
     * Changes the angle by stepSize towards the limit in the direction where the distance is smaller.
     * {@see #approach(float, float, float)}
     */
    public static float approachDegrees(float angle, float limit, float stepSize) {
        float f = degreesDifference(angle, limit);
        return approach(angle, angle + f, stepSize);
    }

    /**
     * Parses the string as an integer or returns the second parameter if it fails.
     */
    public static int getInt(String value, int defaultValue) {
        return NumberUtils.toInt(value, defaultValue);
    }

    /**
     * {@return the input value rounded up to the next highest power of two}
     */
    public static int smallestEncompassingPowerOfTwo(int value) {
        int i = value - 1;
        i |= i >> 1;
        i |= i >> 2;
        i |= i >> 4;
        i |= i >> 8;
        i |= i >> 16;
        return i + 1;
    }

    /**
     * Is the given value a power of two?  (1, 2, 4, 8, 16, ...)
     */
    public static boolean isPowerOfTwo(int value) {
        return value != 0 && (value & value - 1) == 0;
    }

    /**
     * Uses a B(2, 5) De Bruijn sequence and a lookup table to efficiently calculate the log-base-two of the given value. Optimized for cases where the input value is a power-of-two. If the input value is not a power-of-two, then subtract 1 from the return value.
     */
    public static int ceillog2(int value) {
        value = isPowerOfTwo(value) ? value : smallestEncompassingPowerOfTwo(value);
        return MULTIPLY_DE_BRUIJN_BIT_POSITION[(int)((long)value * 125613361L >> 27) & 31];
    }

    /**
     * Efficiently calculates the floor of the base-2 log of an integer value.  This is effectively the index of the highest bit that is set.  For example, if the number in binary is 0...100101, this will return 5.
     */
    public static int log2(int value) {
        return ceillog2(value) - (isPowerOfTwo(value) ? 0 : 1);
    }

    /**
     * Makes an integer color from the given red, green, and blue float values
     */
    public static int color(float r, float g, float b) {
        return FastColor.ARGB32.color(0, floor(r * 255.0F), floor(g * 255.0F), floor(b * 255.0F));
    }

    public static float frac(float number) {
        return number - (float)floor(number);
    }

    /**
     * Gets the decimal portion of the given double. For instance, {@code frac(5.5)} returns {@code .5}.
     */
    public static double frac(double number) {
        return number - (double)lfloor(number);
    }

    @Deprecated
    public static long getSeed(Vec3i pos) {
        return getSeed(pos.getX(), pos.getY(), pos.getZ());
    }

    @Deprecated
    public static long getSeed(int x, int y, int z) {
        long i = (long)(x * 3129871) ^ (long)z * 116129781L ^ (long)y;
        i = i * i * 42317861L + i * 11L;
        return i >> 16;
    }

    public static UUID createInsecureUUID(RandomSource random) {
        long i = random.nextLong() & -61441L | 16384L;
        long j = random.nextLong() & 4611686018427387903L | Long.MIN_VALUE;
        return new UUID(i, j);
    }

    public static UUID createInsecureUUID() {
        return createInsecureUUID(RANDOM);
    }

    public static double inverseLerp(double delta, double start, double end) {
        return (delta - start) / (end - start);
    }

    public static float inverseLerp(float delta, float start, float end) {
        return (delta - start) / (end - start);
    }

    public static boolean rayIntersectsAABB(Vec3 start, Vec3 end, AABB boundingBox) {
        double d0 = (boundingBox.minX + boundingBox.maxX) * 0.5;
        double d1 = (boundingBox.maxX - boundingBox.minX) * 0.5;
        double d2 = start.x - d0;
        if (Math.abs(d2) > d1 && d2 * end.x >= 0.0) {
            return false;
        } else {
            double d3 = (boundingBox.minY + boundingBox.maxY) * 0.5;
            double d4 = (boundingBox.maxY - boundingBox.minY) * 0.5;
            double d5 = start.y - d3;
            if (Math.abs(d5) > d4 && d5 * end.y >= 0.0) {
                return false;
            } else {
                double d6 = (boundingBox.minZ + boundingBox.maxZ) * 0.5;
                double d7 = (boundingBox.maxZ - boundingBox.minZ) * 0.5;
                double d8 = start.z - d6;
                if (Math.abs(d8) > d7 && d8 * end.z >= 0.0) {
                    return false;
                } else {
                    double d9 = Math.abs(end.x);
                    double d10 = Math.abs(end.y);
                    double d11 = Math.abs(end.z);
                    double d12 = end.y * d8 - end.z * d5;
                    if (Math.abs(d12) > d4 * d11 + d7 * d10) {
                        return false;
                    } else {
                        d12 = end.z * d2 - end.x * d8;
                        if (Math.abs(d12) > d1 * d11 + d7 * d9) {
                            return false;
                        } else {
                            d12 = end.x * d5 - end.y * d2;
                            return Math.abs(d12) < d1 * d10 + d4 * d9;
                        }
                    }
                }
            }
        }
    }

    public static double atan2(double y, double x) {
        double d0 = x * x + y * y;
        if (Double.isNaN(d0)) {
            return Double.NaN;
        } else {
            boolean flag = y < 0.0;
            if (flag) {
                y = -y;
            }

            boolean flag1 = x < 0.0;
            if (flag1) {
                x = -x;
            }

            boolean flag2 = y > x;
            if (flag2) {
                double d1 = x;
                x = y;
                y = d1;
            }

            double d9 = fastInvSqrt(d0);
            x *= d9;
            y *= d9;
            double d2 = FRAC_BIAS + y;
            int i = (int)Double.doubleToRawLongBits(d2);
            double d3 = ASIN_TAB[i];
            double d4 = COS_TAB[i];
            double d5 = d2 - FRAC_BIAS;
            double d6 = y * d4 - x * d5;
            double d7 = (6.0 + d6 * d6) * d6 * 0.16666666666666666;
            double d8 = d3 + d7;
            if (flag2) {
                d8 = (Math.PI / 2) - d8;
            }

            if (flag1) {
                d8 = Math.PI - d8;
            }

            if (flag) {
                d8 = -d8;
            }

            return d8;
        }
    }

    public static float invSqrt(float number) {
        return org.joml.Math.invsqrt(number);
    }

    public static double invSqrt(double number) {
        return org.joml.Math.invsqrt(number);
    }

    /**
     * Computes 1/sqrt(n) using <a href="https://en.wikipedia.org/wiki/Fast_inverse_square_root">the fast inverse square root</a> with a constant of 0x5FE6EB50C7B537AA.
     */
    @Deprecated
    public static double fastInvSqrt(double number) {
        double d0 = 0.5 * number;
        long i = Double.doubleToRawLongBits(number);
        i = 6910469410427058090L - (i >> 1);
        number = Double.longBitsToDouble(i);
        return number * (1.5 - d0 * number * number);
    }

    public static float fastInvCubeRoot(float number) {
        int i = Float.floatToIntBits(number);
        i = 1419967116 - i / 3;
        float f = Float.intBitsToFloat(i);
        f = 0.6666667F * f + 1.0F / (3.0F * f * f * number);
        return 0.6666667F * f + 1.0F / (3.0F * f * f * number);
    }

    public static int hsvToRgb(float hue, float saturation, float value) {
        return hsvToArgb(hue, saturation, value, 0);
    }

    public static int hsvToArgb(float hue, float saturation, float value, int alpha) {
        int i = (int)(hue * 6.0F) % 6;
        float f = hue * 6.0F - (float)i;
        float f1 = value * (1.0F - saturation);
        float f2 = value * (1.0F - f * saturation);
        float f3 = value * (1.0F - (1.0F - f) * saturation);
        float f4;
        float f5;
        float f6;
        switch (i) {
            case 0:
                f4 = value;
                f5 = f3;
                f6 = f1;
                break;
            case 1:
                f4 = f2;
                f5 = value;
                f6 = f1;
                break;
            case 2:
                f4 = f1;
                f5 = value;
                f6 = f3;
                break;
            case 3:
                f4 = f1;
                f5 = f2;
                f6 = value;
                break;
            case 4:
                f4 = f3;
                f5 = f1;
                f6 = value;
                break;
            case 5:
                f4 = value;
                f5 = f1;
                f6 = f2;
                break;
            default:
                throw new RuntimeException("Something went wrong when converting from HSV to RGB. Input was " + hue + ", " + saturation + ", " + value);
        }

        return FastColor.ARGB32.color(alpha, clamp((int)(f4 * 255.0F), 0, 255), clamp((int)(f5 * 255.0F), 0, 255), clamp((int)(f6 * 255.0F), 0, 255));
    }

    public static int murmurHash3Mixer(int input) {
        input ^= input >>> 16;
        input *= -2048144789;
        input ^= input >>> 13;
        input *= -1028477387;
        return input ^ input >>> 16;
    }

    public static int binarySearch(int min, int max, IntPredicate isTargetBeforeOrAt) {
        int i = max - min;

        while (i > 0) {
            int j = i / 2;
            int k = min + j;
            if (isTargetBeforeOrAt.test(k)) {
                i = j;
            } else {
                min = k + 1;
                i -= j + 1;
            }
        }

        return min;
    }

    public static int lerpInt(float delta, int start, int end) {
        return start + floor(delta * (float)(end - start));
    }

    public static int lerpDiscrete(float delta, int start, int end) {
        int i = end - start;
        return start + floor(delta * (float)(i - 1)) + (delta > 0.0F ? 1 : 0);
    }

    /**
     * Method for linear interpolation of floats
     *
     * @param delta A value usually between 0 and 1 that indicates the percentage of
     *              the lerp. (0 will give the start value and 1 will give the end
     *              value)
     * @param start Start value for the lerp
     * @param end   End value for the lerp
     */
    public static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    /**
     * Method for linear interpolation of doubles
     *
     * @param delta A value usually between 0 and 1 that indicates the percentage of
     *              the lerp. (0 will give the start value and 1 will give the end
     *              value)
     * @param start Start value for the lerp
     * @param end   End value for the lerp
     */
    public static double lerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }

    public static double lerp2(double delta1, double delta2, double start1, double end1, double start2, double end2) {
        return lerp(delta2, lerp(delta1, start1, end1), lerp(delta1, start2, end2));
    }

    public static double lerp3(
        double delta1,
        double delta2,
        double delta3,
        double start1,
        double end1,
        double start2,
        double end2,
        double start3,
        double end3,
        double start4,
        double end4
    ) {
        return lerp(
            delta3, lerp2(delta1, delta2, start1, end1, start2, end2), lerp2(delta1, delta2, start3, end3, start4, end4)
        );
    }

    public static float catmullrom(float delta, float controlPoint1, float controlPoint2, float controlPoint3, float controlPoint4) {
        return 0.5F
            * (
                2.0F * controlPoint2
                    + (controlPoint3 - controlPoint1) * delta
                    + (2.0F * controlPoint1 - 5.0F * controlPoint2 + 4.0F * controlPoint3 - controlPoint4) * delta * delta
                    + (3.0F * controlPoint2 - controlPoint1 - 3.0F * controlPoint3 + controlPoint4) * delta * delta * delta
            );
    }

    public static double smoothstep(double input) {
        return input * input * input * (input * (input * 6.0 - 15.0) + 10.0);
    }

    public static double smoothstepDerivative(double input) {
        return 30.0 * input * input * (input - 1.0) * (input - 1.0);
    }

    public static int sign(double x) {
        if (x == 0.0) {
            return 0;
        } else {
            return x > 0.0 ? 1 : -1;
        }
    }

    /**
     * Linearly interpolates an angle between the start between the start and end values given as degrees.
     *
     * @param delta A value between 0 and 1 that indicates the percentage of the lerp.
     *              (0 will give the start value and 1 will give the end value)
     */
    public static float rotLerp(float delta, float start, float end) {
        return start + delta * wrapDegrees(end - start);
    }

    public static double rotLerp(double delta, double start, double end) {
        return start + delta * wrapDegrees(end - start);
    }

    public static float triangleWave(float input, float period) {
        return (Math.abs(input % period - period * 0.5F) - period * 0.25F) / (period * 0.25F);
    }

    public static float square(float value) {
        return value * value;
    }

    public static double square(double value) {
        return value * value;
    }

    public static int square(int value) {
        return value * value;
    }

    public static long square(long value) {
        return value * value;
    }

    public static double clampedMap(double input, double inputMin, double inputMax, double ouputMin, double outputMax) {
        return clampedLerp(ouputMin, outputMax, inverseLerp(input, inputMin, inputMax));
    }

    public static float clampedMap(float input, float inputMin, float inputMax, float outputMin, float outputMax) {
        return clampedLerp(outputMin, outputMax, inverseLerp(input, inputMin, inputMax));
    }

    public static double map(double input, double inputMin, double inputMax, double outputMin, double outputMax) {
        return lerp(inverseLerp(input, inputMin, inputMax), outputMin, outputMax);
    }

    public static float map(float input, float inputMin, float inputMax, float outputMin, float outputMax) {
        return lerp(inverseLerp(input, inputMin, inputMax), outputMin, outputMax);
    }

    public static double wobble(double input) {
        return input + (2.0 * RandomSource.create((long)floor(input * 3000.0)).nextDouble() - 1.0) * 1.0E-7 / 2.0;
    }

    /**
     * Rounds the given value up to a multiple of factor.
     * @return The smallest integer multiple of factor that is greater than or equal to the value
     */
    public static int roundToward(int value, int factor) {
        return positiveCeilDiv(value, factor) * factor;
    }

    /**
     * Returns the smallest (closest to negative infinity) int value that is greater than or equal to the algebraic quotient.
     * @see java.lang.Math#floorDiv(int, int)
     */
    public static int positiveCeilDiv(int x, int y) {
        return -Math.floorDiv(-x, y);
    }

    public static int randomBetweenInclusive(RandomSource random, int minInclusive, int maxInclusive) {
        return random.nextInt(maxInclusive - minInclusive + 1) + minInclusive;
    }

    public static float randomBetween(RandomSource random, float minInclusive, float maxExclusive) {
        return random.nextFloat() * (maxExclusive - minInclusive) + minInclusive;
    }

    /**
     * Generates a value from a normal distribution with the given mean and deviation.
     */
    public static float normal(RandomSource random, float mean, float deviation) {
        return mean + (float)random.nextGaussian() * deviation;
    }

    public static double lengthSquared(double xDistance, double yDistance) {
        return xDistance * xDistance + yDistance * yDistance;
    }

    public static double length(double xDistance, double yDistance) {
        return Math.sqrt(lengthSquared(xDistance, yDistance));
    }

    public static double lengthSquared(double xDistance, double yDistance, double zDistance) {
        return xDistance * xDistance + yDistance * yDistance + zDistance * zDistance;
    }

    public static double length(double xDistance, double yDistance, double zDistance) {
        return Math.sqrt(lengthSquared(xDistance, yDistance, zDistance));
    }

    public static float lengthSquared(float xDistance, float yDistance, float zDistance) {
        return xDistance * xDistance + yDistance * yDistance + zDistance * zDistance;
    }

    /**
     * Gets the value closest to zero that is not closer to zero than the given value and is a multiple of the factor.
     */
    public static int quantize(double value, int factor) {
        return floor(value / (double)factor) * factor;
    }

    public static IntStream outFromOrigin(int input, int lowerBound, int upperBound) {
        return outFromOrigin(input, lowerBound, upperBound, 1);
    }

    public static IntStream outFromOrigin(int input, int lowerBound, int upperBound, int steps) {
        if (lowerBound > upperBound) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "upperbound %d expected to be > lowerBound %d", upperBound, lowerBound));
        } else if (steps < 1) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "steps expected to be >= 1, was %d", steps));
        } else {
            return input >= lowerBound && input <= upperBound ? IntStream.iterate(input, p_216282_ -> {
                int i = Math.abs(input - p_216282_);
                return input - i >= lowerBound || input + i <= upperBound;
            }, p_216260_ -> {
                boolean flag = p_216260_ <= input;
                int i = Math.abs(input - p_216260_);
                boolean flag1 = input + i + steps <= upperBound;
                if (!flag || !flag1) {
                    int j = input - i - (flag ? steps : 0);
                    if (j >= lowerBound) {
                        return j;
                    }
                }

                return input + i + steps;
            }) : IntStream.empty();
        }
    }

    public static Quaternionf rotationAroundAxis(Vector3f axis, Quaternionf cameraOrentation, Quaternionf output) {
        float f = axis.dot(cameraOrentation.x, cameraOrentation.y, cameraOrentation.z);
        return output.set(axis.x * f, axis.y * f, axis.z * f, cameraOrentation.w).normalize();
    }

    public static int mulAndTruncate(Fraction fraction, int factor) {
        return fraction.getNumerator() * factor / fraction.getDenominator();
    }

    static {
        for (int i = 0; i < 257; i++) {
            double d0 = (double)i / 256.0;
            double d1 = Math.asin(d0);
            COS_TAB[i] = Math.cos(d1);
            ASIN_TAB[i] = d1;
        }
    }
}
