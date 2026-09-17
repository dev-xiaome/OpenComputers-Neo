package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public class SetBlockCommand {
    private static final SimpleCommandExceptionType ERROR_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.setblock.failed"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("setblock")
                .requires(p_138606_ -> p_138606_.hasPermission(2))
                .then(
                    Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(
                            Commands.argument("block", BlockStateArgument.block(context))
                                .executes(
                                    p_138618_ -> setBlock(
                                            p_138618_.getSource(),
                                            BlockPosArgument.getLoadedBlockPos(p_138618_, "pos"),
                                            BlockStateArgument.getBlock(p_138618_, "block"),
                                            SetBlockCommand.Mode.REPLACE,
                                            null
                                        )
                                )
                                .then(
                                    Commands.literal("destroy")
                                        .executes(
                                            p_138616_ -> setBlock(
                                                    p_138616_.getSource(),
                                                    BlockPosArgument.getLoadedBlockPos(p_138616_, "pos"),
                                                    BlockStateArgument.getBlock(p_138616_, "block"),
                                                    SetBlockCommand.Mode.DESTROY,
                                                    null
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("keep")
                                        .executes(
                                            p_138614_ -> setBlock(
                                                    p_138614_.getSource(),
                                                    BlockPosArgument.getLoadedBlockPos(p_138614_, "pos"),
                                                    BlockStateArgument.getBlock(p_138614_, "block"),
                                                    SetBlockCommand.Mode.REPLACE,
                                                    p_180517_ -> p_180517_.getLevel().isEmptyBlock(p_180517_.getPos())
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("replace")
                                        .executes(
                                            p_138604_ -> setBlock(
                                                    p_138604_.getSource(),
                                                    BlockPosArgument.getLoadedBlockPos(p_138604_, "pos"),
                                                    BlockStateArgument.getBlock(p_138604_, "block"),
                                                    SetBlockCommand.Mode.REPLACE,
                                                    null
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static int setBlock(
        CommandSourceStack source, BlockPos pos, BlockInput state, SetBlockCommand.Mode mode, @Nullable Predicate<BlockInWorld> predicate
    ) throws CommandSyntaxException {
        ServerLevel serverlevel = source.getLevel();
        if (predicate != null && !predicate.test(new BlockInWorld(serverlevel, pos, true))) {
            throw ERROR_FAILED.create();
        } else {
            boolean flag;
            if (mode == SetBlockCommand.Mode.DESTROY) {
                serverlevel.destroyBlock(pos, true);
                flag = !state.getState().isAir() || !serverlevel.getBlockState(pos).isAir();
            } else {
                BlockEntity blockentity = serverlevel.getBlockEntity(pos);
                Clearable.tryClear(blockentity);
                flag = true;
            }

            if (flag && !state.place(serverlevel, pos, 2)) {
                throw ERROR_FAILED.create();
            } else {
                serverlevel.blockUpdated(pos, state.getState().getBlock());
                source.sendSuccess(() -> Component.translatable("commands.setblock.success", pos.getX(), pos.getY(), pos.getZ()), true);
                return 1;
            }
        }
    }

    public interface Filter {
        @Nullable
        BlockInput filter(BoundingBox boundingBox, BlockPos pos, BlockInput blockInput, ServerLevel level);
    }

    public static enum Mode {
        REPLACE,
        DESTROY;
    }
}
