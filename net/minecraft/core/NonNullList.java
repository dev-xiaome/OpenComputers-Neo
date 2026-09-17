package net.minecraft.core;

import com.google.common.collect.Lists;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.lang3.Validate;

public class NonNullList<E> extends AbstractList<E> {

    /**
     * Neo: utility method to construct a Codec for a NonNullList
     * @param entryCodec the codec to use for the elements
     * @param <E> the element type
     * @return a codec that encodes as a list, and decodes into NonNullList
     */
    public static <E> com.mojang.serialization.Codec<NonNullList<E>> codecOf(com.mojang.serialization.Codec<E> entryCodec) {
        return entryCodec.listOf().xmap(NonNullList::copyOf, java.util.function.Function.identity());
    }

    /**
     * Neo: utility method to construct an immutable NonNullList from a given collection
     * @param entries the collection to make a copy of
     * @param <E> the type of the elements in the list
     * @return a new immutable NonNullList
     * @throws NullPointerException if entries is null, or if it contains any nulls
     */
    public static <E> NonNullList<E> copyOf(java.util.Collection<? extends E> entries) {
        return new NonNullList<>(List.copyOf(entries), null);
    }

    private final List<E> list;
    @Nullable
    private final E defaultValue;

    public static <E> NonNullList<E> create() {
        return new NonNullList<>(Lists.newArrayList(), null);
    }

    public static <E> NonNullList<E> createWithCapacity(int initialCapacity) {
        return new NonNullList<>(Lists.newArrayListWithCapacity(initialCapacity), null);
    }

    /**
     * Creates a new NonNullList with <i>fixed</i> size and default value. The list will be filled with the default value.
     */
    public static <E> NonNullList<E> withSize(int size, E defaultValue) {
        Validate.notNull(defaultValue);
        Object[] aobject = new Object[size];
        Arrays.fill(aobject, defaultValue);
        return new NonNullList<>(Arrays.asList((E[])aobject), defaultValue);
    }

    @SafeVarargs
    public static <E> NonNullList<E> of(E defaultValue, E... elements) {
        return new NonNullList<>(Arrays.asList(elements), defaultValue);
    }

    protected NonNullList(List<E> list, @Nullable E defaultValue) {
        this.list = list;
        this.defaultValue = defaultValue;
    }

    @Nonnull
    @Override
    public E get(int index) {
        return this.list.get(index);
    }

    @Override
    public E set(int index, E value) {
        Validate.notNull(value);
        return this.list.set(index, value);
    }

    @Override
    public void add(int index, E value) {
        Validate.notNull(value);
        this.list.add(index, value);
    }

    @Override
    public E remove(int index) {
        return this.list.remove(index);
    }

    @Override
    public int size() {
        return this.list.size();
    }

    @Override
    public void clear() {
        if (this.defaultValue == null) {
            super.clear();
        } else {
            for (int i = 0; i < this.size(); i++) {
                this.set(i, this.defaultValue);
            }
        }
    }
}
