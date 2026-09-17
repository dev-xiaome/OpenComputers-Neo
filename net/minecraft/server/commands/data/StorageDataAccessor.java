package net.minecraft.server.commands.data;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Locale;
import java.util.function.Function;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.CommandStorage;

public class StorageDataAccessor implements DataAccessor {
    static final SuggestionProvider<CommandSourceStack> SUGGEST_STORAGE = (p_139547_, p_139548_) -> SharedSuggestionProvider.suggestResource(
            getGlobalTags(p_139547_).keys(), p_139548_
        );
    public static final Function<String, DataCommands.DataProvider> PROVIDER = p_139554_ -> new DataCommands.DataProvider() {
            @Override
            public DataAccessor access(CommandContext<CommandSourceStack> p_139570_) {
                return new StorageDataAccessor(StorageDataAccessor.getGlobalTags(p_139570_), ResourceLocationArgument.getId(p_139570_, p_139554_));
            }

            @Override
            public ArgumentBuilder<CommandSourceStack, ?> wrap(
                ArgumentBuilder<CommandSourceStack, ?> p_139567_,
                Function<ArgumentBuilder<CommandSourceStack, ?>, ArgumentBuilder<CommandSourceStack, ?>> p_139568_
            ) {
                return p_139567_.then(
                    Commands.literal("storage")
                        .then(p_139568_.apply(Commands.argument(p_139554_, ResourceLocationArgument.id()).suggests(StorageDataAccessor.SUGGEST_STORAGE)))
                );
            }
        };
    private final CommandStorage storage;
    private final ResourceLocation id;

    static CommandStorage getGlobalTags(CommandContext<CommandSourceStack> context) {
        return context.getSource().getServer().getCommandStorage();
    }

    StorageDataAccessor(CommandStorage storage, ResourceLocation id) {
        this.storage = storage;
        this.id = id;
    }

    @Override
    public void setData(CompoundTag other) {
        this.storage.set(this.id, other);
    }

    @Override
    public CompoundTag getData() {
        return this.storage.get(this.id);
    }

    @Override
    public Component getModifiedSuccess() {
        return Component.translatable("commands.data.storage.modified", Component.translationArg(this.id));
    }

    /**
     * Gets the message used as a result of querying the given NBT (both for /data get and /data get path)
     */
    @Override
    public Component getPrintSuccess(Tag nbt) {
        return Component.translatable("commands.data.storage.query", Component.translationArg(this.id), NbtUtils.toPrettyComponent(nbt));
    }

    /**
     * Gets the message used as a result of querying the given path with a scale.
     */
    @Override
    public Component getPrintSuccess(NbtPathArgument.NbtPath path, double scale, int value) {
        return Component.translatable(
            "commands.data.storage.get", path.asString(), Component.translationArg(this.id), String.format(Locale.ROOT, "%.2f", scale), value
        );
    }
}
