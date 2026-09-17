package net.minecraft.client.renderer.debug;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class WorldGenAttemptRenderer implements DebugRenderer.SimpleDebugRenderer {
    private final List<BlockPos> toRender = Lists.newArrayList();
    private final List<Float> scales = Lists.newArrayList();
    private final List<Float> alphas = Lists.newArrayList();
    private final List<Float> reds = Lists.newArrayList();
    private final List<Float> greens = Lists.newArrayList();
    private final List<Float> blues = Lists.newArrayList();

    public void addPos(BlockPos pos, float scale, float red, float green, float blue, float alpha) {
        this.toRender.add(pos);
        this.scales.add(scale);
        this.alphas.add(alpha);
        this.reds.add(red);
        this.greens.add(green);
        this.blues.add(blue);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, double camX, double camY, double camZ) {
        VertexConsumer vertexconsumer = bufferSource.getBuffer(RenderType.debugFilledBox());

        for (int i = 0; i < this.toRender.size(); i++) {
            BlockPos blockpos = this.toRender.get(i);
            Float f = this.scales.get(i);
            float f1 = f / 2.0F;
            LevelRenderer.addChainedFilledBoxVertices(
                poseStack,
                vertexconsumer,
                (double)((float)blockpos.getX() + 0.5F - f1) - camX,
                (double)((float)blockpos.getY() + 0.5F - f1) - camY,
                (double)((float)blockpos.getZ() + 0.5F - f1) - camZ,
                (double)((float)blockpos.getX() + 0.5F + f1) - camX,
                (double)((float)blockpos.getY() + 0.5F + f1) - camY,
                (double)((float)blockpos.getZ() + 0.5F + f1) - camZ,
                this.reds.get(i),
                this.greens.get(i),
                this.blues.get(i),
                this.alphas.get(i)
            );
        }
    }
}
