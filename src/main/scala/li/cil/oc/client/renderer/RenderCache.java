package li.cil.oc.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class RenderCache implements MultiBufferSource {
    public static class DrawEntry implements AutoCloseable {
        private final RenderType type;
        private VertexBuffer vertexBuffer;

        public DrawEntry(RenderType type, BufferBuilder builder) {
            this.type = type;
            try {
                this.vertexBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                this.vertexBuffer.bind();
                // 1.21.1: BufferBuilder.end() / RenderedBuffer 已换成 buildOrThrow() / MeshData。
                MeshData meshData = builder.buildOrThrow();
                this.vertexBuffer.upload(meshData);
                VertexBuffer.unbind();
            } catch (Exception e) {
                if (this.vertexBuffer != null) this.vertexBuffer.close();
                this.vertexBuffer = null;
            }
        }

        public void render(Matrix4f modelView, Matrix4f projection) {
            if (this.vertexBuffer == null) return;

            // 1.21.1 降级说明：RenderType.setupRenderState() 与 clearRenderState() 已不再对外公开
            // （1.20.5 起 RenderType 自行管理状态，公开入口只剩 RenderType.draw(MeshData)，
            // 而它不接受外部传入的 modelView / projection）。这里退化为沿用当前 RenderSystem
            // 状态、只显式挑选 shader，因此该 RenderType 的透明/剔除/深度状态不再由本类设置，
            // 需由调用方（RenderState / GuiGraphics）事先准备。
            ShaderInstance shader = RenderSystem.getShader();

            if (shader == null) {
                if (this.type.format().getElements().contains(VertexFormatElement.UV0)) {
                    RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
                } else {
                    RenderSystem.setShader(GameRenderer::getPositionColorShader);
                }
                shader = RenderSystem.getShader();
            }

            if (shader != null) {
                RenderSystem.polygonOffset(-1.0f, -10.0f);
                RenderSystem.enablePolygonOffset();

                this.vertexBuffer.bind();
                this.vertexBuffer.drawWithShader(modelView, projection, shader);

                RenderSystem.polygonOffset(0.0f, 0.0f);
                RenderSystem.disablePolygonOffset();
            }
        }

        @Override
        public void close() {
            if (vertexBuffer != null) {
                vertexBuffer.close();
                vertexBuffer = null;
            }
        }
    }

    private final List<DrawEntry> cached = new ArrayList<>();
    private RenderType activeType;
    private BufferBuilder activeBuilder;

    public RenderCache() {}

    public boolean isEmpty() { return cached.isEmpty() && activeBuilder == null; }

    public void clear() {
        cached.forEach(DrawEntry::close);
        cached.clear();
        activeType = null;
        activeBuilder = null;
    }

    private void flush() {
        if (activeType != null && activeBuilder != null) {
            cached.add(new DrawEntry(activeType, activeBuilder));
        }
        activeType = null;
        activeBuilder = null;
    }

    @Override
    public @NotNull VertexConsumer getBuffer(@NotNull RenderType type) {
        if (activeType != null && !activeType.equals(type)) {
            flush();
        }
        if (activeBuilder == null) {
            activeType = type;
            // 1.21.1: BufferBuilder 必须用 (ByteBufferBuilder, Mode, VertexFormat) 构造，
            // 不再有 begin(mode, format)。
            activeBuilder = new BufferBuilder(new ByteBufferBuilder(2048), type.mode(), type.format());
        }
        return activeBuilder;
    }

    public void finish() {
        flush();
    }

    public void render(PoseStack poseStack) {
        if (isEmpty()) return;

        // 1.21.1: RenderSystem.getModelViewStack().last().pose() 已换成 getModelViewMatrix()。
        // 原代码还会把 inverse view rotation 临时设为 identity（该 API 在 1.21.1 已移除），
        // 这里不再处理，法线方向按当前视角旋转。
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());

        modelView.mul(poseStack.last().pose());

        Matrix4f projection = RenderSystem.getProjectionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        for (DrawEntry entry : cached) {
            entry.render(modelView, projection);
        }

        VertexBuffer.unbind();
    }
}
