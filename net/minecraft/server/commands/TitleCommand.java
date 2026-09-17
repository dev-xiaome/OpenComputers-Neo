package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import java.util.function.Function;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.TimeArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

public class TitleCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("title")
                .requires(p_139107_ -> p_139107_.hasPermission(2))
                .then(
                    Commands.argument("targets", EntityArgument.players())
                        .then(
                            Commands.literal("clear").executes(p_139134_ -> clearTitle(p_139134_.getSource(), EntityArgument.getPlayers(p_139134_, "targets")))
                        )
                        .then(
                            Commands.literal("reset").executes(p_139132_ -> resetTitle(p_139132_.getSource(), EntityArgument.getPlayers(p_139132_, "targets")))
                        )
                        .then(
                            Commands.literal("title")
                                .then(
                                    Commands.argument("title", ComponentArgument.textComponent(context))
                                        .executes(
                                            p_139130_ -> showTitle(
                                                    p_139130_.getSource(),
                                                    EntityArgument.getPlayers(p_139130_, "targets"),
                                                    ComponentArgument.getComponent(p_139130_, "title"),
                                                    "title",
                                                    ClientboundSetTitleTextPacket::new
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("subtitle")
                                .then(
                                    Commands.argument("title", ComponentArgument.textComponent(context))
                                        .executes(
                                            p_139128_ -> showTitle(
                                                    p_139128_.getSource(),
                                                    EntityArgument.getPlayers(p_139128_, "targets"),
                                                    ComponentArgument.getComponent(p_139128_, "title"),
                                                    "subtitle",
                                                    ClientboundSetSubtitleTextPacket::new
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("actionbar")
                                .then(
                                    Commands.argument("title", ComponentArgument.textComponent(context))
                                        .executes(
                                            p_139123_ -> showTitle(
                                                    p_139123_.getSource(),
                                                    EntityArgument.getPlayers(p_139123_, "targets"),
                                                    ComponentArgument.getComponent(p_139123_, "title"),
                                                    "actionbar",
                                                    ClientboundSetActionBarTextPacket::new
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("times")
                                .then(
                                    Commands.argument("fadeIn", TimeArgument.time())
                                        .then(
                                            Commands.argument("stay", TimeArgument.time())
                                                .then(
                                                    Commands.argument("fadeOut", TimeArgument.time())
                                                        .executes(
                                                            p_139105_ -> setTimes(
                                                                    p_139105_.getSource(),
                                                                    EntityArgument.getPlayers(p_139105_, "targets"),
                                                                    IntegerArgumentType.getInteger(p_139105_, "fadeIn"),
                                                                    IntegerArgumentType.getInteger(p_139105_, "stay"),
                                                                    IntegerArgumentType.getInteger(p_139105_, "fadeOut")
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static int clearTitle(CommandSourceStack source, Collection<ServerPlayer> targets) {
        ClientboundClearTitlesPacket clientboundcleartitlespacket = new ClientboundClearTitlesPacket(false);

        for (ServerPlayer serverplayer : targets) {
            serverplayer.connection.send(clientboundcleartitlespacket);
        }

        if (targets.size() == 1) {
            source.sendSuccess(() -> Component.translatable("commands.title.cleared.single", targets.iterator().next().getDisplayName()), true);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.title.cleared.multiple", targets.size()), true);
        }

        return targets.size();
    }

    private static int resetTitle(CommandSourceStack source, Collection<ServerPlayer> targets) {
        ClientboundClearTitlesPacket clientboundcleartitlespacket = new ClientboundClearTitlesPacket(true);

        for (ServerPlayer serverplayer : targets) {
            serverplayer.connection.send(clientboundcleartitlespacket);
        }

        if (targets.size() == 1) {
            source.sendSuccess(() -> Component.translatable("commands.title.reset.single", targets.iterator().next().getDisplayName()), true);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.title.reset.multiple", targets.size()), true);
        }

        return targets.size();
    }

    private static int showTitle(
        CommandSourceStack source, Collection<ServerPlayer> targets, Component title, String titleType, Function<Component, Packet<?>> packetGetter
    ) throws CommandSyntaxException {
        for (ServerPlayer serverplayer : targets) {
            serverplayer.connection.send(packetGetter.apply(ComponentUtils.updateForEntity(source, title, serverplayer, 0)));
        }

        if (targets.size() == 1) {
            source.sendSuccess(
                () -> Component.translatable("commands.title.show." + titleType + ".single", targets.iterator().next().getDisplayName()), true
            );
        } else {
            source.sendSuccess(() -> Component.translatable("commands.title.show." + titleType + ".multiple", targets.size()), true);
        }

        return targets.size();
    }

    private static int setTimes(CommandSourceStack source, Collection<ServerPlayer> target, int fade, int stay, int fadeOut) {
        ClientboundSetTitlesAnimationPacket clientboundsettitlesanimationpacket = new ClientboundSetTitlesAnimationPacket(fade, stay, fadeOut);

        for (ServerPlayer serverplayer : target) {
            serverplayer.connection.send(clientboundsettitlesanimationpacket);
        }

        if (target.size() == 1) {
            source.sendSuccess(() -> Component.translatable("commands.title.times.single", target.iterator().next().getDisplayName()), true);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.title.times.multiple", target.size()), true);
        }

        return target.size();
    }
}
