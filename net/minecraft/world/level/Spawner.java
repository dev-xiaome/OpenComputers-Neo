package net.minecraft.world.level;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public interface Spawner {
    void setEntityId(EntityType<?> entityType, RandomSource random);

    static void appendHoverText(ItemStack stack, List<Component> tooltipLines, String spawnDataKey) {
        Component component = getSpawnEntityDisplayName(stack, spawnDataKey);
        if (component != null) {
            tooltipLines.add(component);
        } else {
            tooltipLines.add(CommonComponents.EMPTY);
            tooltipLines.add(Component.translatable("block.minecraft.spawner.desc1").withStyle(ChatFormatting.GRAY));
            tooltipLines.add(CommonComponents.space().append(Component.translatable("block.minecraft.spawner.desc2").withStyle(ChatFormatting.BLUE)));
        }
    }

    @Nullable
    static Component getSpawnEntityDisplayName(ItemStack stack, String spawnDataKey) {
        CompoundTag compoundtag = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).getUnsafe();
        ResourceLocation resourcelocation = getEntityKey(compoundtag, spawnDataKey);
        return resourcelocation != null
            ? BuiltInRegistries.ENTITY_TYPE
                .getOptional(resourcelocation)
                .map(p_312609_ -> Component.translatable(p_312609_.getDescriptionId()).withStyle(ChatFormatting.GRAY))
                .orElse(null)
            : null;
    }

    @Nullable
    private static ResourceLocation getEntityKey(CompoundTag tag, String spawnDataKey) {
        if (tag.contains(spawnDataKey, 10)) {
            String s = tag.getCompound(spawnDataKey).getCompound("entity").getString("id");
            return ResourceLocation.tryParse(s);
        } else {
            return null;
        }
    }
}
