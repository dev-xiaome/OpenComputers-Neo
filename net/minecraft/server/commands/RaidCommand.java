package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.entity.raid.Raids;
import net.minecraft.world.phys.Vec3;

public class RaidCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("raid")
                .requires(p_180498_ -> p_180498_.hasPermission(3))
                .then(
                    Commands.literal("start")
                        .then(
                            Commands.argument("omenlvl", IntegerArgumentType.integer(0))
                                .executes(p_180502_ -> start(p_180502_.getSource(), IntegerArgumentType.getInteger(p_180502_, "omenlvl")))
                        )
                )
                .then(Commands.literal("stop").executes(p_180500_ -> stop(p_180500_.getSource())))
                .then(Commands.literal("check").executes(p_180496_ -> check(p_180496_.getSource())))
                .then(
                    Commands.literal("sound")
                        .then(
                            Commands.argument("type", ComponentArgument.textComponent(context))
                                .executes(p_180492_ -> playSound(p_180492_.getSource(), ComponentArgument.getComponent(p_180492_, "type")))
                        )
                )
                .then(Commands.literal("spawnleader").executes(p_180488_ -> spawnLeader(p_180488_.getSource())))
                .then(
                    Commands.literal("setomen")
                        .then(
                            Commands.argument("level", IntegerArgumentType.integer(0))
                                .executes(p_337538_ -> setRaidOmenLevel(p_337538_.getSource(), IntegerArgumentType.getInteger(p_337538_, "level")))
                        )
                )
                .then(Commands.literal("glow").executes(p_180471_ -> glow(p_180471_.getSource())))
        );
    }

    private static int glow(CommandSourceStack source) throws CommandSyntaxException {
        Raid raid = getRaid(source.getPlayerOrException());
        if (raid != null) {
            for (Raider raider : raid.getAllRaiders()) {
                raider.addEffect(new MobEffectInstance(MobEffects.GLOWING, 1000, 1));
            }
        }

        return 1;
    }

    private static int setRaidOmenLevel(CommandSourceStack source, int level) throws CommandSyntaxException {
        Raid raid = getRaid(source.getPlayerOrException());
        if (raid != null) {
            int i = raid.getMaxRaidOmenLevel();
            if (level > i) {
                source.sendFailure(Component.literal("Sorry, the max raid omen level you can set is " + i));
            } else {
                int j = raid.getRaidOmenLevel();
                raid.setRaidOmenLevel(level);
                source.sendSuccess(() -> Component.literal("Changed village's raid omen level from " + j + " to " + level), false);
            }
        } else {
            source.sendFailure(Component.literal("No raid found here"));
        }

        return 1;
    }

    private static int spawnLeader(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Spawned a raid captain"), false);
        Raider raider = EntityType.PILLAGER.create(source.getLevel());
        if (raider == null) {
            source.sendFailure(Component.literal("Pillager failed to spawn"));
            return 0;
        } else {
            raider.setPatrolLeader(true);
            raider.setItemSlot(EquipmentSlot.HEAD, Raid.getLeaderBannerInstance(source.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN)));
            raider.setPos(source.getPosition().x, source.getPosition().y, source.getPosition().z);
            raider.finalizeSpawn(
                source.getLevel(), source.getLevel().getCurrentDifficultyAt(BlockPos.containing(source.getPosition())), MobSpawnType.COMMAND, null
            );
            source.getLevel().addFreshEntityWithPassengers(raider);
            return 1;
        }
    }

    private static int playSound(CommandSourceStack source, @Nullable Component type) {
        if (type != null && type.getString().equals("local")) {
            ServerLevel serverlevel = source.getLevel();
            Vec3 vec3 = source.getPosition().add(5.0, 0.0, 0.0);
            serverlevel.playSeededSound(null, vec3.x, vec3.y, vec3.z, SoundEvents.RAID_HORN, SoundSource.NEUTRAL, 2.0F, 1.0F, serverlevel.random.nextLong());
        }

        return 1;
    }

    private static int start(CommandSourceStack source, int badOmenLevel) throws CommandSyntaxException {
        ServerPlayer serverplayer = source.getPlayerOrException();
        BlockPos blockpos = serverplayer.blockPosition();
        if (serverplayer.serverLevel().isRaided(blockpos)) {
            source.sendFailure(Component.literal("Raid already started close by"));
            return -1;
        } else {
            Raids raids = serverplayer.serverLevel().getRaids();
            Raid raid = raids.createOrExtendRaid(serverplayer, serverplayer.blockPosition());
            if (raid != null) {
                raid.setRaidOmenLevel(badOmenLevel);
                raids.setDirty();
                source.sendSuccess(() -> Component.literal("Created a raid in your local village"), false);
            } else {
                source.sendFailure(Component.literal("Failed to create a raid in your local village"));
            }

            return 1;
        }
    }

    private static int stop(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer serverplayer = source.getPlayerOrException();
        BlockPos blockpos = serverplayer.blockPosition();
        Raid raid = serverplayer.serverLevel().getRaidAt(blockpos);
        if (raid != null) {
            raid.stop();
            source.sendSuccess(() -> Component.literal("Stopped raid"), false);
            return 1;
        } else {
            source.sendFailure(Component.literal("No raid here"));
            return -1;
        }
    }

    private static int check(CommandSourceStack source) throws CommandSyntaxException {
        Raid raid = getRaid(source.getPlayerOrException());
        if (raid != null) {
            StringBuilder stringbuilder = new StringBuilder();
            stringbuilder.append("Found a started raid! ");
            source.sendSuccess(() -> Component.literal(stringbuilder.toString()), false);
            StringBuilder stringbuilder1 = new StringBuilder();
            stringbuilder1.append("Num groups spawned: ");
            stringbuilder1.append(raid.getGroupsSpawned());
            stringbuilder1.append(" Raid omen level: ");
            stringbuilder1.append(raid.getRaidOmenLevel());
            stringbuilder1.append(" Num mobs: ");
            stringbuilder1.append(raid.getTotalRaidersAlive());
            stringbuilder1.append(" Raid health: ");
            stringbuilder1.append(raid.getHealthOfLivingRaiders());
            stringbuilder1.append(" / ");
            stringbuilder1.append(raid.getTotalHealth());
            source.sendSuccess(() -> Component.literal(stringbuilder1.toString()), false);
            return 1;
        } else {
            source.sendFailure(Component.literal("Found no started raids"));
            return 0;
        }
    }

    @Nullable
    private static Raid getRaid(ServerPlayer player) {
        return player.serverLevel().getRaidAt(player.blockPosition());
    }
}
