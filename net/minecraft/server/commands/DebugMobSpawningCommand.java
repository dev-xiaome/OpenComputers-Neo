package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;

public class DebugMobSpawningCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> literalargumentbuilder = Commands.literal("debugmobspawning")
            .requires(p_180113_ -> p_180113_.hasPermission(2));

        for (MobCategory mobcategory : MobCategory.values()) {
            literalargumentbuilder.then(
                Commands.literal(mobcategory.getName())
                    .then(
                        Commands.argument("at", BlockPosArgument.blockPos())
                            .executes(p_180109_ -> spawnMobs(p_180109_.getSource(), mobcategory, BlockPosArgument.getLoadedBlockPos(p_180109_, "at")))
                    )
            );
        }

        dispatcher.register(literalargumentbuilder);
    }

    private static int spawnMobs(CommandSourceStack source, MobCategory mobCategory, BlockPos pos) {
        NaturalSpawner.spawnCategoryForPosition(mobCategory, source.getLevel(), pos);
        return 1;
    }
}
