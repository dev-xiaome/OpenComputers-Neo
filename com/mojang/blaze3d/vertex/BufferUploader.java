package com.mojang.blaze3d.vertex;

import com.mojang.blaze3d.systems.RenderSystem;
import javax.annotation.Nullable;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BufferUploader {
    @Nullable
    private static VertexBuffer lastImmediateBuffer;

    public static void reset() {
        if (lastImmediateBuffer != null) {
            invalidate();
            VertexBuffer.unbind();
        }
    }

    public static void invalidate() {
        lastImmediateBuffer = null;
    }

    public static void drawWithShader(MeshData meshData) {
        if (!RenderSystem.isOnRenderThreadOrInit()) {
            RenderSystem.recordRenderCall(() -> _drawWithShader(meshData));
        } else {
            _drawWithShader(meshData);
        }
    }

    private static void _drawWithShader(MeshData meshData) {
        VertexBuffer vertexbuffer = upload(meshData);
        vertexbuffer.drawWithShader(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
    }

    public static void draw(MeshData meshData) {
        VertexBuffer vertexbuffer = upload(meshData);
        vertexbuffer.draw();
    }

    private static VertexBuffer upload(MeshData meshData) {
        RenderSystem.assertOnRenderThread();
        VertexBuffer vertexbuffer = bindImmediateBuffer(meshData.drawState().format());
        vertexbuffer.upload(meshData);
        return vertexbuffer;
    }

    private static VertexBuffer bindImmediateBuffer(VertexFormat format) {
        VertexBuffer vertexbuffer = format.getImmediateDrawVertexBuffer();
        bindImmediateBuffer(vertexbuffer);
        return vertexbuffer;
    }

    private static void bindImmediateBuffer(VertexBuffer buffer) {
        if (buffer != lastImmediateBuffer) {
            buffer.bind();
            lastImmediateBuffer = buffer;
        }
    }
}
