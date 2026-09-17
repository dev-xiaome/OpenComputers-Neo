package net.minecraft.resources;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;

public class HolderSetCodec<E> implements Codec<HolderSet<E>> {
    private final ResourceKey<? extends Registry<E>> registryKey;
    private final Codec<Holder<E>> elementCodec;
    private final Codec<List<Holder<E>>> homogenousListCodec;
    private final Codec<Either<TagKey<E>, List<Holder<E>>>> registryAwareCodec;
    private final Codec<net.neoforged.neoforge.registries.holdersets.ICustomHolderSet<E>> forgeDispatchCodec;
    private final Codec<Either<net.neoforged.neoforge.registries.holdersets.ICustomHolderSet<E>, Either<TagKey<E>, List<Holder<E>>>>> combinedCodec;

    private static <E> Codec<List<Holder<E>>> homogenousList(Codec<Holder<E>> holderCodec, boolean disallowInline) {
        Codec<List<Holder<E>>> codec = holderCodec.listOf().validate(ExtraCodecs.ensureHomogenous(Holder::kind));
        return disallowInline
            ? codec
            : Codec.either(codec, holderCodec)
                .xmap(
                    p_206664_ -> p_206664_.map(p_206694_ -> p_206694_, List::of),
                    p_206684_ -> p_206684_.size() == 1 ? Either.right(p_206684_.get(0)) : Either.left((List<Holder<E>>)p_206684_)
                );
    }

    public static <E> Codec<HolderSet<E>> create(ResourceKey<? extends Registry<E>> registryKey, Codec<Holder<E>> holderCodec, boolean disallowInline) {
        return new HolderSetCodec<>(registryKey, holderCodec, disallowInline);
    }

    private HolderSetCodec(ResourceKey<? extends Registry<E>> registryKey, Codec<Holder<E>> elementCodec, boolean disallowInline) {
        this.registryKey = registryKey;
        this.elementCodec = elementCodec;
        this.homogenousListCodec = homogenousList(elementCodec, disallowInline);
        this.registryAwareCodec = Codec.either(TagKey.hashedCodec(registryKey), this.homogenousListCodec);
        // FORGE: make registry-specific dispatch codec and make forge-or-vanilla either codec
        this.forgeDispatchCodec = net.neoforged.neoforge.registries.NeoForgeRegistries.HOLDER_SET_TYPES.byNameCodec()
             .dispatch(net.neoforged.neoforge.registries.holdersets.ICustomHolderSet::type, type -> type.makeCodec(registryKey, elementCodec, disallowInline));
        this.combinedCodec = Codec.either(this.forgeDispatchCodec, this.registryAwareCodec);
    }

    @Override
    public <T> DataResult<Pair<HolderSet<E>, T>> decode(DynamicOps<T> ops, T input) {
        if (ops instanceof RegistryOps<T> registryops) {
            Optional<HolderGetter<E>> optional = registryops.getter(this.registryKey);
            if (optional.isPresent()) {
                HolderGetter<E> holdergetter = optional.get();
                // Neo: use the wrapped codec to decode custom/tag/list instead of just tag/list
                return this.combinedCodec.decode(ops, input)
                    .flatMap(
                        p_337522_ -> {
                            DataResult<HolderSet<E>> dataresult = p_337522_.getFirst()
                                .map(
                                    DataResult::success,
                                    tagOrList -> tagOrList.map(
                                    p_332559_ -> lookupTag(holdergetter, (TagKey<E>)p_332559_),
                                    p_332564_ -> DataResult.success(HolderSet.direct((List<? extends Holder<E>>)p_332564_))
                                    )
                                );
                            return dataresult.map(p_332563_ -> Pair.of((HolderSet<E>)p_332563_, (T)p_337522_.getSecond()));
                        }
                    );
            }
        }

        return this.decodeWithoutRegistry(ops, input);
    }

    private static <E> DataResult<HolderSet<E>> lookupTag(HolderGetter<E> input, TagKey<E> tagKey) {
        return input.get(tagKey)
            .<DataResult<HolderSet<E>>>map(DataResult::success)
            .orElseGet(() -> DataResult.error(() -> "Missing tag: '" + tagKey.location() + "' in '" + tagKey.registry().location() + "'"));
    }

    public <T> DataResult<T> encode(HolderSet<E> input, DynamicOps<T> ops, T prefix) {
        if (ops instanceof RegistryOps<T> registryops) {
            Optional<HolderOwner<E>> optional = registryops.owner(this.registryKey);
            if (optional.isPresent()) {
                if (!input.canSerializeIn(optional.get())) {
                    return DataResult.error(() -> "HolderSet " + input + " is not valid in current registry set");
                }

                // FORGE: use the dispatch codec to encode custom holdersets, otherwise fall back to vanilla tag/list
                if (input instanceof net.neoforged.neoforge.registries.holdersets.ICustomHolderSet<E> customHolderSet)
                     return this.forgeDispatchCodec.encode(customHolderSet, ops, prefix);
                return this.registryAwareCodec.encode(input.unwrap().mapRight(List::copyOf), ops, prefix);
            }
        }

        return this.encodeWithoutRegistry(input, ops, prefix);
    }

    private <T> DataResult<Pair<HolderSet<E>, T>> decodeWithoutRegistry(DynamicOps<T> ops, T input) {
        return this.elementCodec.listOf().decode(ops, input).flatMap(p_206666_ -> {
            List<Holder.Direct<E>> list = new ArrayList<>();

            for (Holder<E> holder : p_206666_.getFirst()) {
                if (!(holder instanceof Holder.Direct<E> direct)) {
                    return DataResult.error(() -> "Can't decode element " + holder + " without registry");
                }

                list.add(direct);
            }

            return DataResult.success(new Pair<>(HolderSet.direct(list), p_206666_.getSecond()));
        });
    }

    private <T> DataResult<T> encodeWithoutRegistry(HolderSet<E> input, DynamicOps<T> ops, T prefix) {
        return this.homogenousListCodec.encode(input.stream().toList(), ops, prefix);
    }
}
