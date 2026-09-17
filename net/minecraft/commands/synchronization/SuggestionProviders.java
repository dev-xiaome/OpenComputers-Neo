package net.minecraft.commands.synchronization;

import com.google.common.collect.Maps;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

public class SuggestionProviders {
    private static final Map<ResourceLocation, SuggestionProvider<SharedSuggestionProvider>> PROVIDERS_BY_NAME = Maps.newHashMap();
    private static final ResourceLocation DEFAULT_NAME = ResourceLocation.withDefaultNamespace("ask_server");
    public static final SuggestionProvider<SharedSuggestionProvider> ASK_SERVER = register(
        DEFAULT_NAME, (p_121673_, p_121674_) -> p_121673_.getSource().customSuggestion(p_121673_)
    );
    public static final SuggestionProvider<CommandSourceStack> ALL_RECIPES = register(
        ResourceLocation.withDefaultNamespace("all_recipes"),
        (p_121670_, p_121671_) -> SharedSuggestionProvider.suggestResource(p_121670_.getSource().getRecipeNames(), p_121671_)
    );
    public static final SuggestionProvider<CommandSourceStack> AVAILABLE_SOUNDS = register(
        ResourceLocation.withDefaultNamespace("available_sounds"),
        (p_121667_, p_121668_) -> SharedSuggestionProvider.suggestResource(p_121667_.getSource().getAvailableSounds(), p_121668_)
    );
    public static final SuggestionProvider<CommandSourceStack> SUMMONABLE_ENTITIES = register(
        ResourceLocation.withDefaultNamespace("summonable_entities"),
        (p_344181_, p_344182_) -> SharedSuggestionProvider.suggestResource(
                BuiltInRegistries.ENTITY_TYPE
                    .stream()
                    .filter(p_247987_ -> p_247987_.isEnabled(p_344181_.getSource().enabledFeatures()) && p_247987_.canSummon()),
                p_344182_,
                EntityType::getKey,
                p_212436_ -> Component.translatable(Util.makeDescriptionId("entity", EntityType.getKey(p_212436_)))
            )
    );

    public static <S extends SharedSuggestionProvider> SuggestionProvider<S> register(
        ResourceLocation name, SuggestionProvider<SharedSuggestionProvider> provider
    ) {
        if (PROVIDERS_BY_NAME.containsKey(name)) {
            throw new IllegalArgumentException("A command suggestion provider is already registered with the name " + name);
        } else {
            PROVIDERS_BY_NAME.put(name, provider);
            return (SuggestionProvider<S>)new SuggestionProviders.Wrapper(name, provider);
        }
    }

    public static SuggestionProvider<SharedSuggestionProvider> getProvider(ResourceLocation name) {
        return PROVIDERS_BY_NAME.getOrDefault(name, ASK_SERVER);
    }

    /**
     * Gets the ID for the given provider. If the provider is not a wrapped one created via {@link #register}, then it returns {@link #ASK_SERVER_ID} instead, as there is no known ID but ASK_SERVER always works.
     */
    public static ResourceLocation getName(SuggestionProvider<SharedSuggestionProvider> provider) {
        return provider instanceof SuggestionProviders.Wrapper ? ((SuggestionProviders.Wrapper)provider).name : DEFAULT_NAME;
    }

    /**
     * Checks to make sure that the given suggestion provider is a wrapped one that was created via {@link #register}. If not, returns {@link #ASK_SERVER}. Needed because custom providers don't have a known ID to send to the client, but ASK_SERVER always works.
     */
    public static SuggestionProvider<SharedSuggestionProvider> safelySwap(SuggestionProvider<SharedSuggestionProvider> provider) {
        return provider instanceof SuggestionProviders.Wrapper ? provider : ASK_SERVER;
    }

    protected static class Wrapper implements SuggestionProvider<SharedSuggestionProvider> {
        private final SuggestionProvider<SharedSuggestionProvider> delegate;
        final ResourceLocation name;

        public Wrapper(ResourceLocation name, SuggestionProvider<SharedSuggestionProvider> delegate) {
            this.delegate = delegate;
            this.name = name;
        }

        @Override
        public CompletableFuture<Suggestions> getSuggestions(CommandContext<SharedSuggestionProvider> context, SuggestionsBuilder builder) throws CommandSyntaxException {
            return this.delegate.getSuggestions(context, builder);
        }
    }
}
