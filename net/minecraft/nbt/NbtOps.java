package net.minecraft.nbt;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.RecordBuilder.AbstractStringBuilder;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import javax.annotation.Nullable;

public class NbtOps implements DynamicOps<Tag> {
    public static final NbtOps INSTANCE = new NbtOps();
    private static final String WRAPPER_MARKER = "";

    protected NbtOps() {
    }

    public Tag empty() {
        return EndTag.INSTANCE;
    }

    public <U> U convertTo(DynamicOps<U> ops, Tag tag) {
        return (U)(switch (tag.getId()) {
            case 0 -> (Object)ops.empty();
            case 1 -> (Object)ops.createByte(((NumericTag)tag).getAsByte());
            case 2 -> (Object)ops.createShort(((NumericTag)tag).getAsShort());
            case 3 -> (Object)ops.createInt(((NumericTag)tag).getAsInt());
            case 4 -> (Object)ops.createLong(((NumericTag)tag).getAsLong());
            case 5 -> (Object)ops.createFloat(((NumericTag)tag).getAsFloat());
            case 6 -> (Object)ops.createDouble(((NumericTag)tag).getAsDouble());
            case 7 -> (Object)ops.createByteList(ByteBuffer.wrap(((ByteArrayTag)tag).getAsByteArray()));
            case 8 -> (Object)ops.createString(tag.getAsString());
            case 9 -> (Object)this.convertList(ops, tag);
            case 10 -> (Object)this.convertMap(ops, tag);
            case 11 -> (Object)ops.createIntList(Arrays.stream(((IntArrayTag)tag).getAsIntArray()));
            case 12 -> (Object)ops.createLongList(Arrays.stream(((LongArrayTag)tag).getAsLongArray()));
            default -> throw new IllegalStateException("Unknown tag type: " + tag);
        });
    }

    public DataResult<Number> getNumberValue(Tag tag) {
        return tag instanceof NumericTag numerictag ? DataResult.success(numerictag.getAsNumber()) : DataResult.error(() -> "Not a number");
    }

    public Tag createNumeric(Number data) {
        return DoubleTag.valueOf(data.doubleValue());
    }

    public Tag createByte(byte data) {
        return ByteTag.valueOf(data);
    }

    public Tag createShort(short data) {
        return ShortTag.valueOf(data);
    }

    public Tag createInt(int data) {
        return IntTag.valueOf(data);
    }

    public Tag createLong(long data) {
        return LongTag.valueOf(data);
    }

    public Tag createFloat(float data) {
        return FloatTag.valueOf(data);
    }

    public Tag createDouble(double data) {
        return DoubleTag.valueOf(data);
    }

    public Tag createBoolean(boolean data) {
        return ByteTag.valueOf(data);
    }

    public DataResult<String> getStringValue(Tag tag) {
        return tag instanceof StringTag stringtag ? DataResult.success(stringtag.getAsString()) : DataResult.error(() -> "Not a string");
    }

    public Tag createString(String data) {
        return StringTag.valueOf(data);
    }

    public DataResult<Tag> mergeToList(Tag list, Tag tag) {
        return createCollector(list)
            .map(p_248053_ -> DataResult.success(p_248053_.accept(tag).result()))
            .orElseGet(() -> DataResult.error(() -> "mergeToList called with not a list: " + list, list));
    }

    public DataResult<Tag> mergeToList(Tag list, List<Tag> tags) {
        return createCollector(list)
            .map(p_248048_ -> DataResult.success(p_248048_.acceptAll(tags).result()))
            .orElseGet(() -> DataResult.error(() -> "mergeToList called with not a list: " + list, list));
    }

