package net.minecraft.nbt;

import java.util.AbstractList;

public abstract class CollectionTag<T extends Tag> extends AbstractList<T> implements Tag {
    public abstract T set(int index, T tag);

    public abstract void add(int index, T tag);

    public abstract T remove(int index);

    public abstract boolean setTag(int index, Tag tag);

    public abstract boolean addTag(int index, Tag tag);

    public abstract byte getElementType();
}
