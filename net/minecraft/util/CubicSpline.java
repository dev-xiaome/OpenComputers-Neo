package net.minecraft.util;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.floats.FloatList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang3.mutable.MutableObject;

public interface CubicSpline<C, I extends ToFloatFunction<C>> extends ToFloatFunction<C> {
    @VisibleForDebug
    String parityString();

    CubicSpline<C, I> mapAll(CubicSpline.CoordinateVisitor<I> visitor);

    static <C, I extends ToFloatFunction<C>> Codec<CubicSpline<C, I>> codec(Codec<I> p_184263_) {
        MutableObject<Codec<CubicSpline<C, I>>> mutableobject = new MutableObject<>();

        record Point<C, I extends ToFloatFunction<C>>(float location, CubicSpline<C, I> value, float derivative) {
        }

        Codec<Point<C, I>> codec = RecordCodecBuilder.create(
            p_337574_ -> p_337574_.group(
                        Codec.FLOAT.fieldOf("location").forGetter(Point::location),
                        Codec.lazyInitialized(mutableobject::getValue).fieldOf("value").forGetter(Point::value),
                        Codec.FLOAT.fieldOf("derivative").forGetter(Point::derivative)
                    )
                    .apply(p_337574_, (p_184242_, p_184243_, p_184244_) -> new Point<>(p_184242_, p_184243_, p_184244_))
        );
        Codec<CubicSpline.Multipoint<C, I>> codec1 = RecordCodecBuilder.create(
            p_184267_ -> p_184267_.group(
                        p_184263_.fieldOf("coordinate").forGetter(CubicSpline.Multipoint::coordinate),
                        ExtraCodecs.nonEmptyList(codec.listOf())
                            .fieldOf("points")
                            .forGetter(
                                p_184272_ -> IntStream.range(0, p_184272_.locations.length)
                                        .mapToObj(
                                            p_184249_ -> new Point<>(
                                                    p_184272_.locations()[p_184249_], p_184272_.values().get(p_184249_), p_184272_.derivatives()[p_184249_]
                                                )
                                        )
                                        .toList()
                            )
                    )
                    .apply(p_184267_, (p_184258_, p_184259_) -> {
                        float[] afloat = new float[p_184259_.size()];
                        ImmutableList.Builder<CubicSpline<C, I>> builder = ImmutableList.builder();
                        float[] afloat1 = new float[p_184259_.size()];

                        for (int i = 0; i < p_184259_.size(); i++) {
                            Point<C, I> point = p_184259_.get(i);
                            afloat[i] = point.location();
                            builder.add(point.value());
                            afloat1[i] = point.derivative();
                        }

                        return CubicSpline.Multipoint.create(p_184258_, afloat, builder.build(), afloat1);
                    })
        );
        mutableobject.setValue(
            Codec.either(Codec.FLOAT, codec1)
                .xmap(
                    p_184261_ -> p_184261_.map(CubicSpline.Constant::new, p_184246_ -> p_184246_),
                    p_184251_ -> p_184251_ instanceof CubicSpline.Constant<C, I> constant
                            ? Either.left(constant.value())
                            : Either.right((CubicSpline.Multipoint<C, I>)p_184251_)
                )
        );
        return mutableobject.getValue();
    }

    static <C, I extends ToFloatFunction<C>> CubicSpline<C, I> constant(float value) {
        return new CubicSpline.Constant<>(value);
    }

    static <C, I extends ToFloatFunction<C>> CubicSpline.Builder<C, I> builder(I coordinate) {
        return new CubicSpline.Builder<>(coordinate);
    }

    static <C, I extends ToFloatFunction<C>> CubicSpline.Builder<C, I> builder(I coordinate, ToFloatFunction<Float> valueTransformer) {
        return new CubicSpline.Builder<>(coordinate, valueTransformer);
    }

    public static final class Builder<C, I extends ToFloatFunction<C>> {
        private final I coordinate;
        private final ToFloatFunction<Float> valueTransformer;
        private final FloatList locations = new FloatArrayList();
        private final List<CubicSpline<C, I>> values = Lists.newArrayList();
        private final FloatList derivatives = new FloatArrayList();

        protected Builder(I coordinate) {
            this(coordinate, ToFloatFunction.IDENTITY);
        }

