package net.minecraft.data.tags;

import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.CatVariantTags;
import net.minecraft.world.entity.animal.CatVariant;

public class CatVariantTagsProvider extends TagsProvider<CatVariant> {
    /**
 * @deprecated Forge: Use the {@linkplain #CatVariantTagsProvider(PackOutput,
 *             CompletableFuture, String,
 *             net.neoforged.neoforge.common.data.ExistingFileHelper) mod id
 *             variant}
 */
    @Deprecated
    public CatVariantTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> provider) {
        super(output, Registries.CAT_VARIANT, provider);
    }
    public CatVariantTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> provider, String modId, @org.jetbrains.annotations.Nullable net.neoforged.neoforge.common.data.ExistingFileHelper existingFileHelper) {
        super(output, Registries.CAT_VARIANT, provider, modId, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        this.tag(CatVariantTags.DEFAULT_SPAWNS)
            .add(
                CatVariant.TABBY,
                CatVariant.BLACK,
                CatVariant.RED,
                CatVariant.SIAMESE,
                CatVariant.BRITISH_SHORTHAIR,
                CatVariant.CALICO,
                CatVariant.PERSIAN,
                CatVariant.RAGDOLL,
                CatVariant.WHITE,
                CatVariant.JELLIE
            );
        this.tag(CatVariantTags.FULL_MOON_SPAWNS).addTag(CatVariantTags.DEFAULT_SPAWNS).add(CatVariant.ALL_BLACK);
    }
}