    public DataResult<Tag> mergeToMap(Tag map, Tag key, Tag value) {
        if (!(map instanceof CompoundTag) && !(map instanceof EndTag)) {
            return DataResult.error(() -> "mergeToMap called with not a map: " + map, map);
        } else if (!(key instanceof StringTag)) {
            return DataResult.error(() -> "key is not a string: " + key, map);
        } else {
            CompoundTag compoundtag = map instanceof CompoundTag compoundtag1 ? compoundtag1.shallowCopy() : new CompoundTag();
            compoundtag.put(key.getAsString(), value);
            return DataResult.success(compoundtag);
        }
    }

    public DataResult<Tag> mergeToMap(Tag map, MapLike<Tag> otherMap) {
        if (!(map instanceof CompoundTag) && !(map instanceof EndTag)) {
            return DataResult.error(() -> "mergeToMap called with not a map: " + map, map);
        } else {
            CompoundTag compoundtag = map instanceof CompoundTag compoundtag1 ? compoundtag1.shallowCopy() : new CompoundTag();
            List<Tag> list = new ArrayList<>();
            otherMap.entries().forEach(p_128994_ -> {
                Tag tag = p_128994_.getFirst();
                if (!(tag instanceof StringTag)) {
                    list.add(tag);
                } else {
                    compoundtag.put(tag.getAsString(), p_128994_.getSecond());
                }
            });
            return !list.isEmpty() ? DataResult.error(() -> "some keys are not strings: " + list, compoundtag) : DataResult.success(compoundtag);
        }
    }

    public DataResult<Tag> mergeToMap(Tag p_341945_, Map<Tag, Tag> p_341920_) {
        if (!(p_341945_ instanceof CompoundTag) && !(p_341945_ instanceof EndTag)) {
            return DataResult.error(() -> "mergeToMap called with not a map: " + p_341945_, p_341945_);
        } else {
            CompoundTag compoundtag = p_341945_ instanceof CompoundTag compoundtag1 ? compoundtag1.shallowCopy() : new CompoundTag();
            List<Tag> list = new ArrayList<>();

            for (Entry<Tag, Tag> entry : p_341920_.entrySet()) {
                Tag tag = entry.getKey();
                if (tag instanceof StringTag) {
                    compoundtag.put(tag.getAsString(), entry.getValue());
                } else {
                    list.add(tag);
                }
            }

            return !list.isEmpty() ? DataResult.error(() -> "some keys are not strings: " + list, compoundtag) : DataResult.success(compoundtag);
        }
    }

    public DataResult<Stream<Pair<Tag, Tag>>> getMapValues(Tag map) {
        return map instanceof CompoundTag compoundtag
            ? DataResult.success(compoundtag.entrySet().stream().map(p_341872_ -> (Pair<Tag, Tag>)Pair.of(this.createString(p_341872_.getKey()), p_341872_.getValue())))
            : DataResult.error(() -> "Not a map: " + map);
    }

    public DataResult<Consumer<BiConsumer<Tag, Tag>>> getMapEntries(Tag map) {
        return map instanceof CompoundTag compoundtag ? DataResult.success(p_341867_ -> {
            for (Entry<String, Tag> entry : compoundtag.entrySet()) {
                p_341867_.accept(this.createString(entry.getKey()), entry.getValue());
            }
        }) : DataResult.error(() -> "Not a map: " + map);
    }

    public DataResult<MapLike<Tag>> getMap(Tag map) {
        return map instanceof CompoundTag compoundtag ? DataResult.success(new MapLike<Tag>() {
            @Nullable
            public Tag get(Tag p_129174_) {
                return compoundtag.get(p_129174_.getAsString());
            }

            @Nullable
            public Tag get(String p_129169_) {
                return compoundtag.get(p_129169_);
            }

            @Override
            public Stream<Pair<Tag, Tag>> entries() {
                return compoundtag.entrySet().stream().map(p_341873_ -> Pair.of(NbtOps.this.createString(p_341873_.getKey()), p_341873_.getValue()));
            }

            @Override
            public String toString() {
                return "MapLike[" + compoundtag + "]";
            }
        }) : DataResult.error(() -> "Not a map: " + map);
    }

