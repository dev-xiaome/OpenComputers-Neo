package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.Set;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.IntRange;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * A LootItemFunction that limits the stack's count to fall within a given {@link IntRange}.
 */
public class LimitCount extends LootItemConditionalFunction {
    public static final MapCodec<LimitCount> CODEC = RecordCodecBuilder.mapCodec(
        p_298095_ -> commonFields(p_298095_).and(IntRange.CODEC.fieldOf("limit").forGetter(p_298094_ -> p_298094_.limiter)).apply(p_298095_, LimitCount::new)
    );
    private final IntRange limiter;

    private LimitCount(List<LootItemCondition> conditions, IntRange limiter) {
        super(conditions);
        this.limiter = limiter;
    }

    @Override
    public LootItemFunctionType<LimitCount> getType() {
        return LootItemFunctions.LIMIT_COUNT;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return this.limiter.getReferencedContextParams();
    }

    /**
     * Called to perform the actual action of this function, after conditions have been checked.
     */
    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        int i = this.limiter.clamp(context, stack.getCount());
        stack.setCount(i);
        return stack;
    }

    public static LootItemConditionalFunction.Builder<?> limitCount(IntRange countLimit) {
        return simpleBuilder(p_298093_ -> new LimitCount(p_298093_, countLimit));
    }
}
