package net.minecraft.world.level.storage.loot.entries;

import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.world.level.storage.loot.LootContext;

/**
 * Base interface for loot pool entry containers.
 * A loot pool entry container holds one or more loot pools and will expand into those.
 * Additionally, the container can either succeed or fail, based on its conditions.
 */
@FunctionalInterface
interface ComposableEntryContainer {
    /**
     * A container which always fails.
     */
    ComposableEntryContainer ALWAYS_FALSE = (p_79418_, p_79419_) -> false;
    /**
     * A container that always succeeds.
     */
    ComposableEntryContainer ALWAYS_TRUE = (p_79409_, p_79410_) -> true;

    /**
     * Expand this loot pool entry container by calling {@code entryConsumer} with any applicable entries
     *
     * @return whether this loot pool entry container successfully expanded or not
     */
    boolean expand(LootContext lootContext, Consumer<LootPoolEntry> entryConsumer);

    default ComposableEntryContainer and(ComposableEntryContainer entry) {
        Objects.requireNonNull(entry);
        return (p_79424_, p_79425_) -> this.expand(p_79424_, p_79425_) && entry.expand(p_79424_, p_79425_);
    }

    default ComposableEntryContainer or(ComposableEntryContainer entry) {
        Objects.requireNonNull(entry);
        return (p_79415_, p_79416_) -> this.expand(p_79415_, p_79416_) || entry.expand(p_79415_, p_79416_);
    }
}
