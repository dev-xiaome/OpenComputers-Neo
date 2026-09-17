package net.minecraft.world.level.storage.loot.functions;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.Set;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;
import org.slf4j.Logger;

/**
 * LootItemFunction that sets the stack's damage based on a {@link NumberProvider}, optionally adding to any existing damage.
 */
public class SetItemDamageFunction extends LootItemConditionalFunction {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final MapCodec<SetItemDamageFunction> CODEC = RecordCodecBuilder.mapCodec(
        p_298138_ -> commonFields(p_298138_)
                .and(
                    p_298138_.group(
                        NumberProviders.CODEC.fieldOf("damage").forGetter(p_298141_ -> p_298141_.damage),
                        Codec.BOOL.fieldOf("add").orElse(false).forGetter(p_298134_ -> p_298134_.add)
                    )
                )
                .apply(p_298138_, SetItemDamageFunction::new)
    );
    private final NumberProvider damage;
    private final boolean add;

    private SetItemDamageFunction(List<LootItemCondition> conditions, NumberProvider damage, boolean add) {
        super(conditions);
        this.damage = damage;
        this.add = add;
    }

    @Override
    public LootItemFunctionType<SetItemDamageFunction> getType() {
        return LootItemFunctions.SET_DAMAGE;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return this.damage.getReferencedContextParams();
    }

    /**
     * Called to perform the actual action of this function, after conditions have been checked.
     */
    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        if (stack.isDamageableItem()) {
            int i = stack.getMaxDamage();
            float f = this.add ? 1.0F - (float)stack.getDamageValue() / (float)i : 0.0F;
            float f1 = 1.0F - Mth.clamp(this.damage.getFloat(context) + f, 0.0F, 1.0F);
            stack.setDamageValue(Mth.floor(f1 * (float)i));
        } else {
            LOGGER.warn("Couldn't set damage of loot item {}", stack);
        }

        return stack;
    }

    public static LootItemConditionalFunction.Builder<?> setDamage(NumberProvider damageValue) {
        return simpleBuilder(p_298140_ -> new SetItemDamageFunction(p_298140_, damageValue, false));
    }

    public static LootItemConditionalFunction.Builder<?> setDamage(NumberProvider damageValue, boolean add) {
        return simpleBuilder(p_298137_ -> new SetItemDamageFunction(p_298137_, damageValue, add));
    }
}
