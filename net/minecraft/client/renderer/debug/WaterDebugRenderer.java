package net.minecraft.client.renderer.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class WaterDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
    private final Minecraft minecraft;

    public WaterDebugRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, double camX, double camY, double camZ) {
        BlockPos blockpos = this.minecraft.player.blockPosition();
        LevelReader levelreader = this.minecraft.player.level();

        for (BlockPos blockpos1 : BlockPos.betweenClosed(blockpos.offset(-10, -10, -10), blockpos.offset(10, 10, 10))) {
            FluidState fluidstate = levelreader.getFluidState(blockpos1);
            if (fluidstate.is(FluidTags.WATER)) {
                double d0 = (double)((float)blockpos1.getY() + fluidstate.getHeight(levelreader, blockpos1));
                DebugRenderer.renderFilledBox(
                    poseStack,
                    bufferSource,
                    new AABB(
                            (double)((float)blockpos1.getX() + 0.01F),
                            (double)((float)blockpos1.getY() + 0.01F),
                            (double)((float)blockpos1.getZ() + 0.01F),
                            (double)((float)blockpos1.getX() + 0.99F),
                            d0,
                            (double)((float)blockpos1.getZ() + 0.99F)
                        )
                        .move(-camX, -camY, -camZ),
                    0.0F,
                    1.0F,
                    0.0F,
                    0.15F
                );
            }
        }

        for (BlockPos blockpos2 : BlockPos.betweenClosed(blockpos.offset(-10, -10, -10), blockpos.offset(10, 10, 10))) {
            FluidState fluidstate1 = levelreader.getFluidState(blockpos2);
            if (fluidstate1.is(FluidTags.WATER)) {
                DebugRenderer.renderFloatingText(
                    poseStack,
                    bufferSource,
                    String.valueOf(fluidstate1.getAmount()),
                    (double)blockpos2.getX() + 0.5,
                    (double)((float)blockpos2.getY() + fluidstate1.getHeight(levelreader, blockpos2)),
                    (double)blockpos2.getZ() + 0.5,
                    -16777216
                );
            }
        }
    }
}
