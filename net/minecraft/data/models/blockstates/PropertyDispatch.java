package net.minecraft.data.models.blockstates;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.world.level.block.state.properties.Property;

public abstract class PropertyDispatch {
    private final Map<Selector, List<Variant>> values = Maps.newHashMap();

    protected void putValue(Selector selector, List<Variant> values) {
        List<Variant> list = this.values.put(selector, values);
        if (list != null) {
            throw new IllegalStateException("Value " + selector + " is already defined");
        }
    }

    Map<Selector, List<Variant>> getEntries() {
        this.verifyComplete();
        return ImmutableMap.copyOf(this.values);
    }

    private void verifyComplete() {
        List<Property<?>> list = this.getDefinedProperties();
        Stream<Selector> stream = Stream.of(Selector.empty());

        for (Property<?> property : list) {
            stream = stream.flatMap(p_125316_ -> property.getAllValues().map(p_125316_::extend));
        }

        List<Selector> list1 = stream.filter(p_125318_ -> !this.values.containsKey(p_125318_)).collect(Collectors.toList());
        if (!list1.isEmpty()) {
            throw new IllegalStateException("Missing definition for properties: " + list1);
        }
    }

    abstract List<Property<?>> getDefinedProperties();

    public static <T1 extends Comparable<T1>> PropertyDispatch.C1<T1> property(Property<T1> property1) {
        return new PropertyDispatch.C1<>(property1);
    }

    public static <T1 extends Comparable<T1>, T2 extends Comparable<T2>> PropertyDispatch.C2<T1, T2> properties(Property<T1> property1, Property<T2> property2) {
        return new PropertyDispatch.C2<>(property1, property2);
    }

    public static <T1 extends Comparable<T1>, T2 extends Comparable<T2>, T3 extends Comparable<T3>> PropertyDispatch.C3<T1, T2, T3> properties(
        Property<T1> property1, Property<T2> property2, Property<T3> property3
    ) {
        return new PropertyDispatch.C3<>(property1, property2, property3);
    }

    public static <T1 extends Comparable<T1>, T2 extends Comparable<T2>, T3 extends Comparable<T3>, T4 extends Comparable<T4>> PropertyDispatch.C4<T1, T2, T3, T4> properties(
        Property<T1> property1, Property<T2> property2, Property<T3> property3, Property<T4> property4
    ) {
        return new PropertyDispatch.C4<>(property1, property2, property3, property4);
    }

    public static <T1 extends Comparable<T1>, T2 extends Comparable<T2>, T3 extends Comparable<T3>, T4 extends Comparable<T4>, T5 extends Comparable<T5>> PropertyDispatch.C5<T1, T2, T3, T4, T5> properties(
        Property<T1> property1, Property<T2> property2, Property<T3> property3, Property<T4> property4, Property<T5> property5
    ) {
        return new PropertyDispatch.C5<>(property1, property2, property3, property4, property5);
    }

    public static class C1<T1 extends Comparable<T1>> extends PropertyDispatch {
        private final Property<T1> property1;

        C1(Property<T1> property1) {
            this.property1 = property1;
        }

        @Override
        public List<Property<?>> getDefinedProperties() {
            return ImmutableList.of(this.property1);
        }

        public PropertyDispatch.C1<T1> select(T1 propertyValue, List<Variant> variants) {
            Selector selector = Selector.of(this.property1.value(propertyValue));
            this.putValue(selector, variants);
            return this;
        }

        public PropertyDispatch.C1<T1> select(T1 propertyValue, Variant variant) {
            return this.select(propertyValue, Collections.singletonList(variant));
        }

        public PropertyDispatch generate(Function<T1, Variant> propertyValueToVariantMapper) {
            this.property1.getPossibleValues().forEach(p_125340_ -> this.select((T1)p_125340_, propertyValueToVariantMapper.apply((T1)p_125340_)));
            return this;
        }

        public PropertyDispatch generateList(Function<T1, List<Variant>> propertyValueToVariantsMapper) {
            this.property1.getPossibleValues().forEach(p_176312_ -> this.select((T1)p_176312_, propertyValueToVariantsMapper.apply((T1)p_176312_)));
            return this;
        }
    }

