package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

public class MsgCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> literalcommandnode = dispatcher.register(
            Commands.literal("msg")
                .then(
                    Commands.argument("targets", EntityArgument.players()).then(Commands.argument("message", MessageArgument.message()).executes(p_248155_ -> {
                        Collection<ServerPlayer> collection = EntityArgument.getPlayers(p_248155_, "targets");
                        if (!collection.isEmpty()) {
                            MessageArgument.resolveChatMessage(p_248155_, "message", p_248154_ -> sendMessage(p_248155_.getSource(), collection, p_248154_));
                        }

                        return collection.size();
                    }))
                )
        );
        dispatcher.register(Commands.literal("tell").redirect(literalcommandnode));
        dispatcher.register(Commands.literal("w").redirect(literalcommandnode));
    }

    private static void sendMessage(CommandSourceStack source, Collection<ServerPlayer> targets, PlayerChatMessage message) {
        ChatType.Bound chattype$bound = ChatType.bind(ChatType.MSG_COMMAND_INCOMING, source);
        OutgoingChatMessage outgoingchatmessage = OutgoingChatMessage.create(message);
        boolean flag = false;

        for (ServerPlayer serverplayer : targets) {
            ChatType.Bound chattype$bound1 = ChatType.bind(ChatType.MSG_COMMAND_OUTGOING, source).withTargetName(serverplayer.getDisplayName());
            source.sendChatMessage(outgoingchatmessage, false, chattype$bound1);
            boolean flag1 = source.shouldFilterMessageTo(serverplayer);
            serverplayer.sendChatMessage(outgoingchatmessage, flag1, chattype$bound);
            flag |= flag1 && message.isFullyFiltered();
        }

        if (flag) {
            source.sendSystemMessage(PlayerList.CHAT_FILTERED_FULL);
        }
    }
}
