package net.minecraft.util.datafix;

import com.mojang.datafixers.Typed;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;

public class ExtraDataFixUtils {
    public static Dynamic<?> fixBlockPos(Dynamic<?> data) {
        Optional<Number> optional = data.get("X").asNumber().result();
        Optional<Number> optional1 = data.get("Y").asNumber().result();
        Optional<Number> optional2 = data.get("Z").asNumber().result();
        return !optional.isEmpty() && !optional1.isEmpty() && !optional2.isEmpty()
            ? data.createIntList(IntStream.of(optional.get().intValue(), optional1.get().intValue(), optional2.get().intValue()))
            : data;
    }

    public static <T, R> Typed<R> cast(Type<R> type, Typed<T> data) {
        return new Typed<>(type, data.getOps(), (R)data.getValue());
    }

    @SafeVarargs
    public static <T> Function<Typed<?>, Typed<?>> chainAllFilters(Function<Typed<?>, Typed<?>>... filters) {
        return p_345927_ -> {
            for (Function<Typed<?>, Typed<?>> function : filters) {
                p_345927_ = function.apply(p_345927_);
            }

            return p_345927_;
        };
    }
}
