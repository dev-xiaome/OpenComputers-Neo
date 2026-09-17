package net.minecraft.client.renderer.texture;

import com.mojang.blaze3d.vertex.VertexConsumer;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.SpriteCoordinateExpander;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class TextureAtlasSprite {
    private final ResourceLocation atlasLocation;
    private final SpriteContents contents;
    final int x;
    final int y;
    private final float u0;
    private final float u1;
    private final float v0;
    private final float v1;

    protected TextureAtlasSprite(ResourceLocation atlasLocation, SpriteContents contents, int originX, int originY, int x, int y) {
        this.atlasLocation = atlasLocation;
        this.contents = contents;
        this.x = x;
        this.y = y;
        this.u0 = (float)x / (float)originX;
        this.u1 = (float)(x + contents.width()) / (float)originX;
        this.v0 = (float)y / (float)originY;
        this.v1 = (float)(y + contents.height()) / (float)originY;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public float getU0() {
        return this.u0;
    }

    public float getU1() {
        return this.u1;
    }

    public SpriteContents contents() {
        return this.contents;
    }

    @Nullable
    public TextureAtlasSprite.Ticker createTicker() {
        final SpriteTicker spriteticker = this.contents.createTicker();
        return spriteticker != null ? new TextureAtlasSprite.Ticker() {
            @Override
            public void tickAndUpload() {
                spriteticker.tickAndUpload(TextureAtlasSprite.this.x, TextureAtlasSprite.this.y);
            }

            @Override
            public void close() {
                spriteticker.close();
            }
        } : null;
    }

    public float getU(float u) {
        float f = this.u1 - this.u0;
        return this.u0 + f * u;
    }

    public float getUOffset(float offset) {
        float f = this.u1 - this.u0;
        return (offset - this.u0) / f;
    }

    public float getV0() {
        return this.v0;
    }

    public float getV1() {
        return this.v1;
    }

    public float getV(float v) {
        float f = this.v1 - this.v0;
        return this.v0 + f * v;
    }

    public float getVOffset(float offset) {
        float f = this.v1 - this.v0;
        return (offset - this.v0) / f;
    }

    public ResourceLocation atlasLocation() {
        return this.atlasLocation;
    }

    @Override
    public String toString() {
        return "TextureAtlasSprite{contents='" + this.contents + "', u0=" + this.u0 + ", u1=" + this.u1 + ", v0=" + this.v0 + ", v1=" + this.v1 + "}";
    }

    public void uploadFirstFrame() {
        this.contents.uploadFirstFrame(this.x, this.y);
    }

    private float atlasSize() {
        float f = (float)this.contents.width() / (this.u1 - this.u0);
        float f1 = (float)this.contents.height() / (this.v1 - this.v0);
        return Math.max(f1, f);
    }

    public float uvShrinkRatio() {
        return 4.0F / this.atlasSize();
    }

    public VertexConsumer wrap(VertexConsumer consumer) {
        return new SpriteCoordinateExpander(consumer, this);
    }

    @OnlyIn(Dist.CLIENT)
    public interface Ticker extends AutoCloseable {
        void tickAndUpload();

        @Override
        void close();
    }

    // Neo: Exposed a pixel RGBA getter
    public int getPixelRGBA(int frameIndex, int x, int y) {
         if (this.contents.animatedTexture != null) {
              x += this.contents.animatedTexture.getFrameX(frameIndex) * this.contents.width;
              y += this.contents.animatedTexture.getFrameY(frameIndex) * this.contents.height;
         }

         return this.contents.getOriginalImage().getPixelRGBA(x, y);
    }
}
