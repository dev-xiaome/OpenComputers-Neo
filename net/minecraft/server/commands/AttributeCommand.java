package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.Dynamic3CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

public class AttributeCommand {
    private static final DynamicCommandExceptionType ERROR_NOT_LIVING_ENTITY = new DynamicCommandExceptionType(
        p_304174_ -> Component.translatableEscape("commands.attribute.failed.entity", p_304174_)
    );
    private static final Dynamic2CommandExceptionType ERROR_NO_SUCH_ATTRIBUTE = new Dynamic2CommandExceptionType(
        (p_304185_, p_304186_) -> Component.translatableEscape("commands.attribute.failed.no_attribute", p_304185_, p_304186_)
    );
    private static final Dynamic3CommandExceptionType ERROR_NO_SUCH_MODIFIER = new Dynamic3CommandExceptionType(
        (p_304182_, p_304183_, p_304184_) -> Component.translatableEscape("commands.attribute.failed.no_modifier", p_304183_, p_304182_, p_304184_)
    );
    private static final Dynamic3CommandExceptionType ERROR_MODIFIER_ALREADY_PRESENT = new Dynamic3CommandExceptionType(
        (p_304187_, p_304188_, p_304189_) -> Component.translatableEscape("commands.attribute.failed.modifier_already_present", p_304189_, p_304188_, p_304187_)
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("attribute")
                .requires(p_212441_ -> p_212441_.hasPermission(2))
                .then(
                    Commands.argument("target", EntityArgument.entity())
                        .then(
                            Commands.argument("attribute", ResourceArgument.resource(context, Registries.ATTRIBUTE))
                                .then(
                                    Commands.literal("get")
                                        .executes(
                                            p_248109_ -> getAttributeValue(
                                                    p_248109_.getSource(),
                                                    EntityArgument.getEntity(p_248109_, "target"),
                                                    ResourceArgument.getAttribute(p_248109_, "attribute"),
                                                    1.0
                                                )
                                        )
                                        .then(
                                            Commands.argument("scale", DoubleArgumentType.doubleArg())
                                                .executes(
                                                    p_248104_ -> getAttributeValue(
                                                            p_248104_.getSource(),
                                                            EntityArgument.getEntity(p_248104_, "target"),
                                                            ResourceArgument.getAttribute(p_248104_, "attribute"),
                                                            DoubleArgumentType.getDouble(p_248104_, "scale")
                                                        )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("base")
                                        .then(
                                            Commands.literal("set")
                                                .then(
                                                    Commands.argument("value", DoubleArgumentType.doubleArg())
                                                        .executes(
                                                            p_248102_ -> setAttributeBase(
                                                                    p_248102_.getSource(),
                                                                    EntityArgument.getEntity(p_248102_, "target"),
                                                                    ResourceArgument.getAttribute(p_248102_, "attribute"),
                                                                    DoubleArgumentType.getDouble(p_248102_, "value")
                                                                )
                                                        )
                                                )
                                        )
                                        .then(
                                            Commands.literal("get")
                                                .executes(
                                                    p_248112_ -> getAttributeBase(
                                                            p_248112_.getSource(),
                                                            EntityArgument.getEntity(p_248112_, "target"),
                                                            ResourceArgument.getAttribute(p_248112_, "attribute"),
                                                            1.0
                                                        )
                                                )
                                                .then(
                                                    Commands.argument("scale", DoubleArgumentType.doubleArg())
                                                        .executes(
                                                            p_248106_ -> getAttributeBase(
                                                                    p_248106_.getSource(),
                                                                    EntityArgument.getEntity(p_248106_, "target"),
                                                                    ResourceArgument.getAttribute(p_248106_, "attribute"),
                                                                    DoubleArgumentType.getDouble(p_248106_, "scale")
                                                                )
                                                        )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("modifier")
                                        .then(
                                            Commands.literal("add")
                                                .then(
                                                    Commands.argument("id", ResourceLocationArgument.id())
                                                        .then(
                                                            Commands.argument("value", DoubleArgumentType.doubleArg())
                                                                .then(
                                                                    Commands.literal("add_value")
                                                                        .executes(
                                                                            p_349940_ -> addModifier(
                                                                                    p_349940_.getSource(),
                                                                                    EntityArgument.getEntity(p_349940_, "target"),
                                                                                    ResourceArgument.getAttribute(p_349940_, "attribute"),
                                                                                    ResourceLocationArgument.getId(p_349940_, "id"),
                                                                                    DoubleArgumentType.getDouble(p_349940_, "value"),
                                                                                    AttributeModifier.Operation.ADD_VALUE
                                                                                )
                                                                        )
                                                                )
                                                                .then(
                                                                    Commands.literal("add_multiplied_base")
                                                                        .executes(
                                                                            p_349930_ -> addModifier(
                                                                                    p_349930_.getSource(),
                                                                                    EntityArgument.getEntity(p_349930_, "target"),
                                                                                    ResourceArgument.getAttribute(p_349930_, "attribute"),
                                                                                    ResourceLocationArgument.getId(p_349930_, "id"),
                                                                                    DoubleArgumentType.getDouble(p_349930_, "value"),
                                                                                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE
                                                                                )
                                                                        )
                                                                )
                                                                .then(
                                                                    Commands.literal("add_multiplied_total")
                                                                        .executes(
                                                                            p_349945_ -> addModifier(
                                                                                    p_349945_.getSource(),
                                                                                    EntityArgument.getEntity(p_349945_, "target"),
                                                                                    ResourceArgument.getAttribute(p_349945_, "attribute"),
                                                                                    ResourceLocationArgument.getId(p_349945_, "id"),
                                                                                    DoubleArgumentType.getDouble(p_349945_, "value"),
                                                                                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                        .then(
                                            Commands.literal("remove")
                                                .then(
                                                    Commands.argument("id", ResourceLocationArgument.id())
                                                        .executes(
                                                            p_349938_ -> removeModifier(
                                                                    p_349938_.getSource(),
                                                                    EntityArgument.getEntity(p_349938_, "target"),
                                                                    ResourceArgument.getAttribute(p_349938_, "attribute"),
                                                                    ResourceLocationArgument.getId(p_349938_, "id")
                                                                )
                                                        )
                                                )
                                        )
                                        .then(
                                            Commands.literal("value")
                                                .then(
                                                    Commands.literal("get")
                                                        .then(
                                                            Commands.argument("id", ResourceLocationArgument.id())
                                                                .executes(
                                                                    p_349941_ -> getAttributeModifier(
                                                                            p_349941_.getSource(),
                                                                            EntityArgument.getEntity(p_349941_, "target"),
                                                                            ResourceArgument.getAttribute(p_349941_, "attribute"),
                                                                            ResourceLocationArgument.getId(p_349941_, "id"),
                                                                            1.0
                                                                        )
                                                                )
                                                                .then(
                                                                    Commands.argument("scale", DoubleArgumentType.doubleArg())
                                                                        .executes(
                                                                            p_349939_ -> getAttributeModifier(
                                                                                    p_349939_.getSource(),
                                                                                    EntityArgument.getEntity(p_349939_, "target"),
                                                                                    ResourceArgument.getAttribute(p_349939_, "attribute"),
                                                                                    ResourceLocationArgument.getId(p_349939_, "id"),
                                                                                    DoubleArgumentType.getDouble(p_349939_, "scale")
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static AttributeInstance getAttributeInstance(Entity entity, Holder<Attribute> attribute) throws CommandSyntaxException {
        AttributeInstance attributeinstance = getLivingEntity(entity).getAttributes().getInstance(attribute);
        if (attributeinstance == null) {
            throw ERROR_NO_SUCH_ATTRIBUTE.create(entity.getName(), getAttributeDescription(attribute));
        } else {
            return attributeinstance;
        }
    }

    private static LivingEntity getLivingEntity(Entity target) throws CommandSyntaxException {
        if (!(target instanceof LivingEntity)) {
            throw ERROR_NOT_LIVING_ENTITY.create(target.getName());
        } else {
            return (LivingEntity)target;
        }
    }

    private static LivingEntity getEntityWithAttribute(Entity entity, Holder<Attribute> attribute) throws CommandSyntaxException {
        LivingEntity livingentity = getLivingEntity(entity);
        if (!livingentity.getAttributes().hasAttribute(attribute)) {
            throw ERROR_NO_SUCH_ATTRIBUTE.create(entity.getName(), getAttributeDescription(attribute));
        } else {
            return livingentity;
        }
    }

    private static int getAttributeValue(CommandSourceStack source, Entity entity, Holder<Attribute> attribute, double scale) throws CommandSyntaxException {
        LivingEntity livingentity = getEntityWithAttribute(entity, attribute);
        double d0 = livingentity.getAttributeValue(attribute);
        source.sendSuccess(
            () -> Component.translatable("commands.attribute.value.get.success", getAttributeDescription(attribute), entity.getName(), d0), false
        );
        return (int)(d0 * scale);
    }

    private static int getAttributeBase(CommandSourceStack source, Entity entity, Holder<Attribute> attribute, double scale) throws CommandSyntaxException {
        LivingEntity livingentity = getEntityWithAttribute(entity, attribute);
        double d0 = livingentity.getAttributeBaseValue(attribute);
        source.sendSuccess(
            () -> Component.translatable("commands.attribute.base_value.get.success", getAttributeDescription(attribute), entity.getName(), d0), false
        );
        return (int)(d0 * scale);
    }

    private static int getAttributeModifier(
        CommandSourceStack source, Entity entity, Holder<Attribute> attribute, ResourceLocation id, double scale
    ) throws CommandSyntaxException {
        LivingEntity livingentity = getEntityWithAttribute(entity, attribute);
        AttributeMap attributemap = livingentity.getAttributes();
        if (!attributemap.hasModifier(attribute, id)) {
            throw ERROR_NO_SUCH_MODIFIER.create(entity.getName(), getAttributeDescription(attribute), id);
        } else {
            double d0 = attributemap.getModifierValue(attribute, id);
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.attribute.modifier.value.get.success",
                        Component.translationArg(id),
                        getAttributeDescription(attribute),
                        entity.getName(),
                        d0
                    ),
                false
            );
            return (int)(d0 * scale);
        }
    }

    private static int setAttributeBase(CommandSourceStack source, Entity entity, Holder<Attribute> attribute, double value) throws CommandSyntaxException {
        getAttributeInstance(entity, attribute).setBaseValue(value);
        source.sendSuccess(
            () -> Component.translatable("commands.attribute.base_value.set.success", getAttributeDescription(attribute), entity.getName(), value),
            false
        );
        return 1;
    }

    private static int addModifier(
        CommandSourceStack source,
        Entity entity,
        Holder<Attribute> attribute,
        ResourceLocation id,
        double amount,
        AttributeModifier.Operation operation
    ) throws CommandSyntaxException {
        AttributeInstance attributeinstance = getAttributeInstance(entity, attribute);
        AttributeModifier attributemodifier = new AttributeModifier(id, amount, operation);
        if (attributeinstance.hasModifier(id)) {
            throw ERROR_MODIFIER_ALREADY_PRESENT.create(entity.getName(), getAttributeDescription(attribute), id);
        } else {
            attributeinstance.addPermanentModifier(attributemodifier);
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.attribute.modifier.add.success", Component.translationArg(id), getAttributeDescription(attribute), entity.getName()
                    ),
                false
            );
            return 1;
        }
    }

    private static int removeModifier(CommandSourceStack source, Entity entity, Holder<Attribute> attribute, ResourceLocation id) throws CommandSyntaxException {
        AttributeInstance attributeinstance = getAttributeInstance(entity, attribute);
        if (attributeinstance.removeModifier(id)) {
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.attribute.modifier.remove.success",
                        Component.translationArg(id),
                        getAttributeDescription(attribute),
                        entity.getName()
                    ),
                false
            );
            return 1;
        } else {
            throw ERROR_NO_SUCH_MODIFIER.create(entity.getName(), getAttributeDescription(attribute), id);
        }
    }

    private static Component getAttributeDescription(Holder<Attribute> attribute) {
        return Component.translatable(attribute.value().getDescriptionId());
    }
}
