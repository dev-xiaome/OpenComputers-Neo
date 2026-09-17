package net.minecraft.client.multiplayer;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class TagCollector {
    private final Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> tags = new HashMap<>();

    public void append(ResourceKey<? extends Registry<?>> registryKey, TagNetworkSerialization.NetworkPayload networkPayload) {
        this.tags.put(registryKey, networkPayload);
    }

    private static void refreshBuiltInTagDependentData() {
        AbstractFurnaceBlockEntity.invalidateCache();
        Blocks.rebuildCache();
    }

    private void applyTags(RegistryAccess registryAccess, Predicate<ResourceKey<? extends Registry<?>>> filter) {
        this.tags.forEach((p_326303_, p_326438_) -> {
            if (filter.test((ResourceKey<? extends Registry<?>>)p_326303_)) {
                p_326438_.applyToRegistry(registryAccess.registryOrThrow((ResourceKey<? extends Registry<?>>)p_326303_));
            }
        });
    }

    public void updateTags(RegistryAccess registryAccess, boolean isMemoryConnection) {
        if (isMemoryConnection) {
            this.applyTags(registryAccess, RegistrySynchronization.NETWORKABLE_REGISTRIES::contains);
        } else {
            registryAccess.registries()
                .filter(p_325935_ -> !RegistrySynchronization.NETWORKABLE_REGISTRIES.contains(p_325935_.key()))
                .forEach(p_325919_ -> p_325919_.value().resetTags());
            this.applyTags(registryAccess, p_326446_ -> true);
            refreshBuiltInTagDependentData();
        }
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.TagsUpdatedEvent(registryAccess, true, isMemoryConnection));
    }
}
