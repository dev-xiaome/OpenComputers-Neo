package net.minecraft.commands.arguments;

import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.Dynamic3CommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.datafixers.util.Either;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

public class ResourceOrTagArgument<T> implements ArgumentType<ResourceOrTagArgument.Result<T>> {
    private static final Collection<String> EXAMPLES = Arrays.asList("foo", "foo:bar", "012", "#skeletons", "#minecraft:skeletons");
    private static final Dynamic2CommandExceptionType ERROR_UNKNOWN_TAG = new Dynamic2CommandExceptionType(
        (p_304107_, p_304108_) -> Component.translatableEscape("argument.resource_tag.not_found", p_304107_, p_304108_)
    );
    private static final Dynamic3CommandExceptionType ERROR_INVALID_TAG_TYPE = new Dynamic3CommandExceptionType(
        (p_304109_, p_304110_, p_304111_) -> Component.translatableEscape("argument.resource_tag.invalid_type", p_304109_, p_304110_, p_304111_)
    );
    private final HolderLookup<T> registryLookup;
    final ResourceKey<? extends Registry<T>> registryKey;

    public ResourceOrTagArgument(CommandBuildContext context, ResourceKey<? extends Registry<T>> registryKey) {
        this.registryKey = registryKey;
        this.registryLookup = context.lookupOrThrow(registryKey);
    }

    public static <T> ResourceOrTagArgument<T> resourceOrTag(CommandBuildContext context, ResourceKey<? extends Registry<T>> registryKey) {
        return new ResourceOrTagArgument<>(context, registryKey);
    }

    public static <T> ResourceOrTagArgument.Result<T> getResourceOrTag(
        CommandContext<CommandSourceStack> context, String argument, ResourceKey<Registry<T>> registryKey
    ) throws CommandSyntaxException {
        ResourceOrTagArgument.Result<?> result = context.getArgument(argument, ResourceOrTagArgument.Result.class);
        Optional<ResourceOrTagArgument.Result<T>> optional = result.cast(registryKey);
        return optional.orElseThrow(() -> result.unwrap().map(p_252340_ -> {
                ResourceKey<?> resourcekey = p_252340_.key();
                return ResourceArgument.ERROR_INVALID_RESOURCE_TYPE.create(resourcekey.location(), resourcekey.registry(), registryKey.location());
            }, p_250301_ -> {
                TagKey<?> tagkey = p_250301_.key();
                return ERROR_INVALID_TAG_TYPE.create(tagkey.location(), tagkey.registry(), registryKey.location());
            }));
    }

    public ResourceOrTagArgument.Result<T> parse(StringReader reader) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '#') {
            int i = reader.getCursor();

            try {
                reader.skip();
                ResourceLocation resourcelocation1 = ResourceLocation.read(reader);
                TagKey<T> tagkey = TagKey.create(this.registryKey, resourcelocation1);
                HolderSet.Named<T> named = this.registryLookup
                    .get(tagkey)
                    .orElseThrow(() -> ERROR_UNKNOWN_TAG.createWithContext(reader, resourcelocation1, this.registryKey.location()));
                return new ResourceOrTagArgument.TagResult<>(named);
            } catch (CommandSyntaxException commandsyntaxexception) {
                reader.setCursor(i);
                throw commandsyntaxexception;
            }
        } else {
            ResourceLocation resourcelocation = ResourceLocation.read(reader);
            ResourceKey<T> resourcekey = ResourceKey.create(this.registryKey, resourcelocation);
            Holder.Reference<T> reference = this.registryLookup
                .get(resourcekey)
                .orElseThrow(() -> ResourceArgument.ERROR_UNKNOWN_RESOURCE.createWithContext(reader, resourcelocation, this.registryKey.location()));
            return new ResourceOrTagArgument.ResourceResult<>(reference);
        }
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        SharedSuggestionProvider.suggestResource(this.registryLookup.listTagIds().map(TagKey::location), builder, "#");
        return SharedSuggestionProvider.suggestResource(this.registryLookup.listElementIds().map(ResourceKey::location), builder);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    public static class Info<T> implements ArgumentTypeInfo<ResourceOrTagArgument<T>, ResourceOrTagArgument.Info<T>.Template> {
        public void serializeToNetwork(ResourceOrTagArgument.Info<T>.Template template, FriendlyByteBuf buffer) {
            buffer.writeResourceKey(template.registryKey);
        }

        public ResourceOrTagArgument.Info<T>.Template deserializeFromNetwork(FriendlyByteBuf buffer) {
            return new ResourceOrTagArgument.Info.Template(buffer.readRegistryKey());
        }

        public void serializeToJson(ResourceOrTagArgument.Info<T>.Template template, JsonObject json) {
            json.addProperty("registry", template.registryKey.location().toString());
        }

        public ResourceOrTagArgument.Info<T>.Template unpack(ResourceOrTagArgument<T> argument) {
            return new ResourceOrTagArgument.Info.Template(argument.registryKey);
        }

        public final class Template implements ArgumentTypeInfo.Template<ResourceOrTagArgument<T>> {
            final ResourceKey<? extends Registry<T>> registryKey;

            Template(ResourceKey<? extends Registry<T>> registryKey) {
                this.registryKey = registryKey;
            }

            public ResourceOrTagArgument<T> instantiate(CommandBuildContext context) {
                return new ResourceOrTagArgument<>(context, this.registryKey);
            }

            @Override
            public ArgumentTypeInfo<ResourceOrTagArgument<T>, ?> type() {
                return Info.this;
            }
        }
    }

    static record ResourceResult<T>(Holder.Reference<T> value) implements ResourceOrTagArgument.Result<T> {
        @Override
        public Either<Holder.Reference<T>, HolderSet.Named<T>> unwrap() {
            return Either.left(this.value);
        }

        @Override
        public <E> Optional<ResourceOrTagArgument.Result<E>> cast(ResourceKey<? extends Registry<E>> registryKey) {
            return this.value.key().isFor(registryKey) ? Optional.of((ResourceOrTagArgument.Result<E>)this) : Optional.empty();
        }

        public boolean test(Holder<T> holder) {
            return holder.equals(this.value);
        }

        @Override
        public String asPrintable() {
            return this.value.key().location().toString();
        }
    }

    public interface Result<T> extends Predicate<Holder<T>> {
        Either<Holder.Reference<T>, HolderSet.Named<T>> unwrap();

        <E> Optional<ResourceOrTagArgument.Result<E>> cast(ResourceKey<? extends Registry<E>> registryKey);

        String asPrintable();
    }

    static record TagResult<T>(HolderSet.Named<T> tag) implements ResourceOrTagArgument.Result<T> {
        @Override
        public Either<Holder.Reference<T>, HolderSet.Named<T>> unwrap() {
            return Either.right(this.tag);
        }

        @Override
        public <E> Optional<ResourceOrTagArgument.Result<E>> cast(ResourceKey<? extends Registry<E>> registryKey) {
            return this.tag.key().isFor(registryKey) ? Optional.of((ResourceOrTagArgument.Result<E>)this) : Optional.empty();
        }

        public boolean test(Holder<T> holder) {
            return this.tag.contains(holder);
        }

        @Override
        public String asPrintable() {
            return "#" + this.tag.key().location();
        }
    }
}
