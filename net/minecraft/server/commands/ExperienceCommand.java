package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.ToIntFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public class ExperienceCommand {
    private static final SimpleCommandExceptionType ERROR_SET_POINTS_INVALID = new SimpleCommandExceptionType(
        Component.translatable("commands.experience.set.points.invalid")
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> literalcommandnode = dispatcher.register(
            Commands.literal("experience")
                .requires(p_137324_ -> p_137324_.hasPermission(2))
                .then(
                    Commands.literal("add")
                        .then(
                            Commands.argument("targets", EntityArgument.players())
                                .then(
                                    Commands.argument("amount", IntegerArgumentType.integer())
                                        .executes(
                                            p_137341_ -> addExperience(
                                                    p_137341_.getSource(),
                                                    EntityArgument.getPlayers(p_137341_, "targets"),
                                                    IntegerArgumentType.getInteger(p_137341_, "amount"),
                                                    ExperienceCommand.Type.POINTS
                                                )
                                        )
                                        .then(
                                            Commands.literal("points")
                                                .executes(
                                                    p_137339_ -> addExperience(
                                                            p_137339_.getSource(),
                                                            EntityArgument.getPlayers(p_137339_, "targets"),
                                                            IntegerArgumentType.getInteger(p_137339_, "amount"),
                                                            ExperienceCommand.Type.POINTS
                                                        )
                                                )
                                        )
                                        .then(
                                            Commands.literal("levels")
                                                .executes(
                                                    p_137337_ -> addExperience(
                                                            p_137337_.getSource(),
                                                            EntityArgument.getPlayers(p_137337_, "targets"),
                                                            IntegerArgumentType.getInteger(p_137337_, "amount"),
                                                            ExperienceCommand.Type.LEVELS
                                                        )
                                                )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("set")
                        .then(
                            Commands.argument("targets", EntityArgument.players())
                                .then(
                                    Commands.argument("amount", IntegerArgumentType.integer(0))
                                        .executes(
                                            p_137335_ -> setExperience(
                                                    p_137335_.getSource(),
                                                    EntityArgument.getPlayers(p_137335_, "targets"),
                                                    IntegerArgumentType.getInteger(p_137335_, "amount"),
                                                    ExperienceCommand.Type.POINTS
                                                )
                                        )
                                        .then(
                                            Commands.literal("points")
                                                .executes(
                                                    p_137333_ -> setExperience(
                                                            p_137333_.getSource(),
                                                            EntityArgument.getPlayers(p_137333_, "targets"),
                                                            IntegerArgumentType.getInteger(p_137333_, "amount"),
                                                            ExperienceCommand.Type.POINTS
                                                        )
                                                )
                                        )
                                        .then(
                                            Commands.literal("levels")
                                                .executes(
                                                    p_137331_ -> setExperience(
                                                            p_137331_.getSource(),
                                                            EntityArgument.getPlayers(p_137331_, "targets"),
                                                            IntegerArgumentType.getInteger(p_137331_, "amount"),
                                                            ExperienceCommand.Type.LEVELS
                                                        )
                                                )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("query")
                        .then(
                            Commands.argument("targets", EntityArgument.player())
                                .then(
                                    Commands.literal("points")
                                        .executes(
                                            p_137322_ -> queryExperience(
                                                    p_137322_.getSource(), EntityArgument.getPlayer(p_137322_, "targets"), ExperienceCommand.Type.POINTS
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("levels")
                                        .executes(
                                            p_137309_ -> queryExperience(
                                                    p_137309_.getSource(), EntityArgument.getPlayer(p_137309_, "targets"), ExperienceCommand.Type.LEVELS
                                                )
                                        )
                                )
                        )
                )
        );
        dispatcher.register(Commands.literal("xp").requires(p_137311_ -> p_137311_.hasPermission(2)).redirect(literalcommandnode));
    }

    private static int queryExperience(CommandSourceStack source, ServerPlayer player, ExperienceCommand.Type type) {
        int i = type.query.applyAsInt(player);
        source.sendSuccess(() -> Component.translatable("commands.experience.query." + type.name, player.getDisplayName(), i), false);
        return i;
    }

    private static int addExperience(
        CommandSourceStack source, Collection<? extends ServerPlayer> targets, int amount, ExperienceCommand.Type type
    ) {
        for (ServerPlayer serverplayer : targets) {
            type.add.accept(serverplayer, amount);
        }

        if (targets.size() == 1) {
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.experience.add." + type.name + ".success.single", amount, targets.iterator().next().getDisplayName()
                    ),
                true
            );
        } else {
            source.sendSuccess(
                () -> Component.translatable("commands.experience.add." + type.name + ".success.multiple", amount, targets.size()), true
            );
        }

        return targets.size();
    }

    private static int setExperience(
        CommandSourceStack source, Collection<? extends ServerPlayer> targets, int amount, ExperienceCommand.Type type
    ) throws CommandSyntaxException {
        int i = 0;

        for (ServerPlayer serverplayer : targets) {
            if (type.set.test(serverplayer, amount)) {
                i++;
            }
        }

        if (i == 0) {
            throw ERROR_SET_POINTS_INVALID.create();
        } else {
            if (targets.size() == 1) {
                source.sendSuccess(
                    () -> Component.translatable(
                            "commands.experience.set." + type.name + ".success.single", amount, targets.iterator().next().getDisplayName()
                        ),
                    true
                );
            } else {
                source.sendSuccess(
                    () -> Component.translatable("commands.experience.set." + type.name + ".success.multiple", amount, targets.size()), true
                );
            }

            return targets.size();
        }
    }

    static enum Type {
        POINTS("points", Player::giveExperiencePoints, (p_352687_, p_352688_) -> {
            if (p_352688_ >= p_352687_.getXpNeededForNextLevel()) {
                return false;
            } else {
                p_352687_.setExperiencePoints(p_352688_);
                return true;
            }
        }, p_352689_ -> Mth.floor(p_352689_.experienceProgress * (float)p_352689_.getXpNeededForNextLevel())),
        LEVELS("levels", ServerPlayer::giveExperienceLevels, (p_137360_, p_137361_) -> {
            p_137360_.setExperienceLevels(p_137361_);
            return true;
        }, p_287335_ -> p_287335_.experienceLevel);

        public final BiConsumer<ServerPlayer, Integer> add;
        public final BiPredicate<ServerPlayer, Integer> set;
        public final String name;
        final ToIntFunction<ServerPlayer> query;

        private Type(
            String name, BiConsumer<ServerPlayer, Integer> add, BiPredicate<ServerPlayer, Integer> set, ToIntFunction<ServerPlayer> query
        ) {
            this.add = add;
            this.name = name;
            this.set = set;
            this.query = query;
        }
    }
}
