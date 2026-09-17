package net.minecraft.commands.arguments;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.serialization.Codec;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class ResourceOrIdArgument<T> implements ArgumentType<Holder<T>> {
    private static final Collection<String> EXAMPLES = List.of("foo", "foo:bar", "012", "{}", "true");
    public static final DynamicCommandExceptionType ERROR_FAILED_TO_PARSE = new DynamicCommandExceptionType(
        p_335811_ -> Component.translatableEscape("argument.resource_or_id.failed_to_parse", p_335811_)
    );
    private static final SimpleCommandExceptionType ERROR_INVALID = new SimpleCommandExceptionType(Component.translatable("argument.resource_or_id.invalid"));
    private final HolderLookup.Provider registryLookup;
    private final boolean hasRegistry;
    private final Codec<Holder<T>> codec;

    protected ResourceOrIdArgument(CommandBuildContext registryLookup, ResourceKey<Registry<T>> registryKey, Codec<Holder<T>> codec) {
        this.registryLookup = registryLookup;
        this.hasRegistry = registryLookup.lookup(registryKey).isPresent();
        this.codec = codec;
    }

    public static ResourceOrIdArgument.LootTableArgument lootTable(CommandBuildContext context) {
        return new ResourceOrIdArgument.LootTableArgument(context);
    }

    public static Holder<LootTable> getLootTable(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return getResource(context, name);
    }

    public static ResourceOrIdArgument.LootModifierArgument lootModifier(CommandBuildContext context) {
        return new ResourceOrIdArgument.LootModifierArgument(context);
    }

    public static Holder<LootItemFunction> getLootModifier(CommandContext<CommandSourceStack> context, String name) {
        return getResource(context, name);
    }

    public static ResourceOrIdArgument.LootPredicateArgument lootPredicate(CommandBuildContext context) {
        return new ResourceOrIdArgument.LootPredicateArgument(context);
    }

    public static Holder<LootItemCondition> getLootPredicate(CommandContext<CommandSourceStack> context, String name) {
        return getResource(context, name);
    }

    private static <T> Holder<T> getResource(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, Holder.class);
    }

    @Nullable
    public Holder<T> parse(StringReader reader) throws CommandSyntaxException {
        Tag tag = parseInlineOrId(reader);
        if (!this.hasRegistry) {
            return null;
        } else {
            RegistryOps<Tag> registryops = this.registryLookup.createSerializationContext(NbtOps.INSTANCE);
            return this.codec.parse(registryops, tag).getOrThrow(p_335883_ -> ERROR_FAILED_TO_PARSE.createWithContext(reader, p_335883_));
        }
    }

    @VisibleForTesting
    static Tag parseInlineOrId(StringReader reader) throws CommandSyntaxException {
        int i = reader.getCursor();
        Tag tag = new TagParser(reader).readValue();
        if (hasConsumedWholeArg(reader)) {
            return tag;
        } else {
            reader.setCursor(i);
            ResourceLocation resourcelocation = ResourceLocation.read(reader);
            if (hasConsumedWholeArg(reader)) {
                return StringTag.valueOf(resourcelocation.toString());
            } else {
                reader.setCursor(i);
                throw ERROR_INVALID.createWithContext(reader);
            }
        }
    }

    private static boolean hasConsumedWholeArg(StringReader reader) {
        return !reader.canRead() || reader.peek() == ' ';
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    public static class LootModifierArgument extends ResourceOrIdArgument<LootItemFunction> {
        protected LootModifierArgument(CommandBuildContext context) {
            super(context, Registries.ITEM_MODIFIER, LootItemFunctions.CODEC);
        }
    }

    public static class LootPredicateArgument extends ResourceOrIdArgument<LootItemCondition> {
        protected LootPredicateArgument(CommandBuildContext context) {
            super(context, Registries.PREDICATE, LootItemCondition.CODEC);
        }
    }

    public static class LootTableArgument extends ResourceOrIdArgument<LootTable> {
        protected LootTableArgument(CommandBuildContext context) {
            super(context, Registries.LOOT_TABLE, LootTable.CODEC);
        }
    }
}
