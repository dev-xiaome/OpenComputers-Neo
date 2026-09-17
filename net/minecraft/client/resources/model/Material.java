package net.minecraft.client.resources.model;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class Material {
    public static final Comparator<Material> COMPARATOR = Comparator.comparing(Material::atlasLocation).thenComparing(Material::texture);
    private final ResourceLocation atlasLocation;
    private final ResourceLocation texture;
    @Nullable
    private RenderType renderType;

    public Material(ResourceLocation atlasLocation, ResourceLocation texture) {
        this.atlasLocation = atlasLocation;
        this.texture = texture;
    }

    public ResourceLocation atlasLocation() {
        return this.atlasLocation;
    }

    public ResourceLocation texture() {
        return this.texture;
    }

    public TextureAtlasSprite sprite() {
        return Minecraft.getInstance().getTextureAtlas(this.atlasLocation()).apply(this.texture());
    }

    public RenderType renderType(Function<ResourceLocation, RenderType> renderTypeGetter) {
        if (this.renderType == null) {
            this.renderType = renderTypeGetter.apply(this.atlasLocation);
        }

        return this.renderType;
    }

    public VertexConsumer buffer(MultiBufferSource buffer, Function<ResourceLocation, RenderType> renderTypeGetter) {
        return this.sprite().wrap(buffer.getBuffer(this.renderType(renderTypeGetter)));
    }

    public VertexConsumer buffer(MultiBufferSource buffer, Function<ResourceLocation, RenderType> renderTypeGetter, boolean withGlint) {
        return this.sprite().wrap(ItemRenderer.getFoilBufferDirect(buffer, this.renderType(renderTypeGetter), true, withGlint));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (other != null && this.getClass() == other.getClass()) {
            Material material = (Material)other;
            return this.atlasLocation.equals(material.atlasLocation) && this.texture.equals(material.texture);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.atlasLocation, this.texture);
    }

    @Override
    public String toString() {
        return "Material{atlasLocation=" + this.atlasLocation + ", texture=" + this.texture + "}";
    }
}
