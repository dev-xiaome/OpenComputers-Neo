package net.minecraft.client.renderer.debug;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Collections;
import java.util.List;
import java.util.function.DoubleSupplier;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SupportBlockRenderer implements DebugRenderer.SimpleDebugRenderer {
    private final Minecraft minecraft;
    private double lastUpdateTime = Double.MIN_VALUE;
    private List<Entity> surroundEntities = Collections.emptyList();

    public SupportBlockRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, double camX, double camY, double camZ) {
        double d0 = (double)Util.getNanos();
        if (d0 - this.lastUpdateTime > 1.0E8) {
            this.lastUpdateTime = d0;
            Entity entity = this.minecraft.gameRenderer.getMainCamera().getEntity();
            this.surroundEntities = ImmutableList.copyOf(entity.level().getEntities(entity, entity.getBoundingBox().inflate(16.0)));
        }

        Player player = this.minecraft.player;
        if (player != null && player.mainSupportingBlockPos.isPresent()) {
            this.drawHighlights(poseStack, bufferSource, camX, camY, camZ, player, () -> 0.0, 1.0F, 0.0F, 0.0F);
        }

        for (Entity entity1 : this.surroundEntities) {
            if (entity1 != player) {
                this.drawHighlights(poseStack, bufferSource, camX, camY, camZ, entity1, () -> this.getBias(entity1), 0.0F, 1.0F, 0.0F);
            }
        }
    }

    private void drawHighlights(
        PoseStack poseStack,
        MultiBufferSource buffer,
        double camX,
        double camY,
        double camZ,
        Entity entity,
        DoubleSupplier biasGetter,
        float red,
        float green,
        float blue
    ) {
        entity.mainSupportingBlockPos.ifPresent(p_286428_ -> {
            double d0 = biasGetter.getAsDouble();
            BlockPos blockpos = entity.getOnPos();
            this.highlightPosition(blockpos, poseStack, camX, camY, camZ, buffer, 0.02 + d0, red, green, blue);
            BlockPos blockpos1 = entity.getOnPosLegacy();
            if (!blockpos1.equals(blockpos)) {
                this.highlightPosition(blockpos1, poseStack, camX, camY, camZ, buffer, 0.04 + d0, 0.0F, 1.0F, 1.0F);
            }
        });
    }

    private double getBias(Entity entity) {
        return 0.02 * (double)(String.valueOf((double)entity.getId() + 0.132453657).hashCode() % 1000) / 1000.0;
    }

    private void highlightPosition(
        BlockPos pos,
        PoseStack poseStack,
        double camX,
        double camY,
        double camZ,
        MultiBufferSource buffer,
        double bias,
        float red,
        float green,
        float blue
    ) {
        double d0 = (double)pos.getX() - camX - 2.0 * bias;
        double d1 = (double)pos.getY() - camY - 2.0 * bias;
        double d2 = (double)pos.getZ() - camZ - 2.0 * bias;
        double d3 = d0 + 1.0 + 4.0 * bias;
        double d4 = d1 + 1.0 + 4.0 * bias;
        double d5 = d2 + 1.0 + 4.0 * bias;
        LevelRenderer.renderLineBox(poseStack, buffer.getBuffer(RenderType.lines()), d0, d1, d2, d3, d4, d5, red, green, blue, 0.4F);
        LevelRenderer.renderVoxelShape(
            poseStack,
            buffer.getBuffer(RenderType.lines()),
            this.minecraft
                .level
                .getBlockState(pos)
                .getCollisionShape(this.minecraft.level, pos, CollisionContext.empty())
                .move((double)pos.getX(), (double)pos.getY(), (double)pos.getZ()),
            -camX,
            -camY,
            -camZ,
            red,
            green,
            blue,
            1.0F,
            false
        );
    }
}
