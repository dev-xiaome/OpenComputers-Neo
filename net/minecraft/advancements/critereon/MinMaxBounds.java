package net.minecraft.advancements.critereon;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;

public interface MinMaxBounds<T extends Number> {
    SimpleCommandExceptionType ERROR_EMPTY = new SimpleCommandExceptionType(Component.translatable("argument.range.empty"));
    SimpleCommandExceptionType ERROR_SWAPPED = new SimpleCommandExceptionType(Component.translatable("argument.range.swapped"));

    Optional<T> min();

    Optional<T> max();

    default boolean isAny() {
        return this.min().isEmpty() && this.max().isEmpty();
    }

    default Optional<T> unwrapPoint() {
        Optional<T> optional = this.min();
        Optional<T> optional1 = this.max();
        return optional.equals(optional1) ? optional : Optional.empty();
    }

    static <T extends Number, R extends MinMaxBounds<T>> Codec<R> createCodec(Codec<T> p_codec, MinMaxBounds.BoundsFactory<T, R> boundsFactory) {
        Codec<R> codec = RecordCodecBuilder.create(
            p_337383_ -> p_337383_.group(
                        p_codec.optionalFieldOf("min").forGetter(MinMaxBounds::min), p_codec.optionalFieldOf("max").forGetter(MinMaxBounds::max)
                    )
                    .apply(p_337383_, boundsFactory::create)
        );
        return Codec.either(codec, p_codec)
            .xmap(
                p_298558_ -> p_298558_.map(p_299210_ -> (R)p_299210_, p_298935_ -> boundsFactory.create(Optional.of((T)p_298935_), Optional.of((T)p_298935_))),
                p_298447_ -> {
                    Optional<T> optional = p_298447_.unwrapPoint();
                    return optional.isPresent() ? Either.right(optional.get()) : Either.left((R)p_298447_);
                }
            );
    }

    static <T extends Number, R extends MinMaxBounds<T>> R fromReader(
        StringReader reader,
        MinMaxBounds.BoundsFromReaderFactory<T, R> boundedFactory,
        Function<String, T> valueFactory,
        Supplier<DynamicCommandExceptionType> commandExceptionSupplier,
        Function<T, T> formatter
    ) throws CommandSyntaxException {
        if (!reader.canRead()) {
            throw ERROR_EMPTY.createWithContext(reader);
        } else {
            int i = reader.getCursor();

            try {
                Optional<T> optional = readNumber(reader, valueFactory, commandExceptionSupplier).map(formatter);
                Optional<T> optional1;
                if (reader.canRead(2) && reader.peek() == '.' && reader.peek(1) == '.') {
                    reader.skip();
                    reader.skip();
                    optional1 = readNumber(reader, valueFactory, commandExceptionSupplier).map(formatter);
                    if (optional.isEmpty() && optional1.isEmpty()) {
                        throw ERROR_EMPTY.createWithContext(reader);
                    }
                } else {
                    optional1 = optional;
                }

                if (optional.isEmpty() && optional1.isEmpty()) {
                    throw ERROR_EMPTY.createWithContext(reader);
                } else {
                    return boundedFactory.create(reader, optional, optional1);
                }
            } catch (CommandSyntaxException commandsyntaxexception) {
                reader.setCursor(i);
                throw new CommandSyntaxException(commandsyntaxexception.getType(), commandsyntaxexception.getRawMessage(), commandsyntaxexception.getInput(), i);
            }
        }
    }

    private static <T extends Number> Optional<T> readNumber(
        StringReader reader, Function<String, T> stringToValueFunction, Supplier<DynamicCommandExceptionType> commandExceptionSupplier
    ) throws CommandSyntaxException {
        int i = reader.getCursor();

        while (reader.canRead() && isAllowedInputChat(reader)) {
            reader.skip();
        }

        String s = reader.getString().substring(i, reader.getCursor());
        if (s.isEmpty()) {
            return Optional.empty();
        } else {
            try {
                return Optional.of(stringToValueFunction.apply(s));
            } catch (NumberFormatException numberformatexception) {
                throw commandExceptionSupplier.get().createWithContext(reader, s);
            }
        }
    }

    private static boolean isAllowedInputChat(StringReader reader) {
        char c0 = reader.peek();
        if ((c0 < '0' || c0 > '9') && c0 != '-') {
            return c0 != '.' ? false : !reader.canRead(2) || reader.peek(1) != '.';
        } else {
            return true;
        }
    }

    @FunctionalInterface
    public interface BoundsFactory<T extends Number, R extends MinMaxBounds<T>> {
        R create(Optional<T> min, Optional<T> max);
    }

    @FunctionalInterface
    public interface BoundsFromReaderFactory<T extends Number, R extends MinMaxBounds<T>> {
        R create(StringReader reader, Optional<T> min, Optional<T> max) throws CommandSyntaxException;
    }

    public static record Doubles(Optional<Double> min, Optional<Double> max, Optional<Double> minSq, Optional<Double> maxSq) implements MinMaxBounds<Double> {
        public static final MinMaxBounds.Doubles ANY = new MinMaxBounds.Doubles(Optional.empty(), Optional.empty());
        public static final Codec<MinMaxBounds.Doubles> CODEC = MinMaxBounds.<Double, MinMaxBounds.Doubles>createCodec(Codec.DOUBLE, MinMaxBounds.Doubles::new);

