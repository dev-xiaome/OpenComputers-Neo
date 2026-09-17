package net.minecraft.client.gui.font;

import com.mojang.blaze3d.font.SheetGlyphInfo;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import java.nio.file.Path;
import javax.annotation.Nullable;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.Dumpable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class FontTexture extends AbstractTexture implements Dumpable {
    private static final int SIZE = 256;
    private final GlyphRenderTypes renderTypes;
    private final boolean colored;
    private final FontTexture.Node root;

    public FontTexture(GlyphRenderTypes renderTypes, boolean colored) {
        this.colored = colored;
        this.root = new FontTexture.Node(0, 0, 256, 256);
        TextureUtil.prepareImage(colored ? NativeImage.InternalGlFormat.RGBA : NativeImage.InternalGlFormat.RED, this.getId(), 256, 256);
        this.renderTypes = renderTypes;
    }

    @Override
    public void load(ResourceManager manager) {
    }

    @Override
    public void close() {
        this.releaseId();
    }

    @Nullable
    public BakedGlyph add(SheetGlyphInfo glyphInfo) {
        if (glyphInfo.isColored() != this.colored) {
            return null;
        } else {
            FontTexture.Node fonttexture$node = this.root.insert(glyphInfo);
            if (fonttexture$node != null) {
                this.bind();
                glyphInfo.upload(fonttexture$node.x, fonttexture$node.y);
                float f = 256.0F;
                float f1 = 256.0F;
                float f2 = 0.01F;
                return new BakedGlyph(
                    this.renderTypes,
                    ((float)fonttexture$node.x + 0.01F) / 256.0F,
                    ((float)fonttexture$node.x - 0.01F + (float)glyphInfo.getPixelWidth()) / 256.0F,
                    ((float)fonttexture$node.y + 0.01F) / 256.0F,
                    ((float)fonttexture$node.y - 0.01F + (float)glyphInfo.getPixelHeight()) / 256.0F,
                    glyphInfo.getLeft(),
                    glyphInfo.getRight(),
                    glyphInfo.getTop(),
                    glyphInfo.getBottom()
                );
            } else {
                return null;
            }
        }
    }

    @Override
    public void dumpContents(ResourceLocation resourceLocation, Path path) {
        String s = resourceLocation.toDebugFileName();
        TextureUtil.writeAsPNG(path, s, this.getId(), 0, 256, 256, p_285145_ -> (p_285145_ & 0xFF000000) == 0 ? -16777216 : p_285145_);
    }

    @OnlyIn(Dist.CLIENT)
    static class Node {
        final int x;
        final int y;
        private final int width;
        private final int height;
        @Nullable
        private FontTexture.Node left;
        @Nullable
        private FontTexture.Node right;
        private boolean occupied;

        Node(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        @Nullable
        FontTexture.Node insert(SheetGlyphInfo glyphInfo) {
            if (this.left != null && this.right != null) {
                FontTexture.Node fonttexture$node = this.left.insert(glyphInfo);
                if (fonttexture$node == null) {
                    fonttexture$node = this.right.insert(glyphInfo);
                }

                return fonttexture$node;
            } else if (this.occupied) {
                return null;
            } else {
                int i = glyphInfo.getPixelWidth();
                int j = glyphInfo.getPixelHeight();
                if (i > this.width || j > this.height) {
                    return null;
                } else if (i == this.width && j == this.height) {
                    this.occupied = true;
                    return this;
                } else {
                    int k = this.width - i;
                    int l = this.height - j;
                    if (k > l) {
                        this.left = new FontTexture.Node(this.x, this.y, i, this.height);
                        this.right = new FontTexture.Node(this.x + i + 1, this.y, this.width - i - 1, this.height);
                    } else {
                        this.left = new FontTexture.Node(this.x, this.y, this.width, j);
                        this.right = new FontTexture.Node(this.x, this.y + j + 1, this.width, this.height - j - 1);
                    }

                    return this.left.insert(glyphInfo);
                }
            }
        }
    }
}
