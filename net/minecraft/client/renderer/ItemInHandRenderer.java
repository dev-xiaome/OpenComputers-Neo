package net.minecraft.client.renderer;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public class ItemInHandRenderer {
    private static final RenderType MAP_BACKGROUND = RenderType.text(ResourceLocation.withDefaultNamespace("textures/map/map_background.png"));
    private static final RenderType MAP_BACKGROUND_CHECKERBOARD = RenderType.text(
        ResourceLocation.withDefaultNamespace("textures/map/map_background_checkerboard.png")
    );
    private static final float ITEM_SWING_X_POS_SCALE = -0.4F;
    private static final float ITEM_SWING_Y_POS_SCALE = 0.2F;
    private static final float ITEM_SWING_Z_POS_SCALE = -0.2F;
    private static final float ITEM_HEIGHT_SCALE = -0.6F;
    private static final float ITEM_POS_X = 0.56F;
    private static final float ITEM_POS_Y = -0.52F;
    private static final float ITEM_POS_Z = -0.72F;
    private static final float ITEM_PRESWING_ROT_Y = 45.0F;
    private static final float ITEM_SWING_X_ROT_AMOUNT = -80.0F;
    private static final float ITEM_SWING_Y_ROT_AMOUNT = -20.0F;
    private static final float ITEM_SWING_Z_ROT_AMOUNT = -20.0F;
    private static final float EAT_JIGGLE_X_ROT_AMOUNT = 10.0F;
    private static final float EAT_JIGGLE_Y_ROT_AMOUNT = 90.0F;
    private static final float EAT_JIGGLE_Z_ROT_AMOUNT = 30.0F;
    private static final float EAT_JIGGLE_X_POS_SCALE = 0.6F;
    private static final float EAT_JIGGLE_Y_POS_SCALE = -0.5F;
    private static final float EAT_JIGGLE_Z_POS_SCALE = 0.0F;
    private static final double EAT_JIGGLE_EXPONENT = 27.0;
    private static final float EAT_EXTRA_JIGGLE_CUTOFF = 0.8F;
    private static final float EAT_EXTRA_JIGGLE_SCALE = 0.1F;
    private static final float ARM_SWING_X_POS_SCALE = -0.3F;
    private static final float ARM_SWING_Y_POS_SCALE = 0.4F;
    private static final float ARM_SWING_Z_POS_SCALE = -0.4F;
    private static final float ARM_SWING_Y_ROT_AMOUNT = 70.0F;
    private static final float ARM_SWING_Z_ROT_AMOUNT = -20.0F;
    private static final float ARM_HEIGHT_SCALE = -0.6F;
    private static final float ARM_POS_SCALE = 0.8F;
    private static final float ARM_POS_X = 0.8F;
    private static final float ARM_POS_Y = -0.75F;
    private static final float ARM_POS_Z = -0.9F;
    private static final float ARM_PRESWING_ROT_Y = 45.0F;
    private static final float ARM_PREROTATION_X_OFFSET = -1.0F;
    private static final float ARM_PREROTATION_Y_OFFSET = 3.6F;
    private static final float ARM_PREROTATION_Z_OFFSET = 3.5F;
    private static final float ARM_POSTROTATION_X_OFFSET = 5.6F;
    private static final int ARM_ROT_X = 200;
    private static final int ARM_ROT_Y = -135;
    private static final int ARM_ROT_Z = 120;
    private static final float MAP_SWING_X_POS_SCALE = -0.4F;
    private static final float MAP_SWING_Z_POS_SCALE = -0.2F;
    private static final float MAP_HANDS_POS_X = 0.0F;
    private static final float MAP_HANDS_POS_Y = 0.04F;
    private static final float MAP_HANDS_POS_Z = -0.72F;
    private static final float MAP_HANDS_HEIGHT_SCALE = -1.2F;
    private static final float MAP_HANDS_TILT_SCALE = -0.5F;
    private static final float MAP_PLAYER_PITCH_SCALE = 45.0F;
    private static final float MAP_HANDS_Z_ROT_AMOUNT = -85.0F;
    private static final float MAPHAND_X_ROT_AMOUNT = 45.0F;
    private static final float MAPHAND_Y_ROT_AMOUNT = 92.0F;
    private static final float MAPHAND_Z_ROT_AMOUNT = -41.0F;
    private static final float MAP_HAND_X_POS = 0.3F;
    private static final float MAP_HAND_Y_POS = -1.1F;
    private static final float MAP_HAND_Z_POS = 0.45F;
    private static final float MAP_SWING_X_ROT_AMOUNT = 20.0F;
    private static final float MAP_PRE_ROT_SCALE = 0.38F;
    private static final float MAP_GLOBAL_X_POS = -0.5F;
    private static final float MAP_GLOBAL_Y_POS = -0.5F;
    private static final float MAP_GLOBAL_Z_POS = 0.0F;
    private static final float MAP_FINAL_SCALE = 0.0078125F;
    private static final int MAP_BORDER = 7;
    private static final int MAP_HEIGHT = 128;
    private static final int MAP_WIDTH = 128;
    private static final float BOW_CHARGE_X_POS_SCALE = 0.0F;
    private static final float BOW_CHARGE_Y_POS_SCALE = 0.0F;
    private static final float BOW_CHARGE_Z_POS_SCALE = 0.04F;
    private static final float BOW_CHARGE_SHAKE_X_SCALE = 0.0F;
    private static final float BOW_CHARGE_SHAKE_Y_SCALE = 0.004F;
    private static final float BOW_CHARGE_SHAKE_Z_SCALE = 0.0F;
    private static final float BOW_CHARGE_Z_SCALE = 0.2F;
    private static final float BOW_MIN_SHAKE_CHARGE = 0.1F;
    private final Minecraft minecraft;
    private ItemStack mainHandItem = ItemStack.EMPTY;
    private ItemStack offHandItem = ItemStack.EMPTY;
    private float mainHandHeight;
    private float oMainHandHeight;
    private float offHandHeight;
    private float oOffHandHeight;
    private final EntityRenderDispatcher entityRenderDispatcher;
    private final ItemRenderer itemRenderer;

    public ItemInHandRenderer(Minecraft minecraft, EntityRenderDispatcher entityRenderDispatcher, ItemRenderer itemRenderer) {
        this.minecraft = minecraft;
        this.entityRenderDispatcher = entityRenderDispatcher;
        this.itemRenderer = itemRenderer;
    }

    public void renderItem(
        LivingEntity entity,
        ItemStack itemStack,
        ItemDisplayContext displayContext,
        boolean leftHand,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int seed
    ) {
        if (!itemStack.isEmpty()) {
            this.itemRenderer
                .renderStatic(
                    entity,
                    itemStack,
                    displayContext,
                    leftHand,
                    poseStack,
                    buffer,
                    entity.level(),
                    seed,
                    OverlayTexture.NO_OVERLAY,
                    entity.getId() + displayContext.ordinal()
                );
        }
    }

    /**
     * Return the angle to render the Map
     */
    private float calculateMapTilt(float pitch) {
        float f = 1.0F - pitch / 45.0F + 0.1F;
        f = Mth.clamp(f, 0.0F, 1.0F);
        return -Mth.cos(f * (float) Math.PI) * 0.5F + 0.5F;
    }

    private void renderMapHand(PoseStack poseStack, MultiBufferSource buffer, int packedLight, HumanoidArm side) {
        PlayerRenderer playerrenderer = (PlayerRenderer)this.entityRenderDispatcher.<AbstractClientPlayer>getRenderer(this.minecraft.player);
        poseStack.pushPose();
        float f = side == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        poseStack.mulPose(Axis.YP.rotationDegrees(92.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(45.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(f * -41.0F));
        poseStack.translate(f * 0.3F, -1.1F, 0.45F);
        if (side == HumanoidArm.RIGHT) {
            playerrenderer.renderRightHand(poseStack, buffer, packedLight, this.minecraft.player);
        } else {
            playerrenderer.renderLeftHand(poseStack, buffer, packedLight, this.minecraft.player);
        }

        poseStack.popPose();
    }

    private void renderOneHandedMap(
        PoseStack poseStack, MultiBufferSource buffer, int packedLight, float equippedProgress, HumanoidArm hand, float swingProgress, ItemStack stack
    ) {
        float f = hand == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        poseStack.translate(f * 0.125F, -0.125F, 0.0F);
        if (!this.minecraft.player.isInvisible()) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.ZP.rotationDegrees(f * 10.0F));
            this.renderPlayerArm(poseStack, buffer, packedLight, equippedProgress, swingProgress, hand);
            poseStack.popPose();
        }

        poseStack.pushPose();
        poseStack.translate(f * 0.51F, -0.08F + equippedProgress * -1.2F, -0.75F);
        float f1 = Mth.sqrt(swingProgress);
        float f2 = Mth.sin(f1 * (float) Math.PI);
        float f3 = -0.5F * f2;
        float f4 = 0.4F * Mth.sin(f1 * (float) (Math.PI * 2));
        float f5 = -0.3F * Mth.sin(swingProgress * (float) Math.PI);
        poseStack.translate(f * f3, f4 - 0.3F * f2, f5);
        poseStack.mulPose(Axis.XP.rotationDegrees(f2 * -45.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(f * f2 * -30.0F));
        this.renderMap(poseStack, buffer, packedLight, stack);
        poseStack.popPose();
    }

    private void renderTwoHandedMap(PoseStack poseStack, MultiBufferSource buffer, int packedLight, float pitch, float equippedProgress, float swingProgress) {
        float f = Mth.sqrt(swingProgress);
        float f1 = -0.2F * Mth.sin(swingProgress * (float) Math.PI);
        float f2 = -0.4F * Mth.sin(f * (float) Math.PI);
        poseStack.translate(0.0F, -f1 / 2.0F, f2);
        float f3 = this.calculateMapTilt(pitch);
        poseStack.translate(0.0F, 0.04F + equippedProgress * -1.2F + f3 * -0.5F, -0.72F);
        poseStack.mulPose(Axis.XP.rotationDegrees(f3 * -85.0F));
        if (!this.minecraft.player.isInvisible()) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            this.renderMapHand(poseStack, buffer, packedLight, HumanoidArm.RIGHT);
            this.renderMapHand(poseStack, buffer, packedLight, HumanoidArm.LEFT);
            poseStack.popPose();
        }

        float f4 = Mth.sin(f * (float) Math.PI);
        poseStack.mulPose(Axis.XP.rotationDegrees(f4 * 20.0F));
        poseStack.scale(2.0F, 2.0F, 2.0F);
        this.renderMap(poseStack, buffer, packedLight, this.mainHandItem);
    }

    private void renderMap(PoseStack poseStack, MultiBufferSource buffer, int packedLight, ItemStack stack) {
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        poseStack.scale(0.38F, 0.38F, 0.38F);
        poseStack.translate(-0.5F, -0.5F, 0.0F);
        poseStack.scale(0.0078125F, 0.0078125F, 0.0078125F);
        MapId mapid = stack.get(DataComponents.MAP_ID);
        MapItemSavedData mapitemsaveddata = MapItem.getSavedData(stack, this.minecraft.level);
        VertexConsumer vertexconsumer = buffer.getBuffer(mapitemsaveddata == null ? MAP_BACKGROUND : MAP_BACKGROUND_CHECKERBOARD);
        Matrix4f matrix4f = poseStack.last().pose();
        vertexconsumer.addVertex(matrix4f, -7.0F, 135.0F, 0.0F).setColor(-1).setUv(0.0F, 1.0F).setLight(packedLight);
        vertexconsumer.addVertex(matrix4f, 135.0F, 135.0F, 0.0F).setColor(-1).setUv(1.0F, 1.0F).setLight(packedLight);
        vertexconsumer.addVertex(matrix4f, 135.0F, -7.0F, 0.0F).setColor(-1).setUv(1.0F, 0.0F).setLight(packedLight);
        vertexconsumer.addVertex(matrix4f, -7.0F, -7.0F, 0.0F).setColor(-1).setUv(0.0F, 0.0F).setLight(packedLight);
        if (mapitemsaveddata != null) {
            this.minecraft.gameRenderer.getMapRenderer().render(poseStack, buffer, mapid, mapitemsaveddata, false, packedLight);
        }
    }

    private void renderPlayerArm(PoseStack poseStack, MultiBufferSource buffer, int packedLight, float equippedProgress, float swingProgress, HumanoidArm side) {
        boolean flag = side != HumanoidArm.LEFT;
        float f = flag ? 1.0F : -1.0F;
        float f1 = Mth.sqrt(swingProgress);
        float f2 = -0.3F * Mth.sin(f1 * (float) Math.PI);
        float f3 = 0.4F * Mth.sin(f1 * (float) (Math.PI * 2));
        float f4 = -0.4F * Mth.sin(swingProgress * (float) Math.PI);
        poseStack.translate(f * (f2 + 0.64000005F), f3 + -0.6F + equippedProgress * -0.6F, f4 + -0.71999997F);
        poseStack.mulPose(Axis.YP.rotationDegrees(f * 45.0F));
        float f5 = Mth.sin(swingProgress * swingProgress * (float) Math.PI);
        float f6 = Mth.sin(f1 * (float) Math.PI);
        poseStack.mulPose(Axis.YP.rotationDegrees(f * f6 * 70.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(f * f5 * -20.0F));
        AbstractClientPlayer abstractclientplayer = this.minecraft.player;
        poseStack.translate(f * -1.0F, 3.6F, 3.5F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(f * 120.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(200.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(f * -135.0F));
        poseStack.translate(f * 5.6F, 0.0F, 0.0F);
        PlayerRenderer playerrenderer = (PlayerRenderer)this.entityRenderDispatcher.<AbstractClientPlayer>getRenderer(abstractclientplayer);
        if (flag) {
            playerrenderer.renderRightHand(poseStack, buffer, packedLight, abstractclientplayer);
        } else {
            playerrenderer.renderLeftHand(poseStack, buffer, packedLight, abstractclientplayer);
        }
    }

    private void applyEatTransform(PoseStack poseStack, float partialTick, HumanoidArm arm, ItemStack stack, Player player) {
        float f = (float)player.getUseItemRemainingTicks() - partialTick + 1.0F;
        float f1 = f / (float)stack.getUseDuration(player);
        if (f1 < 0.8F) {
            float f2 = Mth.abs(Mth.cos(f / 4.0F * (float) Math.PI) * 0.1F);
            poseStack.translate(0.0F, f2, 0.0F);
        }

        float f3 = 1.0F - (float)Math.pow((double)f1, 27.0);
        int i = arm == HumanoidArm.RIGHT ? 1 : -1;
        poseStack.translate(f3 * 0.6F * (float)i, f3 * -0.5F, f3 * 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees((float)i * f3 * 90.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(f3 * 10.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees((float)i * f3 * 30.0F));
    }

    private void applyBrushTransform(PoseStack poseStack, float partialTick, HumanoidArm arm, ItemStack stack, Player player, float equippedProgress) {
        this.applyItemArmTransform(poseStack, arm, equippedProgress);
        float f = (float)(player.getUseItemRemainingTicks() % 10);
        float f1 = f - partialTick + 1.0F;
        float f2 = 1.0F - f1 / 10.0F;
        float f3 = -90.0F;
        float f4 = 60.0F;
        float f5 = 150.0F;
        float f6 = -15.0F;
        int i = 2;
        float f7 = -15.0F + 75.0F * Mth.cos(f2 * 2.0F * (float) Math.PI);
        if (arm != HumanoidArm.RIGHT) {
            poseStack.translate(0.1, 0.83, 0.35);
            poseStack.mulPose(Axis.XP.rotationDegrees(-80.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(f7));
            poseStack.translate(-0.3, 0.22, 0.35);
        } else {
            poseStack.translate(-0.25, 0.22, 0.35);
            poseStack.mulPose(Axis.XP.rotationDegrees(-80.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(0.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(f7));
        }
    }

    private void applyItemArmAttackTransform(PoseStack poseStack, HumanoidArm hand, float swingProgress) {
        int i = hand == HumanoidArm.RIGHT ? 1 : -1;
        float f = Mth.sin(swingProgress * swingProgress * (float) Math.PI);
        poseStack.mulPose(Axis.YP.rotationDegrees((float)i * (45.0F + f * -20.0F)));
        float f1 = Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);
        poseStack.mulPose(Axis.ZP.rotationDegrees((float)i * f1 * -20.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(f1 * -80.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees((float)i * -45.0F));
    }

    private void applyItemArmTransform(PoseStack poseStack, HumanoidArm hand, float equippedProg) {
        int i = hand == HumanoidArm.RIGHT ? 1 : -1;
        poseStack.translate((float)i * 0.56F, -0.52F + equippedProg * -0.6F, -0.72F);
    }

    public void renderHandsWithItems(float partialTicks, PoseStack poseStack, MultiBufferSource.BufferSource buffer, LocalPlayer playerEntity, int combinedLight) {
        float f = playerEntity.getAttackAnim(partialTicks);
        InteractionHand interactionhand = MoreObjects.firstNonNull(playerEntity.swingingArm, InteractionHand.MAIN_HAND);
        float f1 = Mth.lerp(partialTicks, playerEntity.xRotO, playerEntity.getXRot());
        ItemInHandRenderer.HandRenderSelection iteminhandrenderer$handrenderselection = evaluateWhichHandsToRender(playerEntity);
        float f2 = Mth.lerp(partialTicks, playerEntity.xBobO, playerEntity.xBob);
        float f3 = Mth.lerp(partialTicks, playerEntity.yBobO, playerEntity.yBob);
        poseStack.mulPose(Axis.XP.rotationDegrees((playerEntity.getViewXRot(partialTicks) - f2) * 0.1F));
        poseStack.mulPose(Axis.YP.rotationDegrees((playerEntity.getViewYRot(partialTicks) - f3) * 0.1F));
        if (iteminhandrenderer$handrenderselection.renderMainHand) {
            float f4 = interactionhand == InteractionHand.MAIN_HAND ? f : 0.0F;
            float f5 = 1.0F - Mth.lerp(partialTicks, this.oMainHandHeight, this.mainHandHeight);
            if(!net.neoforged.neoforge.client.ClientHooks.renderSpecificFirstPersonHand(InteractionHand.MAIN_HAND, poseStack, buffer, combinedLight, partialTicks, f1, f4, f5, this.mainHandItem))
            this.renderArmWithItem(playerEntity, partialTicks, f1, InteractionHand.MAIN_HAND, f4, this.mainHandItem, f5, poseStack, buffer, combinedLight);
        }

        if (iteminhandrenderer$handrenderselection.renderOffHand) {
            float f6 = interactionhand == InteractionHand.OFF_HAND ? f : 0.0F;
            float f7 = 1.0F - Mth.lerp(partialTicks, this.oOffHandHeight, this.offHandHeight);
            if(!net.neoforged.neoforge.client.ClientHooks.renderSpecificFirstPersonHand(InteractionHand.OFF_HAND, poseStack, buffer, combinedLight, partialTicks, f1, f6, f7, this.offHandItem))
            this.renderArmWithItem(playerEntity, partialTicks, f1, InteractionHand.OFF_HAND, f6, this.offHandItem, f7, poseStack, buffer, combinedLight);
        }

        buffer.endBatch();
    }

    @VisibleForTesting
    static ItemInHandRenderer.HandRenderSelection evaluateWhichHandsToRender(LocalPlayer player) {
        ItemStack itemstack = player.getMainHandItem();
        ItemStack itemstack1 = player.getOffhandItem();
        boolean flag = itemstack.is(Items.BOW) || itemstack1.is(Items.BOW);
        boolean flag1 = itemstack.is(Items.CROSSBOW) || itemstack1.is(Items.CROSSBOW);
        if (!flag && !flag1) {
            return ItemInHandRenderer.HandRenderSelection.RENDER_BOTH_HANDS;
        } else if (player.isUsingItem()) {
            return selectionUsingItemWhileHoldingBowLike(player);
        } else {
            return isChargedCrossbow(itemstack)
                ? ItemInHandRenderer.HandRenderSelection.RENDER_MAIN_HAND_ONLY
                : ItemInHandRenderer.HandRenderSelection.RENDER_BOTH_HANDS;
        }
    }

    private static ItemInHandRenderer.HandRenderSelection selectionUsingItemWhileHoldingBowLike(LocalPlayer player) {
        ItemStack itemstack = player.getUseItem();
        InteractionHand interactionhand = player.getUsedItemHand();
        if (!itemstack.is(Items.BOW) && !itemstack.is(Items.CROSSBOW)) {
            return interactionhand == InteractionHand.MAIN_HAND && isChargedCrossbow(player.getOffhandItem())
                ? ItemInHandRenderer.HandRenderSelection.RENDER_MAIN_HAND_ONLY
                : ItemInHandRenderer.HandRenderSelection.RENDER_BOTH_HANDS;
        } else {
            return ItemInHandRenderer.HandRenderSelection.onlyForHand(interactionhand);
        }
    }

    private static boolean isChargedCrossbow(ItemStack stack) {
        return stack.is(Items.CROSSBOW) && CrossbowItem.isCharged(stack);
    }

    private void renderArmWithItem(
        AbstractClientPlayer player,
        float partialTicks,
        float pitch,
        InteractionHand hand,
        float swingProgress,
        ItemStack stack,
        float equippedProgress,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int combinedLight
    ) {
        if (!player.isScoping()) {
            boolean flag = hand == InteractionHand.MAIN_HAND;
            HumanoidArm humanoidarm = flag ? player.getMainArm() : player.getMainArm().getOpposite();
            poseStack.pushPose();
            if (stack.isEmpty()) {
                if (flag && !player.isInvisible()) {
                    this.renderPlayerArm(poseStack, buffer, combinedLight, equippedProgress, swingProgress, humanoidarm);
                }
            } else if (stack.getItem() instanceof MapItem) {
                if (flag && this.offHandItem.isEmpty()) {
                    this.renderTwoHandedMap(poseStack, buffer, combinedLight, pitch, equippedProgress, swingProgress);
                } else {
                    this.renderOneHandedMap(poseStack, buffer, combinedLight, equippedProgress, humanoidarm, swingProgress, stack);
                }
            } else if (stack.getItem() instanceof CrossbowItem) {
                boolean flag1 = CrossbowItem.isCharged(stack);
                boolean flag2 = humanoidarm == HumanoidArm.RIGHT;
                int i = flag2 ? 1 : -1;
                if (player.isUsingItem() && player.getUseItemRemainingTicks() > 0 && player.getUsedItemHand() == hand) {
                    this.applyItemArmTransform(poseStack, humanoidarm, equippedProgress);
                    poseStack.translate((float)i * -0.4785682F, -0.094387F, 0.05731531F);
                    poseStack.mulPose(Axis.XP.rotationDegrees(-11.935F));
                    poseStack.mulPose(Axis.YP.rotationDegrees((float)i * 65.3F));
                    poseStack.mulPose(Axis.ZP.rotationDegrees((float)i * -9.785F));
                    float f9 = (float)stack.getUseDuration(player) - ((float)player.getUseItemRemainingTicks() - partialTicks + 1.0F);
                    float f13 = f9 / (float)CrossbowItem.getChargeDuration(stack, player);
                    if (f13 > 1.0F) {
                        f13 = 1.0F;
                    }

                    if (f13 > 0.1F) {
                        float f16 = Mth.sin((f9 - 0.1F) * 1.3F);
                        float f3 = f13 - 0.1F;
                        float f4 = f16 * f3;
                        poseStack.translate(f4 * 0.0F, f4 * 0.004F, f4 * 0.0F);
                    }

                    poseStack.translate(f13 * 0.0F, f13 * 0.0F, f13 * 0.04F);
                    poseStack.scale(1.0F, 1.0F, 1.0F + f13 * 0.2F);
                    poseStack.mulPose(Axis.YN.rotationDegrees((float)i * 45.0F));
                } else {
                    float f = -0.4F * Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);
                    float f1 = 0.2F * Mth.sin(Mth.sqrt(swingProgress) * (float) (Math.PI * 2));
                    float f2 = -0.2F * Mth.sin(swingProgress * (float) Math.PI);
                    poseStack.translate((float)i * f, f1, f2);
                    this.applyItemArmTransform(poseStack, humanoidarm, equippedProgress);
                    this.applyItemArmAttackTransform(poseStack, humanoidarm, swingProgress);
                    if (flag1 && swingProgress < 0.001F && flag) {
                        poseStack.translate((float)i * -0.641864F, 0.0F, 0.0F);
                        poseStack.mulPose(Axis.YP.rotationDegrees((float)i * 10.0F));
                    }
                }

                this.renderItem(
                    player,
                    stack,
                    flag2 ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                    !flag2,
                    poseStack,
                    buffer,
                    combinedLight
                );
            } else {
                boolean flag3 = humanoidarm == HumanoidArm.RIGHT;
                if (!net.neoforged.neoforge.client.extensions.common.IClientItemExtensions.of(stack).applyForgeHandTransform(poseStack, minecraft.player, humanoidarm, stack, partialTicks, equippedProgress, swingProgress)) // FORGE: Allow items to define custom arm animation
                if (player.isUsingItem() && player.getUseItemRemainingTicks() > 0 && player.getUsedItemHand() == hand) {
                    int k = flag3 ? 1 : -1;
                    switch (stack.getUseAnimation()) {
                        case NONE:
                            this.applyItemArmTransform(poseStack, humanoidarm, equippedProgress);
                            break;
                        case EAT:
                        case DRINK:
                            this.applyEatTransform(poseStack, partialTicks, humanoidarm, stack, player);
                            this.applyItemArmTransform(poseStack, humanoidarm, equippedProgress);
                            break;
                        case BLOCK:
                            this.applyItemArmTransform(poseStack, humanoidarm, equippedProgress);
                            break;
                        case BOW:
                            this.applyItemArmTransform(poseStack, humanoidarm, equippedProgress);
                            poseStack.translate((float)k * -0.2785682F, 0.18344387F, 0.15731531F);
                            poseStack.mulPose(Axis.XP.rotationDegrees(-13.935F));
                            poseStack.mulPose(Axis.YP.rotationDegrees((float)k * 35.3F));
                            poseStack.mulPose(Axis.ZP.rotationDegrees((float)k * -9.785F));
                            float f8 = (float)stack.getUseDuration(player) - ((float)player.getUseItemRemainingTicks() - partialTicks + 1.0F);
                            float f12 = f8 / 20.0F;
                            f12 = (f12 * f12 + f12 * 2.0F) / 3.0F;
                            if (f12 > 1.0F) {
                                f12 = 1.0F;
                            }

                            if (f12 > 0.1F) {
                                float f15 = Mth.sin((f8 - 0.1F) * 1.3F);
                                float f18 = f12 - 0.1F;
                                float f20 = f15 * f18;
                                poseStack.translate(f20 * 0.0F, f20 * 0.004F, f20 * 0.0F);
                            }

                            poseStack.translate(f12 * 0.0F, f12 * 0.0F, f12 * 0.04F);
                            poseStack.scale(1.0F, 1.0F, 1.0F + f12 * 0.2F);
                            poseStack.mulPose(Axis.YN.rotationDegrees((float)k * 45.0F));
                            break;
                        case SPEAR:
                            this.applyItemArmTransform(poseStack, humanoidarm, equippedProgress);
                            poseStack.translate((float)k * -0.5F, 0.7F, 0.1F);
                            poseStack.mulPose(Axis.XP.rotationDegrees(-55.0F));
                            poseStack.mulPose(Axis.YP.rotationDegrees((float)k * 35.3F));
                            poseStack.mulPose(Axis.ZP.rotationDegrees((float)k * -9.785F));
                            float f7 = (float)stack.getUseDuration(player) - ((float)player.getUseItemRemainingTicks() - partialTicks + 1.0F);
                            float f11 = f7 / 10.0F;
                            if (f11 > 1.0F) {
                                f11 = 1.0F;
                            }

                            if (f11 > 0.1F) {
                                float f14 = Mth.sin((f7 - 0.1F) * 1.3F);
                                float f17 = f11 - 0.1F;
                                float f19 = f14 * f17;
                                poseStack.translate(f19 * 0.0F, f19 * 0.004F, f19 * 0.0F);
                            }

                            poseStack.translate(0.0F, 0.0F, f11 * 0.2F);
                            poseStack.scale(1.0F, 1.0F, 1.0F + f11 * 0.2F);
                            poseStack.mulPose(Axis.YN.rotationDegrees((float)k * 45.0F));
                            break;
                        case BRUSH:
                            this.applyBrushTransform(poseStack, partialTicks, humanoidarm, stack, player, equippedProgress);
                    }
                } else if (player.isAutoSpinAttack()) {
                    this.applyItemArmTransform(poseStack, humanoidarm, equippedProgress);
                    int j = flag3 ? 1 : -1;
                    poseStack.translate((float)j * -0.4F, 0.8F, 0.3F);
                    poseStack.mulPose(Axis.YP.rotationDegrees((float)j * 65.0F));
                    poseStack.mulPose(Axis.ZP.rotationDegrees((float)j * -85.0F));
                } else {
                    float f5 = -0.4F * Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);
                    float f6 = 0.2F * Mth.sin(Mth.sqrt(swingProgress) * (float) (Math.PI * 2));
                    float f10 = -0.2F * Mth.sin(swingProgress * (float) Math.PI);
                    int l = flag3 ? 1 : -1;
                    poseStack.translate((float)l * f5, f6, f10);
                    this.applyItemArmTransform(poseStack, humanoidarm, equippedProgress);
                    this.applyItemArmAttackTransform(poseStack, humanoidarm, swingProgress);
                }

                this.renderItem(
                    player,
                    stack,
                    flag3 ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                    !flag3,
                    poseStack,
                    buffer,
                    combinedLight
                );
            }

            poseStack.popPose();
        }
    }

    public void tick() {
        this.oMainHandHeight = this.mainHandHeight;
        this.oOffHandHeight = this.offHandHeight;
        LocalPlayer localplayer = this.minecraft.player;
        ItemStack itemstack = localplayer.getMainHandItem();
        ItemStack itemstack1 = localplayer.getOffhandItem();
        if (ItemStack.matches(this.mainHandItem, itemstack)) {
            this.mainHandItem = itemstack;
        }

        if (ItemStack.matches(this.offHandItem, itemstack1)) {
            this.offHandItem = itemstack1;
        }

        if (localplayer.isHandsBusy()) {
            this.mainHandHeight = Mth.clamp(this.mainHandHeight - 0.4F, 0.0F, 1.0F);
            this.offHandHeight = Mth.clamp(this.offHandHeight - 0.4F, 0.0F, 1.0F);
        } else {
            float f = localplayer.getAttackStrengthScale(1.0F);
            boolean requipM = net.neoforged.neoforge.client.ClientHooks.shouldCauseReequipAnimation(this.mainHandItem, itemstack, localplayer.getInventory().selected);
            boolean requipO = net.neoforged.neoforge.client.ClientHooks.shouldCauseReequipAnimation(this.offHandItem, itemstack1, -1);

            if (!requipM && this.mainHandItem != itemstack)
                this.mainHandItem = itemstack;
            if (!requipO && this.offHandItem != itemstack1)
                this.offHandItem = itemstack1;

            this.mainHandHeight += Mth.clamp((!requipM ? f * f * f : 0.0F) - this.mainHandHeight, -0.4F, 0.4F);
            this.offHandHeight += Mth.clamp((float)(!requipO ? 1 : 0) - this.offHandHeight, -0.4F, 0.4F);
        }

        if (this.mainHandHeight < 0.1F) {
            this.mainHandItem = itemstack;
        }

        if (this.offHandHeight < 0.1F) {
            this.offHandItem = itemstack1;
        }
    }

    public void itemUsed(InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND) {
            this.mainHandHeight = 0.0F;
        } else {
            this.offHandHeight = 0.0F;
        }
    }

    @OnlyIn(Dist.CLIENT)
    @VisibleForTesting
    static enum HandRenderSelection {
        RENDER_BOTH_HANDS(true, true),
        RENDER_MAIN_HAND_ONLY(true, false),
        RENDER_OFF_HAND_ONLY(false, true);

        final boolean renderMainHand;
        final boolean renderOffHand;

        private HandRenderSelection(boolean renderMainHand, boolean renderOffHand) {
            this.renderMainHand = renderMainHand;
            this.renderOffHand = renderOffHand;
        }

        public static ItemInHandRenderer.HandRenderSelection onlyForHand(InteractionHand hand) {
            return hand == InteractionHand.MAIN_HAND ? RENDER_MAIN_HAND_ONLY : RENDER_OFF_HAND_ONLY;
        }
    }
}