        private Doubles(Optional<Double> p_298243_, Optional<Double> p_299159_) {
            this(p_298243_, p_299159_, squareOpt(p_298243_), squareOpt(p_299159_));
        }

        private static MinMaxBounds.Doubles create(StringReader reader, Optional<Double> min, Optional<Double> max) throws CommandSyntaxException {
            if (min.isPresent() && max.isPresent() && min.get() > max.get()) {
                throw ERROR_SWAPPED.createWithContext(reader);
            } else {
                return new MinMaxBounds.Doubles(min, max);
            }
        }

        private static Optional<Double> squareOpt(Optional<Double> value) {
            return value.map(p_297908_ -> p_297908_ * p_297908_);
        }

        public static MinMaxBounds.Doubles exactly(double value) {
            return new MinMaxBounds.Doubles(Optional.of(value), Optional.of(value));
        }

        public static MinMaxBounds.Doubles between(double min, double max) {
            return new MinMaxBounds.Doubles(Optional.of(min), Optional.of(max));
        }

        public static MinMaxBounds.Doubles atLeast(double min) {
            return new MinMaxBounds.Doubles(Optional.of(min), Optional.empty());
        }

        public static MinMaxBounds.Doubles atMost(double max) {
            return new MinMaxBounds.Doubles(Optional.empty(), Optional.of(max));
        }

        public boolean matches(double value) {
            return this.min.isPresent() && this.min.get() > value ? false : this.max.isEmpty() || !(this.max.get() < value);
        }

        public boolean matchesSqr(double value) {
            return this.minSq.isPresent() && this.minSq.get() > value ? false : this.maxSq.isEmpty() || !(this.maxSq.get() < value);
        }

        public static MinMaxBounds.Doubles fromReader(StringReader reader) throws CommandSyntaxException {
            return fromReader(reader, p_154807_ -> p_154807_);
        }

        public static MinMaxBounds.Doubles fromReader(StringReader reader, Function<Double, Double> formatter) throws CommandSyntaxException {
            return MinMaxBounds.fromReader(
                reader, MinMaxBounds.Doubles::create, Double::parseDouble, CommandSyntaxException.BUILT_IN_EXCEPTIONS::readerInvalidDouble, formatter
            );
        }
    }

    public static record Ints(Optional<Integer> min, Optional<Integer> max, Optional<Long> minSq, Optional<Long> maxSq) implements MinMaxBounds<Integer> {
        public static final MinMaxBounds.Ints ANY = new MinMaxBounds.Ints(Optional.empty(), Optional.empty());
        public static final Codec<MinMaxBounds.Ints> CODEC = MinMaxBounds.<Integer, MinMaxBounds.Ints>createCodec(Codec.INT, MinMaxBounds.Ints::new);

        private Ints(Optional<Integer> p_298275_, Optional<Integer> p_298272_) {
            this(p_298275_, p_298272_, p_298275_.map(p_297910_ -> p_297910_.longValue() * p_297910_.longValue()), squareOpt(p_298272_));
        }

        private static MinMaxBounds.Ints create(StringReader reader, Optional<Integer> min, Optional<Integer> max) throws CommandSyntaxException {
            if (min.isPresent() && max.isPresent() && min.get() > max.get()) {
                throw ERROR_SWAPPED.createWithContext(reader);
            } else {
                return new MinMaxBounds.Ints(min, max);
            }
        }

        private static Optional<Long> squareOpt(Optional<Integer> value) {
            return value.map(p_297909_ -> p_297909_.longValue() * p_297909_.longValue());
        }

        public static MinMaxBounds.Ints exactly(int value) {
            return new MinMaxBounds.Ints(Optional.of(value), Optional.of(value));
        }

        public static MinMaxBounds.Ints between(int min, int max) {
            return new MinMaxBounds.Ints(Optional.of(min), Optional.of(max));
        }

        public static MinMaxBounds.Ints atLeast(int min) {
            return new MinMaxBounds.Ints(Optional.of(min), Optional.empty());
        }

        public static MinMaxBounds.Ints atMost(int max) {
            return new MinMaxBounds.Ints(Optional.empty(), Optional.of(max));
        }

        public boolean matches(int value) {
            return this.min.isPresent() && this.min.get() > value ? false : this.max.isEmpty() || this.max.get() >= value;
        }

        public boolean matchesSqr(long value) {
            return this.minSq.isPresent() && this.minSq.get() > value ? false : this.maxSq.isEmpty() || this.maxSq.get() >= value;
        }

        public static MinMaxBounds.Ints fromReader(StringReader reader) throws CommandSyntaxException {
            return fromReader(reader, p_55389_ -> p_55389_);
        }

        public static MinMaxBounds.Ints fromReader(StringReader reader, Function<Integer, Integer> valueFunction) throws CommandSyntaxException {
            return MinMaxBounds.fromReader(
                reader, MinMaxBounds.Ints::create, Integer::parseInt, CommandSyntaxException.BUILT_IN_EXCEPTIONS::readerInvalidInt, valueFunction
            );
        }
    }
}
