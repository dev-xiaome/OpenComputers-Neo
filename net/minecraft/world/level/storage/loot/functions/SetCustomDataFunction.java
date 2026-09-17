package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SetCustomDataFunction extends LootItemConditionalFunction {
    public static final MapCodec<SetCustomDataFunction> CODEC = RecordCodecBuilder.mapCodec(
        p_340803_ -> commonFields(p_340803_)
                .and(TagParser.LENIENT_CODEC.fieldOf("tag").forGetter(p_330759_ -> p_330759_.tag))
                .apply(p_340803_, SetCustomDataFunction::new)
    );
    private final CompoundTag tag;

    private SetCustomDataFunction(List<LootItemCondition> conditions, CompoundTag tag) {
        super(conditions);
        this.tag = tag;
    }

    @Override
    public LootItemFunctionType<SetCustomDataFunction> getType() {
        return LootItemFunctions.SET_CUSTOM_DATA;
    }

    /**
     * Called to perform the actual action of this function, after conditions have been checked.
     */
    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, p_331655_ -> p_331655_.merge(this.tag));
        return stack;
    }

    @Deprecated
    public static LootItemConditionalFunction.Builder<?> setCustomData(CompoundTag tag) {
        return simpleBuilder(p_330691_ -> new SetCustomDataFunction(p_330691_, tag));
    }
}