        protected Builder(I coordinate, ToFloatFunction<Float> valueTransformer) {
            this.coordinate = coordinate;
            this.valueTransformer = valueTransformer;
        }

        public CubicSpline.Builder<C, I> addPoint(float location, float value) {
            return this.addPoint(location, new CubicSpline.Constant<>(this.valueTransformer.apply(value)), 0.0F);
        }

        public CubicSpline.Builder<C, I> addPoint(float location, float value, float derivative) {
            return this.addPoint(location, new CubicSpline.Constant<>(this.valueTransformer.apply(value)), derivative);
        }

        public CubicSpline.Builder<C, I> addPoint(float location, CubicSpline<C, I> value) {
            return this.addPoint(location, value, 0.0F);
        }

        private CubicSpline.Builder<C, I> addPoint(float location, CubicSpline<C, I> value, float derivative) {
            if (!this.locations.isEmpty() && location <= this.locations.getFloat(this.locations.size() - 1)) {
                throw new IllegalArgumentException("Please register points in ascending order");
            } else {
                this.locations.add(location);
                this.values.add(value);
                this.derivatives.add(derivative);
                return this;
            }
        }

        public CubicSpline<C, I> build() {
            if (this.locations.isEmpty()) {
                throw new IllegalStateException("No elements added");
            } else {
                return CubicSpline.Multipoint.create(
                    this.coordinate, this.locations.toFloatArray(), ImmutableList.copyOf(this.values), this.derivatives.toFloatArray()
                );
            }
        }
    }

    @VisibleForDebug
    public static record Constant<C, I extends ToFloatFunction<C>>(float value) implements CubicSpline<C, I> {
        @Override
        public float apply(C p_184313_) {
            return this.value;
        }

        @Override
        public String parityString() {
            return String.format(Locale.ROOT, "k=%.3f", this.value);
        }

        @Override
        public float minValue() {
            return this.value;
        }

        @Override
        public float maxValue() {
            return this.value;
        }

        @Override
        public CubicSpline<C, I> mapAll(CubicSpline.CoordinateVisitor<I> p_211581_) {
            return this;
        }
    }

    public interface CoordinateVisitor<I> {
        I visit(I coordinate);
    }

