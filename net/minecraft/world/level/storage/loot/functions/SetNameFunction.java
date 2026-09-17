package net.minecraft.world.level.storage.loot.functions;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.slf4j.Logger;

/**
 * LootItemFunction that sets a stack's name.
 * The Component for the name is optionally resolved relative to a given {@link LootContext.EntityTarget} for entity-sensitive component data such as scoreboard scores.
 */
public class SetNameFunction extends LootItemConditionalFunction {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final MapCodec<SetNameFunction> CODEC = RecordCodecBuilder.mapCodec(
        p_338152_ -> commonFields(p_338152_)
                .and(
                    p_338152_.group(
                        ComponentSerialization.CODEC.optionalFieldOf("name").forGetter(p_298144_ -> p_298144_.name),
                        LootContext.EntityTarget.CODEC.optionalFieldOf("entity").forGetter(p_298153_ -> p_298153_.resolutionContext),
                        SetNameFunction.Target.CODEC.optionalFieldOf("target", SetNameFunction.Target.CUSTOM_NAME).forGetter(p_338153_ -> p_338153_.target)
                    )
                )
                .apply(p_338152_, SetNameFunction::new)
    );
    private final Optional<Component> name;
    private final Optional<LootContext.EntityTarget> resolutionContext;
    private final SetNameFunction.Target target;

    private SetNameFunction(
        List<LootItemCondition> conditions, Optional<Component> name, Optional<LootContext.EntityTarget> resolutionContext, SetNameFunction.Target target
    ) {
        super(conditions);
        this.name = name;
        this.resolutionContext = resolutionContext;
        this.target = target;
    }

    @Override
    public LootItemFunctionType<SetNameFunction> getType() {
        return LootItemFunctions.SET_NAME;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return this.resolutionContext.<Set<LootContextParam<?>>>map(p_298146_ -> Set.of(p_298146_.getParam())).orElse(Set.of());
    }

    /**
     * Create a UnaryOperator that resolves Components based on the given LootContext and EntityTarget.
     * This will replace for example score components.
     */
    public static UnaryOperator<Component> createResolver(LootContext lootContext, @Nullable LootContext.EntityTarget resolutionContext) {
        if (resolutionContext != null) {
            Entity entity = lootContext.getParamOrNull(resolutionContext.getParam());
            if (entity != null) {
                CommandSourceStack commandsourcestack = entity.createCommandSourceStack().withPermission(2);
                return p_81147_ -> {
                    try {
                        return ComponentUtils.updateForEntity(commandsourcestack, p_81147_, entity, 0);
                    } catch (CommandSyntaxException commandsyntaxexception) {
                        LOGGER.warn("Failed to resolve text component", (Throwable)commandsyntaxexception);
                        return p_81147_;
                    }
                };
            }
        }

        return p_81152_ -> p_81152_;
    }

    /**
     * Called to perform the actual action of this function, after conditions have been checked.
     */
    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        this.name.ifPresent(p_338163_ -> stack.set(this.target.component(), createResolver(context, this.resolutionContext.orElse(null)).apply(p_338163_)));
        return stack;
    }

    public static LootItemConditionalFunction.Builder<?> setName(Component name, SetNameFunction.Target target) {
        return simpleBuilder(p_338156_ -> new SetNameFunction(p_338156_, Optional.of(name), Optional.empty(), target));
    }

    public static LootItemConditionalFunction.Builder<?> setName(Component name, SetNameFunction.Target target, LootContext.EntityTarget resolutionContext) {
        return simpleBuilder(p_338160_ -> new SetNameFunction(p_338160_, Optional.of(name), Optional.of(resolutionContext), target));
    }

    public static enum Target implements StringRepresentable {
        CUSTOM_NAME("custom_name"),
        ITEM_NAME("item_name");

        public static final Codec<SetNameFunction.Target> CODEC = StringRepresentable.fromEnum(SetNameFunction.Target::values);
        private final String name;

        private Target(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public DataComponentType<Component> component() {
            return switch (this) {
                case CUSTOM_NAME -> DataComponents.CUSTOM_NAME;
                case ITEM_NAME -> DataComponents.ITEM_NAME;
            };
        }
    }
}
