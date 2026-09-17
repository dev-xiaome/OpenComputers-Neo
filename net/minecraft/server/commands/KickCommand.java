package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class KickCommand {
    private static final SimpleCommandExceptionType ERROR_KICKING_OWNER = new SimpleCommandExceptionType(Component.translatable("commands.kick.owner.failed"));
    private static final SimpleCommandExceptionType ERROR_SINGLEPLAYER = new SimpleCommandExceptionType(
        Component.translatable("commands.kick.singleplayer.failed")
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("kick")
                .requires(p_137800_ -> p_137800_.hasPermission(3))
                .then(
                    Commands.argument("targets", EntityArgument.players())
                        .executes(
                            p_137806_ -> kickPlayers(
                                    p_137806_.getSource(),
                                    EntityArgument.getPlayers(p_137806_, "targets"),
                                    Component.translatable("multiplayer.disconnect.kicked")
                                )
                        )
                        .then(
                            Commands.argument("reason", MessageArgument.message())
                                .executes(
                                    p_137798_ -> kickPlayers(
                                            p_137798_.getSource(),
                                            EntityArgument.getPlayers(p_137798_, "targets"),
                                            MessageArgument.getMessage(p_137798_, "reason")
                                        )
                                )
                        )
                )
        );
    }

    private static int kickPlayers(CommandSourceStack source, Collection<ServerPlayer> players, Component reason) throws CommandSyntaxException {
        if (!source.getServer().isPublished()) {
            throw ERROR_SINGLEPLAYER.create();
        } else {
            int i = 0;

            for (ServerPlayer serverplayer : players) {
                if (!source.getServer().isSingleplayerOwner(serverplayer.getGameProfile())) {
                    serverplayer.connection.disconnect(reason);
                    source.sendSuccess(() -> Component.translatable("commands.kick.success", serverplayer.getDisplayName(), reason), true);
                    i++;
                }
            }

            if (i == 0) {
                throw ERROR_KICKING_OWNER.create();
            } else {
                return i;
            }
        }
    }
}