    public static class C2<T1 extends Comparable<T1>, T2 extends Comparable<T2>> extends PropertyDispatch {
        private final Property<T1> property1;
        private final Property<T2> property2;

        C2(Property<T1> property1, Property<T2> property2) {
            this.property1 = property1;
            this.property2 = property2;
        }

        @Override
        public List<Property<?>> getDefinedProperties() {
            return ImmutableList.of(this.property1, this.property2);
        }

        public PropertyDispatch.C2<T1, T2> select(T1 property1Value, T2 property2Value, List<Variant> variants) {
            Selector selector = Selector.of(this.property1.value(property1Value), this.property2.value(property2Value));
            this.putValue(selector, variants);
            return this;
        }

        public PropertyDispatch.C2<T1, T2> select(T1 property1Value, T2 property2Value, Variant variant) {
            return this.select(property1Value, property2Value, Collections.singletonList(variant));
        }

        public PropertyDispatch generate(BiFunction<T1, T2, Variant> propertyValuesToVariantMapper) {
            this.property1
                .getPossibleValues()
                .forEach(
                    p_125376_ -> this.property2
                            .getPossibleValues()
                            .forEach(p_176322_ -> this.select((T1)p_125376_, (T2)p_176322_, propertyValuesToVariantMapper.apply((T1)p_125376_, (T2)p_176322_)))
                );
            return this;
        }

        public PropertyDispatch generateList(BiFunction<T1, T2, List<Variant>> propertyValuesToVariantsMapper) {
            this.property1
                .getPossibleValues()
                .forEach(
                    p_125366_ -> this.property2
                            .getPossibleValues()
                            .forEach(p_176318_ -> this.select((T1)p_125366_, (T2)p_176318_, propertyValuesToVariantsMapper.apply((T1)p_125366_, (T2)p_176318_)))
                );
            return this;
        }
    }

    public static class C3<T1 extends Comparable<T1>, T2 extends Comparable<T2>, T3 extends Comparable<T3>> extends PropertyDispatch {
        private final Property<T1> property1;
        private final Property<T2> property2;
        private final Property<T3> property3;

        C3(Property<T1> property1, Property<T2> property2, Property<T3> property3) {
            this.property1 = property1;
            this.property2 = property2;
            this.property3 = property3;
        }

        @Override
        public List<Property<?>> getDefinedProperties() {
            return ImmutableList.of(this.property1, this.property2, this.property3);
        }

        public PropertyDispatch.C3<T1, T2, T3> select(T1 property1Value, T2 property2Value, T3 property3Value, List<Variant> variants) {
            Selector selector = Selector.of(this.property1.value(property1Value), this.property2.value(property2Value), this.property3.value(property3Value));
            this.putValue(selector, variants);
            return this;
        }

        public PropertyDispatch.C3<T1, T2, T3> select(T1 property1Value, T2 property2Value, T3 property3Value, Variant variant) {
            return this.select(property1Value, property2Value, property3Value, Collections.singletonList(variant));
        }

        public PropertyDispatch generate(PropertyDispatch.TriFunction<T1, T2, T3, Variant> propertyValuesToVariantMapper) {
            this.property1
                .getPossibleValues()
                .forEach(
                    p_125404_ -> this.property2
                            .getPossibleValues()
                            .forEach(
                                p_176343_ -> this.property3
                                        .getPossibleValues()
                                        .forEach(
                                            p_176339_ -> this.select(
                                                    (T1)p_125404_, (T2)p_176343_, (T3)p_176339_, propertyValuesToVariantMapper.apply((T1)p_125404_, (T2)p_176343_, (T3)p_176339_)
                                                )
                                        )
                            )
                );
            return this;
        }

