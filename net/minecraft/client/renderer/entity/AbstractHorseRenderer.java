package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HorseModel;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class AbstractHorseRenderer<T extends AbstractHorse, M extends HorseModel<T>> extends MobRenderer<T, M> {
    private final float scale;

    public AbstractHorseRenderer(EntityRendererProvider.Context context, M model, float scale) {
        super(context, model, 0.75F);
        this.scale = scale;
    }

    protected void scale(T livingEntity, PoseStack poseStack, float partialTickTime) {
        poseStack.scale(this.scale, this.scale, this.scale);
        super.scale(livingEntity, poseStack, partialTickTime);
    }
}
