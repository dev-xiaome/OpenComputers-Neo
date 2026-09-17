package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.arguments.coordinates.WorldCoordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class TeleportCommand {
    private static final SimpleCommandExceptionType INVALID_POSITION = new SimpleCommandExceptionType(
        Component.translatable("commands.teleport.invalidPosition")
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> literalcommandnode = dispatcher.register(
            Commands.literal("teleport")
                .requires(p_139039_ -> p_139039_.hasPermission(2))
                .then(
                    Commands.argument("location", Vec3Argument.vec3())
                        .executes(
                            p_139051_ -> teleportToPos(
                                    p_139051_.getSource(),
                                    Collections.singleton(p_139051_.getSource().getEntityOrException()),
                                    p_139051_.getSource().getLevel(),
                                    Vec3Argument.getCoordinates(p_139051_, "location"),
                                    WorldCoordinates.current(),
                                    null
                                )
                        )
                )
                .then(
                    Commands.argument("destination", EntityArgument.entity())
                        .executes(
                            p_139049_ -> teleportToEntity(
                                    p_139049_.getSource(),
                                    Collections.singleton(p_139049_.getSource().getEntityOrException()),
                                    EntityArgument.getEntity(p_139049_, "destination")
                                )
                        )
                )
                .then(
                    Commands.argument("targets", EntityArgument.entities())
                        .then(
                            Commands.argument("location", Vec3Argument.vec3())
                                .executes(
                                    p_139047_ -> teleportToPos(
                                            p_139047_.getSource(),
                                            EntityArgument.getEntities(p_139047_, "targets"),
                                            p_139047_.getSource().getLevel(),
                                            Vec3Argument.getCoordinates(p_139047_, "location"),
                                            null,
                                            null
                                        )
                                )
                                .then(
                                    Commands.argument("rotation", RotationArgument.rotation())
                                        .executes(
                                            p_139045_ -> teleportToPos(
                                                    p_139045_.getSource(),
                                                    EntityArgument.getEntities(p_139045_, "targets"),
                                                    p_139045_.getSource().getLevel(),
                                                    Vec3Argument.getCoordinates(p_139045_, "location"),
                                                    RotationArgument.getRotation(p_139045_, "rotation"),
                                                    null
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("facing")
                                        .then(
                                            Commands.literal("entity")
                                                .then(
                                                    Commands.argument("facingEntity", EntityArgument.entity())
                                                        .executes(
                                                            p_326749_ -> teleportToPos(
                                                                    p_326749_.getSource(),
                                                                    EntityArgument.getEntities(p_326749_, "targets"),
                                                                    p_326749_.getSource().getLevel(),
                                                                    Vec3Argument.getCoordinates(p_326749_, "location"),
                                                                    null,
                                                                    new TeleportCommand.LookAtEntity(
                                                                        EntityArgument.getEntity(p_326749_, "facingEntity"), EntityAnchorArgument.Anchor.FEET
                                                                    )
                                                                )
                                                        )
                                                        .then(
                                                            Commands.argument("facingAnchor", EntityAnchorArgument.anchor())
                                                                .executes(
                                                                    p_326750_ -> teleportToPos(
                                                                            p_326750_.getSource(),
                                                                            EntityArgument.getEntities(p_326750_, "targets"),
                                                                            p_326750_.getSource().getLevel(),
                                                                            Vec3Argument.getCoordinates(p_326750_, "location"),
                                                                            null,
                                                                            new TeleportCommand.LookAtEntity(
                                                                                EntityArgument.getEntity(p_326750_, "facingEntity"),
                                                                                EntityAnchorArgument.getAnchor(p_326750_, "facingAnchor")
                                                                            )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                        .then(
                                            Commands.argument("facingLocation", Vec3Argument.vec3())
                                                .executes(
                                                    p_326751_ -> teleportToPos(
                                                            p_326751_.getSource(),
                                                            EntityArgument.getEntities(p_326751_, "targets"),
                                                            p_326751_.getSource().getLevel(),
                                                            Vec3Argument.getCoordinates(p_326751_, "location"),
                                                            null,
                                                            new TeleportCommand.LookAtPosition(Vec3Argument.getVec3(p_326751_, "facingLocation"))
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.argument("destination", EntityArgument.entity())
                                .executes(
                                    p_139011_ -> teleportToEntity(
                                            p_139011_.getSource(),
                                            EntityArgument.getEntities(p_139011_, "targets"),
                                            EntityArgument.getEntity(p_139011_, "destination")
                                        )
                                )
                        )
                )
        );
        dispatcher.register(Commands.literal("tp").requires(p_139013_ -> p_139013_.hasPermission(2)).redirect(literalcommandnode));
    }

    private static int teleportToEntity(CommandSourceStack source, Collection<? extends Entity> targets, Entity destination) throws CommandSyntaxException {
        for (Entity entity : targets) {
            performTeleport(
                source,
                entity,
                (ServerLevel)destination.level(),
                destination.getX(),
                destination.getY(),
                destination.getZ(),
                EnumSet.noneOf(RelativeMovement.class),
                destination.getYRot(),
                destination.getXRot(),
                null
            );
        }

        if (targets.size() == 1) {
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.teleport.success.entity.single", targets.iterator().next().getDisplayName(), destination.getDisplayName()
                    ),
                true
            );
        } else {
            source.sendSuccess(() -> Component.translatable("commands.teleport.success.entity.multiple", targets.size(), destination.getDisplayName()), true);
        }

        return targets.size();
    }

    private static int teleportToPos(
        CommandSourceStack source,
        Collection<? extends Entity> targets,
        ServerLevel level,
        Coordinates position,
        @Nullable Coordinates rotation,
        @Nullable TeleportCommand.LookAt facing
    ) throws CommandSyntaxException {
        Vec3 vec3 = position.getPosition(source);
        Vec2 vec2 = rotation == null ? null : rotation.getRotation(source);
        Set<RelativeMovement> set = EnumSet.noneOf(RelativeMovement.class);
        if (position.isXRelative()) {
            set.add(RelativeMovement.X);
        }

        if (position.isYRelative()) {
            set.add(RelativeMovement.Y);
        }

        if (position.isZRelative()) {
            set.add(RelativeMovement.Z);
        }

        if (rotation == null) {
            set.add(RelativeMovement.X_ROT);
            set.add(RelativeMovement.Y_ROT);
        } else {
            if (rotation.isXRelative()) {
                set.add(RelativeMovement.X_ROT);
            }

            if (rotation.isYRelative()) {
                set.add(RelativeMovement.Y_ROT);
            }
        }

        for (Entity entity : targets) {
            if (rotation == null) {
                performTeleport(source, entity, level, vec3.x, vec3.y, vec3.z, set, entity.getYRot(), entity.getXRot(), facing);
            } else {
                performTeleport(source, entity, level, vec3.x, vec3.y, vec3.z, set, vec2.y, vec2.x, facing);
            }
        }

        if (targets.size() == 1) {
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.teleport.success.location.single",
                        targets.iterator().next().getDisplayName(),
                        formatDouble(vec3.x),
                        formatDouble(vec3.y),
                        formatDouble(vec3.z)
                    ),
                true
            );
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.teleport.success.location.multiple", targets.size(), formatDouble(vec3.x), formatDouble(vec3.y), formatDouble(vec3.z)
                    ),
                true
            );
        }

        return targets.size();
    }

    private static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%f", value);
    }

    private static void performTeleport(
        CommandSourceStack source,
        Entity entity,
        ServerLevel level,
        double x,
        double y,
        double z,
        Set<RelativeMovement> relativeList,
        float yaw,
        float pitch,
        @Nullable TeleportCommand.LookAt facing
    ) throws CommandSyntaxException {
        net.neoforged.neoforge.event.entity.EntityTeleportEvent.TeleportCommand event = net.neoforged.neoforge.event.EventHooks.onEntityTeleportCommand(entity, x, y, z);
        if (event.isCanceled()) {
             return;
        }
        x = event.getTargetX();
        y = event.getTargetY();
        z = event.getTargetZ();

        BlockPos blockpos = BlockPos.containing(x, y, z);
        if (!Level.isInSpawnableBounds(blockpos)) {
            throw INVALID_POSITION.create();
        } else {
            float f = Mth.wrapDegrees(yaw);
            float f1 = Mth.wrapDegrees(pitch);
            if (entity.teleportTo(level, x, y, z, relativeList, f, f1)) {
                if (facing != null) {
                    facing.perform(source, entity);
                }

                if (!(entity instanceof LivingEntity livingentity) || !livingentity.isFallFlying()) {
                    entity.setDeltaMovement(entity.getDeltaMovement().multiply(1.0, 0.0, 1.0));
                    entity.setOnGround(true);
                }

                if (entity instanceof PathfinderMob pathfindermob) {
                    pathfindermob.getNavigation().stop();
                }
            }
        }
    }

    @FunctionalInterface
    interface LookAt {
        void perform(CommandSourceStack source, Entity entity);
    }

    static record LookAtEntity(Entity entity, EntityAnchorArgument.Anchor anchor) implements TeleportCommand.LookAt {
        @Override
        public void perform(CommandSourceStack p_326864_, Entity p_326807_) {
            if (p_326807_ instanceof ServerPlayer serverplayer) {
                serverplayer.lookAt(p_326864_.getAnchor(), this.entity, this.anchor);
            } else {
                p_326807_.lookAt(p_326864_.getAnchor(), this.anchor.apply(this.entity));
            }
        }
    }

    static record LookAtPosition(Vec3 position) implements TeleportCommand.LookAt {
        @Override
        public void perform(CommandSourceStack p_326870_, Entity p_326894_) {
            p_326894_.lookAt(p_326870_.getAnchor(), this.position);
        }
    }
}
