package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.ContainerComponentManipulator;
import net.minecraft.world.level.storage.loot.ContainerComponentManipulators;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class ModifyContainerContents extends LootItemConditionalFunction {
    public static final MapCodec<ModifyContainerContents> CODEC = RecordCodecBuilder.mapCodec(
        p_341141_ -> commonFields(p_341141_)
                .and(
                    p_341141_.group(
                        ContainerComponentManipulators.CODEC.fieldOf("component").forGetter(p_340814_ -> p_340814_.component),
                        LootItemFunctions.ROOT_CODEC.fieldOf("modifier").forGetter(p_341108_ -> p_341108_.modifier)
                    )
                )
                .apply(p_341141_, ModifyContainerContents::new)
    );
    private final ContainerComponentManipulator<?> component;
    private final LootItemFunction modifier;

    public ModifyContainerContents(List<LootItemCondition> conditions, ContainerComponentManipulator<?> components, LootItemFunction modifier) {
        super(conditions);
        this.component = components;
        this.modifier = modifier;
    }

    @Override
    public LootItemFunctionType<ModifyContainerContents> getType() {
        return LootItemFunctions.MODIFY_CONTENTS;
    }

    /**
     * Called to perform the actual action of this function, after conditions have been checked.
     */
    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        if (stack.isEmpty()) {
            return stack;
        } else {
            this.component.modifyItems(stack, p_341413_ -> this.modifier.apply(p_341413_, context));
            return stack;
        }
    }

    /**
     * Validate that this object is used correctly according to the given ValidationContext.
     */
    @Override
    public void validate(ValidationContext context) {
        super.validate(context);
        this.modifier.validate(context.forChild(".modifier"));
    }
}
