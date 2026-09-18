package li.cil.oc.server.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Optional;
import li.cil.oc.util.ItemColorizer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/// 1.21.1：loot function 的序列化全面改为 codec（`net.minecraft.world.level.storage.loot.Serializer`
/// 与 `LootItemConditionalFunction.Serializer` 都已被移除），条件（predicates）也不再是数组，
/// 而是通过 `commonFields(inst)` 接进 `List<LootItemCondition>`。
public final class SetColor extends LootItemConditionalFunction {
    private static final Codec<Integer> COLOR_CODEC = Codec.intRange(0, 0xFFFFFF);

    public static final MapCodec<SetColor> CODEC = RecordCodecBuilder.mapCodec(inst -> commonFields(inst)
            .and(COLOR_CODEC.optionalFieldOf("color").forGetter(f -> f.color))
            .apply(inst, SetColor::new));

    private final Optional<Integer> color;

    private SetColor(List<LootItemCondition> conditions, Optional<Integer> color) {
        super(conditions);
        this.color = color;
    }

    @Override
    public LootItemFunctionType<SetColor> getType() {
        return LootFunctions.SET_COLOR.get();
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext ctx) {
        if (stack.isEmpty()) return stack;
        if (color.isPresent()) {
            ItemColorizer.setColor(stack, color.get());
        } else {
            ItemColorizer.removeColor(stack);
        }
        return stack;
    }

    public static class Builder extends LootItemConditionalFunction.Builder<Builder> {
        private Optional<Integer> color = Optional.empty();

        @Override
        protected Builder getThis() {
            return this;
        }

        public Builder withoutColor() {
            color = Optional.empty();
            return this;
        }

        public Builder withColor(int color) {
            if (color < 0 || color > 0xFFFFFF) throw new IllegalArgumentException("Invalid RGB color: " + color);
            this.color = Optional.of(color);
            return this;
        }

        @Override
        public SetColor build() {
            return new SetColor(getConditions(), color);
        }
    }

    public static Builder setColor() {
        return new Builder();
    }
}
