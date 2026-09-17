package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;

public class SetOminousBottleAmplifierFunction extends LootItemConditionalFunction {
    static final MapCodec<SetOminousBottleAmplifierFunction> CODEC = RecordCodecBuilder.mapCodec(
        p_338849_ -> commonFields(p_338849_)
                .and(NumberProviders.CODEC.fieldOf("amplifier").forGetter(p_338375_ -> p_338375_.amplifierGenerator))
                .apply(p_338849_, SetOminousBottleAmplifierFunction::new)
    );
    private final NumberProvider amplifierGenerator;

    private SetOminousBottleAmplifierFunction(List<LootItemCondition> conditions, NumberProvider amplifierGenerator) {
        super(conditions);
        this.amplifierGenerator = amplifierGenerator;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return this.amplifierGenerator.getReferencedContextParams();
    }

    @Override
    public LootItemFunctionType<SetOminousBottleAmplifierFunction> getType() {
        return LootItemFunctions.SET_OMINOUS_BOTTLE_AMPLIFIER;
    }

    /**
     * Called to perform the actual action of this function, after conditions have been checked.
     */
    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        int i = Mth.clamp(this.amplifierGenerator.getInt(context), 0, 4);
        stack.set(DataComponents.OMINOUS_BOTTLE_AMPLIFIER, i);
        return stack;
    }

    public NumberProvider amplifier() {
        return this.amplifierGenerator;
    }

    public static LootItemConditionalFunction.Builder<?> setAmplifier(NumberProvider amplifier) {
        return simpleBuilder(p_338611_ -> new SetOminousBottleAmplifierFunction(p_338611_, amplifier));
    }
}
