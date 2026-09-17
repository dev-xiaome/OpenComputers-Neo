package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.Set;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;

/**
 * LootItemFunction that sets the stack's count based on a {@link NumberProvider}, optionally adding to any existing count.
 */
public class SetItemCountFunction extends LootItemConditionalFunction {
    public static final MapCodec<SetItemCountFunction> CODEC = RecordCodecBuilder.mapCodec(
        p_298131_ -> commonFields(p_298131_)
                .and(
                    p_298131_.group(
                        NumberProviders.CODEC.fieldOf("count").forGetter(p_298132_ -> p_298132_.value),
                        Codec.BOOL.fieldOf("add").orElse(false).forGetter(p_298133_ -> p_298133_.add)
                    )
                )
                .apply(p_298131_, SetItemCountFunction::new)
    );
    private final NumberProvider value;
    private final boolean add;

    private SetItemCountFunction(List<LootItemCondition> conditions, NumberProvider value, boolean add) {
        super(conditions);
        this.value = value;
        this.add = add;
    }

    @Override
    public LootItemFunctionType<SetItemCountFunction> getType() {
        return LootItemFunctions.SET_COUNT;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return this.value.getReferencedContextParams();
    }

    /**
     * Called to perform the actual action of this function, after conditions have been checked.
     */
    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        int i = this.add ? stack.getCount() : 0;
        stack.setCount(i + this.value.getInt(context));
        return stack;
    }

    public static LootItemConditionalFunction.Builder<?> setCount(NumberProvider countValue) {
        return simpleBuilder(p_298130_ -> new SetItemCountFunction(p_298130_, countValue, false));
    }

    public static LootItemConditionalFunction.Builder<?> setCount(NumberProvider countValue, boolean add) {
        return simpleBuilder(p_298128_ -> new SetItemCountFunction(p_298128_, countValue, add));
    }
}
