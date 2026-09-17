package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameModeArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

public class DefaultGameModeCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("defaultgamemode")
                .requires(p_136929_ -> p_136929_.hasPermission(2))
                .then(
                    Commands.argument("gamemode", GameModeArgument.gameMode())
                        .executes(p_258227_ -> setMode(p_258227_.getSource(), GameModeArgument.getGameMode(p_258227_, "gamemode")))
                )
        );
    }

    /**
     * Sets the {@link net.minecraft.world.level.GameType} of the player who ran the command.
     */
    private static int setMode(CommandSourceStack commandSource, GameType gamemode) {
        int i = 0;
        MinecraftServer minecraftserver = commandSource.getServer();
        minecraftserver.setDefaultGameType(gamemode);
        GameType gametype = minecraftserver.getForcedGameType();
        if (gametype != null) {
            for (ServerPlayer serverplayer : minecraftserver.getPlayerList().getPlayers()) {
                if (serverplayer.setGameMode(gametype)) {
                    i++;
                }
            }
        }

        commandSource.sendSuccess(() -> Component.translatable("commands.defaultgamemode.success", gamemode.getLongDisplayName()), true);
        return i;
    }
}
