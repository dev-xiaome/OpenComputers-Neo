package li.cil.oc.api.event;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import li.cil.oc.api.component.RackMountable;
import li.cil.oc.api.internal.Rack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Fired to allow rendering a custom overlay for {@link li.cil.oc.api.component.RackMountable}s.
 * <br>
 * When this event is fired, the {@link PoseStack} is set up such that the origin is
 * the top left corner of the mountable the event was fired for. It's the
 * event handler's responsibility to not render outside the are of the
 * mountable (unless that's explicitly what they're going for, of course).
 */
public abstract class RackMountableRenderEvent extends Event {
    /**
     * The rack that house the mountable this event is fired for.
     */
    public final Rack rack;

    /**
     * The index of the mountable in the rack the event is fired for.
     */
    public final int mountable;

    /**
     * Some additional data made available by the mountable. May be <tt>null</tt>.
     *
     * @see RackMountable#getData()
     */
    public final CompoundTag data;

    public RackMountableRenderEvent(Rack rack, int mountable, CompoundTag data) {
        this.rack = rack;
        this.mountable = mountable;
        this.data = data;
    }

    /**
     * Fired when the static rack model is rendered.
     * <br>
     * 1.21.1 没有 {@code ISimpleBlockRenderingHandler} 与 {@code RenderBlocks}，
     * 方块模型由烘焙模型（{@code BakedModel}）渲染，因此这里只暴露一个“正面贴图覆盖”
     * 供事件处理器设置；实际生效需要渲染器在绘制正面时读取该覆盖值。
     * <br>
     * The pose will be set up before this call, so you may adjust it, if you wish.
     */
    public static class Block extends RackMountableRenderEvent implements ICancellableEvent {
        /**
         * The front-facing side, i.e. where the mountable is visible on the rack.
         */
        public final Direction side;

        /**
         * 渲染时使用的姿态栈（原 {@code RenderBlocks} 已不存在）。
         * <br>
         * 事件处理器可在此调整变换；方块模型的最终绘制仍由渲染器完成。
         */
        public final PoseStack pose;

        /**
         * Texture to use for the front of the mountable.
         */
        private ResourceLocation frontTextureOverride;

        public Block(final Rack rack, final int mountable, final CompoundTag data, final Direction side, final PoseStack pose) {
            super(rack, mountable, data);
            this.side = side;
            this.pose = pose;
        }

        /**
         * The texture currently set to use for the front of the mountable, or <tt>null</tt>.
         */
        public ResourceLocation getFrontTextureOverride() {
            return frontTextureOverride;
        }

        /**
         * Set the texture to use for the front of the mountable.
         *
         * @param texture the texture to use.
         */
        public void setFrontTextureOverride(final ResourceLocation texture) {
            frontTextureOverride = texture;
        }
    }

    /**
     * Fired when the dynamic rack model is rendered.
     * <br>
     * This is primarily meant to allow rendering custom overlays, such as LEDs. The
     * pose will have been adjusted such that rendering a one by one quad starting at
     * the origin will fill the full front face of the rack (i.e. rotation and translation
     * have already been applied).
     * <br>
     * 1.21.1 的渲染不再使用全局 {@code Tessellator}，而是通过 {@link MultiBufferSource}
     * 获取 {@link VertexConsumer} 并写出顶点。
     * <br>
     * Use {@link #renderOverlay(MultiBufferSource, ResourceLocation)} to render a slice
     * from a texture in the vertical area occupied by the mountable.
     */
    public static class BlockEntity extends RackMountableRenderEvent {
        /**
         * The vertical low and high texture coordinates for the mountable's slot.
         * <br>
         * This is purely for convenience; they're computed as <tt>(2/16)+i*(3/16)</tt>.
         */
        public final float v0, v1;

        public BlockEntity(final Rack rack, final int mountable, final CompoundTag data, final float v0, final float v1) {
            super(rack, mountable, data);
            this.v0 = v0;
            this.v1 = v1;
        }

        /**
         * Utility method for rendering a texture as the front-side overlay.
         *
         * @param buffer  the buffer source to acquire the vertex consumer from.
         * @param texture the texture to use to render the overlay.
         */
        public void renderOverlay(final MultiBufferSource buffer, final ResourceLocation texture) {
            renderOverlay(buffer, texture, 0, 1);
        }

        /**
         * Utility method for rendering a texture as the front-side overlay
         * over a specified horizontal area.
         * <br>
         * 该方法写出的是一个位于 {@code z = 0}、覆盖整个挂载位正面的四边形，
         * 与 1.7.10 版本的行为一致；纹理需要位于方块图集之外的独立贴图中时，
         * 请自行提供 {@link RenderType} 并调用
         * {@link #renderOverlay(VertexConsumer, float, float)}。
         *
         * @param buffer  the buffer source to acquire the vertex consumer from.
         * @param texture the texture to use to render the overlay.
         * @param u0      the lower end of the vertical area to render at.
         * @param u1      the upper end of the vertical area to render at.
         */
        public void renderOverlay(final MultiBufferSource buffer, final ResourceLocation texture, final float u0, final float u1) {
            renderOverlay(buffer.getBuffer(RenderType.entityCutout(texture)), u0, u1);
        }

        /**
         * 直接向指定的顶点消费者写出覆盖层四边形，供需要自定义 {@link RenderType} 的
         * 事件处理器使用。
         *
         * @param consumer the vertex consumer to write to.
         * @param u0       the lower end of the vertical area to render at.
         * @param u1       the upper end of the vertical area to render at.
         */
        public void renderOverlay(final VertexConsumer consumer, final float u0, final float u1) {
            // 逆时针写出四边形（与 1.7.10 的 addVertexWithUV 顺序等价），法线朝 +Z。
            consumer.addVertex(u0, v0, 0).setUv(u0, v0).setColor(0xFFFFFFFF).setLight(0x00F000F0).setNormal(0, 0, 1);
            consumer.addVertex(u1, v0, 0).setUv(u1, v0).setColor(0xFFFFFFFF).setLight(0x00F000F0).setNormal(0, 0, 1);
            consumer.addVertex(u1, v1, 0).setUv(u1, v1).setColor(0xFFFFFFFF).setLight(0x00F000F0).setNormal(0, 0, 1);
            consumer.addVertex(u0, v1, 0).setUv(u0, v1).setColor(0xFFFFFFFF).setLight(0x00F000F0).setNormal(0, 0, 1);
        }
    }
}
