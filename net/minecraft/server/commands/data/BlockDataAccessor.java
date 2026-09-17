package net.minecraft.server.commands.data;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Locale;
import java.util.function.Function;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class BlockDataAccessor implements DataAccessor {
    static final SimpleCommandExceptionType ERROR_NOT_A_BLOCK_ENTITY = new SimpleCommandExceptionType(Component.translatable("commands.data.block.invalid"));
    public static final Function<String, DataCommands.DataProvider> PROVIDER = p_139305_ -> new DataCommands.DataProvider() {
            @Override
            public DataAccessor access(CommandContext<CommandSourceStack> p_139319_) throws CommandSyntaxException {
                BlockPos blockpos = BlockPosArgument.getLoadedBlockPos(p_139319_, p_139305_ + "Pos");
                BlockEntity blockentity = p_139319_.getSource().getLevel().getBlockEntity(blockpos);
                if (blockentity == null) {
                    throw BlockDataAccessor.ERROR_NOT_A_BLOCK_ENTITY.create();
                } else {
                    return new BlockDataAccessor(blockentity, blockpos);
                }
            }

            @Override
            public ArgumentBuilder<CommandSourceStack, ?> wrap(
                ArgumentBuilder<CommandSourceStack, ?> p_139316_,
                Function<ArgumentBuilder<CommandSourceStack, ?>, ArgumentBuilder<CommandSourceStack, ?>> p_139317_
            ) {
                return p_139316_.then(Commands.literal("block").then(p_139317_.apply(Commands.argument(p_139305_ + "Pos", BlockPosArgument.blockPos()))));
            }
        };
    private final BlockEntity entity;
    private final BlockPos pos;

    public BlockDataAccessor(BlockEntity entity, BlockPos pos) {
        this.entity = entity;
        this.pos = pos;
    }

    @Override
    public void setData(CompoundTag other) {
        BlockState blockstate = this.entity.getLevel().getBlockState(this.pos);
        this.entity.loadWithComponents(other, this.entity.getLevel().registryAccess());
        this.entity.setChanged();
        this.entity.getLevel().sendBlockUpdated(this.pos, blockstate, blockstate, 3);
    }

    @Override
    public CompoundTag getData() {
        return this.entity.saveWithFullMetadata(this.entity.getLevel().registryAccess());
    }

    @Override
    public Component getModifiedSuccess() {
        return Component.translatable("commands.data.block.modified", this.pos.getX(), this.pos.getY(), this.pos.getZ());
    }

    /**
     * Gets the message used as a result of querying the given NBT (both for /data get and /data get path)
     */
    @Override
    public Component getPrintSuccess(Tag nbt) {
        return Component.translatable("commands.data.block.query", this.pos.getX(), this.pos.getY(), this.pos.getZ(), NbtUtils.toPrettyComponent(nbt));
    }

    /**
     * Gets the message used as a result of querying the given path with a scale.
     */
    @Override
    public Component getPrintSuccess(NbtPathArgument.NbtPath path, double scale, int value) {
        return Component.translatable(
            "commands.data.block.get",
            path.asString(),
            this.pos.getX(),
            this.pos.getY(),
            this.pos.getZ(),
            String.format(Locale.ROOT, "%.2f", scale),
            value
        );
    }
}
