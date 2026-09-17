package net.minecraft.client.gui.font;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record GlyphRenderTypes(RenderType normal, RenderType seeThrough, RenderType polygonOffset) {
    public static GlyphRenderTypes createForIntensityTexture(ResourceLocation id) {
        return new GlyphRenderTypes(
            RenderType.textIntensity(id), RenderType.textIntensitySeeThrough(id), RenderType.textIntensityPolygonOffset(id)
        );
    }

    public static GlyphRenderTypes createForColorTexture(ResourceLocation id) {
        return new GlyphRenderTypes(RenderType.text(id), RenderType.textSeeThrough(id), RenderType.textPolygonOffset(id));
    }

    public RenderType select(Font.DisplayMode displayMode) {
        return switch (displayMode) {
            case NORMAL -> this.normal;
            case SEE_THROUGH -> this.seeThrough;
            case POLYGON_OFFSET -> this.polygonOffset;
        };
    }
}
