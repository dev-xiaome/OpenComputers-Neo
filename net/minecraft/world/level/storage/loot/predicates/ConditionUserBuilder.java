package net.minecraft.world.level.storage.loot.predicates;

import java.util.function.Function;

/**
 * Base interface for builders that can accept loot conditions.
 *
 * @see LootItemCondition
 */
public interface ConditionUserBuilder<T extends ConditionUserBuilder<T>> {
    T when(LootItemCondition.Builder conditionBuilder);

    default <E> T when(Iterable<E> builderSources, Function<E, LootItemCondition.Builder> toBuilderFunction) {
        T t = this.unwrap();

        for (E e : builderSources) {
            t = t.when(toBuilderFunction.apply(e));
        }

        return t;
    }

    T unwrap();
}
