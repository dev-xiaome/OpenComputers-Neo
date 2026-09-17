package com.mojang.blaze3d.pipeline;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import java.util.Objects;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MainTarget extends RenderTarget {
    public static final int DEFAULT_WIDTH = 854;
    public static final int DEFAULT_HEIGHT = 480;
    static final MainTarget.Dimension DEFAULT_DIMENSIONS = new MainTarget.Dimension(854, 480);

    public MainTarget(int width, int height) {
        super(true);
        this.createFrameBuffer(width, height);
    }

    private void createFrameBuffer(int width, int height) {
        MainTarget.Dimension maintarget$dimension = this.allocateAttachments(width, height);
        this.frameBufferId = GlStateManager.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(36160, this.frameBufferId);
        GlStateManager._bindTexture(this.colorTextureId);
        GlStateManager._texParameter(3553, 10241, 9728);
        GlStateManager._texParameter(3553, 10240, 9728);
        GlStateManager._texParameter(3553, 10242, 33071);
        GlStateManager._texParameter(3553, 10243, 33071);
        GlStateManager._glFramebufferTexture2D(36160, 36064, 3553, this.colorTextureId, 0);
        GlStateManager._bindTexture(this.depthBufferId);
        GlStateManager._texParameter(3553, 34892, 0);
        GlStateManager._texParameter(3553, 10241, 9728);
        GlStateManager._texParameter(3553, 10240, 9728);
        GlStateManager._texParameter(3553, 10242, 33071);
        GlStateManager._texParameter(3553, 10243, 33071);
        GlStateManager._glFramebufferTexture2D(36160, 36096, 3553, this.depthBufferId, 0);
        GlStateManager._bindTexture(0);
        this.viewWidth = maintarget$dimension.width;
        this.viewHeight = maintarget$dimension.height;
        this.width = maintarget$dimension.width;
        this.height = maintarget$dimension.height;
        this.checkStatus();
        GlStateManager._glBindFramebuffer(36160, 0);
    }

    private MainTarget.Dimension allocateAttachments(int width, int height) {
        RenderSystem.assertOnRenderThreadOrInit();
        this.colorTextureId = TextureUtil.generateTextureId();
        this.depthBufferId = TextureUtil.generateTextureId();
        MainTarget.AttachmentState maintarget$attachmentstate = MainTarget.AttachmentState.NONE;

        for (MainTarget.Dimension maintarget$dimension : MainTarget.Dimension.listWithFallback(width, height)) {
            maintarget$attachmentstate = MainTarget.AttachmentState.NONE;
            if (this.allocateColorAttachment(maintarget$dimension)) {
                maintarget$attachmentstate = maintarget$attachmentstate.with(MainTarget.AttachmentState.COLOR);
            }

            if (this.allocateDepthAttachment(maintarget$dimension)) {
                maintarget$attachmentstate = maintarget$attachmentstate.with(MainTarget.AttachmentState.DEPTH);
            }

            if (maintarget$attachmentstate == MainTarget.AttachmentState.COLOR_DEPTH) {
                return maintarget$dimension;
            }
        }

        throw new RuntimeException("Unrecoverable GL_OUT_OF_MEMORY (allocated attachments = " + maintarget$attachmentstate.name() + ")");
    }

    private boolean allocateColorAttachment(MainTarget.Dimension dimension) {
        RenderSystem.assertOnRenderThreadOrInit();
        GlStateManager._getError();
        GlStateManager._bindTexture(this.colorTextureId);
        GlStateManager._texImage2D(3553, 0, 32856, dimension.width, dimension.height, 0, 6408, 5121, null);
        return GlStateManager._getError() != 1285;
    }

    private boolean allocateDepthAttachment(MainTarget.Dimension dimension) {
        RenderSystem.assertOnRenderThreadOrInit();
        GlStateManager._getError();
        GlStateManager._bindTexture(this.depthBufferId);
        GlStateManager._texImage2D(3553, 0, 6402, dimension.width, dimension.height, 0, 6402, 5126, null);
        return GlStateManager._getError() != 1285;
    }

    @OnlyIn(Dist.CLIENT)
    static enum AttachmentState {
        NONE,
        COLOR,
        DEPTH,
        COLOR_DEPTH;

        private static final MainTarget.AttachmentState[] VALUES = values();

        MainTarget.AttachmentState with(MainTarget.AttachmentState otherState) {
            return VALUES[this.ordinal() | otherState.ordinal()];
        }
    }

    @OnlyIn(Dist.CLIENT)
    static class Dimension {
        public final int width;
        public final int height;

        Dimension(int width, int height) {
            this.width = width;
            this.height = height;
        }

        static List<MainTarget.Dimension> listWithFallback(int width, int height) {
            RenderSystem.assertOnRenderThreadOrInit();
            int i = RenderSystem.maxSupportedTextureSize();
            return width > 0 && width <= i && height > 0 && height <= i
                ? ImmutableList.of(new MainTarget.Dimension(width, height), MainTarget.DEFAULT_DIMENSIONS)
                : ImmutableList.of(MainTarget.DEFAULT_DIMENSIONS);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            } else if (other != null && this.getClass() == other.getClass()) {
                MainTarget.Dimension maintarget$dimension = (MainTarget.Dimension)other;
                return this.width == maintarget$dimension.width && this.height == maintarget$dimension.height;
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.width, this.height);
        }

        @Override
        public String toString() {
            return this.width + "x" + this.height;
        }
    }
}
