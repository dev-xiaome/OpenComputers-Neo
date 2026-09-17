package net.minecraft.commands;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.Level;

public interface SharedSuggestionProvider {
    Collection<String> getOnlinePlayerNames();

    default Collection<String> getCustomTabSugggestions() {
        return this.getOnlinePlayerNames();
    }

    default Collection<String> getSelectedEntities() {
        return Collections.emptyList();
    }

    Collection<String> getAllTeams();

    Stream<ResourceLocation> getAvailableSounds();

    Stream<ResourceLocation> getRecipeNames();

    CompletableFuture<Suggestions> customSuggestion(CommandContext<?> context);

    default Collection<SharedSuggestionProvider.TextCoordinates> getRelevantCoordinates() {
        return Collections.singleton(SharedSuggestionProvider.TextCoordinates.DEFAULT_GLOBAL);
    }

    default Collection<SharedSuggestionProvider.TextCoordinates> getAbsoluteCoordinates() {
        return Collections.singleton(SharedSuggestionProvider.TextCoordinates.DEFAULT_GLOBAL);
    }

    Set<ResourceKey<Level>> levels();

    RegistryAccess registryAccess();

    FeatureFlagSet enabledFeatures();

    default void suggestRegistryElements(Registry<?> registry, SharedSuggestionProvider.ElementSuggestionType type, SuggestionsBuilder builder) {
        if (type.shouldSuggestTags()) {
            suggestResource(registry.getTagNames().map(TagKey::location), builder, "#");
        }

        if (type.shouldSuggestElements()) {
            suggestResource(registry.keySet(), builder);
        }
    }

    CompletableFuture<Suggestions> suggestRegistryElements(
        ResourceKey<? extends Registry<?>> resourceKey,
        SharedSuggestionProvider.ElementSuggestionType registryKey,
        SuggestionsBuilder builder,
        CommandContext<?> context
    );

    boolean hasPermission(int permissionLevel);

    static <T> void filterResources(Iterable<T> resources, String input, Function<T, ResourceLocation> locationFunction, Consumer<T> resourceConsumer) {
        boolean flag = input.indexOf(58) > -1;

        for (T t : resources) {
            ResourceLocation resourcelocation = locationFunction.apply(t);
            if (flag) {
                String s = resourcelocation.toString();
                if (matchesSubStr(input, s)) {
                    resourceConsumer.accept(t);
                }
            } else if (matchesSubStr(input, resourcelocation.getNamespace())
                || matchesSubStr(input, resourcelocation.getPath())) { // Neo: Removed "minecraft" namespace requirement for path searching to allow modded path suggestions to show up
                resourceConsumer.accept(t);
            }
        }
    }

    static <T> void filterResources(Iterable<T> resources, String remaining, String prefix, Function<T, ResourceLocation> locationFunction, Consumer<T> resourceConsumer) {
        if (remaining.isEmpty()) {
            resources.forEach(resourceConsumer);
        } else {
            String s = Strings.commonPrefix(remaining, prefix);
            if (!s.isEmpty()) {
                String s1 = remaining.substring(s.length());
                filterResources(resources, s1, locationFunction, resourceConsumer);
            }
        }
    }

    static CompletableFuture<Suggestions> suggestResource(Iterable<ResourceLocation> resources, SuggestionsBuilder builder, String prefix) {
        String s = builder.getRemaining().toLowerCase(Locale.ROOT);
        filterResources(resources, s, prefix, p_82985_ -> p_82985_, p_339319_ -> builder.suggest(prefix + p_339319_));
        return builder.buildFuture();
    }

    static CompletableFuture<Suggestions> suggestResource(Stream<ResourceLocation> resources, SuggestionsBuilder builder, String prefix) {
        return suggestResource(resources::iterator, builder, prefix);
    }

    static CompletableFuture<Suggestions> suggestResource(Iterable<ResourceLocation> resources, SuggestionsBuilder builder) {
        String s = builder.getRemaining().toLowerCase(Locale.ROOT);
        filterResources(resources, s, p_82966_ -> p_82966_, p_82925_ -> builder.suggest(p_82925_.toString()));
        return builder.buildFuture();
    }

    static <T> CompletableFuture<Suggestions> suggestResource(
        Iterable<T> resources, SuggestionsBuilder builder, Function<T, ResourceLocation> locationFunction, Function<T, Message> suggestionFunction
    ) {
        String s = builder.getRemaining().toLowerCase(Locale.ROOT);
        filterResources(resources, s, locationFunction, p_82922_ -> builder.suggest(locationFunction.apply(p_82922_).toString(), suggestionFunction.apply(p_82922_)));
        return builder.buildFuture();
    }

    static CompletableFuture<Suggestions> suggestResource(Stream<ResourceLocation> resourceLocations, SuggestionsBuilder builder) {
        return suggestResource(resourceLocations::iterator, builder);
    }

    static <T> CompletableFuture<Suggestions> suggestResource(
        Stream<T> resources, SuggestionsBuilder builder, Function<T, ResourceLocation> locationFunction, Function<T, Message> suggestionFunction
    ) {
        return suggestResource(resources::iterator, builder, locationFunction, suggestionFunction);
    }

