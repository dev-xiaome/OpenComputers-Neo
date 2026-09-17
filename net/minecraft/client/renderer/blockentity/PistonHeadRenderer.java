package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class PistonHeadRenderer implements BlockEntityRenderer<PistonMovingBlockEntity> {
    private final BlockRenderDispatcher blockRenderer;

    public PistonHeadRenderer(BlockEntityRendererProvider.Context context) {
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    public void render(PistonMovingBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Level level = blockEntity.getLevel();
        if (level != null) {
            BlockPos blockpos = blockEntity.getBlockPos().relative(blockEntity.getMovementDirection().getOpposite());
            BlockState blockstate = blockEntity.getMovedState();
            if (!blockstate.isAir()) {
                ModelBlockRenderer.enableCaching();
                poseStack.pushPose();
                poseStack.translate(blockEntity.getXOff(partialTick), blockEntity.getYOff(partialTick), blockEntity.getZOff(partialTick));
                if (blockstate.is(Blocks.PISTON_HEAD) && blockEntity.getProgress(partialTick) <= 4.0F) {
                    blockstate = blockstate.setValue(PistonHeadBlock.SHORT, Boolean.valueOf(blockEntity.getProgress(partialTick) <= 0.5F));
                    this.renderBlock(blockpos, blockstate, poseStack, bufferSource, level, false, packedOverlay);
                } else if (blockEntity.isSourcePiston() && !blockEntity.isExtending()) {
                    PistonType pistontype = blockstate.is(Blocks.STICKY_PISTON) ? PistonType.STICKY : PistonType.DEFAULT;
                    BlockState blockstate1 = Blocks.PISTON_HEAD
                        .defaultBlockState()
                        .setValue(PistonHeadBlock.TYPE, pistontype)
                        .setValue(PistonHeadBlock.FACING, blockstate.getValue(PistonBaseBlock.FACING));
                    blockstate1 = blockstate1.setValue(PistonHeadBlock.SHORT, Boolean.valueOf(blockEntity.getProgress(partialTick) >= 0.5F));
                    this.renderBlock(blockpos, blockstate1, poseStack, bufferSource, level, false, packedOverlay);
                    BlockPos blockpos1 = blockpos.relative(blockEntity.getMovementDirection());
                    poseStack.popPose();
                    poseStack.pushPose();
                    blockstate = blockstate.setValue(PistonBaseBlock.EXTENDED, Boolean.valueOf(true));
                    this.renderBlock(blockpos1, blockstate, poseStack, bufferSource, level, true, packedOverlay);
                } else {
                    this.renderBlock(blockpos, blockstate, poseStack, bufferSource, level, false, packedOverlay);
                }

                poseStack.popPose();
                ModelBlockRenderer.clearCache();
            }
        }
    }

    /**
     * @param extended if {@code true}, checks all sides before rendering via {@link
     *                 net.minecraft.world.level.block.Block#shouldRenderFace(
     *                 net.minecraft.world.level.block.state.BlockState,
     *                 net.minecraft.world.level.BlockGetter,
     *                 net.minecraft.core.BlockPos, net.minecraft.core.Direction,
     *                 net.minecraft.core.BlockPos)}
     */
    private void renderBlock(
        BlockPos pos, BlockState state, PoseStack poseStack, MultiBufferSource bufferSource, Level level, boolean extended, int packedOverlay
    ) {
        if (true) {
            net.neoforged.neoforge.client.ClientHooks.renderPistonMovedBlocks(pos, state, poseStack, bufferSource, level, extended, packedOverlay, blockRenderer);
            return;
        }
        RenderType rendertype = ItemBlockRenderTypes.getMovingBlockRenderType(state);
        VertexConsumer vertexconsumer = bufferSource.getBuffer(rendertype);
        this.blockRenderer
            .getModelRenderer()
            .tesselateBlock(
                level,
                this.blockRenderer.getBlockModel(state),
                state,
                pos,
                poseStack,
                vertexconsumer,
                extended,
                RandomSource.create(),
                state.getSeed(pos),
                packedOverlay
            );
    }

    @Override
    public int getViewDistance() {
        return 68;
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(PistonMovingBlockEntity blockEntity) {
        return net.minecraft.world.phys.AABB.INFINITE;
    }
}
