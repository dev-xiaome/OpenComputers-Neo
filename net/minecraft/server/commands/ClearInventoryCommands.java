package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Predicate;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemPredicateArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class ClearInventoryCommands {
    private static final DynamicCommandExceptionType ERROR_SINGLE = new DynamicCommandExceptionType(
        p_304193_ -> Component.translatableEscape("clear.failed.single", p_304193_)
    );
    private static final DynamicCommandExceptionType ERROR_MULTIPLE = new DynamicCommandExceptionType(
        p_304192_ -> Component.translatableEscape("clear.failed.multiple", p_304192_)
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("clear")
                .requires(p_136704_ -> p_136704_.hasPermission(2))
                .executes(
                    p_332568_ -> clearUnlimited(p_332568_.getSource(), Collections.singleton(p_332568_.getSource().getPlayerOrException()), p_180029_ -> true)
                )
                .then(
                    Commands.argument("targets", EntityArgument.players())
                        .executes(p_332566_ -> clearUnlimited(p_332566_.getSource(), EntityArgument.getPlayers(p_332566_, "targets"), p_180027_ -> true))
                        .then(
                            Commands.argument("item", ItemPredicateArgument.itemPredicate(context))
                                .executes(
                                    p_332567_ -> clearUnlimited(
                                            p_332567_.getSource(),
                                            EntityArgument.getPlayers(p_332567_, "targets"),
                                            ItemPredicateArgument.getItemPredicate(p_332567_, "item")
                                        )
                                )
                                .then(
                                    Commands.argument("maxCount", IntegerArgumentType.integer(0))
                                        .executes(
                                            p_323185_ -> clearInventory(
                                                    p_323185_.getSource(),
                                                    EntityArgument.getPlayers(p_323185_, "targets"),
                                                    ItemPredicateArgument.getItemPredicate(p_323185_, "item"),
                                                    IntegerArgumentType.getInteger(p_323185_, "maxCount")
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static int clearUnlimited(CommandSourceStack source, Collection<ServerPlayer> targets, Predicate<ItemStack> filter) throws CommandSyntaxException {
        return clearInventory(source, targets, filter, -1);
    }

    private static int clearInventory(CommandSourceStack source, Collection<ServerPlayer> targetPlayers, Predicate<ItemStack> itemPredicate, int maxCount) throws CommandSyntaxException {
        int i = 0;

        for (ServerPlayer serverplayer : targetPlayers) {
            i += serverplayer.getInventory().clearOrCountMatchingItems(itemPredicate, maxCount, serverplayer.inventoryMenu.getCraftSlots());
            serverplayer.containerMenu.broadcastChanges();
            serverplayer.inventoryMenu.slotsChanged(serverplayer.getInventory());
        }

        if (i == 0) {
            if (targetPlayers.size() == 1) {
                throw ERROR_SINGLE.create(targetPlayers.iterator().next().getName());
            } else {
                throw ERROR_MULTIPLE.create(targetPlayers.size());
            }
        } else {
            int j = i;
            if (maxCount == 0) {
                if (targetPlayers.size() == 1) {
                    source.sendSuccess(() -> Component.translatable("commands.clear.test.single", j, targetPlayers.iterator().next().getDisplayName()), true);
                } else {
                    source.sendSuccess(() -> Component.translatable("commands.clear.test.multiple", j, targetPlayers.size()), true);
                }
            } else if (targetPlayers.size() == 1) {
                source.sendSuccess(() -> Component.translatable("commands.clear.success.single", j, targetPlayers.iterator().next().getDisplayName()), true);
            } else {
                source.sendSuccess(() -> Component.translatable("commands.clear.success.multiple", j, targetPlayers.size()), true);
            }

            return i;
        }
    }
}
