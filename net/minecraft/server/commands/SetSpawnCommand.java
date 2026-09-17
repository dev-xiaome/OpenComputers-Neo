package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import java.util.Collection;
import java.util.Collections;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.AngleArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public class SetSpawnCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("spawnpoint")
                .requires(p_138648_ -> p_138648_.hasPermission(2))
                .executes(
                    p_274828_ -> setSpawn(
                            p_274828_.getSource(),
                            Collections.singleton(p_274828_.getSource().getPlayerOrException()),
                            BlockPos.containing(p_274828_.getSource().getPosition()),
                            0.0F
                        )
                )
                .then(
                    Commands.argument("targets", EntityArgument.players())
                        .executes(
                            p_274829_ -> setSpawn(
                                    p_274829_.getSource(),
                                    EntityArgument.getPlayers(p_274829_, "targets"),
                                    BlockPos.containing(p_274829_.getSource().getPosition()),
                                    0.0F
                                )
                        )
                        .then(
                            Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(
                                    p_138655_ -> setSpawn(
                                            p_138655_.getSource(),
                                            EntityArgument.getPlayers(p_138655_, "targets"),
                                            BlockPosArgument.getSpawnablePos(p_138655_, "pos"),
                                            0.0F
                                        )
                                )
                                .then(
                                    Commands.argument("angle", AngleArgument.angle())
                                        .executes(
                                            p_138646_ -> setSpawn(
                                                    p_138646_.getSource(),
                                                    EntityArgument.getPlayers(p_138646_, "targets"),
                                                    BlockPosArgument.getSpawnablePos(p_138646_, "pos"),
                                                    AngleArgument.getAngle(p_138646_, "angle")
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static int setSpawn(CommandSourceStack source, Collection<ServerPlayer> targets, BlockPos pos, float angle) {
        ResourceKey<Level> resourcekey = source.getLevel().dimension();

        for (ServerPlayer serverplayer : targets) {
            serverplayer.setRespawnPosition(resourcekey, pos, angle, true, false);
        }

        String s = resourcekey.location().toString();
        if (targets.size() == 1) {
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.spawnpoint.success.single",
                        pos.getX(),
                        pos.getY(),
                        pos.getZ(),
                        angle,
                        source.getLevel().getDescription(), // Neo: Use dimension translation, if one exists
                        targets.iterator().next().getDisplayName()
                    ),
                true
            );
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.spawnpoint.success.multiple", pos.getX(), pos.getY(), pos.getZ(), angle, s, targets.size()
                    ),
                true
            );
        }

        return targets.size();
    }
}
