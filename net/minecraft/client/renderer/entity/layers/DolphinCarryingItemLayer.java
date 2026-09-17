package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.DolphinModel;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.animal.Dolphin;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class DolphinCarryingItemLayer extends RenderLayer<Dolphin, DolphinModel<Dolphin>> {
    private final ItemInHandRenderer itemInHandRenderer;

    public DolphinCarryingItemLayer(RenderLayerParent<Dolphin, DolphinModel<Dolphin>> renderer, ItemInHandRenderer itemInHandRenderer) {
        super(renderer);
        this.itemInHandRenderer = itemInHandRenderer;
    }

    public void render(
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        Dolphin livingEntity,
        float limbSwing,
        float limbSwingAmount,
        float partialTicks,
        float ageInTicks,
        float netHeadYaw,
        float headPitch
    ) {
        boolean flag = livingEntity.getMainArm() == HumanoidArm.RIGHT;
        poseStack.pushPose();
        float f = 1.0F;
        float f1 = -1.0F;
        float f2 = Mth.abs(livingEntity.getXRot()) / 60.0F;
        if (livingEntity.getXRot() < 0.0F) {
            poseStack.translate(0.0F, 1.0F - f2 * 0.5F, -1.0F + f2 * 0.5F);
        } else {
            poseStack.translate(0.0F, 1.0F + f2 * 0.8F, -1.0F + f2 * 0.2F);
        }

        ItemStack itemstack = flag ? livingEntity.getMainHandItem() : livingEntity.getOffhandItem();
        this.itemInHandRenderer.renderItem(livingEntity, itemstack, ItemDisplayContext.GROUND, false, poseStack, buffer, packedLight);
        poseStack.popPose();
    }
}
