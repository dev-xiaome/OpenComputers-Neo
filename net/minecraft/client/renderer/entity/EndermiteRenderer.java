package net.minecraft.client.renderer.entity;

import net.minecraft.client.model.EndermiteModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Endermite;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class EndermiteRenderer extends MobRenderer<Endermite, EndermiteModel<Endermite>> {
    private static final ResourceLocation ENDERMITE_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/endermite.png");

    public EndermiteRenderer(EntityRendererProvider.Context p_173994_) {
        super(p_173994_, new EndermiteModel<>(p_173994_.bakeLayer(ModelLayers.ENDERMITE)), 0.3F);
    }

    protected float getFlipDegrees(Endermite livingEntity) {
        return 180.0F;
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getTextureLocation(Endermite entity) {
        return ENDERMITE_LOCATION;
    }
}
