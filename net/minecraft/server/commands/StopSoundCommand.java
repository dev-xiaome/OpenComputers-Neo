package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.Collection;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

public class StopSoundCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        RequiredArgumentBuilder<CommandSourceStack, EntitySelector> requiredargumentbuilder = Commands.argument("targets", EntityArgument.players())
            .executes(p_138809_ -> stopSound(p_138809_.getSource(), EntityArgument.getPlayers(p_138809_, "targets"), null, null))
            .then(
                Commands.literal("*")
                    .then(
                        Commands.argument("sound", ResourceLocationArgument.id())
                            .suggests(SuggestionProviders.AVAILABLE_SOUNDS)
                            .executes(
                                p_138797_ -> stopSound(
                                        p_138797_.getSource(),
                                        EntityArgument.getPlayers(p_138797_, "targets"),
                                        null,
                                        ResourceLocationArgument.getId(p_138797_, "sound")
                                    )
                            )
                    )
            );

        for (SoundSource soundsource : SoundSource.values()) {
            requiredargumentbuilder.then(
                Commands.literal(soundsource.getName())
                    .executes(p_138807_ -> stopSound(p_138807_.getSource(), EntityArgument.getPlayers(p_138807_, "targets"), soundsource, null))
                    .then(
                        Commands.argument("sound", ResourceLocationArgument.id())
                            .suggests(SuggestionProviders.AVAILABLE_SOUNDS)
                            .executes(
                                p_138793_ -> stopSound(
                                        p_138793_.getSource(),
                                        EntityArgument.getPlayers(p_138793_, "targets"),
                                        soundsource,
                                        ResourceLocationArgument.getId(p_138793_, "sound")
                                    )
                            )
                    )
            );
        }

        dispatcher.register(Commands.literal("stopsound").requires(p_138799_ -> p_138799_.hasPermission(2)).then(requiredargumentbuilder));
    }

    private static int stopSound(
        CommandSourceStack source, Collection<ServerPlayer> targets, @Nullable SoundSource category, @Nullable ResourceLocation sound
    ) {
        ClientboundStopSoundPacket clientboundstopsoundpacket = new ClientboundStopSoundPacket(sound, category);

        for (ServerPlayer serverplayer : targets) {
            serverplayer.connection.send(clientboundstopsoundpacket);
        }

        if (category != null) {
            if (sound != null) {
                source.sendSuccess(
                    () -> Component.translatable("commands.stopsound.success.source.sound", Component.translationArg(sound), category.getName()), true
                );
            } else {
                source.sendSuccess(() -> Component.translatable("commands.stopsound.success.source.any", category.getName()), true);
            }
        } else if (sound != null) {
            source.sendSuccess(() -> Component.translatable("commands.stopsound.success.sourceless.sound", Component.translationArg(sound)), true);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.stopsound.success.sourceless.any"), true);
        }

        return targets.size();
    }
}
