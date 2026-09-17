package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class PlaySoundCommand {
    private static final SimpleCommandExceptionType ERROR_TOO_FAR = new SimpleCommandExceptionType(Component.translatable("commands.playsound.failed"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> requiredargumentbuilder = Commands.argument("sound", ResourceLocationArgument.id())
            .suggests(SuggestionProviders.AVAILABLE_SOUNDS)
            .executes(
                p_329892_ -> playSound(
                        p_329892_.getSource(),
                        getCallingPlayerAsCollection(p_329892_.getSource().getPlayer()),
                        ResourceLocationArgument.getId(p_329892_, "sound"),
                        SoundSource.MASTER,
                        p_329892_.getSource().getPosition(),
                        1.0F,
                        1.0F,
                        0.0F
                    )
            );

        for (SoundSource soundsource : SoundSource.values()) {
            requiredargumentbuilder.then(source(soundsource));
        }

        dispatcher.register(Commands.literal("playsound").requires(p_138159_ -> p_138159_.hasPermission(2)).then(requiredargumentbuilder));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> source(SoundSource category) {
        return Commands.literal(category.getName())
            .executes(
                p_329894_ -> playSound(
                        p_329894_.getSource(),
                        getCallingPlayerAsCollection(p_329894_.getSource().getPlayer()),
                        ResourceLocationArgument.getId(p_329894_, "sound"),
                        category,
                        p_329894_.getSource().getPosition(),
                        1.0F,
                        1.0F,
                        0.0F
                    )
            )
            .then(
                Commands.argument("targets", EntityArgument.players())
                    .executes(
                        p_138180_ -> playSound(
                                p_138180_.getSource(),
                                EntityArgument.getPlayers(p_138180_, "targets"),
                                ResourceLocationArgument.getId(p_138180_, "sound"),
                                category,
                                p_138180_.getSource().getPosition(),
                                1.0F,
                                1.0F,
                                0.0F
                            )
                    )
                    .then(
                        Commands.argument("pos", Vec3Argument.vec3())
                            .executes(
                                p_138177_ -> playSound(
                                        p_138177_.getSource(),
                                        EntityArgument.getPlayers(p_138177_, "targets"),
                                        ResourceLocationArgument.getId(p_138177_, "sound"),
                                        category,
                                        Vec3Argument.getVec3(p_138177_, "pos"),
                                        1.0F,
                                        1.0F,
                                        0.0F
                                    )
                            )
                            .then(
                                Commands.argument("volume", FloatArgumentType.floatArg(0.0F))
                                    .executes(
                                        p_138174_ -> playSound(
                                                p_138174_.getSource(),
                                                EntityArgument.getPlayers(p_138174_, "targets"),
                                                ResourceLocationArgument.getId(p_138174_, "sound"),
                                                category,
                                                Vec3Argument.getVec3(p_138174_, "pos"),
                                                p_138174_.getArgument("volume", Float.class),
                                                1.0F,
                                                0.0F
                                            )
                                    )
                                    .then(
                                        Commands.argument("pitch", FloatArgumentType.floatArg(0.0F, 2.0F))
                                            .executes(
                                                p_138171_ -> playSound(
                                                        p_138171_.getSource(),
                                                        EntityArgument.getPlayers(p_138171_, "targets"),
                                                        ResourceLocationArgument.getId(p_138171_, "sound"),
                                                        category,
                                                        Vec3Argument.getVec3(p_138171_, "pos"),
                                                        p_138171_.getArgument("volume", Float.class),
                                                        p_138171_.getArgument("pitch", Float.class),
                                                        0.0F
                                                    )
                                            )
                                            .then(
                                                Commands.argument("minVolume", FloatArgumentType.floatArg(0.0F, 1.0F))
                                                    .executes(
                                                        p_138155_ -> playSound(
                                                                p_138155_.getSource(),
                                                                EntityArgument.getPlayers(p_138155_, "targets"),
                                                                ResourceLocationArgument.getId(p_138155_, "sound"),
                                                                category,
                                                                Vec3Argument.getVec3(p_138155_, "pos"),
                                                                p_138155_.getArgument("volume", Float.class),
                                                                p_138155_.getArgument("pitch", Float.class),
                                                                p_138155_.getArgument("minVolume", Float.class)
                                                            )
                                                    )
                                            )
                                    )
                            )
                    )
            );
    }

    private static Collection<ServerPlayer> getCallingPlayerAsCollection(@Nullable ServerPlayer player) {
        return player != null ? List.of(player) : List.of();
    }

    private static int playSound(
        CommandSourceStack source,
        Collection<ServerPlayer> targets,
        ResourceLocation sound,
        SoundSource category,
        Vec3 pos,
        float volume,
        float pitch,
        float minVolume
    ) throws CommandSyntaxException {
        Holder<SoundEvent> holder = Holder.direct(SoundEvent.createVariableRangeEvent(sound));
        double d0 = (double)Mth.square(holder.value().getRange(volume));
        int i = 0;
        long j = source.getLevel().getRandom().nextLong();

        for (ServerPlayer serverplayer : targets) {
            double d1 = pos.x - serverplayer.getX();
            double d2 = pos.y - serverplayer.getY();
            double d3 = pos.z - serverplayer.getZ();
            double d4 = d1 * d1 + d2 * d2 + d3 * d3;
            Vec3 vec3 = pos;
            float f = volume;
            if (d4 > d0) {
                if (minVolume <= 0.0F) {
                    continue;
                }

                double d5 = Math.sqrt(d4);
                vec3 = new Vec3(serverplayer.getX() + d1 / d5 * 2.0, serverplayer.getY() + d2 / d5 * 2.0, serverplayer.getZ() + d3 / d5 * 2.0);
                f = minVolume;
            }

            serverplayer.connection.send(new ClientboundSoundPacket(holder, category, vec3.x(), vec3.y(), vec3.z(), f, pitch, j));
            i++;
        }

        if (i == 0) {
            throw ERROR_TOO_FAR.create();
        } else {
            if (targets.size() == 1) {
                source.sendSuccess(
                    () -> Component.translatable(
                            "commands.playsound.success.single", Component.translationArg(sound), targets.iterator().next().getDisplayName()
                        ),
                    true
                );
            } else {
                source.sendSuccess(
                    () -> Component.translatable("commands.playsound.success.multiple", Component.translationArg(sound), targets.size()), true
                );
            }

            return i;
        }
    }
}
