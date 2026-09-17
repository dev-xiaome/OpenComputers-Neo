package net.minecraft.network;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import io.netty.util.ByteProcessor;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.charset.Charset;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.codec.StreamEncoder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Crypt;
import net.minecraft.util.CryptException;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class FriendlyByteBuf extends ByteBuf implements net.neoforged.neoforge.common.extensions.IFriendlyByteBufExtension {
    public static final int DEFAULT_NBT_QUOTA = 2097152;
    private final ByteBuf source;
    public static final short MAX_STRING_LENGTH = 32767;
    public static final int MAX_COMPONENT_STRING_LENGTH = 262144;
    private static final int PUBLIC_KEY_SIZE = 256;
    private static final int MAX_PUBLIC_KEY_HEADER_SIZE = 256;
    private static final int MAX_PUBLIC_KEY_LENGTH = 512;
    private static final Gson GSON = new Gson();

    public FriendlyByteBuf(ByteBuf source) {
        this.source = source;
    }

    @Deprecated
    public <T> T readWithCodecTrusted(DynamicOps<Tag> ops, Codec<T> codec) {
        return this.readWithCodec(ops, codec, NbtAccounter.unlimitedHeap());
    }

    @Deprecated
    public <T> T readWithCodec(DynamicOps<Tag> ops, Codec<T> codec, NbtAccounter nbtAccounter) {
        Tag tag = this.readNbt(nbtAccounter);
        return codec.parse(ops, tag).getOrThrow(p_339398_ -> new DecoderException("Failed to decode: " + p_339398_ + " " + tag));
    }

    @Deprecated
    public <T> FriendlyByteBuf writeWithCodec(DynamicOps<Tag> ops, Codec<T> codec, T value) {
        Tag tag = codec.encodeStart(ops, value).getOrThrow(p_339400_ -> new EncoderException("Failed to encode: " + p_339400_ + " " + value));
        this.writeNbt(tag);
        return this;
    }

    public <T> T readJsonWithCodec(Codec<T> codec) {
        JsonElement jsonelement = GsonHelper.fromJson(GSON, this.readUtf(), JsonElement.class);
        DataResult<T> dataresult = codec.parse(JsonOps.INSTANCE, jsonelement);
        return dataresult.getOrThrow(p_272382_ -> new DecoderException("Failed to decode json: " + p_272382_));
    }

    public <T> void writeJsonWithCodec(Codec<T> codec, T value) {
        DataResult<JsonElement> dataresult = codec.encodeStart(JsonOps.INSTANCE, value);
        this.writeUtf(GSON.toJson(dataresult.getOrThrow(p_339402_ -> new EncoderException("Failed to encode: " + p_339402_ + " " + value))));
    }

    public static <T> IntFunction<T> limitValue(IntFunction<T> function, int limit) {
        return p_182686_ -> {
            if (p_182686_ > limit) {
                throw new DecoderException("Value " + p_182686_ + " is larger than limit " + limit);
            } else {
                return function.apply(p_182686_);
            }
        };
    }

    public <T, C extends Collection<T>> C readCollection(IntFunction<C> collectionFactory, StreamDecoder<? super FriendlyByteBuf, T> elementReader) {
        int i = this.readVarInt();
        C c = (C)collectionFactory.apply(Math.min(i, net.minecraft.network.codec.ByteBufCodecs.MAX_INITIAL_COLLECTION_SIZE));

        for (int j = 0; j < i; j++) {
            c.add(elementReader.decode(this));
        }

        return c;
    }

    public <T> void writeCollection(Collection<T> collection, StreamEncoder<? super FriendlyByteBuf, T> elementWriter) {
        this.writeVarInt(collection.size());

        for (T t : collection) {
            elementWriter.encode(this, t);
        }
    }

    public <T> List<T> readList(StreamDecoder<? super FriendlyByteBuf, T> elementReader) {
        return this.readCollection(Lists::newArrayListWithCapacity, elementReader);
    }

    public IntList readIntIdList() {
        int i = this.readVarInt();
        IntList intlist = new IntArrayList();

        for (int j = 0; j < i; j++) {
            intlist.add(this.readVarInt());
        }

        return intlist;
    }

    /**
     * Write an IntList to this buffer. Every element is encoded as a VarInt.
     *
     * @see #readIntIdList
     */
    public void writeIntIdList(IntList itIdList) {
        this.writeVarInt(itIdList.size());
        itIdList.forEach(this::writeVarInt);
    }

    public <K, V, M extends Map<K, V>> M readMap(
        IntFunction<M> mapFactory, StreamDecoder<? super FriendlyByteBuf, K> keyReader, StreamDecoder<? super FriendlyByteBuf, V> valueReader
    ) {
        int i = this.readVarInt();
        M m = (M)mapFactory.apply(Math.min(i, net.minecraft.network.codec.ByteBufCodecs.MAX_INITIAL_COLLECTION_SIZE));

        for (int j = 0; j < i; j++) {
            K k = keyReader.decode(this);
            V v = valueReader.decode(this);
            m.put(k, v);
        }

        return m;
    }

    public <K, V> Map<K, V> readMap(StreamDecoder<? super FriendlyByteBuf, K> keyReader, StreamDecoder<? super FriendlyByteBuf, V> valueReader) {
        return this.readMap(Maps::newHashMapWithExpectedSize, keyReader, valueReader);
    }

    public <K, V> void writeMap(Map<K, V> map, StreamEncoder<? super FriendlyByteBuf, K> keyWriter, StreamEncoder<? super FriendlyByteBuf, V> valueWriter) {
        this.writeVarInt(map.size());
        map.forEach((p_319534_, p_319535_) -> {
            keyWriter.encode(this, (K)p_319534_);
            valueWriter.encode(this, (V)p_319535_);
        });
    }

    /**
     * Read a VarInt N from this buffer, then reads N values by calling {@code reader}.
     */
    public void readWithCount(Consumer<FriendlyByteBuf> reader) {
        int i = this.readVarInt();

        for (int j = 0; j < i; j++) {
            reader.accept(this);
        }
    }

    public <E extends Enum<E>> void writeEnumSet(EnumSet<E> enumSet, Class<E> enumClass) {
        E[] ae = (E[])enumClass.getEnumConstants();
        BitSet bitset = new BitSet(ae.length);

        for (int i = 0; i < ae.length; i++) {
            bitset.set(i, enumSet.contains(ae[i]));
        }

        this.writeFixedBitSet(bitset, ae.length);
    }

    public <E extends Enum<E>> EnumSet<E> readEnumSet(Class<E> enumClass) {
        E[] ae = (E[])enumClass.getEnumConstants();
        BitSet bitset = this.readFixedBitSet(ae.length);
        EnumSet<E> enumset = EnumSet.noneOf(enumClass);

        for (int i = 0; i < ae.length; i++) {
            if (bitset.get(i)) {
                enumset.add(ae[i]);
            }
        }

        return enumset;
    }

    public <T> void writeOptional(Optional<T> optional, StreamEncoder<? super FriendlyByteBuf, T> writer) {
        if (optional.isPresent()) {
            this.writeBoolean(true);
            writer.encode(this, optional.get());
        } else {
            this.writeBoolean(false);
        }
    }

    public <T> Optional<T> readOptional(StreamDecoder<? super FriendlyByteBuf, T> reader) {
        return this.readBoolean() ? Optional.of(reader.decode(this)) : Optional.empty();
    }

    @Nullable
    public <T> T readNullable(StreamDecoder<? super FriendlyByteBuf, T> reader) {
        return readNullable(this, reader);
    }

    @Nullable
    public static <T, B extends ByteBuf> T readNullable(B buffer, StreamDecoder<? super B, T> reader) {
        return buffer.readBoolean() ? reader.decode(buffer) : null;
    }

    public <T> void writeNullable(@Nullable T value, StreamEncoder<? super FriendlyByteBuf, T> writer) {
        writeNullable(this, value, writer);
    }

    public static <T, B extends ByteBuf> void writeNullable(B buffer, @Nullable T value, StreamEncoder<? super B, T> writer) {
        if (value != null) {
            buffer.writeBoolean(true);
            writer.encode(buffer, value);
        } else {
            buffer.writeBoolean(false);
        }
    }

    public byte[] readByteArray() {
        return readByteArray(this);
    }

    public static byte[] readByteArray(ByteBuf buffer) {
        return readByteArray(buffer, buffer.readableBytes());
    }

    public FriendlyByteBuf writeByteArray(byte[] array) {
        writeByteArray(this, array);
        return this;
    }

    public static void writeByteArray(ByteBuf buffer, byte[] array) {
        VarInt.write(buffer, array.length);
        buffer.writeBytes(array);
    }

    public byte[] readByteArray(int maxLength) {
        return readByteArray(this, maxLength);
    }

    public static byte[] readByteArray(ByteBuf buffer, int maxSize) {
        int i = VarInt.read(buffer);
        if (i > maxSize) {
            throw new DecoderException("ByteArray with size " + i + " is bigger than allowed " + maxSize);
        } else {
            byte[] abyte = new byte[i];
            buffer.readBytes(abyte);
            return abyte;
        }
    }

    /**
     * Writes an array of VarInts to the buffer, prefixed by the length of the array (as a VarInt).
     *
     * @see #readVarIntArray
     */
    public FriendlyByteBuf writeVarIntArray(int[] array) {
        this.writeVarInt(array.length);

        for (int i : array) {
            this.writeVarInt(i);
        }

        return this;
    }

    public int[] readVarIntArray() {
        return this.readVarIntArray(this.readableBytes());
    }

    /**
     * Reads an array of VarInts with a maximum length from this buffer.
     *
     * @see #writeVarIntArray
     */
    public int[] readVarIntArray(int maxLength) {
        int i = this.readVarInt();
        if (i > maxLength) {
            throw new DecoderException("VarIntArray with size " + i + " is bigger than allowed " + maxLength);
        } else {
            int[] aint = new int[i];

            for (int j = 0; j < aint.length; j++) {
                aint[j] = this.readVarInt();
            }

            return aint;
        }
    }

    /**
     * Writes an array of longs to the buffer, prefixed by the length of the array (as a VarInt).
     *
     * @see #readLongArray
     */
    public FriendlyByteBuf writeLongArray(long[] array) {
        this.writeVarInt(array.length);

        for (long i : array) {
            this.writeLong(i);
        }

        return this;
    }

    public long[] readLongArray() {
        return this.readLongArray(null);
    }

    /**
     * Reads a length-prefixed array of longs from the buffer.
     * Will try to use the given long[] if possible. Note that if an array with the correct size is given, maxLength is ignored.
     */
    public long[] readLongArray(@Nullable long[] array) {
        return this.readLongArray(array, this.readableBytes() / 8);
    }

    /**
     * Reads a length-prefixed array of longs with a maximum length from the buffer.
     * Will try to use the given long[] if possible. Note that if an array with the correct size is given, maxLength is ignored.
     */
    public long[] readLongArray(@Nullable long[] array, int maxLength) {
        int i = this.readVarInt();
        if (array == null || array.length != i) {
            if (i > maxLength) {
                throw new DecoderException("LongArray with size " + i + " is bigger than allowed " + maxLength);
            }

            array = new long[i];
        }

        for (int j = 0; j < array.length; j++) {
            array[j] = this.readLong();
        }

        return array;
    }

    public BlockPos readBlockPos() {
        return readBlockPos(this);
    }

    public static BlockPos readBlockPos(ByteBuf buffer) {
        return BlockPos.of(buffer.readLong());
    }

    /**
     * Writes a BlockPos encoded as a long to the buffer.
     *
     * @see #readBlockPos
     */
    public FriendlyByteBuf writeBlockPos(BlockPos pos) {
        writeBlockPos(this, pos);
        return this;
    }

    public static void writeBlockPos(ByteBuf buffer, BlockPos pos) {
        buffer.writeLong(pos.asLong());
    }

    public ChunkPos readChunkPos() {
        return new ChunkPos(this.readLong());
    }

    /**
     * Writes a ChunkPos encoded as a long to the buffer.
     *
     * @see #readChunkPos
     */
    public FriendlyByteBuf writeChunkPos(ChunkPos chunkPos) {
        this.writeLong(chunkPos.toLong());
        return this;
    }

    public SectionPos readSectionPos() {
        return SectionPos.of(this.readLong());
    }

    /**
     * Writes a SectionPos encoded as a long to the buffer.
     *
     * @see #readSectionPos
     */
    public FriendlyByteBuf writeSectionPos(SectionPos sectionPos) {
        this.writeLong(sectionPos.asLong());
        return this;
    }

    public GlobalPos readGlobalPos() {
        ResourceKey<Level> resourcekey = this.readResourceKey(Registries.DIMENSION);
        BlockPos blockpos = this.readBlockPos();
        return GlobalPos.of(resourcekey, blockpos);
    }

    public void writeGlobalPos(GlobalPos pos) {
        this.writeResourceKey(pos.dimension());
        this.writeBlockPos(pos.pos());
    }

    public Vector3f readVector3f() {
        return readVector3f(this);
    }

    public static Vector3f readVector3f(ByteBuf buffer) {
        return new Vector3f(buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
    }

    public void writeVector3f(Vector3f vector3f) {
        writeVector3f(this, vector3f);
    }

    public static void writeVector3f(ByteBuf buffer, Vector3f vector3f) {
        buffer.writeFloat(vector3f.x());
        buffer.writeFloat(vector3f.y());
        buffer.writeFloat(vector3f.z());
    }

    public Quaternionf readQuaternion() {
        return readQuaternion(this);
    }

    public static Quaternionf readQuaternion(ByteBuf buffer) {
        return new Quaternionf(buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
    }

    public void writeQuaternion(Quaternionf quaternion) {
        writeQuaternion(this, quaternion);
    }

    public static void writeQuaternion(ByteBuf buffer, Quaternionf quaternion) {
        buffer.writeFloat(quaternion.x);
        buffer.writeFloat(quaternion.y);
        buffer.writeFloat(quaternion.z);
        buffer.writeFloat(quaternion.w);
    }

    public Vec3 readVec3() {
        return new Vec3(this.readDouble(), this.readDouble(), this.readDouble());
    }

    public void writeVec3(Vec3 vec3) {
        this.writeDouble(vec3.x());
        this.writeDouble(vec3.y());
        this.writeDouble(vec3.z());
    }

    /**
     * Reads an enum of the given type T using the ordinal encoded as a VarInt from the buffer.
     *
     * @see #writeEnum
     */
    public <T extends Enum<T>> T readEnum(Class<T> enumClass) {
        return enumClass.getEnumConstants()[this.readVarInt()];
    }

    /**
     * Writes an enum of the given type T using the ordinal encoded as a VarInt to the buffer.
     *
     * @see #readEnum
     */
    public FriendlyByteBuf writeEnum(Enum<?> value) {
        return this.writeVarInt(value.ordinal());
    }

    public <T> T readById(IntFunction<T> idLookuo) {
        int i = this.readVarInt();
        return idLookuo.apply(i);
    }

    public <T> FriendlyByteBuf writeById(ToIntFunction<T> idGetter, T value) {
        int i = idGetter.applyAsInt(value);
        return this.writeVarInt(i);
    }

    public int readVarInt() {
        return VarInt.read(this.source);
    }

    public long readVarLong() {
        return VarLong.read(this.source);
    }

    /**
     * Writes a UUID encoded as two longs to this buffer.
     *
     * @see #readUUID
     */
    public FriendlyByteBuf writeUUID(UUID uuid) {
        writeUUID(this, uuid);
        return this;
    }

    public static void writeUUID(ByteBuf buffer, UUID id) {
        buffer.writeLong(id.getMostSignificantBits());
        buffer.writeLong(id.getLeastSignificantBits());
    }

    public UUID readUUID() {
        return readUUID(this);
    }

    public static UUID readUUID(ByteBuf buffer) {
        return new UUID(buffer.readLong(), buffer.readLong());
    }

    /**
     * Writes a compressed int to the buffer. The smallest number of bytes to fit the passed int will be written. Of each such byte only 7 bits will be used to describe the actual value since its most significant bit dictates whether the next byte is part of that same int. Micro-optimization for int values that are usually small.
     */
    public FriendlyByteBuf writeVarInt(int input) {
        VarInt.write(this.source, input);
        return this;
    }

    /**
     * Writes a compressed long to the buffer. The smallest number of bytes to fit the passed long will be written. Of each such byte only 7 bits will be used to describe the actual value since its most significant bit dictates whether the next byte is part of that same long. Micro-optimization for long values that are usually small.
     */
    public FriendlyByteBuf writeVarLong(long value) {
        VarLong.write(this.source, value);
        return this;
    }

    public FriendlyByteBuf writeNbt(@Nullable Tag tag) {
        writeNbt(this, tag);
        return this;
    }

    public static void writeNbt(ByteBuf buffer, @Nullable Tag nbt) {
        if (nbt == null) {
            nbt = EndTag.INSTANCE;
        }

        try {
            NbtIo.writeAnyTag(nbt, new ByteBufOutputStream(buffer));
        } catch (IOException ioexception) {
            throw new EncoderException(ioexception);
        }
    }

    @Nullable
    public CompoundTag readNbt() {
        return readNbt(this);
    }

    @Nullable
    public static CompoundTag readNbt(ByteBuf buffer) {
        Tag tag = readNbt(buffer, NbtAccounter.create(2097152L));
        if (tag != null && !(tag instanceof CompoundTag)) {
            throw new DecoderException("Not a compound tag: " + tag);
        } else {
            return (CompoundTag)tag;
        }
    }

    @Nullable
    public static Tag readNbt(ByteBuf buffer, NbtAccounter nbtAccounter) {
        try {
            Tag tag = NbtIo.readAnyTag(new ByteBufInputStream(buffer), nbtAccounter);
            return tag.getId() == 0 ? null : tag;
        } catch (IOException ioexception) {
            throw new EncoderException(ioexception);
        }
    }

    @Nullable
    public Tag readNbt(NbtAccounter nbtAccounter) {
        return readNbt(this, nbtAccounter);
    }

    public String readUtf() {
        return this.readUtf(32767);
    }

    /**
     * Reads a string with a maximum length from this buffer.
     *
     * @see #writeUtf
     */
    public String readUtf(int maxLength) {
        return Utf8String.read(this.source, maxLength);
    }

    /**
     * Writes a String with a maximum length of {@code Short.MAX_VALUE}.
     *
     * @see #readUtf
     */
    public FriendlyByteBuf writeUtf(String string) {
        return this.writeUtf(string, 32767);
    }

    /**
     * Writes a String with a maximum length.
     *
     * @see #readUtf
     */
    public FriendlyByteBuf writeUtf(String string, int maxLength) {
        Utf8String.write(this.source, string, maxLength);
        return this;
    }

    public ResourceLocation readResourceLocation() {
        return ResourceLocation.parse(this.readUtf(32767));
    }

    /**
     * Write a ResourceLocation using its String representation.
     *
     * @see #readResourceLocation
     */
    public FriendlyByteBuf writeResourceLocation(ResourceLocation resourceLocation) {
        this.writeUtf(resourceLocation.toString());
        return this;
    }

    public <T> ResourceKey<T> readResourceKey(ResourceKey<? extends Registry<T>> registryKey) {
        ResourceLocation resourcelocation = this.readResourceLocation();
        return ResourceKey.create(registryKey, resourcelocation);
    }

    public void writeResourceKey(ResourceKey<?> resourceKey) {
        this.writeResourceLocation(resourceKey.location());
    }

    public <T> ResourceKey<? extends Registry<T>> readRegistryKey() {
        ResourceLocation resourcelocation = this.readResourceLocation();
        return ResourceKey.createRegistryKey(resourcelocation);
    }

    public Date readDate() {
        return new Date(this.readLong());
    }

    /**
     * Write a timestamp as milliseconds since the unix epoch.
     *
     * @see #readDate
     */
    public FriendlyByteBuf writeDate(Date time) {
        this.writeLong(time.getTime());
        return this;
    }

    public Instant readInstant() {
        return Instant.ofEpochMilli(this.readLong());
    }

    public void writeInstant(Instant instant) {
        this.writeLong(instant.toEpochMilli());
    }

    public PublicKey readPublicKey() {
        try {
            return Crypt.byteToPublicKey(this.readByteArray(512));
        } catch (CryptException cryptexception) {
            throw new DecoderException("Malformed public key bytes", cryptexception);
        }
    }

    public FriendlyByteBuf writePublicKey(PublicKey publicKey) {
        this.writeByteArray(publicKey.getEncoded());
        return this;
    }

    public BlockHitResult readBlockHitResult() {
        BlockPos blockpos = this.readBlockPos();
        Direction direction = this.readEnum(Direction.class);
        float f = this.readFloat();
        float f1 = this.readFloat();
        float f2 = this.readFloat();
        boolean flag = this.readBoolean();
        return new BlockHitResult(
            new Vec3((double)blockpos.getX() + (double)f, (double)blockpos.getY() + (double)f1, (double)blockpos.getZ() + (double)f2),
            direction,
            blockpos,
            flag
        );
    }

    /**
     * Write a BlockHitResult.
     *
     * @see #readBlockHitResult
     */
    public void writeBlockHitResult(BlockHitResult result) {
        BlockPos blockpos = result.getBlockPos();
        this.writeBlockPos(blockpos);
        this.writeEnum(result.getDirection());
        Vec3 vec3 = result.getLocation();
        this.writeFloat((float)(vec3.x - (double)blockpos.getX()));
        this.writeFloat((float)(vec3.y - (double)blockpos.getY()));
        this.writeFloat((float)(vec3.z - (double)blockpos.getZ()));
        this.writeBoolean(result.isInside());
    }

    public BitSet readBitSet() {
        return BitSet.valueOf(this.readLongArray());
    }

    /**
     * Write a BitSet as a long[].
     *
     * @see #readBitSet
     */
    public void writeBitSet(BitSet bitSet) {
        this.writeLongArray(bitSet.toLongArray());
    }

    public BitSet readFixedBitSet(int size) {
        byte[] abyte = new byte[Mth.positiveCeilDiv(size, 8)];
        this.readBytes(abyte);
        return BitSet.valueOf(abyte);
    }

    public void writeFixedBitSet(BitSet bitSet, int size) {
        if (bitSet.length() > size) {
            throw new EncoderException("BitSet is larger than expected size (" + bitSet.length() + ">" + size + ")");
        } else {
            byte[] abyte = bitSet.toByteArray();
            this.writeBytes(Arrays.copyOf(abyte, Mth.positiveCeilDiv(size, 8)));
        }
    }

    @Override
    public boolean isContiguous() {
        return this.source.isContiguous();
    }

    @Override
    public int maxFastWritableBytes() {
        return this.source.maxFastWritableBytes();
    }

    @Override
    public int capacity() {
        return this.source.capacity();
    }

    public FriendlyByteBuf capacity(int newCapacity) {
        this.source.capacity(newCapacity);
        return this;
    }

    @Override
    public int maxCapacity() {
        return this.source.maxCapacity();
    }

    @Override
    public ByteBufAllocator alloc() {
        return this.source.alloc();
    }

    @Override
    public ByteOrder order() {
        return this.source.order();
    }

    @Override
    public ByteBuf order(ByteOrder endianness) {
        return this.source.order(endianness);
    }

    @Override
    public ByteBuf unwrap() {
        return this.source;
    }

    @Override
    public boolean isDirect() {
        return this.source.isDirect();
    }

    @Override
    public boolean isReadOnly() {
        return this.source.isReadOnly();
    }

    @Override
    public ByteBuf asReadOnly() {
        return this.source.asReadOnly();
    }

    @Override
    public int readerIndex() {
        return this.source.readerIndex();
    }

    public FriendlyByteBuf readerIndex(int readerIndex) {
        this.source.readerIndex(readerIndex);
        return this;
    }

    @Override
    public int writerIndex() {
        return this.source.writerIndex();
    }

    public FriendlyByteBuf writerIndex(int writerIndex) {
        this.source.writerIndex(writerIndex);
        return this;
    }

    public FriendlyByteBuf setIndex(int readerIndex, int writerIndex) {
        this.source.setIndex(readerIndex, writerIndex);
        return this;
    }

    @Override
    public int readableBytes() {
        return this.source.readableBytes();
    }

    @Override
    public int writableBytes() {
        return this.source.writableBytes();
    }

    @Override
    public int maxWritableBytes() {
        return this.source.maxWritableBytes();
    }

    @Override
    public boolean isReadable() {
        return this.source.isReadable();
    }

    @Override
    public boolean isReadable(int size) {
        return this.source.isReadable(size);
    }

    @Override
    public boolean isWritable() {
        return this.source.isWritable();
    }

    @Override
    public boolean isWritable(int size) {
        return this.source.isWritable(size);
    }

    public FriendlyByteBuf clear() {
        this.source.clear();
        return this;
    }

    public FriendlyByteBuf markReaderIndex() {
        this.source.markReaderIndex();
        return this;
    }

    public FriendlyByteBuf resetReaderIndex() {
        this.source.resetReaderIndex();
        return this;
    }

    public FriendlyByteBuf markWriterIndex() {
        this.source.markWriterIndex();
        return this;
    }

    public FriendlyByteBuf resetWriterIndex() {
        this.source.resetWriterIndex();
        return this;
    }

    public FriendlyByteBuf discardReadBytes() {
        this.source.discardReadBytes();
        return this;
    }

    public FriendlyByteBuf discardSomeReadBytes() {
        this.source.discardSomeReadBytes();
        return this;
    }

    public FriendlyByteBuf ensureWritable(int size) {
        this.source.ensureWritable(size);
        return this;
    }

    @Override
    public int ensureWritable(int size, boolean force) {
        return this.source.ensureWritable(size, force);
    }

    @Override
    public boolean getBoolean(int index) {
        return this.source.getBoolean(index);
    }

    @Override
    public byte getByte(int index) {
        return this.source.getByte(index);
    }

    @Override
    public short getUnsignedByte(int index) {
        return this.source.getUnsignedByte(index);
    }

    @Override
    public short getShort(int index) {
        return this.source.getShort(index);
    }

    @Override
    public short getShortLE(int index) {
        return this.source.getShortLE(index);
    }

    @Override
    public int getUnsignedShort(int index) {
        return this.source.getUnsignedShort(index);
    }

    @Override
    public int getUnsignedShortLE(int index) {
        return this.source.getUnsignedShortLE(index);
    }

    @Override
    public int getMedium(int index) {
        return this.source.getMedium(index);
    }

    @Override
    public int getMediumLE(int index) {
        return this.source.getMediumLE(index);
    }

    @Override
    public int getUnsignedMedium(int index) {
        return this.source.getUnsignedMedium(index);
    }

    @Override
    public int getUnsignedMediumLE(int index) {
        return this.source.getUnsignedMediumLE(index);
    }

    @Override
    public int getInt(int index) {
        return this.source.getInt(index);
    }

    @Override
    public int getIntLE(int index) {
        return this.source.getIntLE(index);
    }

    @Override
    public long getUnsignedInt(int index) {
        return this.source.getUnsignedInt(index);
    }

    @Override
    public long getUnsignedIntLE(int index) {
        return this.source.getUnsignedIntLE(index);
    }

    @Override
    public long getLong(int index) {
        return this.source.getLong(index);
    }

    @Override
    public long getLongLE(int index) {
        return this.source.getLongLE(index);
    }

    @Override
    public char getChar(int index) {
        return this.source.getChar(index);
    }

    @Override
    public float getFloat(int index) {
        return this.source.getFloat(index);
    }

    @Override
    public double getDouble(int index) {
        return this.source.getDouble(index);
    }

    public FriendlyByteBuf getBytes(int index, ByteBuf destination) {
        this.source.getBytes(index, destination);
        return this;
    }

    public FriendlyByteBuf getBytes(int index, ByteBuf destination, int length) {
        this.source.getBytes(index, destination, length);
        return this;
    }

    public FriendlyByteBuf getBytes(int index, ByteBuf destination, int destinationIndex, int length) {
        this.source.getBytes(index, destination, destinationIndex, length);
        return this;
    }

    public FriendlyByteBuf getBytes(int index, byte[] destination) {
        this.source.getBytes(index, destination);
        return this;
    }

    public FriendlyByteBuf getBytes(int index, byte[] destination, int destinationIndex, int length) {
        this.source.getBytes(index, destination, destinationIndex, length);
        return this;
    }

    public FriendlyByteBuf getBytes(int index, ByteBuffer destination) {
        this.source.getBytes(index, destination);
        return this;
    }

    public FriendlyByteBuf getBytes(int index, OutputStream out, int length) throws IOException {
        this.source.getBytes(index, out, length);
        return this;
    }

    @Override
    public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
        return this.source.getBytes(index, out, length);
    }

    @Override
    public int getBytes(int index, FileChannel out, long position, int length) throws IOException {
        return this.source.getBytes(index, out, position, length);
    }

    @Override
    public CharSequence getCharSequence(int index, int length, Charset charset) {
        return this.source.getCharSequence(index, length, charset);
    }

    public FriendlyByteBuf setBoolean(int index, boolean value) {
        this.source.setBoolean(index, value);
        return this;
    }

    public FriendlyByteBuf setByte(int index, int value) {
        this.source.setByte(index, value);
        return this;
    }

    public FriendlyByteBuf setShort(int index, int value) {
        this.source.setShort(index, value);
        return this;
    }

    public FriendlyByteBuf setShortLE(int index, int value) {
        this.source.setShortLE(index, value);
        return this;
    }

    public FriendlyByteBuf setMedium(int index, int value) {
        this.source.setMedium(index, value);
        return this;
    }

    public FriendlyByteBuf setMediumLE(int index, int value) {
        this.source.setMediumLE(index, value);
        return this;
    }

    public FriendlyByteBuf setInt(int index, int value) {
        this.source.setInt(index, value);
        return this;
    }

    public FriendlyByteBuf setIntLE(int index, int value) {
        this.source.setIntLE(index, value);
        return this;
    }

    public FriendlyByteBuf setLong(int index, long value) {
        this.source.setLong(index, value);
        return this;
    }

    public FriendlyByteBuf setLongLE(int index, long value) {
        this.source.setLongLE(index, value);
        return this;
    }

    public FriendlyByteBuf setChar(int index, int value) {
        this.source.setChar(index, value);
        return this;
    }

    public FriendlyByteBuf setFloat(int index, float value) {
        this.source.setFloat(index, value);
        return this;
    }

    public FriendlyByteBuf setDouble(int index, double value) {
        this.source.setDouble(index, value);
        return this;
    }

    public FriendlyByteBuf setBytes(int index, ByteBuf source) {
        this.source.setBytes(index, source);
        return this;
    }

    public FriendlyByteBuf setBytes(int index, ByteBuf source, int length) {
        this.source.setBytes(index, source, length);
        return this;
    }

    public FriendlyByteBuf setBytes(int index, ByteBuf source, int sourceIndex, int length) {
        this.source.setBytes(index, source, sourceIndex, length);
        return this;
    }

    public FriendlyByteBuf setBytes(int index, byte[] source) {
        this.source.setBytes(index, source);
        return this;
    }

    public FriendlyByteBuf setBytes(int index, byte[] source, int sourceIndex, int length) {
        this.source.setBytes(index, source, sourceIndex, length);
        return this;
    }

    public FriendlyByteBuf setBytes(int index, ByteBuffer source) {
        this.source.setBytes(index, source);
        return this;
    }

    @Override
    public int setBytes(int index, InputStream in, int length) throws IOException {
        return this.source.setBytes(index, in, length);
    }

    @Override
    public int setBytes(int index, ScatteringByteChannel in, int length) throws IOException {
        return this.source.setBytes(index, in, length);
    }

    @Override
    public int setBytes(int index, FileChannel in, long position, int length) throws IOException {
        return this.source.setBytes(index, in, position, length);
    }

    public FriendlyByteBuf setZero(int index, int length) {
        this.source.setZero(index, length);
        return this;
    }

    @Override
    public int setCharSequence(int index, CharSequence charSequence, Charset charset) {
        return this.source.setCharSequence(index, charSequence, charset);
    }

    @Override
    public boolean readBoolean() {
        return this.source.readBoolean();
    }

    @Override
    public byte readByte() {
        return this.source.readByte();
    }

    @Override
    public short readUnsignedByte() {
        return this.source.readUnsignedByte();
    }

    @Override
    public short readShort() {
        return this.source.readShort();
    }

    @Override
    public short readShortLE() {
        return this.source.readShortLE();
    }

    @Override
    public int readUnsignedShort() {
        return this.source.readUnsignedShort();
    }

    @Override
    public int readUnsignedShortLE() {
        return this.source.readUnsignedShortLE();
    }

    @Override
    public int readMedium() {
        return this.source.readMedium();
    }

    @Override
    public int readMediumLE() {
        return this.source.readMediumLE();
    }

    @Override
    public int readUnsignedMedium() {
        return this.source.readUnsignedMedium();
    }

    @Override
    public int readUnsignedMediumLE() {
        return this.source.readUnsignedMediumLE();
    }

    @Override
    public int readInt() {
        return this.source.readInt();
    }

    @Override
    public int readIntLE() {
        return this.source.readIntLE();
    }

    @Override
    public long readUnsignedInt() {
        return this.source.readUnsignedInt();
    }

    @Override
    public long readUnsignedIntLE() {
        return this.source.readUnsignedIntLE();
    }

    @Override
    public long readLong() {
        return this.source.readLong();
    }

    @Override
    public long readLongLE() {
        return this.source.readLongLE();
    }

    @Override
    public char readChar() {
        return this.source.readChar();
    }

    @Override
    public float readFloat() {
        return this.source.readFloat();
    }

    @Override
    public double readDouble() {
        return this.source.readDouble();
    }

    @Override
    public ByteBuf readBytes(int length) {
        return this.source.readBytes(length);
    }

    @Override
    public ByteBuf readSlice(int length) {
        return this.source.readSlice(length);
    }

    @Override
    public ByteBuf readRetainedSlice(int length) {
        return this.source.readRetainedSlice(length);
    }

    public FriendlyByteBuf readBytes(ByteBuf destination) {
        this.source.readBytes(destination);
        return this;
    }

    public FriendlyByteBuf readBytes(ByteBuf destination, int length) {
        this.source.readBytes(destination, length);
        return this;
    }

    public FriendlyByteBuf readBytes(ByteBuf destination, int destinationIndex, int length) {
        this.source.readBytes(destination, destinationIndex, length);
        return this;
    }

    public FriendlyByteBuf readBytes(byte[] destination) {
        this.source.readBytes(destination);
        return this;
    }

    public FriendlyByteBuf readBytes(byte[] destination, int destinationIndex, int length) {
        this.source.readBytes(destination, destinationIndex, length);
        return this;
    }

    public FriendlyByteBuf readBytes(ByteBuffer destination) {
        this.source.readBytes(destination);
        return this;
    }

    public FriendlyByteBuf readBytes(OutputStream out, int length) throws IOException {
        this.source.readBytes(out, length);
        return this;
    }

    @Override
    public int readBytes(GatheringByteChannel out, int length) throws IOException {
        return this.source.readBytes(out, length);
    }

    @Override
    public CharSequence readCharSequence(int length, Charset charset) {
        return this.source.readCharSequence(length, charset);
    }

    @Override
    public int readBytes(FileChannel out, long position, int length) throws IOException {
        return this.source.readBytes(out, position, length);
    }

    public FriendlyByteBuf skipBytes(int length) {
        this.source.skipBytes(length);
        return this;
    }

    public FriendlyByteBuf writeBoolean(boolean value) {
        this.source.writeBoolean(value);
        return this;
    }

    public FriendlyByteBuf writeByte(int value) {
        this.source.writeByte(value);
        return this;
    }

    public FriendlyByteBuf writeShort(int value) {
        this.source.writeShort(value);
        return this;
    }

    public FriendlyByteBuf writeShortLE(int value) {
        this.source.writeShortLE(value);
        return this;
    }

    public FriendlyByteBuf writeMedium(int value) {
        this.source.writeMedium(value);
        return this;
    }

    public FriendlyByteBuf writeMediumLE(int value) {
        this.source.writeMediumLE(value);
        return this;
    }

    public FriendlyByteBuf writeInt(int value) {
        this.source.writeInt(value);
        return this;
    }

    public FriendlyByteBuf writeIntLE(int value) {
        this.source.writeIntLE(value);
        return this;
    }

    public FriendlyByteBuf writeLong(long value) {
        this.source.writeLong(value);
        return this;
    }

    public FriendlyByteBuf writeLongLE(long value) {
        this.source.writeLongLE(value);
        return this;
    }

    public FriendlyByteBuf writeChar(int value) {
        this.source.writeChar(value);
        return this;
    }

    public FriendlyByteBuf writeFloat(float value) {
        this.source.writeFloat(value);
        return this;
    }

    public FriendlyByteBuf writeDouble(double value) {
        this.source.writeDouble(value);
        return this;
    }

    public FriendlyByteBuf writeBytes(ByteBuf source) {
        this.source.writeBytes(source);
        return this;
    }

    public FriendlyByteBuf writeBytes(ByteBuf source, int length) {
        this.source.writeBytes(source, length);
        return this;
    }

    public FriendlyByteBuf writeBytes(ByteBuf source, int sourceIndex, int length) {
        this.source.writeBytes(source, sourceIndex, length);
        return this;
    }

    public FriendlyByteBuf writeBytes(byte[] source) {
        this.source.writeBytes(source);
        return this;
    }

    public FriendlyByteBuf writeBytes(byte[] source, int sourceIndex, int length) {
        this.source.writeBytes(source, sourceIndex, length);
        return this;
    }

    public FriendlyByteBuf writeBytes(ByteBuffer source) {
        this.source.writeBytes(source);
        return this;
    }

    @Override
    public int writeBytes(InputStream in, int length) throws IOException {
        return this.source.writeBytes(in, length);
    }

    @Override
    public int writeBytes(ScatteringByteChannel in, int length) throws IOException {
        return this.source.writeBytes(in, length);
    }

    @Override
    public int writeBytes(FileChannel in, long position, int length) throws IOException {
        return this.source.writeBytes(in, position, length);
    }

    public FriendlyByteBuf writeZero(int length) {
        this.source.writeZero(length);
        return this;
    }

    @Override
    public int writeCharSequence(CharSequence charSequence, Charset charset) {
        return this.source.writeCharSequence(charSequence, charset);
    }

    @Override
    public int indexOf(int fromIndex, int toIndex, byte value) {
        return this.source.indexOf(fromIndex, toIndex, value);
    }

    @Override
    public int bytesBefore(byte value) {
        return this.source.bytesBefore(value);
    }

    @Override
    public int bytesBefore(int length, byte value) {
        return this.source.bytesBefore(length, value);
    }

    @Override
    public int bytesBefore(int index, int length, byte value) {
        return this.source.bytesBefore(index, length, value);
    }

    @Override
    public int forEachByte(ByteProcessor processor) {
        return this.source.forEachByte(processor);
    }

    @Override
    public int forEachByte(int index, int length, ByteProcessor processor) {
        return this.source.forEachByte(index, length, processor);
    }

    @Override
    public int forEachByteDesc(ByteProcessor processor) {
        return this.source.forEachByteDesc(processor);
    }

    @Override
    public int forEachByteDesc(int index, int length, ByteProcessor processor) {
        return this.source.forEachByteDesc(index, length, processor);
    }

    @Override
    public ByteBuf copy() {
        return this.source.copy();
    }

    @Override
    public ByteBuf copy(int index, int length) {
        return this.source.copy(index, length);
    }

    @Override
    public ByteBuf slice() {
        return this.source.slice();
    }

    @Override
    public ByteBuf retainedSlice() {
        return this.source.retainedSlice();
    }

    @Override
    public ByteBuf slice(int index, int length) {
        return this.source.slice(index, length);
    }

    @Override
    public ByteBuf retainedSlice(int index, int length) {
        return this.source.retainedSlice(index, length);
    }

    @Override
    public ByteBuf duplicate() {
        return this.source.duplicate();
    }

    @Override
    public ByteBuf retainedDuplicate() {
        return this.source.retainedDuplicate();
    }

    @Override
    public int nioBufferCount() {
        return this.source.nioBufferCount();
    }

    @Override
    public ByteBuffer nioBuffer() {
        return this.source.nioBuffer();
    }

    @Override
    public ByteBuffer nioBuffer(int index, int length) {
        return this.source.nioBuffer(index, length);
    }

    @Override
    public ByteBuffer internalNioBuffer(int index, int length) {
        return this.source.internalNioBuffer(index, length);
    }

    @Override
    public ByteBuffer[] nioBuffers() {
        return this.source.nioBuffers();
    }

    @Override
    public ByteBuffer[] nioBuffers(int index, int length) {
        return this.source.nioBuffers(index, length);
    }

    @Override
    public boolean hasArray() {
        return this.source.hasArray();
    }

    @Override
    public byte[] array() {
        return this.source.array();
    }

    @Override
    public int arrayOffset() {
        return this.source.arrayOffset();
    }

    @Override
    public boolean hasMemoryAddress() {
        return this.source.hasMemoryAddress();
    }

    @Override
    public long memoryAddress() {
        return this.source.memoryAddress();
    }

    @Override
    public String toString(Charset charset) {
        return this.source.toString(charset);
    }

    @Override
    public String toString(int index, int length, Charset charset) {
        return this.source.toString(index, length, charset);
    }

    @Override
    public int hashCode() {
        return this.source.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        return this.source.equals(other);
    }

    @Override
    public int compareTo(ByteBuf other) {
        return this.source.compareTo(other);
    }

    @Override
    public String toString() {
        return this.source.toString();
    }

    public FriendlyByteBuf retain(int increment) {
        this.source.retain(increment);
        return this;
    }

    public FriendlyByteBuf retain() {
        this.source.retain();
        return this;
    }

    public FriendlyByteBuf touch() {
        this.source.touch();
        return this;
    }

    public FriendlyByteBuf touch(Object hint) {
        this.source.touch(hint);
        return this;
    }

    @Override
    public int refCnt() {
        return this.source.refCnt();
    }

    @Override
    public boolean release() {
        return this.source.release();
    }

    @Override
    public boolean release(int decrement) {
        return this.source.release(decrement);
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public ByteBuf getSource() {
        return this.source;
    }
}
