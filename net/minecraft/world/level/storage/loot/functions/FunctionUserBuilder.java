package net.minecraft.world.level.storage.loot.functions;

import java.util.Arrays;
import java.util.function.Function;

/**
 * Base interface for builders that accept loot functions.
 *
 * @see LootItemFunction
 */
public interface FunctionUserBuilder<T extends FunctionUserBuilder<T>> {
    T apply(LootItemFunction.Builder functionBuilder);

    default <E> T apply(Iterable<E> builderSources, Function<E, LootItemFunction.Builder> toBuilderFunction) {
        T t = this.unwrap();

        for (E e : builderSources) {
            t = t.apply(toBuilderFunction.apply(e));
        }

        return t;
    }

    default <E> T apply(E[] builderSources, Function<E, LootItemFunction.Builder> toBuilderFunction) {
        return this.apply(Arrays.asList(builderSources), toBuilderFunction);
    }

    T unwrap();
}