        public PropertyDispatch generateList(PropertyDispatch.TriFunction<T1, T2, T3, List<Variant>> propertyValuesToVariantsMapper) {
            this.property1
                .getPossibleValues()
                .forEach(
                    p_176334_ -> this.property2
                            .getPossibleValues()
                            .forEach(
                                p_176331_ -> this.property3
                                        .getPossibleValues()
                                        .forEach(
                                            p_176327_ -> this.select(
                                                    (T1)p_176334_, (T2)p_176331_, (T3)p_176327_, propertyValuesToVariantsMapper.apply((T1)p_176334_, (T2)p_176331_, (T3)p_176327_)
                                                )
                                        )
                            )
                );
            return this;
        }
    }

    public static class C4<T1 extends Comparable<T1>, T2 extends Comparable<T2>, T3 extends Comparable<T3>, T4 extends Comparable<T4>> extends PropertyDispatch {
        private final Property<T1> property1;
        private final Property<T2> property2;
        private final Property<T3> property3;
        private final Property<T4> property4;

        C4(Property<T1> property1, Property<T2> property2, Property<T3> property3, Property<T4> property4) {
            this.property1 = property1;
            this.property2 = property2;
            this.property3 = property3;
            this.property4 = property4;
        }

        @Override
        public List<Property<?>> getDefinedProperties() {
            return ImmutableList.of(this.property1, this.property2, this.property3, this.property4);
        }

        public PropertyDispatch.C4<T1, T2, T3, T4> select(T1 property1Value, T2 property2Value, T3 property3Value, T4 property4Value, List<Variant> variants) {
            Selector selector = Selector.of(
                this.property1.value(property1Value), this.property2.value(property2Value), this.property3.value(property3Value), this.property4.value(property4Value)
            );
            this.putValue(selector, variants);
            return this;
        }

        public PropertyDispatch.C4<T1, T2, T3, T4> select(T1 property1Value, T2 property2Value, T3 property3Value, T4 property4Value, Variant variant) {
            return this.select(property1Value, property2Value, property3Value, property4Value, Collections.singletonList(variant));
        }

        public PropertyDispatch generate(PropertyDispatch.QuadFunction<T1, T2, T3, T4, Variant> propertyValuesToVariantMapper) {
            this.property1
                .getPossibleValues()
                .forEach(
                    p_176385_ -> this.property2
                            .getPossibleValues()
                            .forEach(
                                p_176380_ -> this.property3
                                        .getPossibleValues()
                                        .forEach(
                                            p_176376_ -> this.property4
                                                    .getPossibleValues()
                                                    .forEach(
                                                        p_176371_ -> this.select(
                                                                (T1)p_176385_,
                                                                (T2)p_176380_,
                                                                (T3)p_176376_,
                                                                (T4)p_176371_,
                                                                propertyValuesToVariantMapper.apply((T1)p_176385_, (T2)p_176380_, (T3)p_176376_, (T4)p_176371_)
                                                            )
                                                    )
                                        )
                            )
                );
            return this;
        }

        public PropertyDispatch generateList(PropertyDispatch.QuadFunction<T1, T2, T3, T4, List<Variant>> propertyValuesToVariantsMapper) {
            this.property1
                .getPossibleValues()
                .forEach(
                    p_176365_ -> this.property2
                            .getPossibleValues()
                            .forEach(
                                p_176360_ -> this.property3
                                        .getPossibleValues()
                                        .forEach(
                                            p_176356_ -> this.property4
                                                    .getPossibleValues()
                                                    .forEach(
                                                        p_176351_ -> this.select(
                                                                (T1)p_176365_,
                                                                (T2)p_176360_,
                                                                (T3)p_176356_,
                                                                (T4)p_176351_,
                                                                propertyValuesToVariantsMapper.apply((T1)p_176365_, (T2)p_176360_, (T3)p_176356_, (T4)p_176351_)
                                                            )
                                                    )
                                        )
                            )
                );
            return this;
        }
    }

