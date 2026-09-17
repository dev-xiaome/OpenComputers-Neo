package net.minecraft.client.renderer.texture;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class PreloadedTexture extends SimpleTexture {
    @Nullable
    private CompletableFuture<SimpleTexture.TextureImage> future;

    public PreloadedTexture(ResourceManager resourceManager, ResourceLocation location, Executor backgroundExecutor) {
        super(location);
        this.future = CompletableFuture.supplyAsync(() -> SimpleTexture.TextureImage.load(resourceManager, location), backgroundExecutor);
    }

    @Override
    protected SimpleTexture.TextureImage getTextureImage(ResourceManager resourceManager) {
        if (this.future != null) {
            SimpleTexture.TextureImage simpletexture$textureimage = this.future.join();
            this.future = null;
            return simpletexture$textureimage;
        } else {
            return SimpleTexture.TextureImage.load(resourceManager, this.location);
        }
    }

    public CompletableFuture<Void> getFuture() {
        return this.future == null ? CompletableFuture.completedFuture(null) : this.future.thenApply(p_118110_ -> null);
    }

    @Override
    public void reset(TextureManager textureManager, ResourceManager resourceManager, ResourceLocation resourceLocation, Executor p_executor) {
        this.future = CompletableFuture.supplyAsync(() -> SimpleTexture.TextureImage.load(resourceManager, this.location), Util.backgroundExecutor());
        this.future.thenRunAsync(() -> textureManager.register(this.location, this), executor(p_executor));
    }

    private static Executor executor(Executor executor) {
        return p_118124_ -> executor.execute(() -> RenderSystem.recordRenderCall(p_118124_::run));
    }
}
