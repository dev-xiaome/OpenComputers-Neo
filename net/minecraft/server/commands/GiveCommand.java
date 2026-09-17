package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

public class GiveCommand {
    public static final int MAX_ALLOWED_ITEMSTACKS = 100;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("give")
                .requires(p_137777_ -> p_137777_.hasPermission(2))
                .then(
                    Commands.argument("targets", EntityArgument.players())
                        .then(
                            Commands.argument("item", ItemArgument.item(context))
                                .executes(
                                    p_137784_ -> giveItem(
                                            p_137784_.getSource(), ItemArgument.getItem(p_137784_, "item"), EntityArgument.getPlayers(p_137784_, "targets"), 1
                                        )
                                )
                                .then(
                                    Commands.argument("count", IntegerArgumentType.integer(1))
                                        .executes(
                                            p_137775_ -> giveItem(
                                                    p_137775_.getSource(),
                                                    ItemArgument.getItem(p_137775_, "item"),
                                                    EntityArgument.getPlayers(p_137775_, "targets"),
                                                    IntegerArgumentType.getInteger(p_137775_, "count")
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static int giveItem(CommandSourceStack source, ItemInput item, Collection<ServerPlayer> targets, int count) throws CommandSyntaxException {
        ItemStack itemstack = item.createItemStack(1, false);
        int i = itemstack.getMaxStackSize();
        int j = i * 100;
        if (count > j) {
            source.sendFailure(Component.translatable("commands.give.failed.toomanyitems", j, itemstack.getDisplayName()));
            return 0;
        } else {
            for (ServerPlayer serverplayer : targets) {
                int k = count;

                while (k > 0) {
                    int l = Math.min(i, k);
                    k -= l;
                    ItemStack itemstack1 = item.createItemStack(l, false);
                    boolean flag = serverplayer.getInventory().add(itemstack1);
                    if (flag && itemstack1.isEmpty()) {
                        ItemEntity itementity1 = serverplayer.drop(itemstack, false);
                        if (itementity1 != null) {
                            itementity1.makeFakeItem();
                        }

                        serverplayer.level()
                            .playSound(
                                null,
                                serverplayer.getX(),
                                serverplayer.getY(),
                                serverplayer.getZ(),
                                SoundEvents.ITEM_PICKUP,
                                SoundSource.PLAYERS,
                                0.2F,
                                ((serverplayer.getRandom().nextFloat() - serverplayer.getRandom().nextFloat()) * 0.7F + 1.0F) * 2.0F
                            );
                        serverplayer.containerMenu.broadcastChanges();
                    } else {
                        ItemEntity itementity = serverplayer.drop(itemstack1, false);
                        if (itementity != null) {
                            itementity.setNoPickUpDelay();
                            itementity.setTarget(serverplayer.getUUID());
                        }
                    }
                }
            }

            if (targets.size() == 1) {
                source.sendSuccess(
                    () -> Component.translatable(
                            "commands.give.success.single", count, itemstack.getDisplayName(), targets.iterator().next().getDisplayName()
                        ),
                    true
                );
            } else {
                source.sendSuccess(
                    () -> Component.translatable("commands.give.success.single", count, itemstack.getDisplayName(), targets.size()), true
                );
            }

            return targets.size();
        }
    }
}
