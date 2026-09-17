package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SetItemFunction extends LootItemConditionalFunction {
    public static final MapCodec<SetItemFunction> CODEC = RecordCodecBuilder.mapCodec(
        p_341309_ -> commonFields(p_341309_)
                .and(RegistryFixedCodec.create(Registries.ITEM).fieldOf("item").forGetter(p_340993_ -> p_340993_.item))
                .apply(p_341309_, SetItemFunction::new)
    );
    private final Holder<Item> item;

    public SetItemFunction(List<LootItemCondition> conditions, Holder<Item> item) {
        super(conditions);
        this.item = item;
    }

    @Override
    public LootItemFunctionType<SetItemFunction> getType() {
        return LootItemFunctions.SET_ITEM;
    }

    /**
     * Called to perform the actual action of this function, after conditions have been checked.
     */
    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        return stack.transmuteCopy(this.item.value());
    }
}