    public Tag createMap(Stream<Pair<Tag, Tag>> data) {
        CompoundTag compoundtag = new CompoundTag();
        data.forEach(p_129018_ -> compoundtag.put(p_129018_.getFirst().getAsString(), p_129018_.getSecond()));
        return compoundtag;
    }

    private static Tag tryUnwrap(CompoundTag p_tag) {
        if (p_tag.size() == 1) {
            Tag tag = p_tag.get("");
            if (tag != null) {
                return tag;
            }
        }

        return p_tag;
    }

    public DataResult<Stream<Tag>> getStream(Tag tag) {
        if (tag instanceof ListTag listtag) {
            return listtag.getElementType() == 10
                ? DataResult.success(listtag.stream().map(p_248049_ -> tryUnwrap((CompoundTag)p_248049_)))
                : DataResult.success(listtag.stream());
        } else {
            return tag instanceof CollectionTag<?> collectiontag
                ? DataResult.success(collectiontag.stream().map(p_129158_ -> p_129158_))
                : DataResult.error(() -> "Not a list");
        }
    }

    public DataResult<Consumer<Consumer<Tag>>> getList(Tag p_tag) {
        if (p_tag instanceof ListTag listtag) {
            return listtag.getElementType() == 10 ? DataResult.success(p_341869_ -> {
                for (Tag tag : listtag) {
                    p_341869_.accept(tryUnwrap((CompoundTag)tag));
                }
            }) : DataResult.success(listtag::forEach);
        } else {
            return p_tag instanceof CollectionTag<?> collectiontag
                ? DataResult.success(collectiontag::forEach)
                : DataResult.error(() -> "Not a list: " + p_tag);
        }
    }

    public DataResult<ByteBuffer> getByteBuffer(Tag tag) {
        return tag instanceof ByteArrayTag bytearraytag
            ? DataResult.success(ByteBuffer.wrap(bytearraytag.getAsByteArray()))
            : DynamicOps.super.getByteBuffer(tag);
    }

    public Tag createByteList(ByteBuffer data) {
        ByteBuffer bytebuffer = data.duplicate().clear();
        byte[] abyte = new byte[data.capacity()];
        bytebuffer.get(0, abyte, 0, abyte.length);
        return new ByteArrayTag(abyte);
    }

    public DataResult<IntStream> getIntStream(Tag tag) {
        return tag instanceof IntArrayTag intarraytag
            ? DataResult.success(Arrays.stream(intarraytag.getAsIntArray()))
            : DynamicOps.super.getIntStream(tag);
    }

    public Tag createIntList(IntStream data) {
        return new IntArrayTag(data.toArray());
    }

    public DataResult<LongStream> getLongStream(Tag tag) {
        return tag instanceof LongArrayTag longarraytag
            ? DataResult.success(Arrays.stream(longarraytag.getAsLongArray()))
            : DynamicOps.super.getLongStream(tag);
    }

    public Tag createLongList(LongStream data) {
        return new LongArrayTag(data.toArray());
    }

    public Tag createList(Stream<Tag> data) {
        return NbtOps.InitialListCollector.INSTANCE.acceptAll(data).result();
    }

    public Tag remove(Tag map, String removeKey) {
        if (map instanceof CompoundTag compoundtag) {
            CompoundTag compoundtag1 = compoundtag.shallowCopy();
            compoundtag1.remove(removeKey);
            return compoundtag1;
        } else {
            return map;
        }
    }

    @Override
    public String toString() {
        return "NBT";
    }

    @Override
    public RecordBuilder<Tag> mapBuilder() {
        return new NbtOps.NbtRecordBuilder();
    }

