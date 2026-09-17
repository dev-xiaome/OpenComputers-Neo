package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import java.util.function.Function;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;

public class ListPlayersCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("list")
                .executes(p_137830_ -> listPlayers(p_137830_.getSource()))
                .then(Commands.literal("uuids").executes(p_137823_ -> listPlayersWithUuids(p_137823_.getSource())))
        );
    }

    private static int listPlayers(CommandSourceStack source) {
        return format(source, Player::getDisplayName);
    }

    private static int listPlayersWithUuids(CommandSourceStack source) {
        return format(
            source,
            p_352690_ -> Component.translatable("commands.list.nameAndId", p_352690_.getName(), Component.translationArg(p_352690_.getGameProfile().getId()))
        );
    }

    private static int format(CommandSourceStack source, Function<ServerPlayer, Component> nameExtractor) {
        PlayerList playerlist = source.getServer().getPlayerList();
        List<ServerPlayer> list = playerlist.getPlayers();
        Component component = ComponentUtils.formatList(list, nameExtractor);
        source.sendSuccess(() -> Component.translatable("commands.list.players", list.size(), playerlist.getMaxPlayers(), component), false);
        return list.size();
    }
}
