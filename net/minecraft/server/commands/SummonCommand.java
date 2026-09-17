package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class SummonCommand {
    private static final SimpleCommandExceptionType ERROR_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.summon.failed"));
    private static final SimpleCommandExceptionType ERROR_DUPLICATE_UUID = new SimpleCommandExceptionType(Component.translatable("commands.summon.failed.uuid"));
    private static final SimpleCommandExceptionType INVALID_POSITION = new SimpleCommandExceptionType(Component.translatable("commands.summon.invalidPosition"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("summon")
                .requires(p_138819_ -> p_138819_.hasPermission(2))
                .then(
                    Commands.argument("entity", ResourceArgument.resource(context, Registries.ENTITY_TYPE))
                        .suggests(SuggestionProviders.SUMMONABLE_ENTITIES)
                        .executes(
                            p_248175_ -> spawnEntity(
                                    p_248175_.getSource(),
                                    ResourceArgument.getSummonableEntityType(p_248175_, "entity"),
                                    p_248175_.getSource().getPosition(),
                                    new CompoundTag(),
                                    true
                                )
                        )
                        .then(
                            Commands.argument("pos", Vec3Argument.vec3())
                                .executes(
                                    p_248173_ -> spawnEntity(
                                            p_248173_.getSource(),
                                            ResourceArgument.getSummonableEntityType(p_248173_, "entity"),
                                            Vec3Argument.getVec3(p_248173_, "pos"),
                                            new CompoundTag(),
                                            true
                                        )
                                )
                                .then(
                                    Commands.argument("nbt", CompoundTagArgument.compoundTag())
                                        .executes(
                                            p_248174_ -> spawnEntity(
                                                    p_248174_.getSource(),
                                                    ResourceArgument.getSummonableEntityType(p_248174_, "entity"),
                                                    Vec3Argument.getVec3(p_248174_, "pos"),
                                                    CompoundTagArgument.getCompoundTag(p_248174_, "nbt"),
                                                    false
                                                )
                                        )
                                )
                        )
                )
        );
    }

    public static Entity createEntity(
        CommandSourceStack source, Holder.Reference<EntityType<?>> type, Vec3 pos, CompoundTag tag, boolean randomizeProperties
    ) throws CommandSyntaxException {
        BlockPos blockpos = BlockPos.containing(pos);
        if (!Level.isInSpawnableBounds(blockpos)) {
            throw INVALID_POSITION.create();
        } else {
            CompoundTag compoundtag = tag.copy();
            compoundtag.putString("id", type.key().location().toString());
            ServerLevel serverlevel = source.getLevel();
            Entity entity = EntityType.loadEntityRecursive(compoundtag, serverlevel, p_138828_ -> {
                p_138828_.moveTo(pos.x, pos.y, pos.z, p_138828_.getYRot(), p_138828_.getXRot());
                return p_138828_;
            });
            if (entity == null) {
                throw ERROR_FAILED.create();
            } else {
                if (randomizeProperties && entity instanceof Mob) {
                    ((Mob)entity)
                        .finalizeSpawn(source.getLevel(), source.getLevel().getCurrentDifficultyAt(entity.blockPosition()), MobSpawnType.COMMAND, null);
                }

                if (!serverlevel.tryAddFreshEntityWithPassengers(entity)) {
                    throw ERROR_DUPLICATE_UUID.create();
                } else {
                    return entity;
                }
            }
        }
    }

    private static int spawnEntity(
        CommandSourceStack source, Holder.Reference<EntityType<?>> type, Vec3 pos, CompoundTag tag, boolean randomizeProperties
    ) throws CommandSyntaxException {
        Entity entity = createEntity(source, type, pos, tag, randomizeProperties);
        source.sendSuccess(() -> Component.translatable("commands.summon.success", entity.getDisplayName()), true);
        return 1;
    }
}
