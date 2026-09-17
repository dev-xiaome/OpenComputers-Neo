package net.minecraft.client.renderer.texture;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.gui.screens.AddRealmPopupScreen;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class TextureManager implements PreparableReloadListener, Tickable, AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceLocation INTENTIONAL_MISSING_TEXTURE = ResourceLocation.withDefaultNamespace("");
    private final Map<ResourceLocation, AbstractTexture> byPath = Maps.newHashMap();
    private final Set<Tickable> tickableTextures = Sets.newHashSet();
    private final Map<String, Integer> prefixRegister = Maps.newHashMap();
    private final ResourceManager resourceManager;

    public TextureManager(ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    public void bindForSetup(ResourceLocation path) {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(() -> this._bind(path));
        } else {
            this._bind(path);
        }
    }

    private void _bind(ResourceLocation path) {
        AbstractTexture abstracttexture = this.byPath.get(path);
        if (abstracttexture == null) {
            abstracttexture = new SimpleTexture(path);
            this.register(path, abstracttexture);
        }

        abstracttexture.bind();
    }

    public void register(ResourceLocation path, AbstractTexture texture) {
        texture = this.loadTexture(path, texture);
        AbstractTexture abstracttexture = this.byPath.put(path, texture);
        if (abstracttexture != texture) {
            if (abstracttexture != null && abstracttexture != MissingTextureAtlasSprite.getTexture()) {
                this.safeClose(path, abstracttexture);
            }

            if (texture instanceof Tickable) {
                this.tickableTextures.add((Tickable)texture);
            }
        }
    }

    private void safeClose(ResourceLocation path, AbstractTexture texture) {
        if (texture != MissingTextureAtlasSprite.getTexture()) {
            this.tickableTextures.remove(texture);

            try {
                texture.close();
            } catch (Exception exception) {
                LOGGER.warn("Failed to close texture {}", path, exception);
            }
        }

        texture.releaseId();
    }

    private AbstractTexture loadTexture(ResourceLocation path, AbstractTexture texture) {
        try {
            texture.load(this.resourceManager);
            return texture;
        } catch (IOException ioexception) {
            if (path != INTENTIONAL_MISSING_TEXTURE) {
                LOGGER.warn("Failed to load texture: {}", path, ioexception);
            }

            return MissingTextureAtlasSprite.getTexture();
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Registering texture");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Resource location being registered");
            crashreportcategory.setDetail("Resource location", path);
            crashreportcategory.setDetail("Texture object class", () -> texture.getClass().getName());
            throw new ReportedException(crashreport);
        }
    }

    public AbstractTexture getTexture(ResourceLocation path) {
        AbstractTexture abstracttexture = this.byPath.get(path);
        if (abstracttexture == null) {
            abstracttexture = new SimpleTexture(path);
            this.register(path, abstracttexture);
        }

        return abstracttexture;
    }

    public AbstractTexture getTexture(ResourceLocation path, AbstractTexture defaultTexture) {
        return this.byPath.getOrDefault(path, defaultTexture);
    }

    public ResourceLocation register(String name, DynamicTexture texture) {
        Integer integer = this.prefixRegister.get(name);
        if (integer == null) {
            integer = 1;
        } else {
            integer = integer + 1;
        }

        this.prefixRegister.put(name, integer);
        ResourceLocation resourcelocation = ResourceLocation.withDefaultNamespace(String.format(Locale.ROOT, "dynamic/%s_%d", name, integer));
        this.register(resourcelocation, texture);
        return resourcelocation;
    }

    public CompletableFuture<Void> preload(ResourceLocation path, Executor backgroundExecutor) {
        if (!this.byPath.containsKey(path)) {
            PreloadedTexture preloadedtexture = new PreloadedTexture(this.resourceManager, path, backgroundExecutor);
            this.byPath.put(path, preloadedtexture);
            return preloadedtexture.getFuture().thenRunAsync(() -> this.register(path, preloadedtexture), TextureManager::execute);
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static void execute(Runnable runnable) {
        Minecraft.getInstance().execute(() -> RenderSystem.recordRenderCall(runnable::run));
    }

    @Override
    public void tick() {
        for (Tickable tickable : this.tickableTextures) {
            tickable.tick();
        }
    }

    public void release(ResourceLocation path) {
        AbstractTexture abstracttexture = this.byPath.remove(path);
        if (abstracttexture != null) {
            this.safeClose(path, abstracttexture);
        }
    }

    @Override
    public void close() {
        this.byPath.forEach(this::safeClose);
        this.byPath.clear();
        this.tickableTextures.clear();
        this.prefixRegister.clear();
    }

    @Override
    public CompletableFuture<Void> reload(
        PreparableReloadListener.PreparationBarrier stage,
        ResourceManager resourceManager,
        ProfilerFiller preparationsProfiler,
        ProfilerFiller reloadProfiler,
        Executor backgroundExecutor,
        Executor gameExecutor
    ) {
        CompletableFuture<Void> completablefuture = new CompletableFuture<>();
        TitleScreen.preloadResources(this, backgroundExecutor).thenCompose(stage::wait).thenAcceptAsync(p_247950_ -> {
            MissingTextureAtlasSprite.getTexture();
            AddRealmPopupScreen.updateCarouselImages(this.resourceManager);
            Iterator<Entry<ResourceLocation, AbstractTexture>> iterator = this.byPath.entrySet().iterator();

            while (iterator.hasNext()) {
                Entry<ResourceLocation, AbstractTexture> entry = iterator.next();
                ResourceLocation resourcelocation = entry.getKey();
                AbstractTexture abstracttexture = entry.getValue();
                if (abstracttexture == MissingTextureAtlasSprite.getTexture() && !resourcelocation.equals(MissingTextureAtlasSprite.getLocation())) {
                    iterator.remove();
                } else {
                    abstracttexture.reset(this, resourceManager, resourcelocation, gameExecutor);
                }
            }

            Minecraft.getInstance().tell(() -> completablefuture.complete(null));
        }, p_118505_ -> RenderSystem.recordRenderCall(p_118505_::run));
        return completablefuture;
    }

    public void dumpAllSheets(Path path) {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(() -> this._dumpAllSheets(path));
        } else {
            this._dumpAllSheets(path);
        }
    }

    private void _dumpAllSheets(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException ioexception) {
            LOGGER.error("Failed to create directory {}", path, ioexception);
            return;
        }

        this.byPath.forEach((p_276101_, p_276102_) -> {
            if (p_276102_ instanceof Dumpable dumpable) {
                try {
                    dumpable.dumpContents(p_276101_, path);
                } catch (IOException ioexception1) {
                    LOGGER.error("Failed to dump texture {}", p_276101_, ioexception1);
                }
            }
        });
    }
}
