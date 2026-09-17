package net.minecraft.world.level.storage.loot.entries;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * A loot pool entry that always generates a given item.
 */
public class LootItem extends LootPoolSingletonContainer {
    public static final MapCodec<LootItem> CODEC = RecordCodecBuilder.mapCodec(
        p_344670_ -> p_344670_.group(BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("name").forGetter(p_298016_ -> p_298016_.item))
                .and(singletonFields(p_344670_))
                .apply(p_344670_, LootItem::new)
    );
    private final Holder<Item> item;

    private LootItem(Holder<Item> item, int weight, int quality, List<LootItemCondition> conditions, List<LootItemFunction> functions) {
        super(weight, quality, conditions, functions);
        this.item = item;
    }

    @Override
    public LootPoolEntryType getType() {
        return LootPoolEntries.ITEM;
    }

    /**
     * Generate the loot stacks of this entry.
     * Contrary to the method name this method does not always generate one stack, it can also generate zero or multiple stacks.
     */
    @Override
    public void createItemStack(Consumer<ItemStack> stackConsumer, LootContext lootContext) {
        stackConsumer.accept(new ItemStack(this.item));
    }

    public static LootPoolSingletonContainer.Builder<?> lootTableItem(ItemLike item) {
        return simpleBuilder(
            (p_298018_, p_298019_, p_298020_, p_298021_) -> new LootItem(item.asItem().builtInRegistryHolder(), p_298018_, p_298019_, p_298020_, p_298021_)
        );
    }
}
