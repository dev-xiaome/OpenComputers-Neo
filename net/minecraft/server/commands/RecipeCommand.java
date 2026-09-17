package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import java.util.Collections;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;

public class RecipeCommand {
    private static final SimpleCommandExceptionType ERROR_GIVE_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.recipe.give.failed"));
    private static final SimpleCommandExceptionType ERROR_TAKE_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.recipe.take.failed"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("recipe")
                .requires(p_138205_ -> p_138205_.hasPermission(2))
                .then(
                    Commands.literal("give")
                        .then(
                            Commands.argument("targets", EntityArgument.players())
                                .then(
                                    Commands.argument("recipe", ResourceLocationArgument.id())
                                        .suggests(SuggestionProviders.ALL_RECIPES)
                                        .executes(
                                            p_300765_ -> giveRecipes(
                                                    p_300765_.getSource(),
                                                    EntityArgument.getPlayers(p_300765_, "targets"),
                                                    Collections.singleton(ResourceLocationArgument.getRecipe(p_300765_, "recipe"))
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("*")
                                        .executes(
                                            p_138217_ -> giveRecipes(
                                                    p_138217_.getSource(),
                                                    EntityArgument.getPlayers(p_138217_, "targets"),
                                                    p_138217_.getSource().getServer().getRecipeManager().getRecipes()
                                                )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("take")
                        .then(
                            Commands.argument("targets", EntityArgument.players())
                                .then(
                                    Commands.argument("recipe", ResourceLocationArgument.id())
                                        .suggests(SuggestionProviders.ALL_RECIPES)
                                        .executes(
                                            p_300766_ -> takeRecipes(
                                                    p_300766_.getSource(),
                                                    EntityArgument.getPlayers(p_300766_, "targets"),
                                                    Collections.singleton(ResourceLocationArgument.getRecipe(p_300766_, "recipe"))
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("*")
                                        .executes(
                                            p_138203_ -> takeRecipes(
                                                    p_138203_.getSource(),
                                                    EntityArgument.getPlayers(p_138203_, "targets"),
                                                    p_138203_.getSource().getServer().getRecipeManager().getRecipes()
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static int giveRecipes(CommandSourceStack source, Collection<ServerPlayer> targets, Collection<RecipeHolder<?>> recipes) throws CommandSyntaxException {
        int i = 0;

        for (ServerPlayer serverplayer : targets) {
            i += serverplayer.awardRecipes(recipes);
        }

        if (i == 0) {
            throw ERROR_GIVE_FAILED.create();
        } else {
            if (targets.size() == 1) {
                source.sendSuccess(
                    () -> Component.translatable("commands.recipe.give.success.single", recipes.size(), targets.iterator().next().getDisplayName()), true
                );
            } else {
                source.sendSuccess(() -> Component.translatable("commands.recipe.give.success.multiple", recipes.size(), targets.size()), true);
            }

            return i;
        }
    }

    private static int takeRecipes(CommandSourceStack source, Collection<ServerPlayer> targets, Collection<RecipeHolder<?>> recipes) throws CommandSyntaxException {
        int i = 0;

        for (ServerPlayer serverplayer : targets) {
            i += serverplayer.resetRecipes(recipes);
        }

        if (i == 0) {
            throw ERROR_TAKE_FAILED.create();
        } else {
            if (targets.size() == 1) {
                source.sendSuccess(
                    () -> Component.translatable("commands.recipe.take.success.single", recipes.size(), targets.iterator().next().getDisplayName()), true
                );
            } else {
                source.sendSuccess(() -> Component.translatable("commands.recipe.take.success.multiple", recipes.size(), targets.size()), true);
            }

            return i;
        }
    }
}
