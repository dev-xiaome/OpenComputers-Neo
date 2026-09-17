package net.minecraft.network.codec;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.IdMap;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.Utf8String;
import net.minecraft.network.VarInt;
import net.minecraft.network.VarLong;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public interface ByteBufCodecs {
    int MAX_INITIAL_COLLECTION_SIZE = 65536;
    StreamCodec<ByteBuf, Boolean> BOOL = new StreamCodec<ByteBuf, Boolean>() {
        public Boolean decode(ByteBuf p_320813_) {
            return p_320813_.readBoolean();
        }

        public void encode(ByteBuf p_319896_, Boolean p_320251_) {
            p_319896_.writeBoolean(p_320251_);
        }
    };
    StreamCodec<ByteBuf, Byte> BYTE = new StreamCodec<ByteBuf, Byte>() {
        public Byte decode(ByteBuf p_320628_) {
            return p_320628_.readByte();
        }

        public void encode(ByteBuf p_320364_, Byte p_320618_) {
            p_320364_.writeByte(p_320618_);
        }
    };
    StreamCodec<ByteBuf, Short> SHORT = new StreamCodec<ByteBuf, Short>() {
        public Short decode(ByteBuf p_320513_) {
            return p_320513_.readShort();
        }

        public void encode(ByteBuf p_320028_, Short p_320388_) {
            p_320028_.writeShort(p_320388_);
        }
    };
    StreamCodec<ByteBuf, Integer> UNSIGNED_SHORT = new StreamCodec<ByteBuf, Integer>() {
        public Integer decode(ByteBuf p_320319_) {
            return p_320319_.readUnsignedShort();
        }

        public void encode(ByteBuf p_320669_, Integer p_320205_) {
            p_320669_.writeShort(p_320205_);
        }
    };
    StreamCodec<ByteBuf, Integer> INT = new StreamCodec<ByteBuf, Integer>() {
        public Integer decode(ByteBuf p_320253_) {
            return p_320253_.readInt();
        }

        public void encode(ByteBuf p_320753_, Integer p_330380_) {
            p_320753_.writeInt(p_330380_);
        }
    };
    StreamCodec<ByteBuf, Integer> VAR_INT = new StreamCodec<ByteBuf, Integer>() {
        public Integer decode(ByteBuf p_320759_) {
            return VarInt.read(p_320759_);
        }

        public void encode(ByteBuf p_320314_, Integer p_341414_) {
            VarInt.write(p_320314_, p_341414_);
        }
    };
    StreamCodec<ByteBuf, Long> VAR_LONG = new StreamCodec<ByteBuf, Long>() {
        public Long decode(ByteBuf p_320635_) {
            return VarLong.read(p_320635_);
        }

        public void encode(ByteBuf p_320545_, Long p_341419_) {
            VarLong.write(p_320545_, p_341419_);
        }
    };
    StreamCodec<ByteBuf, Float> FLOAT = new StreamCodec<ByteBuf, Float>() {
        public Float decode(ByteBuf p_320259_) {
            return p_320259_.readFloat();
        }

        public void encode(ByteBuf p_320199_, Float p_341020_) {
            p_320199_.writeFloat(p_341020_);
        }
    };
    StreamCodec<ByteBuf, Double> DOUBLE = new StreamCodec<ByteBuf, Double>() {
        public Double decode(ByteBuf p_320599_) {
            return p_320599_.readDouble();
        }

        public void encode(ByteBuf p_320880_, Double p_340812_) {
            p_320880_.writeDouble(p_340812_);
        }
    };
    StreamCodec<ByteBuf, byte[]> BYTE_ARRAY = new StreamCodec<ByteBuf, byte[]>() {
        public byte[] decode(ByteBuf buffer) {
            return FriendlyByteBuf.readByteArray(buffer);
        }

        public void encode(ByteBuf buffer, byte[] value) {
            FriendlyByteBuf.writeByteArray(buffer, value);
        }
    };
    StreamCodec<ByteBuf, String> STRING_UTF8 = stringUtf8(32767);
    StreamCodec<ByteBuf, Tag> TAG = tagCodec(() -> NbtAccounter.create(2097152L));
    StreamCodec<ByteBuf, Tag> TRUSTED_TAG = tagCodec(NbtAccounter::unlimitedHeap);
    StreamCodec<ByteBuf, CompoundTag> COMPOUND_TAG = compoundTagCodec(() -> NbtAccounter.create(2097152L));
    StreamCodec<ByteBuf, CompoundTag> TRUSTED_COMPOUND_TAG = compoundTagCodec(NbtAccounter::unlimitedHeap);
    StreamCodec<ByteBuf, Optional<CompoundTag>> OPTIONAL_COMPOUND_TAG = new StreamCodec<ByteBuf, Optional<CompoundTag>>() {
        public Optional<CompoundTag> decode(ByteBuf p_320103_) {
            return Optional.ofNullable(FriendlyByteBuf.readNbt(p_320103_));
        }

        public void encode(ByteBuf p_320012_, Optional<CompoundTag> p_341059_) {
            FriendlyByteBuf.writeNbt(p_320012_, p_341059_.orElse(null));
        }
    };
    StreamCodec<ByteBuf, Vector3f> VECTOR3F = new StreamCodec<ByteBuf, Vector3f>() {
        public Vector3f decode(ByteBuf p_319897_) {
            return FriendlyByteBuf.readVector3f(p_319897_);
        }

        public void encode(ByteBuf p_320441_, Vector3f p_340932_) {
            FriendlyByteBuf.writeVector3f(p_320441_, p_340932_);
        }
    };
    StreamCodec<ByteBuf, Quaternionf> QUATERNIONF = new StreamCodec<ByteBuf, Quaternionf>() {
        public Quaternionf decode(ByteBuf p_324083_) {
            return FriendlyByteBuf.readQuaternion(p_324083_);
        }

        public void encode(ByteBuf p_324192_, Quaternionf p_341304_) {
            FriendlyByteBuf.writeQuaternion(p_324192_, p_341304_);
        }
    };
    StreamCodec<ByteBuf, PropertyMap> GAME_PROFILE_PROPERTIES = new StreamCodec<ByteBuf, PropertyMap>() {
        private static final int MAX_PROPERTY_NAME_LENGTH = 64;
        private static final int MAX_PROPERTY_VALUE_LENGTH = 32767;
        private static final int MAX_PROPERTY_SIGNATURE_LENGTH = 1024;
        private static final int MAX_PROPERTIES = 16;

        public PropertyMap decode(ByteBuf p_331129_) {
            int i = ByteBufCodecs.readCount(p_331129_, 16);
            PropertyMap propertymap = new PropertyMap();

            for (int j = 0; j < i; j++) {
                String s = Utf8String.read(p_331129_, 64);
                String s1 = Utf8String.read(p_331129_, 32767);
                String s2 = FriendlyByteBuf.readNullable(p_331129_, p_341239_ -> Utf8String.read(p_341239_, 1024));
                Property property = new Property(s, s1, s2);
                propertymap.put(property.name(), property);
            }

            return propertymap;
        }

        public void encode(ByteBuf p_331394_, PropertyMap p_341001_) {
            ByteBufCodecs.writeCount(p_331394_, p_341001_.size(), 16);

            for (Property property : p_341001_.values()) {
                Utf8String.write(p_331394_, property.name(), 64);
                Utf8String.write(p_331394_, property.value(), 32767);
                FriendlyByteBuf.writeNullable(p_331394_, property.signature(), (p_340917_, p_341030_) -> Utf8String.write(p_340917_, p_341030_, 1024));
            }
        }
    };
    StreamCodec<ByteBuf, GameProfile> GAME_PROFILE = new StreamCodec<ByteBuf, GameProfile>() {
        public GameProfile decode(ByteBuf p_341302_) {
            UUID uuid = UUIDUtil.STREAM_CODEC.decode(p_341302_);
            String s = Utf8String.read(p_341302_, 16);
            GameProfile gameprofile = new GameProfile(uuid, s);
            gameprofile.getProperties().putAll(ByteBufCodecs.GAME_PROFILE_PROPERTIES.decode(p_341302_));
            return gameprofile;
        }

        public void encode(ByteBuf p_340881_, GameProfile p_341071_) {
            UUIDUtil.STREAM_CODEC.encode(p_340881_, p_341071_.getId());
            Utf8String.write(p_340881_, p_341071_.getName(), 16);
            ByteBufCodecs.GAME_PROFILE_PROPERTIES.encode(p_340881_, p_341071_.getProperties());
        }
    };

    static StreamCodec<ByteBuf, byte[]> byteArray(final int maxSize) {
        return new StreamCodec<ByteBuf, byte[]>() {
            public byte[] decode(ByteBuf p_319947_) {
                return FriendlyByteBuf.readByteArray(p_319947_, maxSize);
            }

            public void encode(ByteBuf p_320370_, byte[] p_331189_) {
                if (p_331189_.length > maxSize) {
                    throw new EncoderException("ByteArray with size " + p_331189_.length + " is bigger than allowed " + maxSize);
                } else {
                    FriendlyByteBuf.writeByteArray(p_320370_, p_331189_);
                }
            }
        };
    }

    static StreamCodec<ByteBuf, String> stringUtf8(final int maxLength) {
        return new StreamCodec<ByteBuf, String>() {
            public String decode(ByteBuf p_332176_) {
                return Utf8String.read(p_332176_, maxLength);
            }

            public void encode(ByteBuf p_331068_, String p_341104_) {
                Utf8String.write(p_331068_, p_341104_, maxLength);
            }
        };
    }

    static StreamCodec<ByteBuf, Tag> tagCodec(final Supplier<NbtAccounter> accounter) {
        return new StreamCodec<ByteBuf, Tag>() {
            public Tag decode(ByteBuf p_341393_) {
                Tag tag = FriendlyByteBuf.readNbt(p_341393_, accounter.get());
                if (tag == null) {
                    throw new DecoderException("Expected non-null compound tag");
                } else {
                    return tag;
                }
            }

            public void encode(ByteBuf p_340857_, Tag p_341321_) {
                if (p_341321_ == EndTag.INSTANCE) {
                    throw new EncoderException("Expected non-null compound tag");
                } else {
                    FriendlyByteBuf.writeNbt(p_340857_, p_341321_);
                }
            }
        };
    }

    static StreamCodec<ByteBuf, CompoundTag> compoundTagCodec(Supplier<NbtAccounter> accounterSupplier) {
        return tagCodec(accounterSupplier).map(p_339405_ -> {
            if (p_339405_ instanceof CompoundTag) {
                return (CompoundTag)p_339405_;
            } else {
                throw new DecoderException("Not a compound tag: " + p_339405_);
            }
        }, p_330975_ -> (Tag)p_330975_);
    }

    static <T> StreamCodec<ByteBuf, T> fromCodecTrusted(Codec<T> codec) {
        return fromCodec(codec, NbtAccounter::unlimitedHeap);
    }

    static <T> StreamCodec<ByteBuf, T> fromCodec(Codec<T> codec) {
        return fromCodec(codec, () -> NbtAccounter.create(2097152L));
    }

    static <T> StreamCodec<ByteBuf, T> fromCodec(Codec<T> codec, Supplier<NbtAccounter> accounterSupplier) {
        return tagCodec(accounterSupplier)
            .map(
                p_337514_ -> codec.parse(NbtOps.INSTANCE, p_337514_)
                        .getOrThrow(p_339407_ -> new DecoderException("Failed to decode: " + p_339407_ + " " + p_337514_)),
                p_337516_ -> codec.encodeStart(NbtOps.INSTANCE, (T)p_337516_)
                        .getOrThrow(p_339409_ -> new EncoderException("Failed to encode: " + p_339409_ + " " + p_337516_))
            );
    }

    static <T> StreamCodec<RegistryFriendlyByteBuf, T> fromCodecWithRegistriesTrusted(Codec<T> codec) {
        return fromCodecWithRegistries(codec, NbtAccounter::unlimitedHeap);
    }

    static <T> StreamCodec<RegistryFriendlyByteBuf, T> fromCodecWithRegistries(Codec<T> codec) {
        return fromCodecWithRegistries(codec, () -> NbtAccounter.create(2097152L));
    }

    static <T> StreamCodec<RegistryFriendlyByteBuf, T> fromCodecWithRegistries(final Codec<T> codec, Supplier<NbtAccounter> accounterSupplier) {
        final StreamCodec<ByteBuf, Tag> streamcodec = tagCodec(accounterSupplier);
        return new StreamCodec<RegistryFriendlyByteBuf, T>() {
            public T decode(RegistryFriendlyByteBuf p_340878_) {
                Tag tag = streamcodec.decode(p_340878_);
                RegistryOps<Tag> registryops = p_340878_.registryAccess().createSerializationContext(NbtOps.INSTANCE);
                return codec.parse(registryops, tag).getOrThrow(p_340924_ -> new DecoderException("Failed to decode: " + p_340924_ + " " + tag));
            }

            public void encode(RegistryFriendlyByteBuf p_341221_, T p_341320_) {
                RegistryOps<Tag> registryops = p_341221_.registryAccess().createSerializationContext(NbtOps.INSTANCE);
                Tag tag = codec.encodeStart(registryops, p_341320_)
                    .getOrThrow(p_341126_ -> new EncoderException("Failed to encode: " + p_341126_ + " " + p_341320_));
                streamcodec.encode(p_341221_, tag);
            }
        };
    }

    static <B extends ByteBuf, V> StreamCodec<B, Optional<V>> optional(final StreamCodec<B, V> codec) {
        return new StreamCodec<B, Optional<V>>() {
            public Optional<V> decode(B p_324595_) {
                return p_324595_.readBoolean() ? Optional.of(codec.decode(p_324595_)) : Optional.empty();
            }

            public void encode(B p_324147_, Optional<V> p_340875_) {
                if (p_340875_.isPresent()) {
                    p_324147_.writeBoolean(true);
                    codec.encode(p_324147_, p_340875_.get());
                } else {
                    p_324147_.writeBoolean(false);
                }
            }
        };
    }

    static int readCount(ByteBuf buffer, int maxSize) {
        int i = VarInt.read(buffer);
        if (i > maxSize) {
            throw new DecoderException(i + " elements exceeded max size of: " + maxSize);
        } else {
            return i;
        }
    }

    static void writeCount(ByteBuf buffer, int count, int maxSize) {
        if (count > maxSize) {
            throw new EncoderException(count + " elements exceeded max size of: " + maxSize);
        } else {
            VarInt.write(buffer, count);
        }
    }

    static <B extends ByteBuf, V, C extends Collection<V>> StreamCodec<B, C> collection(IntFunction<C> factory, StreamCodec<? super B, V> codec) {
        return collection(factory, codec, Integer.MAX_VALUE);
    }

    static <B extends ByteBuf, V, C extends Collection<V>> StreamCodec<B, C> collection(
        final IntFunction<C> factory, final StreamCodec<? super B, V> codec, final int maxSize
    ) {
        return new StreamCodec<B, C>() {
            public C decode(B p_324220_) {
                int i = ByteBufCodecs.readCount(p_324220_, maxSize);
                C c = factory.apply(Math.min(i, 65536));

                for (int j = 0; j < i; j++) {
                    c.add(codec.decode(p_324220_));
                }

                return c;
            }

            public void encode(B p_323874_, C p_340813_) {
                ByteBufCodecs.writeCount(p_323874_, p_340813_.size(), maxSize);

                for (V v : p_340813_) {
                    codec.encode(p_323874_, v);
                }
            }
        };
    }

    static <B extends ByteBuf, V, C extends Collection<V>> StreamCodec.CodecOperation<B, V, C> collection(IntFunction<C> factory) {
        return p_319785_ -> collection(factory, p_319785_);
    }

    static <B extends ByteBuf, V> StreamCodec.CodecOperation<B, V, List<V>> list() {
        return p_320272_ -> collection(ArrayList::new, p_320272_);
    }

    static <B extends ByteBuf, V> StreamCodec.CodecOperation<B, V, List<V>> list(int maxSize) {
        return p_329871_ -> collection(ArrayList::new, p_329871_, maxSize);
    }

    static <B extends ByteBuf, K, V, M extends Map<K, V>> StreamCodec<B, M> map(
        IntFunction<? extends M> factory, StreamCodec<? super B, K> keyCodec, StreamCodec<? super B, V> valueCodec
    ) {
        return map(factory, keyCodec, valueCodec, Integer.MAX_VALUE);
    }

    static <B extends ByteBuf, K, V, M extends Map<K, V>> StreamCodec<B, M> map(
        final IntFunction<? extends M> factory, final StreamCodec<? super B, K> keyCodec, final StreamCodec<? super B, V> valueCodec, final int maxSize
    ) {
        return new StreamCodec<B, M>() {
            public void encode(B p_331539_, M p_341314_) {
                ByteBufCodecs.writeCount(p_331539_, p_341314_.size(), maxSize);
                p_341314_.forEach((p_340647_, p_340648_) -> {
                    keyCodec.encode(p_331539_, (K)p_340647_);
                    valueCodec.encode(p_331539_, (V)p_340648_);
                });
            }

            public M decode(B p_331901_) {
                int i = ByteBufCodecs.readCount(p_331901_, maxSize);
                M m = (M)factory.apply(Math.min(i, 65536));

                for (int j = 0; j < i; j++) {
                    K k = keyCodec.decode(p_331901_);
                    V v = valueCodec.decode(p_331901_);
                    m.put(k, v);
                }

                return m;
            }
        };
    }

    static <B extends ByteBuf, L, R> StreamCodec<B, Either<L, R>> either(final StreamCodec<? super B, L> leftCodec, final StreamCodec<? super B, R> rightCodec) {
        return new StreamCodec<B, Either<L, R>>() {
            public Either<L, R> decode(B p_332082_) {
                return p_332082_.readBoolean() ? Either.left(leftCodec.decode(p_332082_)) : Either.right(rightCodec.decode(p_332082_));
            }

            public void encode(B p_331172_, Either<L, R> p_340944_) {
                p_340944_.ifLeft(p_341317_ -> {
                    p_331172_.writeBoolean(true);
                    leftCodec.encode(p_331172_, (L)p_341317_);
                }).ifRight(p_341155_ -> {
                    p_331172_.writeBoolean(false);
                    rightCodec.encode(p_331172_, (R)p_341155_);
                });
            }
        };
    }

    static <T> StreamCodec<ByteBuf, T> idMapper(final IntFunction<T> idLookup, final ToIntFunction<T> idGetter) {
        return new StreamCodec<ByteBuf, T>() {
            public T decode(ByteBuf p_340809_) {
                int i = VarInt.read(p_340809_);
                return idLookup.apply(i);
            }

            public void encode(ByteBuf p_341417_, T p_330257_) {
                int i = idGetter.applyAsInt(p_330257_);
                VarInt.write(p_341417_, i);
            }
        };
    }

    static <T> StreamCodec<ByteBuf, T> idMapper(IdMap<T> idMap) {
        return idMapper(idMap::byIdOrThrow, idMap::getIdOrThrow);
    }

    private static <T, R> StreamCodec<RegistryFriendlyByteBuf, R> registry(
        final ResourceKey<? extends Registry<T>> registryKey, final Function<Registry<T>, IdMap<R>> idGetter
    ) {
        return new StreamCodec<RegistryFriendlyByteBuf, R>() {
            private IdMap<R> getRegistryOrThrow(RegistryFriendlyByteBuf p_330361_) {
                var registry = p_330361_.registryAccess().registryOrThrow(registryKey);
                if (net.neoforged.neoforge.registries.RegistryManager.isNonSyncedBuiltInRegistry(registry)) {
                    throw new IllegalStateException("Cannot use ID syncing for non-synced built-in registry: " + registry.key());
                }
                return idGetter.apply(registry);
            }

            public R decode(RegistryFriendlyByteBuf p_331253_) {
                int i = VarInt.read(p_331253_);
                return (R)this.getRegistryOrThrow(p_331253_).byIdOrThrow(i);
            }

            public void encode(RegistryFriendlyByteBuf p_331775_, R p_341178_) {
                int i = this.getRegistryOrThrow(p_331775_).getIdOrThrow(p_341178_);
                VarInt.write(p_331775_, i);
            }
        };
    }

    static <T> StreamCodec<RegistryFriendlyByteBuf, T> registry(ResourceKey<? extends Registry<T>> registryKey) {
        return registry(registryKey, p_332056_ -> p_332056_);
    }

    static <T> StreamCodec<RegistryFriendlyByteBuf, Holder<T>> holderRegistry(ResourceKey<? extends Registry<T>> registryKey) {
        return registry(registryKey, Registry::asHolderIdMap);
    }

    static <T> StreamCodec<RegistryFriendlyByteBuf, Holder<T>> holder(
        final ResourceKey<? extends Registry<T>> registryKey, final StreamCodec<? super RegistryFriendlyByteBuf, T> codec
    ) {
        return new StreamCodec<RegistryFriendlyByteBuf, Holder<T>>() {
            private static final int DIRECT_HOLDER_ID = 0;

            private IdMap<Holder<T>> getRegistryOrThrow(RegistryFriendlyByteBuf p_341377_) {
                return p_341377_.registryAccess().registryOrThrow(registryKey).asHolderIdMap();
            }

            public Holder<T> decode(RegistryFriendlyByteBuf p_330998_) {
                int i = VarInt.read(p_330998_);
                return i == 0 ? Holder.direct(codec.decode(p_330998_)) : (Holder)this.getRegistryOrThrow(p_330998_).byIdOrThrow(i - 1);
            }

            public void encode(RegistryFriendlyByteBuf p_330557_, Holder<T> p_341109_) {
                switch (p_341109_.kind()) {
                    case REFERENCE:
                        int i = this.getRegistryOrThrow(p_330557_).getIdOrThrow(p_341109_);
                        VarInt.write(p_330557_, i + 1);
                        break;
                    case DIRECT:
                        VarInt.write(p_330557_, 0);
                        codec.encode(p_330557_, p_341109_.value());
                }
            }
        };
    }

    static <T> StreamCodec<RegistryFriendlyByteBuf, HolderSet<T>> holderSet(final ResourceKey<? extends Registry<T>> registryKey) {
        return new StreamCodec<RegistryFriendlyByteBuf, HolderSet<T>>() {
            private static final int NAMED_SET = -1;
            private final StreamCodec<RegistryFriendlyByteBuf, Holder<T>> holderCodec = ByteBufCodecs.holderRegistry(registryKey);

            private final Map<net.neoforged.neoforge.registries.holdersets.HolderSetType, StreamCodec<RegistryFriendlyByteBuf, ? extends net.neoforged.neoforge.registries.holdersets.ICustomHolderSet<T>>> holderSetCodecs = new java.util.concurrent.ConcurrentHashMap<>();

            private StreamCodec<RegistryFriendlyByteBuf, ? extends net.neoforged.neoforge.registries.holdersets.ICustomHolderSet<T>> holderSetCodec(net.neoforged.neoforge.registries.holdersets.HolderSetType type) {
                return this.holderSetCodecs.computeIfAbsent(type, key -> key.makeStreamCodec(registryKey));
            }

            private <H extends net.neoforged.neoforge.registries.holdersets.ICustomHolderSet<T>> H cast(net.neoforged.neoforge.registries.holdersets.ICustomHolderSet<T> holderSet) {
                return (H) holderSet;
            }

            public HolderSet<T> decode(RegistryFriendlyByteBuf p_340887_) {
                int i = VarInt.read(p_340887_) - 1;
                // Neo: Co-opt negative VarInt values within the HolderSet codec as an HolderSetType id.
                // Vanilla uses 0 for tag and [1, Integer.MAX_VALUE] for list size [0, Integer.MAX_VALUE - 1].
                // So we may encode the registry id for custom holder set types in [Integer.MIN_VALUE + 1, -1] (local variable i must not be underflow).
                // The registry id for custom holder set types is (-1 - network id), while local variable i is (network id - 1), so the registry id would be (-2 - i).
                if (i < -1) {
                    return this.holderSetCodec(net.neoforged.neoforge.registries.NeoForgeRegistries.HOLDER_SET_TYPES.byIdOrThrow(-2 - i)).decode(p_340887_);
                }
                if (i == -1) {
                    Registry<T> registry = p_340887_.registryAccess().registryOrThrow(registryKey);
                    return registry.getTag(TagKey.create(registryKey, ResourceLocation.STREAM_CODEC.decode(p_340887_))).orElseThrow();
                } else {
                    List<Holder<T>> list = new ArrayList<>(Math.min(i, 65536));

                    for (int j = 0; j < i; j++) {
                        list.add(this.holderCodec.decode(p_340887_));
                    }

                    return HolderSet.direct(list);
                }
            }

            public void encode(RegistryFriendlyByteBuf p_341009_, HolderSet<T> p_340834_) {
                // Neo: Co-opt negative VarInt values within the HolderSet codec as an HolderSetType id.
                // Vanilla uses 0 for tag and [1, Integer.MAX_VALUE] for list size [0, Integer.MAX_VALUE - 1] (local variable i in decode() must not be underflow).
                // So we may encode the registry id for custom holder set types in [Integer.MIN_VALUE + 1, -1].
                // The network id for custom holder set types is (-1 - registry id)
                if (p_341009_.getConnectionType().isNeoForge() && p_340834_ instanceof net.neoforged.neoforge.registries.holdersets.ICustomHolderSet<T> customHolderSet) {
                    VarInt.write(p_341009_, -1 - net.neoforged.neoforge.registries.NeoForgeRegistries.HOLDER_SET_TYPES.getId(customHolderSet.type()));
                    this.holderSetCodec(customHolderSet.type()).encode(p_341009_, cast(customHolderSet));
                    return;
                }
                Optional<TagKey<T>> optional = p_340834_.unwrapKey();
                if (optional.isPresent()) {
                    VarInt.write(p_341009_, 0);
                    ResourceLocation.STREAM_CODEC.encode(p_341009_, optional.get().location());
                } else {
                    VarInt.write(p_341009_, p_340834_.size() + 1);

                    for (Holder<T> holder : p_340834_) {
                        this.holderCodec.encode(p_341009_, holder);
                    }
                }
            }
        };
    }
}
