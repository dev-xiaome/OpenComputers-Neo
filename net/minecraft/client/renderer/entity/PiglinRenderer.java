package net.minecraft.client.renderer.entity;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.PiglinModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class PiglinRenderer extends HumanoidMobRenderer<Mob, PiglinModel<Mob>> {
    private static final Map<EntityType<?>, ResourceLocation> TEXTURES = ImmutableMap.of(
        EntityType.PIGLIN,
        ResourceLocation.withDefaultNamespace("textures/entity/piglin/piglin.png"),
        EntityType.ZOMBIFIED_PIGLIN,
        ResourceLocation.withDefaultNamespace("textures/entity/piglin/zombified_piglin.png"),
        EntityType.PIGLIN_BRUTE,
        ResourceLocation.withDefaultNamespace("textures/entity/piglin/piglin_brute.png")
    );
    private static final float PIGLIN_CUSTOM_HEAD_SCALE = 1.0019531F;

    public PiglinRenderer(
        EntityRendererProvider.Context context, ModelLayerLocation layer, ModelLayerLocation p_174346_, ModelLayerLocation p_174347_, boolean noRightEar
    ) {
        super(context, createModel(context.getModelSet(), layer, noRightEar), 0.5F, 1.0019531F, 1.0F, 1.0019531F);
        this.addLayer(
            new HumanoidArmorLayer<>(
                this,
                new HumanoidArmorModel(context.bakeLayer(p_174346_)),
                new HumanoidArmorModel(context.bakeLayer(p_174347_)),
                context.getModelManager()
            )
        );
    }

    private static PiglinModel<Mob> createModel(EntityModelSet modelSet, ModelLayerLocation layer, boolean noRightEar) {
        PiglinModel<Mob> piglinmodel = new PiglinModel<>(modelSet.bakeLayer(layer));
        if (noRightEar) {
            piglinmodel.rightEar.visible = false;
        }

        return piglinmodel;
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getTextureLocation(Mob entity) {
        ResourceLocation resourcelocation = TEXTURES.get(entity.getType());
        if (resourcelocation == null) {
            throw new IllegalArgumentException("I don't know what texture to use for " + entity.getType());
        } else {
            return resourcelocation;
        }
    }

    protected boolean isShaking(Mob entity) {
        return super.isShaking(entity) || entity instanceof AbstractPiglin && ((AbstractPiglin)entity).isConverting();
    }
}