    @VisibleForDebug
    public static record Multipoint<C, I extends ToFloatFunction<C>>(
        I coordinate, float[] locations, List<CubicSpline<C, I>> values, float[] derivatives, float minValue, float maxValue
    ) implements CubicSpline<C, I> {
        public Multipoint(I coordinate, float[] locations, List<CubicSpline<C, I>> values, float[] derivatives, float minValue, float maxValue) {
            validateSizes(locations, values, derivatives);
            this.coordinate = coordinate;
            this.locations = locations;
            this.values = values;
            this.derivatives = derivatives;
            this.minValue = minValue;
            this.maxValue = maxValue;
        }

        static <C, I extends ToFloatFunction<C>> CubicSpline.Multipoint<C, I> create(
            I coordinate, float[] locations, List<CubicSpline<C, I>> values, float[] derivatives
        ) {
            validateSizes(locations, values, derivatives);
            int i = locations.length - 1;
            float f = Float.POSITIVE_INFINITY;
            float f1 = Float.NEGATIVE_INFINITY;
            float f2 = coordinate.minValue();
            float f3 = coordinate.maxValue();
            if (f2 < locations[0]) {
                float f4 = linearExtend(f2, locations, values.get(0).minValue(), derivatives, 0);
                float f5 = linearExtend(f2, locations, values.get(0).maxValue(), derivatives, 0);
                f = Math.min(f, Math.min(f4, f5));
                f1 = Math.max(f1, Math.max(f4, f5));
            }

            if (f3 > locations[i]) {
                float f24 = linearExtend(f3, locations, values.get(i).minValue(), derivatives, i);
                float f25 = linearExtend(f3, locations, values.get(i).maxValue(), derivatives, i);
                f = Math.min(f, Math.min(f24, f25));
                f1 = Math.max(f1, Math.max(f24, f25));
            }

            for (CubicSpline<C, I> cubicspline2 : values) {
                f = Math.min(f, cubicspline2.minValue());
                f1 = Math.max(f1, cubicspline2.maxValue());
            }

            for (int j = 0; j < i; j++) {
                float f26 = locations[j];
                float f6 = locations[j + 1];
                float f7 = f6 - f26;
                CubicSpline<C, I> cubicspline = values.get(j);
                CubicSpline<C, I> cubicspline1 = values.get(j + 1);
                float f8 = cubicspline.minValue();
                float f9 = cubicspline.maxValue();
                float f10 = cubicspline1.minValue();
                float f11 = cubicspline1.maxValue();
                float f12 = derivatives[j];
                float f13 = derivatives[j + 1];
                if (f12 != 0.0F || f13 != 0.0F) {
                    float f14 = f12 * f7;
                    float f15 = f13 * f7;
                    float f16 = Math.min(f8, f10);
                    float f17 = Math.max(f9, f11);
                    float f18 = f14 - f11 + f8;
                    float f19 = f14 - f10 + f9;
                    float f20 = -f15 + f10 - f9;
                    float f21 = -f15 + f11 - f8;
                    float f22 = Math.min(f18, f20);
                    float f23 = Math.max(f19, f21);
                    f = Math.min(f, f16 + 0.25F * f22);
                    f1 = Math.max(f1, f17 + 0.25F * f23);
                }
            }

            return new CubicSpline.Multipoint<>(coordinate, locations, values, derivatives, f, f1);
        }

        private static float linearExtend(float coordinate, float[] locations, float value, float[] derivatives, int index) {
            float f = derivatives[index];
            return f == 0.0F ? value : value + f * (coordinate - locations[index]);
        }

        private static <C, I extends ToFloatFunction<C>> void validateSizes(float[] locations, List<CubicSpline<C, I>> values, float[] derivatives) {
            if (locations.length != values.size() || locations.length != derivatives.length) {
                throw new IllegalArgumentException("All lengths must be equal, got: " + locations.length + " " + values.size() + " " + derivatives.length);
            } else if (locations.length == 0) {
                throw new IllegalArgumentException("Cannot create a multipoint spline with no points");
            }
        }

        @Override
        public float apply(C object) {
            float f = this.coordinate.apply(object);
            int i = findIntervalStart(this.locations, f);
            int j = this.locations.length - 1;
            if (i < 0) {
                return linearExtend(f, this.locations, this.values.get(0).apply(object), this.derivatives, 0);
            } else if (i == j) {
                return linearExtend(f, this.locations, this.values.get(j).apply(object), this.derivatives, j);
            } else {
                float f1 = this.locations[i];
                float f2 = this.locations[i + 1];
                float f3 = (f - f1) / (f2 - f1);
                ToFloatFunction<C> tofloatfunction = (ToFloatFunction<C>)this.values.get(i);
                ToFloatFunction<C> tofloatfunction1 = (ToFloatFunction<C>)this.values.get(i + 1);
                float f4 = this.derivatives[i];
                float f5 = this.derivatives[i + 1];
                float f6 = tofloatfunction.apply(object);
                float f7 = tofloatfunction1.apply(object);
                float f8 = f4 * (f2 - f1) - (f7 - f6);
                float f9 = -f5 * (f2 - f1) + (f7 - f6);
                return Mth.lerp(f3, f6, f7) + f3 * (1.0F - f3) * Mth.lerp(f3, f8, f9);
            }
        }

        private static int findIntervalStart(float[] locations, float start) {
            return Mth.binarySearch(0, locations.length, p_216142_ -> start < locations[p_216142_]) - 1;
        }

        @VisibleForTesting
        @Override
        public String parityString() {
            return "Spline{coordinate="
                + this.coordinate
                + ", locations="
                + this.toString(this.locations)
                + ", derivatives="
                + this.toString(this.derivatives)
                + ", values="
                + this.values.stream().map(CubicSpline::parityString).collect(Collectors.joining(", ", "[", "]"))
                + "}";
        }

        private String toString(float[] locations) {
            return "["
                + IntStream.range(0, locations.length)
                    .mapToDouble(p_184338_ -> (double)locations[p_184338_])
                    .mapToObj(p_184330_ -> String.format(Locale.ROOT, "%.3f", p_184330_))
                    .collect(Collectors.joining(", "))
                + "]";
        }

        @Override
        public CubicSpline<C, I> mapAll(CubicSpline.CoordinateVisitor<I> visitor) {
            return create(
                visitor.visit(this.coordinate),
                this.locations,
                this.values().stream().map(p_211588_ -> p_211588_.mapAll(visitor)).toList(),
                this.derivatives
            );
        }
    }
}
