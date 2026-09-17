package net.minecraft.client.resources;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class TextureAtlasHolder implements PreparableReloadListener, AutoCloseable {
    protected final TextureAtlas textureAtlas;
    private final ResourceLocation atlasInfoLocation;
    private final Set<MetadataSectionSerializer<?>> metadataSections;

    public TextureAtlasHolder(TextureManager textureManager, ResourceLocation textureAtlasLocation, ResourceLocation atlasInfoLocation) {
        this(textureManager, textureAtlasLocation, atlasInfoLocation, SpriteLoader.DEFAULT_METADATA_SECTIONS);
    }

    public TextureAtlasHolder(TextureManager textureManager, ResourceLocation textureAtlasLocation, ResourceLocation atlasInfoLocation, Set<MetadataSectionSerializer<?>> metadataSections) {
        this.atlasInfoLocation = atlasInfoLocation;
        this.textureAtlas = new TextureAtlas(textureAtlasLocation);
        textureManager.register(this.textureAtlas.location(), this.textureAtlas);
        this.metadataSections = metadataSections;
    }

    /**
     * Gets a sprite associated with the passed resource location.
     */
    protected TextureAtlasSprite getSprite(ResourceLocation location) {
        return this.textureAtlas.getSprite(location);
    }

    @Override
    public final CompletableFuture<Void> reload(
        PreparableReloadListener.PreparationBarrier preparationBarrier,
        ResourceManager resourceManager,
        ProfilerFiller preparationsProfiler,
        ProfilerFiller reloadProfiler,
        Executor backgroundExecutor,
        Executor gameExecutor
    ) {
        return SpriteLoader.create(this.textureAtlas)
            .loadAndStitch(resourceManager, this.atlasInfoLocation, 0, backgroundExecutor, this.metadataSections)
            .thenCompose(SpriteLoader.Preparations::waitForUpload)
            .thenCompose(preparationBarrier::wait)
            .thenAcceptAsync(p_249246_ -> this.apply(p_249246_, reloadProfiler), gameExecutor);
    }

    private void apply(SpriteLoader.Preparations preparations, ProfilerFiller profiler) {
        profiler.startTick();
        profiler.push("upload");
        this.textureAtlas.upload(preparations);
        profiler.pop();
        profiler.endTick();
    }

    @Override
    public void close() {
        this.textureAtlas.clearTextureData();
    }
}
