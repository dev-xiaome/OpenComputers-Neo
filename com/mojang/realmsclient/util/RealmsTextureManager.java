package com.mojang.realmsclient.util;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class RealmsTextureManager {
    private static final Map<String, RealmsTextureManager.RealmsTexture> TEXTURES = Maps.newHashMap();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation TEMPLATE_ICON_LOCATION = ResourceLocation.withDefaultNamespace("textures/gui/presets/isles.png");

    public static ResourceLocation worldTemplate(String key, @Nullable String image) {
        return image == null ? TEMPLATE_ICON_LOCATION : getTexture(key, image);
    }

    private static ResourceLocation getTexture(String key, String image) {
        RealmsTextureManager.RealmsTexture realmstexturemanager$realmstexture = TEXTURES.get(key);
        if (realmstexturemanager$realmstexture != null && realmstexturemanager$realmstexture.image().equals(image)) {
            return realmstexturemanager$realmstexture.textureId;
        } else {
            NativeImage nativeimage = loadImage(image);
            if (nativeimage == null) {
                ResourceLocation resourcelocation1 = MissingTextureAtlasSprite.getLocation();
                TEXTURES.put(key, new RealmsTextureManager.RealmsTexture(image, resourcelocation1));
                return resourcelocation1;
            } else {
                ResourceLocation resourcelocation = ResourceLocation.fromNamespaceAndPath("realms", "dynamic/" + key);
                Minecraft.getInstance().getTextureManager().register(resourcelocation, new DynamicTexture(nativeimage));
                TEXTURES.put(key, new RealmsTextureManager.RealmsTexture(image, resourcelocation));
                return resourcelocation;
            }
        }
    }

    @Nullable
    private static NativeImage loadImage(String base64Image) {
        byte[] abyte = Base64.getDecoder().decode(base64Image);
        ByteBuffer bytebuffer = MemoryUtil.memAlloc(abyte.length);

        try {
            return NativeImage.read(bytebuffer.put(abyte).flip());
        } catch (IOException ioexception) {
            LOGGER.warn("Failed to load world image: {}", base64Image, ioexception);
        } finally {
            MemoryUtil.memFree(bytebuffer);
        }

        return null;
    }

    @OnlyIn(Dist.CLIENT)
    public static record RealmsTexture(String image, ResourceLocation textureId) {
    }
}