    private static Optional<NbtOps.ListCollector> createCollector(Tag tag) {
        if (tag instanceof EndTag) {
            return Optional.of(NbtOps.InitialListCollector.INSTANCE);
        } else {
            if (tag instanceof CollectionTag<?> collectiontag) {
                if (collectiontag.isEmpty()) {
                    return Optional.of(NbtOps.InitialListCollector.INSTANCE);
                }

                if (collectiontag instanceof ListTag listtag) {
                    return switch (listtag.getElementType()) {
                        case 0 -> Optional.of(NbtOps.InitialListCollector.INSTANCE);
                        case 10 -> Optional.of(new NbtOps.HeterogenousListCollector(listtag));
                        default -> Optional.of(new NbtOps.HomogenousListCollector(listtag));
                    };
                }

                if (collectiontag instanceof ByteArrayTag bytearraytag) {
                    return Optional.of(new NbtOps.ByteListCollector(bytearraytag.getAsByteArray()));
                }

                if (collectiontag instanceof IntArrayTag intarraytag) {
                    return Optional.of(new NbtOps.IntListCollector(intarraytag.getAsIntArray()));
                }

                if (collectiontag instanceof LongArrayTag longarraytag) {
                    return Optional.of(new NbtOps.LongListCollector(longarraytag.getAsLongArray()));
                }
            }

            return Optional.empty();
        }
    }

    static class ByteListCollector implements NbtOps.ListCollector {
        private final ByteArrayList values = new ByteArrayList();

        public ByteListCollector(byte value) {
            this.values.add(value);
        }

        public ByteListCollector(byte[] values) {
            this.values.addElements(0, values);
        }

        @Override
        public NbtOps.ListCollector accept(Tag tag) {
            if (tag instanceof ByteTag bytetag) {
                this.values.add(bytetag.getAsByte());
                return this;
            } else {
                return new NbtOps.HeterogenousListCollector(this.values).accept(tag);
            }
        }

        @Override
        public Tag result() {
            return new ByteArrayTag(this.values.toByteArray());
        }
    }

    static class HeterogenousListCollector implements NbtOps.ListCollector {
        private final ListTag result = new ListTag();

        public HeterogenousListCollector() {
        }

        public HeterogenousListCollector(Collection<Tag> tags) {
            this.result.addAll(tags);
        }

        public HeterogenousListCollector(IntArrayList data) {
            data.forEach(p_249166_ -> this.result.add(wrapElement(IntTag.valueOf(p_249166_))));
        }

        public HeterogenousListCollector(ByteArrayList data) {
            data.forEach(p_249160_ -> this.result.add(wrapElement(ByteTag.valueOf(p_249160_))));
        }

        public HeterogenousListCollector(LongArrayList data) {
            data.forEach(p_249754_ -> this.result.add(wrapElement(LongTag.valueOf(p_249754_))));
        }

        private static boolean isWrapper(CompoundTag tag) {
            return tag.size() == 1 && tag.contains("");
        }

        private static Tag wrapIfNeeded(Tag tag) {
            if (tag instanceof CompoundTag compoundtag && !isWrapper(compoundtag)) {
                return compoundtag;
            }

            return wrapElement(tag);
        }

        private static CompoundTag wrapElement(Tag tag) {
            CompoundTag compoundtag = new CompoundTag();
            compoundtag.put("", tag);
            return compoundtag;
        }

        @Override
        public NbtOps.ListCollector accept(Tag tag) {
            this.result.add(wrapIfNeeded(tag));
            return this;
        }

        @Override
        public Tag result() {
            return this.result;
        }
    }

    static class HomogenousListCollector implements NbtOps.ListCollector {
        private final ListTag result = new ListTag();

        HomogenousListCollector(Tag value) {
            this.result.add(value);
        }

        HomogenousListCollector(ListTag values) {
            this.result.addAll(values);
        }

        @Override
        public NbtOps.ListCollector accept(Tag tag) {
            if (tag.getId() != this.result.getElementType()) {
                return new NbtOps.HeterogenousListCollector().acceptAll(this.result).accept(tag);
            } else {
                this.result.add(tag);
                return this;
            }
        }

        @Override
        public Tag result() {
            return this.result;
        }
    }

