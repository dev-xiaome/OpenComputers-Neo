package net.minecraft.client.gui.screens;

import com.google.common.hash.Hashing;
import com.mojang.blaze3d.platform.NativeImage;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class FaviconTexture implements AutoCloseable {
    private static final ResourceLocation MISSING_LOCATION = ResourceLocation.withDefaultNamespace("textures/misc/unknown_server.png");
    private static final int WIDTH = 64;
    private static final int HEIGHT = 64;
    private final TextureManager textureManager;
    private final ResourceLocation textureLocation;
    @Nullable
    private DynamicTexture texture;
    private boolean closed;

    private FaviconTexture(TextureManager textureManager, ResourceLocation textureLocation) {
        this.textureManager = textureManager;
        this.textureLocation = textureLocation;
    }

    public static FaviconTexture forWorld(TextureManager textureManager, String worldName) {
        return new FaviconTexture(
            textureManager,
            ResourceLocation.withDefaultNamespace(
                "worlds/" + Util.sanitizeName(worldName, ResourceLocation::validPathChar) + "/" + Hashing.sha1().hashUnencodedChars(worldName) + "/icon"
            )
        );
    }

    public static FaviconTexture forServer(TextureManager textureManager, String worldName) {
        return new FaviconTexture(textureManager, ResourceLocation.withDefaultNamespace("servers/" + Hashing.sha1().hashUnencodedChars(worldName) + "/icon"));
    }

    public void upload(NativeImage image) {
        if (image.getWidth() == 64 && image.getHeight() == 64) {
            try {
                this.checkOpen();
                if (this.texture == null) {
                    this.texture = new DynamicTexture(image);
                } else {
                    this.texture.setPixels(image);
                    this.texture.upload();
                }

                this.textureManager.register(this.textureLocation, this.texture);
            } catch (Throwable throwable) {
                image.close();
                this.clear();
                throw throwable;
            }
        } else {
            image.close();
            throw new IllegalArgumentException("Icon must be 64x64, but was " + image.getWidth() + "x" + image.getHeight());
        }
    }

    public void clear() {
        this.checkOpen();
        if (this.texture != null) {
            this.textureManager.release(this.textureLocation);
            this.texture.close();
            this.texture = null;
        }
    }

    public ResourceLocation textureLocation() {
        return this.texture != null ? this.textureLocation : MISSING_LOCATION;
    }

    @Override
    public void close() {
        this.clear();
        this.closed = true;
    }

    private void checkOpen() {
        if (this.closed) {
            throw new IllegalStateException("Icon already closed");
        }
    }
}
