package net.minecraft.util;

import it.unimi.dsi.fastutil.objects.ObjectArrays;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;
import javax.annotation.Nullable;

public class SortedArraySet<T> extends AbstractSet<T> {
    private static final int DEFAULT_INITIAL_CAPACITY = 10;
    private final Comparator<T> comparator;
    T[] contents;
    int size;

    private SortedArraySet(int initialCapacity, Comparator<T> comparator) {
        this.comparator = comparator;
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Initial capacity (" + initialCapacity + ") is negative");
        } else {
            this.contents = (T[])castRawArray(new Object[initialCapacity]);
        }
    }

    public static <T extends Comparable<T>> SortedArraySet<T> create() {
        return create(10);
    }

    public static <T extends Comparable<T>> SortedArraySet<T> create(int initialCapacity) {
        return new SortedArraySet<>(initialCapacity, Comparator.<T>naturalOrder());
    }

    public static <T> SortedArraySet<T> create(Comparator<T> comparator) {
        return create(comparator, 10);
    }

    public static <T> SortedArraySet<T> create(Comparator<T> comparator, int initialCapacity) {
        return new SortedArraySet<>(initialCapacity, comparator);
    }

    private static <T> T[] castRawArray(Object[] array) {
        return (T[])array;
    }

    private int findIndex(T object) {
        return Arrays.binarySearch(this.contents, 0, this.size, object, this.comparator);
    }

    private static int getInsertionPosition(int index) {
        return -index - 1;
    }

    @Override
    public boolean add(T element) {
        int i = this.findIndex(element);
        if (i >= 0) {
            return false;
        } else {
            int j = getInsertionPosition(i);
            this.addInternal(element, j);
            return true;
        }
    }

    private void grow(int size) {
        if (size > this.contents.length) {
            if (this.contents != ObjectArrays.DEFAULT_EMPTY_ARRAY) {
                size = (int)Math.max(Math.min((long)this.contents.length + (long)(this.contents.length >> 1), 2147483639L), (long)size);
            } else if (size < 10) {
                size = 10;
            }

            Object[] aobject = new Object[size];
            System.arraycopy(this.contents, 0, aobject, 0, this.size);
            this.contents = (T[])castRawArray(aobject);
        }
    }

    private void addInternal(T element, int index) {
        this.grow(this.size + 1);
        if (index != this.size) {
            System.arraycopy(this.contents, index, this.contents, index + 1, this.size - index);
        }

        this.contents[index] = element;
        this.size++;
    }

    void removeInternal(int index) {
        this.size--;
        if (index != this.size) {
            System.arraycopy(this.contents, index + 1, this.contents, index, this.size - index);
        }

        this.contents[this.size] = null;
    }

    private T getInternal(int index) {
        return this.contents[index];
    }

    public T addOrGet(T element) {
        int i = this.findIndex(element);
        if (i >= 0) {
            return this.getInternal(i);
        } else {
            this.addInternal(element, getInsertionPosition(i));
            return element;
        }
    }

    @Override
    public boolean remove(Object element) {
        int i = this.findIndex((T)element);
        if (i >= 0) {
            this.removeInternal(i);
            return true;
        } else {
            return false;
        }
    }

    @Nullable
    public T get(T element) {
        int i = this.findIndex(element);
        return i >= 0 ? this.getInternal(i) : null;
    }

    public T first() {
        return this.getInternal(0);
    }

    public T last() {
        return this.getInternal(this.size - 1);
    }

    @Override
    public boolean contains(Object element) {
        int i = this.findIndex((T)element);
        return i >= 0;
    }

    @Override
    public Iterator<T> iterator() {
        return new SortedArraySet.ArrayIterator();
    }

    @Override
    public int size() {
        return this.size;
    }

    @Override
    public Object[] toArray() {
        return Arrays.copyOf(this.contents, this.size, Object[].class);
    }

    @Override
    public <U> U[] toArray(U[] output) {
        if (output.length < this.size) {
            return (U[])Arrays.copyOf(this.contents, this.size, (Class<? extends T[]>)output.getClass());
        } else {
            System.arraycopy(this.contents, 0, output, 0, this.size);
            if (output.length > this.size) {
                output[this.size] = null;
            }

            return output;
        }
    }

    @Override
    public void clear() {
        Arrays.fill(this.contents, 0, this.size, null);
        this.size = 0;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else {
            if (other instanceof SortedArraySet<?> sortedarrayset && this.comparator.equals(sortedarrayset.comparator)) {
                return this.size == sortedarrayset.size && Arrays.equals(this.contents, sortedarrayset.contents);
            }

            return super.equals(other);
        }
    }

    class ArrayIterator implements Iterator<T> {
        private int index;
        private int last = -1;

        @Override
        public boolean hasNext() {
            return this.index < SortedArraySet.this.size;
        }

        @Override
        public T next() {
            if (this.index >= SortedArraySet.this.size) {
                throw new NoSuchElementException();
            } else {
                this.last = this.index++;
                return SortedArraySet.this.contents[this.last];
            }
        }

        @Override
        public void remove() {
            if (this.last == -1) {
                throw new IllegalStateException();
            } else {
                SortedArraySet.this.removeInternal(this.last);
                this.index--;
                this.last = -1;
            }
        }
    }
}