    static CompletableFuture<Suggestions> suggestCoordinates(
        String remaining, Collection<SharedSuggestionProvider.TextCoordinates> coordinates, SuggestionsBuilder builder, Predicate<String> validator
    ) {
        List<String> list = Lists.newArrayList();
        if (Strings.isNullOrEmpty(remaining)) {
            for (SharedSuggestionProvider.TextCoordinates sharedsuggestionprovider$textcoordinates : coordinates) {
                String s = sharedsuggestionprovider$textcoordinates.x
                    + " "
                    + sharedsuggestionprovider$textcoordinates.y
                    + " "
                    + sharedsuggestionprovider$textcoordinates.z;
                if (validator.test(s)) {
                    list.add(sharedsuggestionprovider$textcoordinates.x);
                    list.add(sharedsuggestionprovider$textcoordinates.x + " " + sharedsuggestionprovider$textcoordinates.y);
                    list.add(s);
                }
            }
        } else {
            String[] astring = remaining.split(" ");
            if (astring.length == 1) {
                for (SharedSuggestionProvider.TextCoordinates sharedsuggestionprovider$textcoordinates1 : coordinates) {
                    String s1 = astring[0] + " " + sharedsuggestionprovider$textcoordinates1.y + " " + sharedsuggestionprovider$textcoordinates1.z;
                    if (validator.test(s1)) {
                        list.add(astring[0] + " " + sharedsuggestionprovider$textcoordinates1.y);
                        list.add(s1);
                    }
                }
            } else if (astring.length == 2) {
                for (SharedSuggestionProvider.TextCoordinates sharedsuggestionprovider$textcoordinates2 : coordinates) {
                    String s2 = astring[0] + " " + astring[1] + " " + sharedsuggestionprovider$textcoordinates2.z;
                    if (validator.test(s2)) {
                        list.add(s2);
                    }
                }
            }
        }

        return suggest(list, builder);
    }

    static CompletableFuture<Suggestions> suggest2DCoordinates(
        String remaining, Collection<SharedSuggestionProvider.TextCoordinates> coordinates, SuggestionsBuilder builder, Predicate<String> validator
    ) {
        List<String> list = Lists.newArrayList();
        if (Strings.isNullOrEmpty(remaining)) {
            for (SharedSuggestionProvider.TextCoordinates sharedsuggestionprovider$textcoordinates : coordinates) {
                String s = sharedsuggestionprovider$textcoordinates.x + " " + sharedsuggestionprovider$textcoordinates.z;
                if (validator.test(s)) {
                    list.add(sharedsuggestionprovider$textcoordinates.x);
                    list.add(s);
                }
            }
        } else {
            String[] astring = remaining.split(" ");
            if (astring.length == 1) {
                for (SharedSuggestionProvider.TextCoordinates sharedsuggestionprovider$textcoordinates1 : coordinates) {
                    String s1 = astring[0] + " " + sharedsuggestionprovider$textcoordinates1.z;
                    if (validator.test(s1)) {
                        list.add(s1);
                    }
                }
            }
        }

        return suggest(list, builder);
    }

    static CompletableFuture<Suggestions> suggest(Iterable<String> strings, SuggestionsBuilder builder) {
        String s = builder.getRemaining().toLowerCase(Locale.ROOT);

        for (String s1 : strings) {
            if (matchesSubStr(s, s1.toLowerCase(Locale.ROOT))) {
                builder.suggest(s1);
            }
        }

        return builder.buildFuture();
    }

    static CompletableFuture<Suggestions> suggest(Stream<String> strings, SuggestionsBuilder builder) {
        String s = builder.getRemaining().toLowerCase(Locale.ROOT);
        strings.filter(p_82975_ -> matchesSubStr(s, p_82975_.toLowerCase(Locale.ROOT))).forEach(builder::suggest);
        return builder.buildFuture();
    }

    static CompletableFuture<Suggestions> suggest(String[] strings, SuggestionsBuilder builder) {
        String s = builder.getRemaining().toLowerCase(Locale.ROOT);

        for (String s1 : strings) {
            if (matchesSubStr(s, s1.toLowerCase(Locale.ROOT))) {
                builder.suggest(s1);
            }
        }

        return builder.buildFuture();
    }

    static <T> CompletableFuture<Suggestions> suggest(
        Iterable<T> resources, SuggestionsBuilder builder, Function<T, String> stringFunction, Function<T, Message> suggestionFunction
    ) {
        String s = builder.getRemaining().toLowerCase(Locale.ROOT);

        for (T t : resources) {
            String s1 = stringFunction.apply(t);
            if (matchesSubStr(s, s1.toLowerCase(Locale.ROOT))) {
                builder.suggest(s1, suggestionFunction.apply(t));
            }
        }

        return builder.buildFuture();
    }

    static boolean matchesSubStr(String input, String substring) {
        for (int k = 0; !substring.startsWith(input, k); k++) {
            int i = substring.indexOf(46, k);
            int j = substring.indexOf(95, k);
            if (Math.max(i, j) < 0) {
                return false;
            }

            if (i >= 0 && j >= 0) {
                k = Math.min(j, i);
            } else {
                k = i >= 0 ? i : j;
            }
        }

        return true;
    }

    public static enum ElementSuggestionType {
        TAGS,
        ELEMENTS,
        ALL;

        public boolean shouldSuggestTags() {
            return this == TAGS || this == ALL;
        }

        public boolean shouldSuggestElements() {
            return this == ELEMENTS || this == ALL;
        }
    }

    public static class TextCoordinates {
        public static final SharedSuggestionProvider.TextCoordinates DEFAULT_LOCAL = new SharedSuggestionProvider.TextCoordinates("^", "^", "^");
        public static final SharedSuggestionProvider.TextCoordinates DEFAULT_GLOBAL = new SharedSuggestionProvider.TextCoordinates("~", "~", "~");
        public final String x;
        public final String y;
        public final String z;

        public TextCoordinates(String x, String y, String z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
