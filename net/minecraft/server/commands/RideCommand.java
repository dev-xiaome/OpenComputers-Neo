package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

public class RideCommand {
    private static final DynamicCommandExceptionType ERROR_NOT_RIDING = new DynamicCommandExceptionType(
        p_304286_ -> Component.translatableEscape("commands.ride.not_riding", p_304286_)
    );
    private static final Dynamic2CommandExceptionType ERROR_ALREADY_RIDING = new Dynamic2CommandExceptionType(
        (p_304284_, p_304285_) -> Component.translatableEscape("commands.ride.already_riding", p_304284_, p_304285_)
    );
    private static final Dynamic2CommandExceptionType ERROR_MOUNT_FAILED = new Dynamic2CommandExceptionType(
        (p_304287_, p_304288_) -> Component.translatableEscape("commands.ride.mount.failure.generic", p_304287_, p_304288_)
    );
    private static final SimpleCommandExceptionType ERROR_MOUNTING_PLAYER = new SimpleCommandExceptionType(
        Component.translatable("commands.ride.mount.failure.cant_ride_players")
    );
    private static final SimpleCommandExceptionType ERROR_MOUNTING_LOOP = new SimpleCommandExceptionType(
        Component.translatable("commands.ride.mount.failure.loop")
    );
    private static final SimpleCommandExceptionType ERROR_WRONG_DIMENSION = new SimpleCommandExceptionType(
        Component.translatable("commands.ride.mount.failure.wrong_dimension")
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("ride")
                .requires(p_265326_ -> p_265326_.hasPermission(2))
                .then(
                    Commands.argument("target", EntityArgument.entity())
                        .then(
                            Commands.literal("mount")
                                .then(
                                    Commands.argument("vehicle", EntityArgument.entity())
                                        .executes(
                                            p_265139_ -> mount(
                                                    p_265139_.getSource(),
                                                    EntityArgument.getEntity(p_265139_, "target"),
                                                    EntityArgument.getEntity(p_265139_, "vehicle")
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("dismount").executes(p_265418_ -> dismount(p_265418_.getSource(), EntityArgument.getEntity(p_265418_, "target")))
                        )
                )
        );
    }

    private static int mount(CommandSourceStack source, Entity target, Entity vehicle) throws CommandSyntaxException {
        Entity entity = target.getVehicle();
        if (entity != null) {
            throw ERROR_ALREADY_RIDING.create(target.getDisplayName(), entity.getDisplayName());
        } else if (vehicle.getType() == EntityType.PLAYER) {
            throw ERROR_MOUNTING_PLAYER.create();
        } else if (target.getSelfAndPassengers().anyMatch(p_265501_ -> p_265501_ == vehicle)) {
            throw ERROR_MOUNTING_LOOP.create();
        } else if (target.level() != vehicle.level()) {
            throw ERROR_WRONG_DIMENSION.create();
        } else if (!target.startRiding(vehicle, true)) {
            throw ERROR_MOUNT_FAILED.create(target.getDisplayName(), vehicle.getDisplayName());
        } else {
            source.sendSuccess(() -> Component.translatable("commands.ride.mount.success", target.getDisplayName(), vehicle.getDisplayName()), true);
            return 1;
        }
    }

    private static int dismount(CommandSourceStack source, Entity target) throws CommandSyntaxException {
        Entity entity = target.getVehicle();
        if (entity == null) {
            throw ERROR_NOT_RIDING.create(target.getDisplayName());
        } else {
            target.stopRiding();
            source.sendSuccess(() -> Component.translatable("commands.ride.dismount.success", target.getDisplayName(), entity.getDisplayName()), true);
            return 1;
        }
    }
}
