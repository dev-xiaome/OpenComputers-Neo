package net.minecraft.client.gui.font.glyphs;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public class BakedGlyph {
    private final GlyphRenderTypes renderTypes;
    private final float u0;
    private final float u1;
    private final float v0;
    private final float v1;
    private final float left;
    private final float right;
    private final float up;
    private final float down;

    public BakedGlyph(
        GlyphRenderTypes renderTypes,
        float u0,
        float u1,
        float v0,
        float v1,
        float left,
        float right,
        float up,
        float down
    ) {
        this.renderTypes = renderTypes;
        this.u0 = u0;
        this.u1 = u1;
        this.v0 = v0;
        this.v1 = v1;
        this.left = left;
        this.right = right;
        this.up = up;
        this.down = down;
    }

    public void render(
        boolean italic,
        float x,
        float y,
        Matrix4f matrix,
        VertexConsumer buffer,
        float red,
        float green,
        float blue,
        float alpha,
        int packedLight
    ) {
        float f = x + this.left;
        float f1 = x + this.right;
        float f2 = y + this.up;
        float f3 = y + this.down;
        float f4 = italic ? 1.0F - 0.25F * this.up : 0.0F;
        float f5 = italic ? 1.0F - 0.25F * this.down : 0.0F;
        buffer.addVertex(matrix, f + f4, f2, 0.0F).setColor(red, green, blue, alpha).setUv(this.u0, this.v0).setLight(packedLight);
        buffer.addVertex(matrix, f + f5, f3, 0.0F).setColor(red, green, blue, alpha).setUv(this.u0, this.v1).setLight(packedLight);
        buffer.addVertex(matrix, f1 + f5, f3, 0.0F).setColor(red, green, blue, alpha).setUv(this.u1, this.v1).setLight(packedLight);
        buffer.addVertex(matrix, f1 + f4, f2, 0.0F).setColor(red, green, blue, alpha).setUv(this.u1, this.v0).setLight(packedLight);
    }

    public void renderEffect(BakedGlyph.Effect effect, Matrix4f matrix, VertexConsumer buffer, int packedLight) {
        buffer.addVertex(matrix, effect.x0, effect.y0, effect.depth)
            .setColor(effect.r, effect.g, effect.b, effect.a)
            .setUv(this.u0, this.v0)
            .setLight(packedLight);
        buffer.addVertex(matrix, effect.x1, effect.y0, effect.depth)
            .setColor(effect.r, effect.g, effect.b, effect.a)
            .setUv(this.u0, this.v1)
            .setLight(packedLight);
        buffer.addVertex(matrix, effect.x1, effect.y1, effect.depth)
            .setColor(effect.r, effect.g, effect.b, effect.a)
            .setUv(this.u1, this.v1)
            .setLight(packedLight);
        buffer.addVertex(matrix, effect.x0, effect.y1, effect.depth)
            .setColor(effect.r, effect.g, effect.b, effect.a)
            .setUv(this.u1, this.v0)
            .setLight(packedLight);
    }

    public RenderType renderType(Font.DisplayMode displayMode) {
        return this.renderTypes.select(displayMode);
    }

    @OnlyIn(Dist.CLIENT)
    public static class Effect {
        protected final float x0;
        protected final float y0;
        protected final float x1;
        protected final float y1;
        protected final float depth;
        protected final float r;
        protected final float g;
        protected final float b;
        protected final float a;

        public Effect(
            float x0, float y0, float x1, float y1, float depth, float r, float g, float b, float a
        ) {
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
            this.depth = depth;
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
        }
    }
}
