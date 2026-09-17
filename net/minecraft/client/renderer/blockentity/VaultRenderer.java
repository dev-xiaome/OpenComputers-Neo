package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultClientData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class VaultRenderer implements BlockEntityRenderer<VaultBlockEntity> {
    private final ItemRenderer itemRenderer;
    private final RandomSource random = RandomSource.create();

    public VaultRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    public void render(VaultBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (VaultBlockEntity.Client.shouldDisplayActiveEffects(blockEntity.getSharedData())) {
            Level level = blockEntity.getLevel();
            if (level != null) {
                ItemStack itemstack = blockEntity.getSharedData().getDisplayItem();
                if (!itemstack.isEmpty()) {
                    this.random.setSeed((long)ItemEntityRenderer.getSeedForItemStack(itemstack));
                    VaultClientData vaultclientdata = blockEntity.getClientData();
                    renderItemInside(
                        partialTick,
                        level,
                        poseStack,
                        bufferSource,
                        packedLight,
                        itemstack,
                        this.itemRenderer,
                        vaultclientdata.previousSpin(),
                        vaultclientdata.currentSpin(),
                        this.random
                    );
                }
            }
        }
    }

    public static void renderItemInside(
        float partialTick,
        Level level,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        ItemStack item,
        ItemRenderer itemRenderer,
        float previousSpin,
        float currentSpin,
        RandomSource random
    ) {
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.4F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.rotLerp(partialTick, previousSpin, currentSpin)));
        ItemEntityRenderer.renderMultipleFromCount(itemRenderer, poseStack, buffer, packedLight, item, random, level);
        poseStack.popPose();
    }
}
