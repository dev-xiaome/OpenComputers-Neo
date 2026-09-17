package net.minecraft.client.renderer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SpriteCoordinateExpander implements VertexConsumer {
    private final VertexConsumer delegate;
    private final TextureAtlasSprite sprite;

    public SpriteCoordinateExpander(VertexConsumer delegate, TextureAtlasSprite sprite) {
        this.delegate = delegate;
        this.sprite = sprite;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        this.delegate.addVertex(x, y, z);
        return this; //Neo: Fix MC-263524 not working with chained methods
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        this.delegate.setColor(red, green, blue, alpha);
        return this; //Neo: Fix MC-263524 not working with chained methods
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        this.delegate.setUv(this.sprite.getU(u), this.sprite.getV(v));
        return this; //Neo: Fix MC-263524 not working with chained methods
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        this.delegate.setUv1(u, v);
        return this; //Neo: Fix MC-263524 not working with chained methods
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        this.delegate.setUv2(u, v);
        return this; //Neo: Fix MC-263524 not working with chained methods
    }

    @Override
    public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
        this.delegate.setNormal(normalX, normalY, normalZ);
        return this; //Neo: Fix MC-263524 not working with chained methods
    }

    @Override
    public void addVertex(
        float x,
        float y,
        float z,
        int color,
        float u,
        float v,
        int packedOverlay,
        int packedLight,
        float normalX,
        float normalY,
        float normalZ
    ) {
        this.delegate
            .addVertex(
                x,
                y,
                z,
                color,
                this.sprite.getU(u),
                this.sprite.getV(v),
                packedOverlay,
                packedLight,
                normalX,
                normalY,
                normalZ
            );
    }
}
