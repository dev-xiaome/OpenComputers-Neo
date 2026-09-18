package li.cil.oc.server.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import li.cil.oc.api.internal.Colored;
import li.cil.oc.util.ItemColorizer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/// 见 [SetColor]：1.21.1 的 loot function 改用 codec 序列化。
public final class CopyColor extends LootItemConditionalFunction {
    public static final MapCodec<CopyColor> CODEC = RecordCodecBuilder.mapCodec(inst ->
            commonFields(inst).apply(inst, CopyColor::new));

    private CopyColor(List<LootItemCondition> conditions) {
        super(conditions);
    }

    @Override
    public LootItemFunctionType<CopyColor> getType() {
        return LootFunctions.COPY_COLOR.get();
    }

    public static LootItemConditionalFunction.Builder<?> copyColor() {
        return simpleBuilder(CopyColor::new);
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext ctx) {
        if (stack.isEmpty()) return stack;

        BlockEntity be = ctx.getParamOrNull(LootContextParams.BLOCK_ENTITY);

        if (be instanceof Colored colored) {
            ItemColorizer.setColor(stack, colored.getColor());
        } else {
            ItemColorizer.removeColor(stack);
        }
        return stack;
    }
}
