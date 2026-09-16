package li.cil.oc.server.loot;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import li.cil.oc.api.internal.Colored;
import li.cil.oc.util.ItemColorizer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.jetbrains.annotations.NotNull;

public final class CopyColor extends LootItemConditionalFunction {
    private CopyColor(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    public LootItemFunctionType getType() {
        return LootFunctions.COPY_COLOR.get();
    }

    public static LootItemConditionalFunction.Builder<?> copyColor() {
        return simpleBuilder(CopyColor::new);
    }

    @Override
    public @NotNull ItemStack run(ItemStack stack, @NotNull LootContext ctx) {
        if (stack.isEmpty()) return stack;

        BlockEntity be = ctx.getParamOrNull(LootContextParams.BLOCK_ENTITY);

        if (be instanceof Colored colored) {
            ItemColorizer.setColor(stack, colored.getColor());
        } else {
            ItemColorizer.removeColor(stack);
        }
        return stack;
    }

    public static class Serializer extends LootItemConditionalFunction.Serializer<CopyColor> {
        @Override
        public CopyColor deserialize(JsonObject src, JsonDeserializationContext ctx, LootItemCondition[] conditions) {
            return new CopyColor(conditions);
        }
    }
}