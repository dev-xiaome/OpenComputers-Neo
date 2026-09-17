package net.minecraft.commands.arguments;

import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.Dynamic3CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.structure.Structure;

public class ResourceArgument<T> implements ArgumentType<Holder.Reference<T>> {
    private static final Collection<String> EXAMPLES = Arrays.asList("foo", "foo:bar", "012");
    private static final DynamicCommandExceptionType ERROR_NOT_SUMMONABLE_ENTITY = new DynamicCommandExceptionType(
        p_304094_ -> Component.translatableEscape("entity.not_summonable", p_304094_)
    );
    public static final Dynamic2CommandExceptionType ERROR_UNKNOWN_RESOURCE = new Dynamic2CommandExceptionType(
        (p_304095_, p_304096_) -> Component.translatableEscape("argument.resource.not_found", p_304095_, p_304096_)
    );
    public static final Dynamic3CommandExceptionType ERROR_INVALID_RESOURCE_TYPE = new Dynamic3CommandExceptionType(
        (p_304097_, p_304098_, p_304099_) -> Component.translatableEscape("argument.resource.invalid_type", p_304097_, p_304098_, p_304099_)
    );
    final ResourceKey<? extends Registry<T>> registryKey;
    private final HolderLookup<T> registryLookup;

    public ResourceArgument(CommandBuildContext context, ResourceKey<? extends Registry<T>> registryKey) {
        this.registryKey = registryKey;
        this.registryLookup = context.lookupOrThrow(registryKey);
    }

    public static <T> ResourceArgument<T> resource(CommandBuildContext context, ResourceKey<? extends Registry<T>> registryKey) {
        return new ResourceArgument<>(context, registryKey);
    }

    public static <T> Holder.Reference<T> getResource(CommandContext<CommandSourceStack> context, String argument, ResourceKey<Registry<T>> registryKey) throws CommandSyntaxException {
        Holder.Reference<T> reference = context.getArgument(argument, Holder.Reference.class);
        ResourceKey<?> resourcekey = reference.key();
        if (resourcekey.isFor(registryKey)) {
            return reference;
        } else {
            throw ERROR_INVALID_RESOURCE_TYPE.create(resourcekey.location(), resourcekey.registry(), registryKey.location());
        }
    }

    public static Holder.Reference<Attribute> getAttribute(CommandContext<CommandSourceStack> context, String argument) throws CommandSyntaxException {
        return getResource(context, argument, Registries.ATTRIBUTE);
    }

    public static Holder.Reference<ConfiguredFeature<?, ?>> getConfiguredFeature(CommandContext<CommandSourceStack> context, String argument) throws CommandSyntaxException {
        return getResource(context, argument, Registries.CONFIGURED_FEATURE);
    }

    public static Holder.Reference<Structure> getStructure(CommandContext<CommandSourceStack> context, String argument) throws CommandSyntaxException {
        return getResource(context, argument, Registries.STRUCTURE);
    }

    public static Holder.Reference<EntityType<?>> getEntityType(CommandContext<CommandSourceStack> context, String argument) throws CommandSyntaxException {
        return getResource(context, argument, Registries.ENTITY_TYPE);
    }

    public static Holder.Reference<EntityType<?>> getSummonableEntityType(CommandContext<CommandSourceStack> context, String argument) throws CommandSyntaxException {
        Holder.Reference<EntityType<?>> reference = getResource(context, argument, Registries.ENTITY_TYPE);
        if (!reference.value().canSummon()) {
            throw ERROR_NOT_SUMMONABLE_ENTITY.create(reference.key().location().toString());
        } else {
            return reference;
        }
    }

    public static Holder.Reference<MobEffect> getMobEffect(CommandContext<CommandSourceStack> context, String argument) throws CommandSyntaxException {
        return getResource(context, argument, Registries.MOB_EFFECT);
    }

    public static Holder.Reference<Enchantment> getEnchantment(CommandContext<CommandSourceStack> context, String argument) throws CommandSyntaxException {
        return getResource(context, argument, Registries.ENCHANTMENT);
    }

    public Holder.Reference<T> parse(StringReader builder) throws CommandSyntaxException {
        ResourceLocation resourcelocation = ResourceLocation.read(builder);
        ResourceKey<T> resourcekey = ResourceKey.create(this.registryKey, resourcelocation);
        return this.registryLookup
            .get(resourcekey)
            .orElseThrow(() -> ERROR_UNKNOWN_RESOURCE.createWithContext(builder, resourcelocation, this.registryKey.location()));
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggestResource(this.registryLookup.listElementIds().map(ResourceKey::location), builder);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    public static class Info<T> implements ArgumentTypeInfo<ResourceArgument<T>, ResourceArgument.Info<T>.Template> {
        public void serializeToNetwork(ResourceArgument.Info<T>.Template template, FriendlyByteBuf buffer) {
            buffer.writeResourceKey(template.registryKey);
        }

        public ResourceArgument.Info<T>.Template deserializeFromNetwork(FriendlyByteBuf buffer) {
            return new ResourceArgument.Info.Template(buffer.readRegistryKey());
        }

        public void serializeToJson(ResourceArgument.Info<T>.Template template, JsonObject json) {
            json.addProperty("registry", template.registryKey.location().toString());
        }

        public ResourceArgument.Info<T>.Template unpack(ResourceArgument<T> argument) {
            return new ResourceArgument.Info.Template(argument.registryKey);
        }

        public final class Template implements ArgumentTypeInfo.Template<ResourceArgument<T>> {
            final ResourceKey<? extends Registry<T>> registryKey;

            Template(ResourceKey<? extends Registry<T>> registryKey) {
                this.registryKey = registryKey;
            }

            public ResourceArgument<T> instantiate(CommandBuildContext context) {
                return new ResourceArgument<>(context, this.registryKey);
            }

            @Override
            public ArgumentTypeInfo<ResourceArgument<T>, ?> type() {
                return Info.this;
            }
        }
    }
}
