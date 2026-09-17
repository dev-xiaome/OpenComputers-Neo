package net.minecraft.client.renderer.texture;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import javax.annotation.Nullable;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class SimpleTexture extends AbstractTexture {
    static final Logger LOGGER = LogUtils.getLogger();
    protected final ResourceLocation location;

    public SimpleTexture(ResourceLocation location) {
        this.location = location;
    }

    @Override
    public void load(ResourceManager resourceManager) throws IOException {
        SimpleTexture.TextureImage simpletexture$textureimage = this.getTextureImage(resourceManager);
        simpletexture$textureimage.throwIfError();
        TextureMetadataSection texturemetadatasection = simpletexture$textureimage.getTextureMetadata();
        boolean flag;
        boolean flag1;
        if (texturemetadatasection != null) {
            flag = texturemetadatasection.isBlur();
            flag1 = texturemetadatasection.isClamp();
        } else {
            flag = false;
            flag1 = false;
        }

        NativeImage nativeimage = simpletexture$textureimage.getImage();
        if (!RenderSystem.isOnRenderThreadOrInit()) {
            RenderSystem.recordRenderCall(() -> this.doLoad(nativeimage, flag, flag1));
        } else {
            this.doLoad(nativeimage, flag, flag1);
        }
    }

    private void doLoad(NativeImage image, boolean blur, boolean clamp) {
        TextureUtil.prepareImage(this.getId(), 0, image.getWidth(), image.getHeight());
        image.upload(0, 0, 0, 0, 0, image.getWidth(), image.getHeight(), blur, clamp, false, true);
    }

    protected SimpleTexture.TextureImage getTextureImage(ResourceManager resourceManager) {
        return SimpleTexture.TextureImage.load(resourceManager, this.location);
    }

    @OnlyIn(Dist.CLIENT)
    protected static class TextureImage implements Closeable {
        @Nullable
        private final TextureMetadataSection metadata;
        @Nullable
        private final NativeImage image;
        @Nullable
        private final IOException exception;

        public TextureImage(IOException exception) {
            this.exception = exception;
            this.metadata = null;
            this.image = null;
        }

        public TextureImage(@Nullable TextureMetadataSection metadata, NativeImage image) {
            this.exception = null;
            this.metadata = metadata;
            this.image = image;
        }

        public static SimpleTexture.TextureImage load(ResourceManager resourceManager, ResourceLocation location) {
            try {
                Resource resource = resourceManager.getResourceOrThrow(location);

                NativeImage nativeimage;
                try (InputStream inputstream = resource.open()) {
                    nativeimage = NativeImage.read(inputstream);
                }

                TextureMetadataSection texturemetadatasection = null;

                try {
                    texturemetadatasection = resource.metadata().getSection(TextureMetadataSection.SERIALIZER).orElse(null);
                } catch (RuntimeException runtimeexception) {
                    SimpleTexture.LOGGER.warn("Failed reading metadata of: {}", location, runtimeexception);
                }

                return new SimpleTexture.TextureImage(texturemetadatasection, nativeimage);
            } catch (IOException ioexception) {
                return new SimpleTexture.TextureImage(ioexception);
            }
        }

        @Nullable
        public TextureMetadataSection getTextureMetadata() {
            return this.metadata;
        }

        public NativeImage getImage() throws IOException {
            if (this.exception != null) {
                throw this.exception;
            } else {
                return this.image;
            }
        }

        @Override
        public void close() {
            if (this.image != null) {
                this.image.close();
            }
        }

        public void throwIfError() throws IOException {
            if (this.exception != null) {
                throw this.exception;
            }
        }
    }
}