    public static class C5<T1 extends Comparable<T1>, T2 extends Comparable<T2>, T3 extends Comparable<T3>, T4 extends Comparable<T4>, T5 extends Comparable<T5>>
        extends PropertyDispatch {
        private final Property<T1> property1;
        private final Property<T2> property2;
        private final Property<T3> property3;
        private final Property<T4> property4;
        private final Property<T5> property5;

        C5(Property<T1> property1, Property<T2> property2, Property<T3> property3, Property<T4> property4, Property<T5> property5) {
            this.property1 = property1;
            this.property2 = property2;
            this.property3 = property3;
            this.property4 = property4;
            this.property5 = property5;
        }

        @Override
        public List<Property<?>> getDefinedProperties() {
            return ImmutableList.of(this.property1, this.property2, this.property3, this.property4, this.property5);
        }

        public PropertyDispatch.C5<T1, T2, T3, T4, T5> select(T1 property1Value, T2 property2Value, T3 property3Value, T4 property4Value, T5 property5Value, List<Variant> variants) {
            Selector selector = Selector.of(
                this.property1.value(property1Value),
                this.property2.value(property2Value),
                this.property3.value(property3Value),
                this.property4.value(property4Value),
                this.property5.value(property5Value)
            );
            this.putValue(selector, variants);
            return this;
        }

        public PropertyDispatch.C5<T1, T2, T3, T4, T5> select(T1 property1Value, T2 property2Value, T3 property3Value, T4 property4Value, T5 property5Value, Variant variant) {
            return this.select(property1Value, property2Value, property3Value, property4Value, property5Value, Collections.singletonList(variant));
        }

        public PropertyDispatch generate(PropertyDispatch.PentaFunction<T1, T2, T3, T4, T5, Variant> propertyValuesToVariantMapper) {
            this.property1
                .getPossibleValues()
                .forEach(
                    p_176439_ -> this.property2
                            .getPossibleValues()
                            .forEach(
                                p_176434_ -> this.property3
                                        .getPossibleValues()
                                        .forEach(
                                            p_176430_ -> this.property4
                                                    .getPossibleValues()
                                                    .forEach(
                                                        p_176425_ -> this.property5
                                                                .getPossibleValues()
                                                                .forEach(
                                                                    p_176419_ -> this.select(
                                                                            (T1)p_176439_,
                                                                            (T2)p_176434_,
                                                                            (T3)p_176430_,
                                                                            (T4)p_176425_,
                                                                            (T5)p_176419_,
                                                                            propertyValuesToVariantMapper.apply(
                                                                                (T1)p_176439_, (T2)p_176434_, (T3)p_176430_, (T4)p_176425_, (T5)p_176419_
                                                                            )
                                                                        )
                                                                )
                                                    )
                                        )
                            )
                );
            return this;
        }

        public PropertyDispatch generateList(PropertyDispatch.PentaFunction<T1, T2, T3, T4, T5, List<Variant>> propertyValuesToVariantsMapper) {
            this.property1
                .getPossibleValues()
                .forEach(
                    p_176412_ -> this.property2
                            .getPossibleValues()
                            .forEach(
                                p_176407_ -> this.property3
                                        .getPossibleValues()
                                        .forEach(
                                            p_176403_ -> this.property4
                                                    .getPossibleValues()
                                                    .forEach(
                                                        p_176398_ -> this.property5
                                                                .getPossibleValues()
                                                                .forEach(
                                                                    p_176392_ -> this.select(
                                                                            (T1)p_176412_,
                                                                            (T2)p_176407_,
                                                                            (T3)p_176403_,
                                                                            (T4)p_176398_,
                                                                            (T5)p_176392_,
                                                                            propertyValuesToVariantsMapper.apply(
                                                                                (T1)p_176412_, (T2)p_176407_, (T3)p_176403_, (T4)p_176398_, (T5)p_176392_
                                                                            )
                                                                        )
                                                                )
                                                    )
                                        )
                            )
                );
            return this;
        }
    }

    @FunctionalInterface
    public interface PentaFunction<P1, P2, P3, P4, P5, R> {
        R apply(P1 p1, P2 p2, P3 p3, P4 p4, P5 p5);
    }

    @FunctionalInterface
    public interface QuadFunction<P1, P2, P3, P4, R> {
        R apply(P1 p1, P2 p2, P3 p3, P4 p4);
    }

    @FunctionalInterface
    public interface TriFunction<P1, P2, P3, R> {
        R apply(P1 p1, P2 p2, P3 p3);
    }
}