    static class InitialListCollector implements NbtOps.ListCollector {
        public static final NbtOps.InitialListCollector INSTANCE = new NbtOps.InitialListCollector();

        private InitialListCollector() {
        }

        @Override
        public NbtOps.ListCollector accept(Tag p_251635_) {
            if (p_251635_ instanceof CompoundTag compoundtag) {
                return new NbtOps.HeterogenousListCollector().accept(compoundtag);
            } else if (p_251635_ instanceof ByteTag bytetag) {
                return new NbtOps.ByteListCollector(bytetag.getAsByte());
            } else if (p_251635_ instanceof IntTag inttag) {
                return new NbtOps.IntListCollector(inttag.getAsInt());
            } else {
                return (NbtOps.ListCollector)(p_251635_ instanceof LongTag longtag
                    ? new NbtOps.LongListCollector(longtag.getAsLong())
                    : new NbtOps.HomogenousListCollector(p_251635_));
            }
        }

        @Override
        public Tag result() {
            return new ListTag();
        }
    }

    static class IntListCollector implements NbtOps.ListCollector {
        private final IntArrayList values = new IntArrayList();

        public IntListCollector(int value) {
            this.values.add(value);
        }

        public IntListCollector(int[] values) {
            this.values.addElements(0, values);
        }

        @Override
        public NbtOps.ListCollector accept(Tag tag) {
            if (tag instanceof IntTag inttag) {
                this.values.add(inttag.getAsInt());
                return this;
            } else {
                return new NbtOps.HeterogenousListCollector(this.values).accept(tag);
            }
        }

        @Override
        public Tag result() {
            return new IntArrayTag(this.values.toIntArray());
        }
    }

    interface ListCollector {
        NbtOps.ListCollector accept(Tag tag);

        default NbtOps.ListCollector acceptAll(Iterable<Tag> tags) {
            NbtOps.ListCollector nbtops$listcollector = this;

            for (Tag tag : tags) {
                nbtops$listcollector = nbtops$listcollector.accept(tag);
            }

            return nbtops$listcollector;
        }

        default NbtOps.ListCollector acceptAll(Stream<Tag> tags) {
            return this.acceptAll(tags::iterator);
        }

        Tag result();
    }

    static class LongListCollector implements NbtOps.ListCollector {
        private final LongArrayList values = new LongArrayList();

        public LongListCollector(long value) {
            this.values.add(value);
        }

        public LongListCollector(long[] values) {
            this.values.addElements(0, values);
        }

        @Override
        public NbtOps.ListCollector accept(Tag tag) {
            if (tag instanceof LongTag longtag) {
                this.values.add(longtag.getAsLong());
                return this;
            } else {
                return new NbtOps.HeterogenousListCollector(this.values).accept(tag);
            }
        }

        @Override
        public Tag result() {
            return new LongArrayTag(this.values.toLongArray());
        }
    }

    class NbtRecordBuilder extends AbstractStringBuilder<Tag, CompoundTag> {
        protected NbtRecordBuilder() {
            super(NbtOps.this);
        }

        protected CompoundTag initBuilder() {
            return new CompoundTag();
        }

        protected CompoundTag append(String key, Tag value, CompoundTag tag) {
            tag.put(key, value);
            return tag;
        }

        protected DataResult<Tag> build(CompoundTag p_129190_, Tag p_129191_) {
            if (p_129191_ == null || p_129191_ == EndTag.INSTANCE) {
                return DataResult.success(p_129190_);
            } else if (!(p_129191_ instanceof CompoundTag compoundtag)) {
                return DataResult.error(() -> "mergeToMap called with not a map: " + p_129191_, p_129191_);
            } else {
                CompoundTag compoundtag1 = compoundtag.shallowCopy();

                for (Entry<String, Tag> entry : p_129190_.entrySet()) {
                    compoundtag1.put(entry.getKey(), entry.getValue());
                }

                return DataResult.success(compoundtag1);
            }
        }
    }
}
