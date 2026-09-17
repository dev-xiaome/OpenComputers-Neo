package net.minecraft.resources;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.ListBuilder;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.ListBuilder.Builder;
import com.mojang.serialization.RecordBuilder.MapBuilder;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * A {@link DynamicOps} that delegates all functionality to an internal delegate. Comments and parameters here are copied from {@link DynamicOps} in DataFixerUpper.
 */
public abstract class DelegatingOps<T> implements DynamicOps<T> {
    protected final DynamicOps<T> delegate;

    protected DelegatingOps(DynamicOps<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public T empty() {
        return this.delegate.empty();
    }

    @Override
    public T emptyMap() {
        return this.delegate.emptyMap();
    }

    @Override
    public T emptyList() {
        return this.delegate.emptyList();
    }

    @Override
    public <U> U convertTo(DynamicOps<U> outOps, T input) {
        return this.delegate.convertTo(outOps, input);
    }

    @Override
    public DataResult<Number> getNumberValue(T input) {
        return this.delegate.getNumberValue(input);
    }

    @Override
    public T createNumeric(Number i) {
        return this.delegate.createNumeric(i);
    }

    @Override
    public T createByte(byte value) {
        return this.delegate.createByte(value);
    }

    @Override
    public T createShort(short value) {
        return this.delegate.createShort(value);
    }

    @Override
    public T createInt(int value) {
        return this.delegate.createInt(value);
    }

    @Override
    public T createLong(long value) {
        return this.delegate.createLong(value);
    }

    @Override
    public T createFloat(float value) {
        return this.delegate.createFloat(value);
    }

    @Override
    public T createDouble(double value) {
        return this.delegate.createDouble(value);
    }

    @Override
    public DataResult<Boolean> getBooleanValue(T input) {
        return this.delegate.getBooleanValue(input);
    }

    @Override
    public T createBoolean(boolean value) {
        return this.delegate.createBoolean(value);
    }

    @Override
    public DataResult<String> getStringValue(T input) {
        return this.delegate.getStringValue(input);
    }

    @Override
    public T createString(String value) {
        return this.delegate.createString(value);
    }

    /**
     * Only successful if first argument is a list/array or empty.
     */
    @Override
    public DataResult<T> mergeToList(T list, T value) {
        return this.delegate.mergeToList(list, value);
    }

    @Override
    public DataResult<T> mergeToList(T list, List<T> values) {
        return this.delegate.mergeToList(list, values);
    }

    /**
     * Only successful if first argument is a map or empty.
     */
    @Override
    public DataResult<T> mergeToMap(T map, T key, T value) {
        return this.delegate.mergeToMap(map, key, value);
    }

    @Override
    public DataResult<T> mergeToMap(T map, MapLike<T> values) {
        return this.delegate.mergeToMap(map, values);
    }

    @Override
    public DataResult<T> mergeToMap(T map, Map<T, T> values) {
        return this.delegate.mergeToMap(map, values);
    }

    @Override
    public DataResult<T> mergeToPrimitive(T prefix, T value) {
        return this.delegate.mergeToPrimitive(prefix, value);
    }

    @Override
    public DataResult<Stream<Pair<T, T>>> getMapValues(T input) {
        return this.delegate.getMapValues(input);
    }

    @Override
    public DataResult<Consumer<BiConsumer<T, T>>> getMapEntries(T input) {
        return this.delegate.getMapEntries(input);
    }

    @Override
    public T createMap(Map<T, T> map) {
        return this.delegate.createMap(map);
    }

    @Override
    public T createMap(Stream<Pair<T, T>> map) {
        return this.delegate.createMap(map);
    }

    @Override
    public DataResult<MapLike<T>> getMap(T input) {
        return this.delegate.getMap(input);
    }

    @Override
    public DataResult<Stream<T>> getStream(T input) {
        return this.delegate.getStream(input);
    }

    @Override
    public DataResult<Consumer<Consumer<T>>> getList(T input) {
        return this.delegate.getList(input);
    }

    @Override
    public T createList(Stream<T> input) {
        return this.delegate.createList(input);
    }

    @Override
    public DataResult<ByteBuffer> getByteBuffer(T input) {
        return this.delegate.getByteBuffer(input);
    }

    @Override
    public T createByteList(ByteBuffer input) {
        return this.delegate.createByteList(input);
    }

    @Override
    public DataResult<IntStream> getIntStream(T input) {
        return this.delegate.getIntStream(input);
    }

    @Override
    public T createIntList(IntStream input) {
        return this.delegate.createIntList(input);
    }

    @Override
    public DataResult<LongStream> getLongStream(T input) {
        return this.delegate.getLongStream(input);
    }

    @Override
    public T createLongList(LongStream input) {
        return this.delegate.createLongList(input);
    }

    @Override
    public T remove(T input, String key) {
        return this.delegate.remove(input, key);
    }

    @Override
    public boolean compressMaps() {
        return this.delegate.compressMaps();
    }

    @Override
    public ListBuilder<T> listBuilder() {
        return new Builder<>(this);
    }

    @Override
    public RecordBuilder<T> mapBuilder() {
        return new MapBuilder<>(this);
    }
}
