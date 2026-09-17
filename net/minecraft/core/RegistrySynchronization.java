package net.minecraft.core;

import com.mojang.serialization.DynamicOps;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.repository.KnownPack;

public class RegistrySynchronization {
    public static final Set<ResourceKey<? extends Registry<?>>> NETWORKABLE_REGISTRIES = RegistryDataLoader.SYNCHRONIZED_REGISTRIES
        .stream()
        .map(RegistryDataLoader.RegistryData::key)
        .collect(Collectors.toUnmodifiableSet());

    public static void packRegistries(
        DynamicOps<Tag> ops,
        RegistryAccess registryAccess,
        Set<KnownPack> packs,
        BiConsumer<ResourceKey<? extends Registry<?>>, List<RegistrySynchronization.PackedRegistryEntry>> packetSender
    ) {
        RegistryDataLoader.SYNCHRONIZED_REGISTRIES
            .forEach(p_325532_ -> packRegistry(ops, (RegistryDataLoader.RegistryData<?>)p_325532_, registryAccess, packs, packetSender));
    }

    private static <T> void packRegistry(
        DynamicOps<Tag> ops,
        RegistryDataLoader.RegistryData<T> registryData,
        RegistryAccess registryAccess,
        Set<KnownPack> packs,
        BiConsumer<ResourceKey<? extends Registry<?>>, List<RegistrySynchronization.PackedRegistryEntry>> packetSender
    ) {
        registryAccess.registry(registryData.key())
            .ifPresent(
                p_344187_ -> {
                    List<RegistrySynchronization.PackedRegistryEntry> list = new ArrayList<>(p_344187_.size());
                    p_344187_.holders()
                        .forEach(
                            p_325522_ -> {
                                boolean flag = p_344187_.registrationInfo(p_325522_.key())
                                    .flatMap(RegistrationInfo::knownPackInfo)
                                    .filter(packs::contains)
                                    .isPresent();
                                Optional<Tag> optional;
                                if (flag) {
                                    optional = Optional.empty();
                                } else {
                                    Tag tag = registryData.elementCodec()
                                        .encodeStart(ops, p_325522_.value())
                                        .getOrThrow(p_339341_ -> new IllegalArgumentException("Failed to serialize " + p_325522_.key() + ": " + p_339341_));
                                    optional = Optional.of(tag);
                                }

                                list.add(new RegistrySynchronization.PackedRegistryEntry(p_325522_.key().location(), optional));
                            }
                        );
                    packetSender.accept(p_344187_.key(), list);
                }
            );
    }

    private static Stream<RegistryAccess.RegistryEntry<?>> ownedNetworkableRegistries(RegistryAccess registryAccess) {
        return registryAccess.registries().filter(p_321394_ -> NETWORKABLE_REGISTRIES.contains(p_321394_.key()));
    }

    public static Stream<RegistryAccess.RegistryEntry<?>> networkedRegistries(LayeredRegistryAccess<RegistryLayer> registryAccess) {
        return ownedNetworkableRegistries(registryAccess.getAccessFrom(RegistryLayer.WORLDGEN));
    }

    public static Stream<RegistryAccess.RegistryEntry<?>> networkSafeRegistries(LayeredRegistryAccess<RegistryLayer> registryAccess) {
        Stream<RegistryAccess.RegistryEntry<?>> stream = registryAccess.getLayer(RegistryLayer.STATIC).registries();
        Stream<RegistryAccess.RegistryEntry<?>> stream1 = networkedRegistries(registryAccess);
        return Stream.concat(stream1, stream);
    }

    public static record PackedRegistryEntry(ResourceLocation id, Optional<Tag> data) {
        public static final StreamCodec<ByteBuf, RegistrySynchronization.PackedRegistryEntry> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC,
            RegistrySynchronization.PackedRegistryEntry::id,
            ByteBufCodecs.TAG.apply(ByteBufCodecs::optional),
            RegistrySynchronization.PackedRegistryEntry::data,
            RegistrySynchronization.PackedRegistryEntry::new
        );
    }
}
