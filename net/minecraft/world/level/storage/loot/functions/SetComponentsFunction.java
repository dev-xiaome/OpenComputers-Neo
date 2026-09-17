package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SetComponentsFunction extends LootItemConditionalFunction {
    public static final MapCodec<SetComponentsFunction> CODEC = RecordCodecBuilder.mapCodec(
        p_331915_ -> commonFields(p_331915_)
                .and(DataComponentPatch.CODEC.fieldOf("components").forGetter(p_331262_ -> p_331262_.components))
                .apply(p_331915_, SetComponentsFunction::new)
    );
    private final DataComponentPatch components;

    private SetComponentsFunction(List<LootItemCondition> condition, DataComponentPatch components) {
        super(condition);
        this.components = components;
    }

    @Override
    public LootItemFunctionType<SetComponentsFunction> getType() {
        return LootItemFunctions.SET_COMPONENTS;
    }

    /**
     * Called to perform the actual action of this function, after conditions have been checked.
     */
    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        stack.applyComponentsAndValidate(this.components);
        return stack;
    }

    public static <T> LootItemConditionalFunction.Builder<?> setComponent(DataComponentType<T> component, T value) {
        return simpleBuilder(p_331753_ -> new SetComponentsFunction(p_331753_, DataComponentPatch.builder().set(component, value).build()));
    }
}
