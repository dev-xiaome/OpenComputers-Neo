package net.minecraft.data.models.model;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class ModelLocationUtils {
    @Deprecated
    public static ResourceLocation decorateBlockModelLocation(String blockModelLocation) {
        return ResourceLocation.withDefaultNamespace("block/" + blockModelLocation);
    }

    public static ResourceLocation decorateItemModelLocation(String itemModelLocation) {
        return ResourceLocation.withDefaultNamespace("item/" + itemModelLocation);
    }

    public static ResourceLocation getModelLocation(Block block, String modelLocationSuffix) {
        ResourceLocation resourcelocation = BuiltInRegistries.BLOCK.getKey(block);
        return resourcelocation.withPath(p_251253_ -> "block/" + p_251253_ + modelLocationSuffix);
    }

    public static ResourceLocation getModelLocation(Block block) {
        ResourceLocation resourcelocation = BuiltInRegistries.BLOCK.getKey(block);
        return resourcelocation.withPrefix("block/");
    }

    public static ResourceLocation getModelLocation(Item item) {
        ResourceLocation resourcelocation = BuiltInRegistries.ITEM.getKey(item);
        return resourcelocation.withPrefix("item/");
    }

    public static ResourceLocation getModelLocation(Item item, String modelLocationSuffix) {
        ResourceLocation resourcelocation = BuiltInRegistries.ITEM.getKey(item);
        return resourcelocation.withPath(p_251542_ -> "item/" + p_251542_ + modelLocationSuffix);
    }
}
