package net.minecraft.data.tags;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.TagBuilder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public abstract class ItemTagsProvider extends IntrinsicHolderTagsProvider<Item> {
    /**
     * A function that resolves block tag builders.
     */
    private final CompletableFuture<TagsProvider.TagLookup<Block>> blockTags;
    private final Map<TagKey<Block>, TagKey<Item>> tagsToCopy = new HashMap<>();

    public ItemTagsProvider(
    /**
     * @deprecated Forge: Use the {@linkplain #ItemTagsProvider(PackOutput, CompletableFuture, CompletableFuture, String, net.neoforged.neoforge.common.data.ExistingFileHelper) mod id variant}
     */
    @Deprecated
        PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, CompletableFuture<TagsProvider.TagLookup<Block>> blockTags
    ) {
        this(output, lookupProvider, blockTags, "vanilla", null);
    }

    /**
     * @deprecated Forge: Use the {@linkplain #ItemTagsProvider(PackOutput,
     *             CompletableFuture, CompletableFuture, CompletableFuture, String,
     *             net.neoforged.neoforge.common.data.ExistingFileHelper) mod id
     *             variant}
     */
    @Deprecated
    public ItemTagsProvider(
        PackOutput output,
        CompletableFuture<HolderLookup.Provider> lookupProvider,
        CompletableFuture<TagsProvider.TagLookup<Item>> parentProvider,
        CompletableFuture<TagsProvider.TagLookup<Block>> blockTags
    ) {
        this(output, lookupProvider, parentProvider, blockTags, "vanilla", null);
    }

    public ItemTagsProvider(
        PackOutput output,
        CompletableFuture<HolderLookup.Provider> lookupProvider,
        CompletableFuture<TagsProvider.TagLookup<Block>> blockTags,
        String modId,
        @org.jetbrains.annotations.Nullable net.neoforged.neoforge.common.data.ExistingFileHelper existingFileHelper
    ) {
        super(output, Registries.ITEM, lookupProvider, p_255790_ -> p_255790_.builtInRegistryHolder().key(), modId, existingFileHelper);
        this.blockTags = blockTags;
    }

    public ItemTagsProvider(
        PackOutput output,
        CompletableFuture<HolderLookup.Provider> lookupProvider,
        CompletableFuture<TagsProvider.TagLookup<Item>> parentProvider,
        CompletableFuture<TagsProvider.TagLookup<Block>> blockTags,
        String modId,
        @org.jetbrains.annotations.Nullable net.neoforged.neoforge.common.data.ExistingFileHelper existingFileHelper
    ) {
        super(output, Registries.ITEM, lookupProvider, parentProvider, p_274765_ -> p_274765_.builtInRegistryHolder().key(), modId, existingFileHelper);
        this.blockTags = blockTags;
    }

    /**
     * Copies the entries from a block tag into an item tag.
     */
    protected void copy(TagKey<Block> blockTag, TagKey<Item> itemTag) {
        this.tagsToCopy.put(blockTag, itemTag);
    }

    @Override
    protected CompletableFuture<HolderLookup.Provider> createContentsProvider() {
        return super.createContentsProvider().thenCombine(this.blockTags, (p_274766_, p_274767_) -> {
            this.tagsToCopy.forEach((p_274763_, p_274764_) -> {
                TagBuilder tagbuilder = this.getOrCreateRawBuilder((TagKey<Item>)p_274764_);
                Optional<TagBuilder> optional = p_274767_.apply(p_274763_);
                // Neo: Copy over the remove entries as well
                var fromBuilder = optional.orElseThrow(() -> new IllegalStateException("Missing block tag " + p_274764_.location()));
                fromBuilder.build().forEach(tagbuilder::add);
                fromBuilder.getRemoveEntries().forEach(tagbuilder::remove);
            });
            return (HolderLookup.Provider)p_274766_;
        });
    }
}
