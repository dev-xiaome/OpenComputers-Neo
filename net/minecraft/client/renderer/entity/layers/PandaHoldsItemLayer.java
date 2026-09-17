package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PandaModel;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Panda;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class PandaHoldsItemLayer extends RenderLayer<Panda, PandaModel<Panda>> {
    private final ItemInHandRenderer itemInHandRenderer;

    public PandaHoldsItemLayer(RenderLayerParent<Panda, PandaModel<Panda>> renderer, ItemInHandRenderer itemInHandRenderer) {
        super(renderer);
        this.itemInHandRenderer = itemInHandRenderer;
    }

    public void render(
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        Panda livingEntity,
        float limbSwing,
        float limbSwingAmount,
        float partialTicks,
        float ageInTicks,
        float netHeadYaw,
        float headPitch
    ) {
        ItemStack itemstack = livingEntity.getItemBySlot(EquipmentSlot.MAINHAND);
        if (livingEntity.isSitting() && !livingEntity.isScared()) {
            float f = -0.6F;
            float f1 = 1.4F;
            if (livingEntity.isEating()) {
                f -= 0.2F * Mth.sin(ageInTicks * 0.6F) + 0.2F;
                f1 -= 0.09F * Mth.sin(ageInTicks * 0.6F);
            }

            poseStack.pushPose();
            poseStack.translate(0.1F, f1, f);
            this.itemInHandRenderer.renderItem(livingEntity, itemstack, ItemDisplayContext.GROUND, false, poseStack, buffer, packedLight);
            poseStack.popPose();
        }
    }
}
