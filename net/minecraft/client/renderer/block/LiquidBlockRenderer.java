package net.minecraft.client.renderer.block;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class LiquidBlockRenderer {
    private static final float MAX_FLUID_HEIGHT = 0.8888889F;
    private final TextureAtlasSprite[] lavaIcons = new TextureAtlasSprite[2];
    private final TextureAtlasSprite[] waterIcons = new TextureAtlasSprite[2];
    private TextureAtlasSprite waterOverlay;

    protected void setupSprites() {
        this.lavaIcons[0] = Minecraft.getInstance().getModelManager().getBlockModelShaper().getBlockModel(Blocks.LAVA.defaultBlockState()).getParticleIcon();
        this.lavaIcons[1] = ModelBakery.LAVA_FLOW.sprite();
        this.waterIcons[0] = Minecraft.getInstance().getModelManager().getBlockModelShaper().getBlockModel(Blocks.WATER.defaultBlockState()).getParticleIcon();
        this.waterIcons[1] = ModelBakery.WATER_FLOW.sprite();
        this.waterOverlay = ModelBakery.WATER_OVERLAY.sprite();
        net.neoforged.neoforge.client.textures.FluidSpriteCache.reload();
    }

    private static boolean isNeighborSameFluid(FluidState firstState, FluidState secondState) {
        return secondState.getType().isSame(firstState.getType());
    }

    private static boolean isNeighborStateHidingOverlay(FluidState selfState, BlockState otherState, Direction neighborFace) {
        return otherState.shouldHideAdjacentFluidFace(neighborFace, selfState);
    }

    private static boolean isFaceOccludedByState(BlockGetter level, Direction face, float height, BlockPos pos, BlockState state) {
        if (state.canOcclude()) {
            VoxelShape voxelshape = Shapes.box(0.0, 0.0, 0.0, 1.0, (double)height, 1.0);
            VoxelShape voxelshape1 = state.getOcclusionShape(level, pos);
            return Shapes.blockOccudes(voxelshape, voxelshape1, face);
        } else {
            return false;
        }
    }

    private static boolean isFaceOccludedByNeighbor(BlockGetter level, BlockPos pos, Direction side, float height, BlockState blockState) {
        return isFaceOccludedByState(level, side, height, pos.relative(side), blockState);
    }

    private static boolean isFaceOccludedBySelf(BlockGetter level, BlockPos pos, BlockState state, Direction face) {
        return isFaceOccludedByState(level, face.getOpposite(), 1.0F, pos, state);
    }

    /**
 * @deprecated Neo: use overload that accepts BlockState
 */
    public static boolean shouldRenderFace(
        BlockAndTintGetter level, BlockPos pos, FluidState fluidState, BlockState blockState, Direction side, FluidState neighborFluid
    ) {
        return !isFaceOccludedBySelf(level, pos, blockState, side) && !isNeighborSameFluid(fluidState, neighborFluid);
    }

    public static boolean shouldRenderFace(
            BlockAndTintGetter level, BlockPos pos, FluidState fluidState, BlockState selfState, Direction direction, BlockState otherState
    ) {
        return !isFaceOccludedBySelf(level, pos, selfState, direction) && !isNeighborStateHidingOverlay(fluidState, otherState, direction.getOpposite());
    }



    public void tesselate(BlockAndTintGetter level, BlockPos pos, VertexConsumer buffer, BlockState blockState, FluidState fluidState) {
        boolean flag = fluidState.is(FluidTags.LAVA);
        TextureAtlasSprite[] atextureatlassprite = net.neoforged.neoforge.client.textures.FluidSpriteCache.getFluidSprites(level, pos, fluidState);
        int i = net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions.of(fluidState).getTintColor(fluidState, level, pos);
        float alpha = (float)(i >> 24 & 255) / 255.0F;
        float f = (float)(i >> 16 & 0xFF) / 255.0F;
        float f1 = (float)(i >> 8 & 0xFF) / 255.0F;
        float f2 = (float)(i & 0xFF) / 255.0F;
        BlockState blockstate = level.getBlockState(pos.relative(Direction.DOWN));
        FluidState fluidstate = blockstate.getFluidState();
        BlockState blockstate1 = level.getBlockState(pos.relative(Direction.UP));
        FluidState fluidstate1 = blockstate1.getFluidState();
        BlockState blockstate2 = level.getBlockState(pos.relative(Direction.NORTH));
        FluidState fluidstate2 = blockstate2.getFluidState();
        BlockState blockstate3 = level.getBlockState(pos.relative(Direction.SOUTH));
        FluidState fluidstate3 = blockstate3.getFluidState();
        BlockState blockstate4 = level.getBlockState(pos.relative(Direction.WEST));
        FluidState fluidstate4 = blockstate4.getFluidState();
        BlockState blockstate5 = level.getBlockState(pos.relative(Direction.EAST));
        FluidState fluidstate5 = blockstate5.getFluidState();
        boolean flag1 = !isNeighborStateHidingOverlay(fluidState, blockstate1, Direction.DOWN);
        boolean flag2 = shouldRenderFace(level, pos, fluidState, blockState, Direction.DOWN, blockstate)
            && !isFaceOccludedByNeighbor(level, pos, Direction.DOWN, 0.8888889F, blockstate);
        boolean flag3 = shouldRenderFace(level, pos, fluidState, blockState, Direction.NORTH, blockstate2);
        boolean flag4 = shouldRenderFace(level, pos, fluidState, blockState, Direction.SOUTH, blockstate3);
        boolean flag5 = shouldRenderFace(level, pos, fluidState, blockState, Direction.WEST, blockstate4);
        boolean flag6 = shouldRenderFace(level, pos, fluidState, blockState, Direction.EAST, blockstate5);
        if (flag1 || flag2 || flag6 || flag5 || flag3 || flag4) {
            float f3 = level.getShade(Direction.DOWN, true);
            float f4 = level.getShade(Direction.UP, true);
            float f5 = level.getShade(Direction.NORTH, true);
            float f6 = level.getShade(Direction.WEST, true);
            Fluid fluid = fluidState.getType();
            float f11 = this.getHeight(level, fluid, pos, blockState, fluidState);
            float f7;
            float f8;
            float f9;
            float f10;
            if (f11 >= 1.0F) {
                f7 = 1.0F;
                f8 = 1.0F;
                f9 = 1.0F;
                f10 = 1.0F;
            } else {
                float f12 = this.getHeight(level, fluid, pos.north(), blockstate2, fluidstate2);
                float f13 = this.getHeight(level, fluid, pos.south(), blockstate3, fluidstate3);
                float f14 = this.getHeight(level, fluid, pos.east(), blockstate5, fluidstate5);
                float f15 = this.getHeight(level, fluid, pos.west(), blockstate4, fluidstate4);
                f7 = this.calculateAverageHeight(level, fluid, f11, f12, f14, pos.relative(Direction.NORTH).relative(Direction.EAST));
                f8 = this.calculateAverageHeight(level, fluid, f11, f12, f15, pos.relative(Direction.NORTH).relative(Direction.WEST));
                f9 = this.calculateAverageHeight(level, fluid, f11, f13, f14, pos.relative(Direction.SOUTH).relative(Direction.EAST));
                f10 = this.calculateAverageHeight(level, fluid, f11, f13, f15, pos.relative(Direction.SOUTH).relative(Direction.WEST));
            }

            float f36 = (float)(pos.getX() & 15);
            float f37 = (float)(pos.getY() & 15);
            float f38 = (float)(pos.getZ() & 15);
            float f39 = 0.001F;
            float f16 = flag2 ? 0.001F : 0.0F;
            if (flag1 && !isFaceOccludedByNeighbor(level, pos, Direction.UP, Math.min(Math.min(f8, f10), Math.min(f9, f7)), blockstate1)) {
                f8 -= 0.001F;
                f10 -= 0.001F;
                f9 -= 0.001F;
                f7 -= 0.001F;
                Vec3 vec3 = fluidState.getFlow(level, pos);
                float f17;
                float f18;
                float f19;
                float f20;
                float f21;
                float f22;
                float f23;
                float f24;
                if (vec3.x == 0.0 && vec3.z == 0.0) {
                    TextureAtlasSprite textureatlassprite1 = atextureatlassprite[0];
                    f17 = textureatlassprite1.getU(0.0F);
                    f21 = textureatlassprite1.getV(0.0F);
                    f18 = f17;
                    f22 = textureatlassprite1.getV(1.0F);
                    f19 = textureatlassprite1.getU(1.0F);
                    f23 = f22;
                    f20 = f19;
                    f24 = f21;
                } else {
                    TextureAtlasSprite textureatlassprite = atextureatlassprite[1];
                    float f25 = (float)Mth.atan2(vec3.z, vec3.x) - (float) (Math.PI / 2);
                    float f26 = Mth.sin(f25) * 0.25F;
                    float f27 = Mth.cos(f25) * 0.25F;
                    float f28 = 0.5F;
                    f17 = textureatlassprite.getU(0.5F + (-f27 - f26));
                    f21 = textureatlassprite.getV(0.5F + -f27 + f26);
                    f18 = textureatlassprite.getU(0.5F + -f27 + f26);
                    f22 = textureatlassprite.getV(0.5F + f27 + f26);
                    f19 = textureatlassprite.getU(0.5F + f27 + f26);
                    f23 = textureatlassprite.getV(0.5F + (f27 - f26));
                    f20 = textureatlassprite.getU(0.5F + (f27 - f26));
                    f24 = textureatlassprite.getV(0.5F + (-f27 - f26));
                }

                float f53 = (f17 + f18 + f19 + f20) / 4.0F;
                float f54 = (f21 + f22 + f23 + f24) / 4.0F;
                float f55 = atextureatlassprite[0].uvShrinkRatio();
                f17 = Mth.lerp(f55, f17, f53);
                f18 = Mth.lerp(f55, f18, f53);
                f19 = Mth.lerp(f55, f19, f53);
                f20 = Mth.lerp(f55, f20, f53);
                f21 = Mth.lerp(f55, f21, f54);
                f22 = Mth.lerp(f55, f22, f54);
                f23 = Mth.lerp(f55, f23, f54);
                f24 = Mth.lerp(f55, f24, f54);
                int l = this.getLightColor(level, pos);
                float f57 = f4 * f;
                float f29 = f4 * f1;
                float f30 = f4 * f2;
                this.vertex(buffer, f36 + 0.0F, f37 + f8, f38 + 0.0F, f57, f29, f30, alpha, f17, f21, l);
                this.vertex(buffer, f36 + 0.0F, f37 + f10, f38 + 1.0F, f57, f29, f30, alpha, f18, f22, l);
                this.vertex(buffer, f36 + 1.0F, f37 + f9, f38 + 1.0F, f57, f29, f30, alpha, f19, f23, l);
                this.vertex(buffer, f36 + 1.0F, f37 + f7, f38 + 0.0F, f57, f29, f30, alpha, f20, f24, l);
                if (fluidState.shouldRenderBackwardUpFace(level, pos.above())) {
                    this.vertex(buffer, f36 + 0.0F, f37 + f8, f38 + 0.0F, f57, f29, f30, alpha, f17, f21, l);
                    this.vertex(buffer, f36 + 1.0F, f37 + f7, f38 + 0.0F, f57, f29, f30, alpha, f20, f24, l);
                    this.vertex(buffer, f36 + 1.0F, f37 + f9, f38 + 1.0F, f57, f29, f30, alpha, f19, f23, l);
                    this.vertex(buffer, f36 + 0.0F, f37 + f10, f38 + 1.0F, f57, f29, f30, alpha, f18, f22, l);
                }
            }

            if (flag2) {
                float f40 = atextureatlassprite[0].getU0();
                float f41 = atextureatlassprite[0].getU1();
                float f42 = atextureatlassprite[0].getV0();
                float f43 = atextureatlassprite[0].getV1();
                int k = this.getLightColor(level, pos.below());
                float f46 = f3 * f;
                float f48 = f3 * f1;
                float f50 = f3 * f2;
                this.vertex(buffer, f36, f37 + f16, f38 + 1.0F, f46, f48, f50, alpha, f40, f43, k);
                this.vertex(buffer, f36, f37 + f16, f38, f46, f48, f50, alpha, f40, f42, k);
                this.vertex(buffer, f36 + 1.0F, f37 + f16, f38, f46, f48, f50, alpha, f41, f42, k);
                this.vertex(buffer, f36 + 1.0F, f37 + f16, f38 + 1.0F, f46, f48, f50, alpha, f41, f43, k);
            }

            int j = this.getLightColor(level, pos);

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                float f44;
                float f45;
                float f47;
                float f49;
                float f51;
                float f52;
                boolean flag7;
                switch (direction) {
                    case NORTH:
                        f44 = f8;
                        f45 = f7;
                        f47 = f36;
                        f51 = f36 + 1.0F;
                        f49 = f38 + 0.001F;
                        f52 = f38 + 0.001F;
                        flag7 = flag3;
                        break;
                    case SOUTH:
                        f44 = f9;
                        f45 = f10;
                        f47 = f36 + 1.0F;
                        f51 = f36;
                        f49 = f38 + 1.0F - 0.001F;
                        f52 = f38 + 1.0F - 0.001F;
                        flag7 = flag4;
                        break;
                    case WEST:
                        f44 = f10;
                        f45 = f8;
                        f47 = f36 + 0.001F;
                        f51 = f36 + 0.001F;
                        f49 = f38 + 1.0F;
                        f52 = f38;
                        flag7 = flag5;
                        break;
                    default:
                        f44 = f7;
                        f45 = f9;
                        f47 = f36 + 1.0F - 0.001F;
                        f51 = f36 + 1.0F - 0.001F;
                        f49 = f38;
                        f52 = f38 + 1.0F;
                        flag7 = flag6;
                }

                if (flag7
                    && !isFaceOccludedByNeighbor(level, pos, direction, Math.max(f44, f45), level.getBlockState(pos.relative(direction)))) {
                    BlockPos blockpos = pos.relative(direction);
                    TextureAtlasSprite textureatlassprite2 = atextureatlassprite[1];
                    if (atextureatlassprite[2] != null) {
                        if (level.getBlockState(blockpos).shouldDisplayFluidOverlay(level, blockpos, fluidState)) {
                            textureatlassprite2 = atextureatlassprite[2];
                        }
                    }

                    float f56 = textureatlassprite2.getU(0.0F);
                    float f58 = textureatlassprite2.getU(0.5F);
                    float f59 = textureatlassprite2.getV((1.0F - f44) * 0.5F);
                    float f60 = textureatlassprite2.getV((1.0F - f45) * 0.5F);
                    float f31 = textureatlassprite2.getV(0.5F);
                    float f32 = direction.getAxis() == Direction.Axis.Z ? f5 : f6;
                    float f33 = f4 * f32 * f;
                    float f34 = f4 * f32 * f1;
                    float f35 = f4 * f32 * f2;
                    this.vertex(buffer, f47, f37 + f44, f49, f33, f34, f35, alpha, f56, f59, j);
                    this.vertex(buffer, f51, f37 + f45, f52, f33, f34, f35, alpha, f58, f60, j);
                    this.vertex(buffer, f51, f37 + f16, f52, f33, f34, f35, alpha, f58, f31, j);
                    this.vertex(buffer, f47, f37 + f16, f49, f33, f34, f35, alpha, f56, f31, j);
                    if (textureatlassprite2 != atextureatlassprite[2]) { // Neo: use custom fluid's overlay texture
                        this.vertex(buffer, f47, f37 + f16, f49, f33, f34, f35, alpha, f56, f31, j);
                        this.vertex(buffer, f51, f37 + f16, f52, f33, f34, f35, alpha, f58, f31, j);
                        this.vertex(buffer, f51, f37 + f45, f52, f33, f34, f35, alpha, f58, f60, j);
                        this.vertex(buffer, f47, f37 + f44, f49, f33, f34, f35, alpha, f56, f59, j);
                    }
                }
            }
        }
    }

    private float calculateAverageHeight(BlockAndTintGetter level, Fluid fluid, float currentHeight, float height1, float height2, BlockPos pos) {
        if (!(height2 >= 1.0F) && !(height1 >= 1.0F)) {
            float[] afloat = new float[2];
            if (height2 > 0.0F || height1 > 0.0F) {
                float f = this.getHeight(level, fluid, pos);
                if (f >= 1.0F) {
                    return 1.0F;
                }

                this.addWeightedHeight(afloat, f);
            }

            this.addWeightedHeight(afloat, currentHeight);
            this.addWeightedHeight(afloat, height2);
            this.addWeightedHeight(afloat, height1);
            return afloat[0] / afloat[1];
        } else {
            return 1.0F;
        }
    }

    private void addWeightedHeight(float[] output, float height) {
        if (height >= 0.8F) {
            output[0] += height * 10.0F;
            output[1] += 10.0F;
        } else if (height >= 0.0F) {
            output[0] += height;
            output[1]++;
        }
    }

    private float getHeight(BlockAndTintGetter level, Fluid fluid, BlockPos pos) {
        BlockState blockstate = level.getBlockState(pos);
        return this.getHeight(level, fluid, pos, blockstate, blockstate.getFluidState());
    }

    private void vertex(
            VertexConsumer p_110985_,
            float p_110989_,
            float p_110990_,
            float p_110991_,
            float p_110992_,
            float p_110993_,
            float p_350595_,
            float alpha,
            float p_350459_,
            float p_350437_,
            int p_110994_
    ) {
        p_110985_.addVertex(p_110989_, p_110990_, p_110991_)
                .setColor(p_110992_, p_110993_, p_350595_, alpha)
                .setUv(p_350459_, p_350437_)
                .setLight(p_110994_)
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    private float getHeight(BlockAndTintGetter level, Fluid fluid, BlockPos pos, BlockState blockState, FluidState fluidState) {
        if (fluid.isSame(fluidState.getType())) {
            BlockState blockstate = level.getBlockState(pos.above());
            return fluid.isSame(blockstate.getFluidState().getType()) ? 1.0F : fluidState.getOwnHeight();
        } else {
            return !blockState.isSolid() ? 0.0F : -1.0F;
        }
    }

    private void vertex(
        VertexConsumer buffer,
        float x,
        float y,
        float z,
        float red,
        float green,
        float blue,
        float u,
        float v,
        int packedLight
    ) {
        buffer.addVertex(x, y, z)
            .setColor(red, green, blue, 1.0F)
            .setUv(u, v)
            .setLight(packedLight)
            .setNormal(0.0F, 1.0F, 0.0F);
    }

    private int getLightColor(BlockAndTintGetter level, BlockPos pos) {
        int i = LevelRenderer.getLightColor(level, pos);
        int j = LevelRenderer.getLightColor(level, pos.above());
        int k = i & 0xFF;
        int l = j & 0xFF;
        int i1 = i >> 16 & 0xFF;
        int j1 = j >> 16 & 0xFF;
        return (k > l ? k : l) | (i1 > j1 ? i1 : j1) << 16;
    }
}
