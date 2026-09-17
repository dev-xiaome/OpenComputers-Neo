package net.minecraft.server.commands;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public class EffectCommands {
    private static final SimpleCommandExceptionType ERROR_GIVE_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.effect.give.failed"));
    private static final SimpleCommandExceptionType ERROR_CLEAR_EVERYTHING_FAILED = new SimpleCommandExceptionType(
        Component.translatable("commands.effect.clear.everything.failed")
    );
    private static final SimpleCommandExceptionType ERROR_CLEAR_SPECIFIC_FAILED = new SimpleCommandExceptionType(
        Component.translatable("commands.effect.clear.specific.failed")
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("effect")
                .requires(p_136958_ -> p_136958_.hasPermission(2))
                .then(
                    Commands.literal("clear")
                        .executes(p_136984_ -> clearEffects(p_136984_.getSource(), ImmutableList.of(p_136984_.getSource().getEntityOrException())))
                        .then(
                            Commands.argument("targets", EntityArgument.entities())
                                .executes(p_136982_ -> clearEffects(p_136982_.getSource(), EntityArgument.getEntities(p_136982_, "targets")))
                                .then(
                                    Commands.argument("effect", ResourceArgument.resource(context, Registries.MOB_EFFECT))
                                        .executes(
                                            p_248126_ -> clearEffect(
                                                    p_248126_.getSource(),
                                                    EntityArgument.getEntities(p_248126_, "targets"),
                                                    ResourceArgument.getMobEffect(p_248126_, "effect")
                                                )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("give")
                        .then(
                            Commands.argument("targets", EntityArgument.entities())
                                .then(
                                    Commands.argument("effect", ResourceArgument.resource(context, Registries.MOB_EFFECT))
                                        .executes(
                                            p_248127_ -> giveEffect(
                                                    p_248127_.getSource(),
                                                    EntityArgument.getEntities(p_248127_, "targets"),
                                                    ResourceArgument.getMobEffect(p_248127_, "effect"),
                                                    null,
                                                    0,
                                                    true
                                                )
                                        )
                                        .then(
                                            Commands.argument("seconds", IntegerArgumentType.integer(1, 1000000))
                                                .executes(
                                                    p_248124_ -> giveEffect(
                                                            p_248124_.getSource(),
                                                            EntityArgument.getEntities(p_248124_, "targets"),
                                                            ResourceArgument.getMobEffect(p_248124_, "effect"),
                                                            IntegerArgumentType.getInteger(p_248124_, "seconds"),
                                                            0,
                                                            true
                                                        )
                                                )
                                                .then(
                                                    Commands.argument("amplifier", IntegerArgumentType.integer(0, 255))
                                                        .executes(
                                                            p_248123_ -> giveEffect(
                                                                    p_248123_.getSource(),
                                                                    EntityArgument.getEntities(p_248123_, "targets"),
                                                                    ResourceArgument.getMobEffect(p_248123_, "effect"),
                                                                    IntegerArgumentType.getInteger(p_248123_, "seconds"),
                                                                    IntegerArgumentType.getInteger(p_248123_, "amplifier"),
                                                                    true
                                                                )
                                                        )
                                                        .then(
                                                            Commands.argument("hideParticles", BoolArgumentType.bool())
                                                                .executes(
                                                                    p_248125_ -> giveEffect(
                                                                            p_248125_.getSource(),
                                                                            EntityArgument.getEntities(p_248125_, "targets"),
                                                                            ResourceArgument.getMobEffect(p_248125_, "effect"),
                                                                            IntegerArgumentType.getInteger(p_248125_, "seconds"),
                                                                            IntegerArgumentType.getInteger(p_248125_, "amplifier"),
                                                                            !BoolArgumentType.getBool(p_248125_, "hideParticles")
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                        .then(
                                            Commands.literal("infinite")
                                                .executes(
                                                    p_267907_ -> giveEffect(
                                                            p_267907_.getSource(),
                                                            EntityArgument.getEntities(p_267907_, "targets"),
                                                            ResourceArgument.getMobEffect(p_267907_, "effect"),
                                                            -1,
                                                            0,
                                                            true
                                                        )
                                                )
                                                .then(
                                                    Commands.argument("amplifier", IntegerArgumentType.integer(0, 255))
                                                        .executes(
                                                            p_267908_ -> giveEffect(
                                                                    p_267908_.getSource(),
                                                                    EntityArgument.getEntities(p_267908_, "targets"),
                                                                    ResourceArgument.getMobEffect(p_267908_, "effect"),
                                                                    -1,
                                                                    IntegerArgumentType.getInteger(p_267908_, "amplifier"),
                                                                    true
                                                                )
                                                        )
                                                        .then(
                                                            Commands.argument("hideParticles", BoolArgumentType.bool())
                                                                .executes(
                                                                    p_267909_ -> giveEffect(
                                                                            p_267909_.getSource(),
                                                                            EntityArgument.getEntities(p_267909_, "targets"),
                                                                            ResourceArgument.getMobEffect(p_267909_, "effect"),
                                                                            -1,
                                                                            IntegerArgumentType.getInteger(p_267909_, "amplifier"),
                                                                            !BoolArgumentType.getBool(p_267909_, "hideParticles")
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

    private static int giveEffect(
        CommandSourceStack source,
        Collection<? extends Entity> targets,
        Holder<MobEffect> effect,
        @Nullable Integer seconds,
        int amplifier,
        boolean showParticles
    ) throws CommandSyntaxException {
        MobEffect mobeffect = effect.value();
        int i = 0;
        int j;
        if (seconds != null) {
            if (mobeffect.isInstantenous()) {
                j = seconds;
            } else if (seconds == -1) {
                j = -1;
            } else {
                j = seconds * 20;
            }
        } else if (mobeffect.isInstantenous()) {
            j = 1;
        } else {
            j = 600;
        }

        for (Entity entity : targets) {
            if (entity instanceof LivingEntity) {
                MobEffectInstance mobeffectinstance = new MobEffectInstance(effect, j, amplifier, false, showParticles);
                if (((LivingEntity)entity).addEffect(mobeffectinstance, source.getEntity())) {
                    i++;
                }
            }
        }

        if (i == 0) {
            throw ERROR_GIVE_FAILED.create();
        } else {
            if (targets.size() == 1) {
                source.sendSuccess(
                    () -> Component.translatable(
                            "commands.effect.give.success.single", mobeffect.getDisplayName(), targets.iterator().next().getDisplayName(), j / 20
                        ),
                    true
                );
            } else {
                source.sendSuccess(
                    () -> Component.translatable("commands.effect.give.success.multiple", mobeffect.getDisplayName(), targets.size(), j / 20), true
                );
            }

            return i;
        }
    }

    private static int clearEffects(CommandSourceStack source, Collection<? extends Entity> targets) throws CommandSyntaxException {
        int i = 0;

        for (Entity entity : targets) {
            if (entity instanceof LivingEntity && ((LivingEntity)entity).removeAllEffects()) {
                i++;
            }
        }

        if (i == 0) {
            throw ERROR_CLEAR_EVERYTHING_FAILED.create();
        } else {
            if (targets.size() == 1) {
                source.sendSuccess(
                    () -> Component.translatable("commands.effect.clear.everything.success.single", targets.iterator().next().getDisplayName()), true
                );
            } else {
                source.sendSuccess(() -> Component.translatable("commands.effect.clear.everything.success.multiple", targets.size()), true);
            }

            return i;
        }
    }

    private static int clearEffect(CommandSourceStack source, Collection<? extends Entity> targets, Holder<MobEffect> effect) throws CommandSyntaxException {
        MobEffect mobeffect = effect.value();
        int i = 0;

        for (Entity entity : targets) {
            if (entity instanceof LivingEntity && ((LivingEntity)entity).removeEffect(effect)) {
                i++;
            }
        }

        if (i == 0) {
            throw ERROR_CLEAR_SPECIFIC_FAILED.create();
        } else {
            if (targets.size() == 1) {
                source.sendSuccess(
                    () -> Component.translatable(
                            "commands.effect.clear.specific.success.single", mobeffect.getDisplayName(), targets.iterator().next().getDisplayName()
                        ),
                    true
                );
            } else {
                source.sendSuccess(
                    () -> Component.translatable("commands.effect.clear.specific.success.multiple", mobeffect.getDisplayName(), targets.size()), true
                );
            }

            return i;
        }
    }
}
