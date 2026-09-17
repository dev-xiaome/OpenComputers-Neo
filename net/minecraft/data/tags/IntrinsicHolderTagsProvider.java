package net.minecraft.data.tags;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagBuilder;
import net.minecraft.tags.TagKey;

public abstract class IntrinsicHolderTagsProvider<T> extends TagsProvider<T> {
    private final Function<T, ResourceKey<T>> keyExtractor;

    @Deprecated
    public IntrinsicHolderTagsProvider(
        PackOutput output,
        ResourceKey<? extends Registry<T>> registryKey,
        CompletableFuture<HolderLookup.Provider> lookupProvider,
        Function<T, ResourceKey<T>> keyExtractor
    ) {
        this(output, registryKey, lookupProvider, keyExtractor, "vanilla", null);
    }

    @Deprecated
    public IntrinsicHolderTagsProvider(
        PackOutput output,
        ResourceKey<? extends Registry<T>> registryKey,
        CompletableFuture<HolderLookup.Provider> lookupProvider,
        CompletableFuture<TagsProvider.TagLookup<T>> parentProvider,
        Function<T, ResourceKey<T>> keyExtractor
    ) {
        this(output, registryKey, lookupProvider, parentProvider, keyExtractor, "vanilla", null);
    }

    public IntrinsicHolderTagsProvider(
        PackOutput output,
        ResourceKey<? extends Registry<T>> registryKey,
        CompletableFuture<HolderLookup.Provider> lookupProvider,
        Function<T, ResourceKey<T>> keyExtractor,
        String modId,
        @org.jetbrains.annotations.Nullable net.neoforged.neoforge.common.data.ExistingFileHelper existingFileHelper
    ) {
        super(output, registryKey, lookupProvider, modId, existingFileHelper);
        this.keyExtractor = keyExtractor;
    }

    public IntrinsicHolderTagsProvider(
        PackOutput output,
        ResourceKey<? extends Registry<T>> registryKey,
        CompletableFuture<HolderLookup.Provider> lookupProvider,
        CompletableFuture<TagsProvider.TagLookup<T>> parentProvider,
        Function<T, ResourceKey<T>> keyExtractor,
        String modId,
        @org.jetbrains.annotations.Nullable net.neoforged.neoforge.common.data.ExistingFileHelper existingFileHelper
    ) {
        super(output, registryKey, lookupProvider, parentProvider, modId, existingFileHelper);
        this.keyExtractor = keyExtractor;
    }



    protected IntrinsicHolderTagsProvider.IntrinsicTagAppender<T> tag(TagKey<T> tag) {
        TagBuilder tagbuilder = this.getOrCreateRawBuilder(tag);
        return new IntrinsicHolderTagsProvider.IntrinsicTagAppender<>(tagbuilder, this.keyExtractor, this.modId);
    }

    public static class IntrinsicTagAppender<T> extends TagsProvider.TagAppender<T> implements net.neoforged.neoforge.common.extensions.IIntrinsicHolderTagAppenderExtension<T> {
        private final Function<T, ResourceKey<T>> keyExtractor;

        IntrinsicTagAppender(TagBuilder builder, Function<T, ResourceKey<T>> keyExtractor, String modId) {
            super(builder, modId);
            this.keyExtractor = keyExtractor;
        }

        public IntrinsicHolderTagsProvider.IntrinsicTagAppender<T> addTag(TagKey<T> tag) {
            super.addTag(tag);
            return this;
        }

        public final IntrinsicHolderTagsProvider.IntrinsicTagAppender<T> add(T value) {
            this.add(this.keyExtractor.apply(value));
            return this;
        }

        @SafeVarargs
        public final IntrinsicHolderTagsProvider.IntrinsicTagAppender<T> add(T... values) {
            Stream.<T>of(values).map(this.keyExtractor).forEach(this::add);
            return this;
        }

        @Override
        public final ResourceKey<T> getKey(T value) {
            return this.keyExtractor.apply(value);
        }
    }
}
