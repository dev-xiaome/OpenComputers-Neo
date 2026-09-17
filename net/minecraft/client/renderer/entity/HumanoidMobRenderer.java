package net.minecraft.client.renderer.entity;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.Mob;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class HumanoidMobRenderer<T extends Mob, M extends HumanoidModel<T>> extends MobRenderer<T, M> {
    public HumanoidMobRenderer(EntityRendererProvider.Context context, M model, float shadowRadius) {
        this(context, model, shadowRadius, 1.0F, 1.0F, 1.0F);
    }

    public HumanoidMobRenderer(EntityRendererProvider.Context context, M model, float shadowRadius, float scaleX, float scaleY, float scaleZ) {
        super(context, model, shadowRadius);
        this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), scaleX, scaleY, scaleZ, context.getItemInHandRenderer()));
        this.addLayer(new ElytraLayer<>(this, context.getModelSet()));
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }
}
