package com.mojang.blaze3d.platform;

import com.google.common.base.Charsets;
import com.mojang.blaze3d.DontObfuscate;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

@OnlyIn(Dist.CLIENT)
@DontObfuscate
public class GlStateManager {
    private static final boolean ON_LINUX = Util.getPlatform() == Util.OS.LINUX;
    public static final int TEXTURE_COUNT = 12;
    private static final GlStateManager.BlendState BLEND = new GlStateManager.BlendState();
    private static final GlStateManager.DepthState DEPTH = new GlStateManager.DepthState();
    private static final GlStateManager.CullState CULL = new GlStateManager.CullState();
    private static final GlStateManager.PolygonOffsetState POLY_OFFSET = new GlStateManager.PolygonOffsetState();
    private static final GlStateManager.ColorLogicState COLOR_LOGIC = new GlStateManager.ColorLogicState();
    private static final GlStateManager.StencilState STENCIL = new GlStateManager.StencilState();
    private static final GlStateManager.ScissorState SCISSOR = new GlStateManager.ScissorState();
    private static int activeTexture;
    private static final GlStateManager.TextureState[] TEXTURES = IntStream.range(0, 12)
        .mapToObj(p_157120_ -> new GlStateManager.TextureState())
        .toArray(GlStateManager.TextureState[]::new);
    private static final GlStateManager.ColorMask COLOR_MASK = new GlStateManager.ColorMask();

    public static void _disableScissorTest() {
        RenderSystem.assertOnRenderThreadOrInit();
        SCISSOR.mode.disable();
    }

    public static void _enableScissorTest() {
        RenderSystem.assertOnRenderThreadOrInit();
        SCISSOR.mode.enable();
    }

    public static void _scissorBox(int x, int y, int width, int height) {
        RenderSystem.assertOnRenderThreadOrInit();
        GL20.glScissor(x, y, width, height);
    }

    public static void _disableDepthTest() {
        RenderSystem.assertOnRenderThreadOrInit();
        DEPTH.mode.disable();
    }

    public static void _enableDepthTest() {
        RenderSystem.assertOnRenderThreadOrInit();
        DEPTH.mode.enable();
    }

    public static void _depthFunc(int depthFunc) {
        RenderSystem.assertOnRenderThreadOrInit();
        if (depthFunc != DEPTH.func) {
            DEPTH.func = depthFunc;
            GL11.glDepthFunc(depthFunc);
        }
    }

    public static void _depthMask(boolean flag) {
        RenderSystem.assertOnRenderThread();
        if (flag != DEPTH.mask) {
            DEPTH.mask = flag;
            GL11.glDepthMask(flag);
        }
    }

    public static void _disableBlend() {
        RenderSystem.assertOnRenderThread();
        BLEND.mode.disable();
    }

    public static void _enableBlend() {
        RenderSystem.assertOnRenderThread();
        BLEND.mode.enable();
    }

    public static void _blendFunc(int sourceFactor, int destFactor) {
        RenderSystem.assertOnRenderThread();
        if (sourceFactor != BLEND.srcRgb || destFactor != BLEND.dstRgb) {
            BLEND.srcRgb = sourceFactor;
            BLEND.dstRgb = destFactor;
            GL11.glBlendFunc(sourceFactor, destFactor);
        }
    }

    public static void _blendFuncSeparate(int srcFactor, int dstFactor, int srcFactorAlpha, int dstFactorAlpha) {
        RenderSystem.assertOnRenderThread();
        if (srcFactor != BLEND.srcRgb || dstFactor != BLEND.dstRgb || srcFactorAlpha != BLEND.srcAlpha || dstFactorAlpha != BLEND.dstAlpha) {
            BLEND.srcRgb = srcFactor;
            BLEND.dstRgb = dstFactor;
            BLEND.srcAlpha = srcFactorAlpha;
            BLEND.dstAlpha = dstFactorAlpha;
            glBlendFuncSeparate(srcFactor, dstFactor, srcFactorAlpha, dstFactorAlpha);
        }
    }

    public static void _blendEquation(int mode) {
        RenderSystem.assertOnRenderThread();
        GL14.glBlendEquation(mode);
    }

    public static int glGetProgrami(int program, int pname) {
        RenderSystem.assertOnRenderThread();
        return GL20.glGetProgrami(program, pname);
    }

    public static void glAttachShader(int program, int shader) {
        RenderSystem.assertOnRenderThread();
        GL20.glAttachShader(program, shader);
    }

    public static void glDeleteShader(int shader) {
        RenderSystem.assertOnRenderThread();
        GL20.glDeleteShader(shader);
    }

    public static int glCreateShader(int type) {
        RenderSystem.assertOnRenderThread();
        return GL20.glCreateShader(type);
    }

    /**
     * @param shader The shader object whose source code is to be replaced.
     */
    public static void glShaderSource(int shader, List<String> shaderData) {
        RenderSystem.assertOnRenderThread();
        StringBuilder stringbuilder = new StringBuilder();

        for (String s : shaderData) {
            stringbuilder.append(s);
        }

        byte[] abyte = stringbuilder.toString().getBytes(Charsets.UTF_8);
        ByteBuffer bytebuffer = MemoryUtil.memAlloc(abyte.length + 1);
        bytebuffer.put(abyte);
        bytebuffer.put((byte)0);
        bytebuffer.flip();

        try (MemoryStack memorystack = MemoryStack.stackPush()) {
            PointerBuffer pointerbuffer = memorystack.mallocPointer(1);
            pointerbuffer.put(bytebuffer);
            GL20C.nglShaderSource(shader, 1, pointerbuffer.address0(), 0L);
        } finally {
            MemoryUtil.memFree(bytebuffer);
        }
    }

    public static void glCompileShader(int shader) {
        RenderSystem.assertOnRenderThread();
        GL20.glCompileShader(shader);
    }

    public static int glGetShaderi(int shader, int pname) {
        RenderSystem.assertOnRenderThread();
        return GL20.glGetShaderi(shader, pname);
    }

    public static void _glUseProgram(int program) {
        RenderSystem.assertOnRenderThread();
        GL20.glUseProgram(program);
    }

    public static int glCreateProgram() {
        RenderSystem.assertOnRenderThread();
        return GL20.glCreateProgram();
    }

    public static void glDeleteProgram(int program) {
        RenderSystem.assertOnRenderThread();
        GL20.glDeleteProgram(program);
    }

    public static void glLinkProgram(int program) {
        RenderSystem.assertOnRenderThread();
        GL20.glLinkProgram(program);
    }

    public static int _glGetUniformLocation(int program, CharSequence name) {
        RenderSystem.assertOnRenderThread();
        return GL20.glGetUniformLocation(program, name);
    }

    public static void _glUniform1(int location, IntBuffer value) {
        RenderSystem.assertOnRenderThread();
        GL20.glUniform1iv(location, value);
    }

    public static void _glUniform1i(int location, int value) {
        RenderSystem.assertOnRenderThread();
        GL20.glUniform1i(location, value);
    }

    public static void _glUniform1(int location, FloatBuffer value) {
        RenderSystem.assertOnRenderThread();
        GL20.glUniform1fv(location, value);
    }

    public static void _glUniform2(int location, IntBuffer value) {
        RenderSystem.assertOnRenderThread();
        GL20.glUniform2iv(location, value);
    }

    public static void _glUniform2(int location, FloatBuffer value) {
        RenderSystem.assertOnRenderThread();
        GL20.glUniform2fv(location, value);
    }

    public static void _glUniform3(int location, IntBuffer value) {
        RenderSystem.assertOnRenderThread();
        GL20.glUniform3iv(location, value);
    }

    public static void _glUniform3(int location, FloatBuffer value) {
        RenderSystem.assertOnRenderThread();
        GL20.glUniform3fv(location, value);
    }

    public static void _glUniform4(int location, IntBuffer value) {
        RenderSystem.assertOnRenderThread();
        GL20.glUniform4iv(location, value);
    }

    public static void _glUniform4(int location, FloatBuffer value) {
        RenderSystem.assertOnRenderThread();
        GL20.glUniform4fv(location, value);
    }

    public static void _glUniformMatrix2(int location, boolean transpose, FloatBuffer value) {
        RenderSystem.assertOnRenderThread();
        GL20.glUniformMatrix2fv(location, transpose, value);
    }

    public static void _glUniformMatrix3(int location, boolean transpose, FloatBuffer value) {
        RenderSystem.assertOnRenderThread();
        GL20.glUniformMatrix3fv(location, transpose, value);
    }

    public static void _glUniformMatrix4(int location, boolean transpose, FloatBuffer value) {
        RenderSystem.assertOnRenderThread();
        GL20.glUniformMatrix4fv(location, transpose, value);
    }

    public static int _glGetAttribLocation(int program, CharSequence name) {
        RenderSystem.assertOnRenderThread();
        return GL20.glGetAttribLocation(program, name);
    }

    public static void _glBindAttribLocation(int program, int index, CharSequence name) {
        RenderSystem.assertOnRenderThread();
        GL20.glBindAttribLocation(program, index, name);
    }

    public static int _glGenBuffers() {
        RenderSystem.assertOnRenderThreadOrInit();
        return GL15.glGenBuffers();
    }

    public static int _glGenVertexArrays() {
        RenderSystem.assertOnRenderThreadOrInit();
        return GL30.glGenVertexArrays();
    }

    public static void _glBindBuffer(int target, int buffer) {
        RenderSystem.assertOnRenderThreadOrInit();
        GL15.glBindBuffer(target, buffer);
    }

    public static void _glBindVertexArray(int array) {
        RenderSystem.assertOnRenderThreadOrInit();
        GL30.glBindVertexArray(array);
    }

    public static void _glBufferData(int target, ByteBuffer data, int usage) {
        RenderSystem.assertOnRenderThreadOrInit();
        GL15.glBufferData(target, data, usage);
    }

    public static void _glBufferData(int target, long size, int usage) {
        RenderSystem.assertOnRenderThreadOrInit();
        GL15.glBufferData(target, size, usage);
    }

    @Nullable
    public static ByteBuffer _glMapBuffer(int target, int access) {
        RenderSystem.assertOnRenderThreadOrInit();
        return GL15.glMapBuffer(target, access);
    }

    public static void _glUnmapBuffer(int target) {
        RenderSystem.assertOnRenderThreadOrInit();
        GL15.glUnmapBuffer(target);
    }

    public static void _glDeleteBuffers(int buffer) {
        RenderSystem.assertOnRenderThread();
        if (ON_LINUX) {
            GL32C.glBindBuffer(34962, buffer);
            GL32C.glBufferData(34962, 0L, 35048);
            GL32C.glBindBuffer(34962, 0);
        }

        GL15.glDeleteBuffers(buffer);
    }

    public static void _glCopyTexSubImage2D(int target, int level, int xOffset, int yOffset, int x, int y, int width, int height) {
        RenderSystem.assertOnRenderThreadOrInit();
        GL20.glCopyTexSubImage2D(target, level, xOffset, yOffset, x, y, width, height);
    }

    public static void _glDeleteVertexArrays(int array) {
        RenderSystem.assertOnRenderThread();
        GL30.glDeleteVertexArrays(array);
    }

    public static void _glBindFramebuffer(int target, int framebuffer) {
        RenderSystem.assertOnRenderThreadOrInit();
        GL30.glBindFramebuffer(target, framebuffer);
    }

    public static void _glBlitFrameBuffer(
        int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter
    ) {
        RenderSystem.assertOnRenderThreadOrInit();
        GL30.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }

    public static void _glBindRenderbuffer(int target, int renderBuffer) {
        RenderSystem.assertOnRenderThreadOrInit();
        GL30.glBindRenderbuffer(target, renderBuffer);
    }

    public static void _glDeleteRenderbuffers(int renderBuffer) {
        RenderSystem.assertOnRenderThreadOrInit();
        GL30.glDeleteRenderbuffers(renderBuffer);
    }

    public static void _glDeleteFramebuffers(int frameBuffer) {
        RenderSystem.assertOnRenderThreadOrInit();
        GL30.glDeleteFramebuffers(frameBuffer);
    }

    public static int glGenFramebuffers() {
        RenderSystem.assertOnRenderThreadOrInit();
        return GL30.glGenFramebuffers();
    }

    public static int glGenRenderbuffers() {
        RenderSystem.assertOnRenderThreadOrInit();
        return GL30.glGenRenderbuffers();
    }

    public static void _glRenderbufferStorage(int target, int internalFormat, int width, int height) {
        RenderSystem.assertOnRenderThreadOrInit();
        GL30.glRenderbufferStorage(target, internalFormat, width, height);
    }

    public static void _glFramebufferRenderbuffer(int target, int attachment, int renderBufferTarget, int renderBuffer) {
        RenderSystem.assertOnRenderThreadOrInit();
        GL30.glFramebufferRenderbuffer(target, attachment, renderBufferTarget, renderBuffer);
    }

    public static int glCheckFramebufferStatus(int target) {
        RenderSystem.assertOnRenderThreadOrInit();
        return GL30.glCheckFramebufferStatus(target);
    }

    public static void _glFramebufferTexture2D(int target, int attachment, int texTarget, int texture, int level) {
        RenderSystem.assertOnRenderThreadOrInit();
        GL30.glFramebufferTexture2D(target, attachment, texTarget, texture, level);
    }

    public static int getBoundFramebuffer() {
        RenderSystem.assertOnRenderThread();
        return _getInteger(36006);
    }

    public static void glActiveTexture(int texture) {
        RenderSystem.assertOnRenderThread();
        GL13.glActiveTexture(texture);
    }

    public static void glBlendFuncSeparate(int sFactorRGB, int dFactorRGB, int sFactorAlpha, int dFactorAlpha) {
        RenderSystem.assertOnRenderThread();
        GL14.glBlendFuncSeparate(sFactorRGB, dFactorRGB, sFactorAlpha, dFactorAlpha);
    }

    public static String glGetShaderInfoLog(int shader, int maxLength) {
        RenderSystem.assertOnRenderThread();
        return GL20.glGetShaderInfoLog(shader, maxLength);
    }

    public static String glGetProgramInfoLog(int program, int maxLength) {
        RenderSystem.assertOnRenderThread();
        return GL20.glGetProgramInfoLog(program, maxLength);
    }

    public static void setupLevelDiffuseLighting(Vector3f lightingVector1, Vector3f lightingVector2, Matrix4f matrix) {
        RenderSystem.assertOnRenderThread();
        RenderSystem.setShaderLights(matrix.transformDirection(lightingVector1, new Vector3f()), matrix.transformDirection(lightingVector2, new Vector3f()));
    }

    public static void setupGuiFlatDiffuseLighting(Vector3f lightingVector1, Vector3f lightingVector2) {
        RenderSystem.assertOnRenderThread();
        Matrix4f matrix4f = new Matrix4f().rotationY((float) (-Math.PI / 8)).rotateX((float) (Math.PI * 3.0 / 4.0));
        setupLevelDiffuseLighting(lightingVector1, lightingVector2, matrix4f);
    }

    public static void setupGui3DDiffuseLighting(Vector3f lightingVector1, Vector3f lightingVector2) {
        RenderSystem.assertOnRenderThread();
        Matrix4f matrix4f = new Matrix4f()
            .scaling(1.0F, -1.0F, 1.0F)
            .rotateYXZ(1.0821041F, 3.2375858F, 0.0F)
            .rotateYXZ((float) (-Math.PI / 8), (float) (Math.PI * 3.0 / 4.0), 0.0F);
        setupLevelDiffuseLighting(lightingVector1, lightingVector2, matrix4f);
    }

    public static void _enableCull() {
        RenderSystem.assertOnRenderThread();
        CULL.enable.enable();
    }

    public static void _disableCull() {
        RenderSystem.assertOnRenderThread();
        CULL.enable.disable();
    }

    public static void _polygonMode(int face, int mode) {
        RenderSystem.assertOnRenderThread();
        GL11.glPolygonMode(face, mode);
    }

    public static void _enablePolygonOffset() {
        RenderSystem.assertOnRenderThread();
        POLY_OFFSET.fill.enable();
    }

    public static void _disablePolygonOffset() {
        RenderSystem.assertOnRenderThread();
        POLY_OFFSET.fill.disable();
    }

    public static void _polygonOffset(float factor, float units) {
        RenderSystem.assertOnRenderThread();
        if (factor != POLY_OFFSET.factor || units != POLY_OFFSET.units) {
            POLY_OFFSET.factor = factor;
            POLY_OFFSET.units = units;
            GL11.glPolygonOffset(factor, units);
        }
    }

    public static void _enableColorLogicOp() {
        RenderSystem.assertOnRenderThread();
        COLOR_LOGIC.enable.enable();
    }

    public static void _disableColorLogicOp() {
        RenderSystem.assertOnRenderThread();
        COLOR_LOGIC.enable.disable();
    }

    public static void _logicOp(int logicOperation) {
        RenderSystem.assertOnRenderThread();
        if (logicOperation != COLOR_LOGIC.op) {
            COLOR_LOGIC.op = logicOperation;
            GL11.glLogicOp(logicOperation);
        }
    }

    public static void _activeTexture(int texture) {
        RenderSystem.assertOnRenderThread();
        if (activeTexture != texture - 33984) {
            activeTexture = texture - 33984;
            glActiveTexture(texture);
        }
    }

    public static void _texParameter(int target, int parameterName, float parameter) {
        RenderSystem.assertOnRenderThreadOrInit();
        GL11.glTexParameterf(target, parameterName, parameter);
    }

    public static void _texParameter(int target, int parameterName, int parameter) {
        RenderSystem.assertOnRenderThreadOrInit();
        GL11.glTexParameteri(target, parameterName, parameter);
    }

    public static int _getTexLevelParameter(int target, int level, int parameterName) {
        return GL11.glGetTexLevelParameteri(target, level, parameterName);
    }

    public static int _genTexture() {
        RenderSystem.assertOnRenderThreadOrInit();
        return GL11.glGenTextures();
    }

    public static void _genTextures(int[] textures) {
        RenderSystem.assertOnRenderThreadOrInit();
        GL11.glGenTextures(textures);
    }

    public static void _deleteTexture(int texture) {
        RenderSystem.assertOnRenderThreadOrInit();
        GL11.glDeleteTextures(texture);

        for (GlStateManager.TextureState glstatemanager$texturestate : TEXTURES) {
            if (glstatemanager$texturestate.binding == texture) {
                glstatemanager$texturestate.binding = -1;
            }
        }
    }

    public static void _deleteTextures(int[] textures) {
        RenderSystem.assertOnRenderThreadOrInit();

        for (GlStateManager.TextureState glstatemanager$texturestate : TEXTURES) {
            for (int i : textures) {
                if (glstatemanager$texturestate.binding == i) {
                    glstatemanager$texturestate.binding = -1;
                }
            }
        }

        GL11.glDeleteTextures(textures);
    }

    public static void _bindTexture(int texture) {
        RenderSystem.assertOnRenderThreadOrInit();
        if (texture != TEXTURES[activeTexture].binding) {
            TEXTURES[activeTexture].binding = texture;
            GL11.glBindTexture(3553, texture);
        }
    }

    public static int _getActiveTexture() {
        return activeTexture + 33984;
    }

    public static void _texImage2D(
        int target, int level, int internalFormat, int width, int height, int border, int format, int type, @Nullable IntBuffer pixels
    ) {
        RenderSystem.assertOnRenderThreadOrInit();
        GL11.glTexImage2D(target, level, internalFormat, width, height, border, format, type, pixels);
    }

    public static void _texSubImage2D(
        int target, int level, int xOffset, int yOffset, int width, int height, int format, int type, long pixels
    ) {
        RenderSystem.assertOnRenderThreadOrInit();
        GL11.glTexSubImage2D(target, level, xOffset, yOffset, width, height, format, type, pixels);
    }

    public static void upload(
        int level,
        int xOffset,
        int yOffset,
        int width,
        int height,
        NativeImage.Format format,
        IntBuffer pixels,
        Consumer<IntBuffer> output
    ) {
        if (!RenderSystem.isOnRenderThreadOrInit()) {
            RenderSystem.recordRenderCall(() -> _upload(level, xOffset, yOffset, width, height, format, pixels, output));
        } else {
            _upload(level, xOffset, yOffset, width, height, format, pixels, output);
        }
    }

    private static void _upload(
        int level,
        int xOffset,
        int yOffset,
        int width,
        int height,
        NativeImage.Format format,
        IntBuffer pixels,
        Consumer<IntBuffer> output
    ) {
        try {
            RenderSystem.assertOnRenderThreadOrInit();
            _pixelStore(3314, width);
            _pixelStore(3316, 0);
            _pixelStore(3315, 0);
            format.setUnpackPixelStoreState();
            GL11.glTexSubImage2D(3553, level, xOffset, yOffset, width, height, format.glFormat(), 5121, pixels);
        } finally {
            output.accept(pixels);
        }
    }

    public static void _getTexImage(int tex, int level, int format, int type, long pixels) {
        RenderSystem.assertOnRenderThread();
        GL11.glGetTexImage(tex, level, format, type, pixels);
    }

    public static void _viewport(int x, int y, int width, int height) {
        RenderSystem.assertOnRenderThreadOrInit();
        GlStateManager.Viewport.INSTANCE.x = x;
        GlStateManager.Viewport.INSTANCE.y = y;
        GlStateManager.Viewport.INSTANCE.width = width;
        GlStateManager.Viewport.INSTANCE.height = height;
        GL11.glViewport(x, y, width, height);
    }

    public static void _colorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        RenderSystem.assertOnRenderThread();
        if (red != COLOR_MASK.red || green != COLOR_MASK.green || blue != COLOR_MASK.blue || alpha != COLOR_MASK.alpha) {
            COLOR_MASK.red = red;
            COLOR_MASK.green = green;
            COLOR_MASK.blue = blue;
            COLOR_MASK.alpha = alpha;
            GL11.glColorMask(red, green, blue, alpha);
        }
    }

    public static void _stencilFunc(int func, int ref, int mask) {
        RenderSystem.assertOnRenderThread();
        if (func != STENCIL.func.func || func != STENCIL.func.ref || func != STENCIL.func.mask) {
            STENCIL.func.func = func;
            STENCIL.func.ref = ref;
            STENCIL.func.mask = mask;
            GL11.glStencilFunc(func, ref, mask);
        }
    }

    public static void _stencilMask(int mask) {
        RenderSystem.assertOnRenderThread();
        if (mask != STENCIL.mask) {
            STENCIL.mask = mask;
            GL11.glStencilMask(mask);
        }
    }

    /**
     * @param sfail  The action to take if the stencil test fails.
     * @param dpfail The action to take if the depth buffer test fails.
     * @param dppass The action to take if the depth buffer test passes.
     */
    public static void _stencilOp(int sfail, int dpfail, int dppass) {
        RenderSystem.assertOnRenderThread();
        if (sfail != STENCIL.fail || dpfail != STENCIL.zfail || dppass != STENCIL.zpass) {
            STENCIL.fail = sfail;
            STENCIL.zfail = dpfail;
            STENCIL.zpass = dppass;
            GL11.glStencilOp(sfail, dpfail, dppass);
        }
    }

    public static void _clearDepth(double depth) {
        RenderSystem.assertOnRenderThreadOrInit();
        GL11.glClearDepth(depth);
    }

    public static void _clearColor(float red, float green, float blue, float alpha) {
        RenderSystem.assertOnRenderThreadOrInit();
        GL11.glClearColor(red, green, blue, alpha);
    }

    public static void _clearStencil(int index) {
        RenderSystem.assertOnRenderThread();
        GL11.glClearStencil(index);
    }

    public static void _clear(int mask, boolean checkError) {
        RenderSystem.assertOnRenderThreadOrInit();
        GL11.glClear(mask);
        if (checkError) {
            _getError();
        }
    }

    public static void _glDrawPixels(int width, int height, int format, int type, long pixels) {
        RenderSystem.assertOnRenderThread();
        GL11.glDrawPixels(width, height, format, type, pixels);
    }

    public static void _vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long pointer) {
        RenderSystem.assertOnRenderThread();
        GL20.glVertexAttribPointer(index, size, type, normalized, stride, pointer);
    }

    public static void _vertexAttribIPointer(int index, int size, int type, int stride, long pointer) {
        RenderSystem.assertOnRenderThread();
        GL30.glVertexAttribIPointer(index, size, type, stride, pointer);
    }

    public static void _enableVertexAttribArray(int index) {
        RenderSystem.assertOnRenderThread();
        GL20.glEnableVertexAttribArray(index);
    }

    public static void _disableVertexAttribArray(int index) {
        RenderSystem.assertOnRenderThread();
        GL20.glDisableVertexAttribArray(index);
    }

    public static void _drawElements(int mode, int count, int type, long indices) {
        RenderSystem.assertOnRenderThread();
        GL11.glDrawElements(mode, count, type, indices);
    }

    public static void _pixelStore(int parameterName, int param) {
        RenderSystem.assertOnRenderThreadOrInit();
        GL11.glPixelStorei(parameterName, param);
    }

    public static void _readPixels(int x, int y, int width, int height, int format, int type, ByteBuffer pixels) {
        RenderSystem.assertOnRenderThread();
        GL11.glReadPixels(x, y, width, height, format, type, pixels);
    }

    public static void _readPixels(int x, int y, int width, int height, int format, int type, long pixels) {
        RenderSystem.assertOnRenderThread();
        GL11.glReadPixels(x, y, width, height, format, type, pixels);
    }

    public static int _getError() {
        RenderSystem.assertOnRenderThread();
        return GL11.glGetError();
    }

    public static String _getString(int name) {
        RenderSystem.assertOnRenderThread();
        return GL11.glGetString(name);
    }

    public static int _getInteger(int pname) {
        RenderSystem.assertOnRenderThreadOrInit();
        return GL11.glGetInteger(pname);
    }

    @OnlyIn(Dist.CLIENT)
    static class BlendState {
        public final GlStateManager.BooleanState mode = new GlStateManager.BooleanState(3042);
        public int srcRgb = 1;
        public int dstRgb = 0;
        public int srcAlpha = 1;
        public int dstAlpha = 0;
    }

    @OnlyIn(Dist.CLIENT)
    static class BooleanState {
        private final int state;
        private boolean enabled;

        public BooleanState(int state) {
            this.state = state;
        }

        public void disable() {
            this.setEnabled(false);
        }

        public void enable() {
            this.setEnabled(true);
        }

        public void setEnabled(boolean enabled) {
            RenderSystem.assertOnRenderThreadOrInit();
            if (enabled != this.enabled) {
                this.enabled = enabled;
                if (enabled) {
                    GL11.glEnable(this.state);
                } else {
                    GL11.glDisable(this.state);
                }
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    static class ColorLogicState {
        public final GlStateManager.BooleanState enable = new GlStateManager.BooleanState(3058);
        public int op = 5379;
    }

    @OnlyIn(Dist.CLIENT)
    static class ColorMask {
        public boolean red = true;
        public boolean green = true;
        public boolean blue = true;
        public boolean alpha = true;
    }

    @OnlyIn(Dist.CLIENT)
    static class CullState {
        public final GlStateManager.BooleanState enable = new GlStateManager.BooleanState(2884);
        public int mode = 1029;
    }

    @OnlyIn(Dist.CLIENT)
    static class DepthState {
        public final GlStateManager.BooleanState mode = new GlStateManager.BooleanState(2929);
        public boolean mask = true;
        public int func = 513;
    }

    @OnlyIn(Dist.CLIENT)
    @DontObfuscate
    public static enum DestFactor {
        CONSTANT_ALPHA(32771),
        CONSTANT_COLOR(32769),
        DST_ALPHA(772),
        DST_COLOR(774),
        ONE(1),
        ONE_MINUS_CONSTANT_ALPHA(32772),
        ONE_MINUS_CONSTANT_COLOR(32770),
        ONE_MINUS_DST_ALPHA(773),
        ONE_MINUS_DST_COLOR(775),
        ONE_MINUS_SRC_ALPHA(771),
        ONE_MINUS_SRC_COLOR(769),
        SRC_ALPHA(770),
        SRC_COLOR(768),
        ZERO(0);

        public final int value;

        private DestFactor(int value) {
            this.value = value;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static enum LogicOp {
        AND(5377),
        AND_INVERTED(5380),
        AND_REVERSE(5378),
        CLEAR(5376),
        COPY(5379),
        COPY_INVERTED(5388),
        EQUIV(5385),
        INVERT(5386),
        NAND(5390),
        NOOP(5381),
        NOR(5384),
        OR(5383),
        OR_INVERTED(5389),
        OR_REVERSE(5387),
        SET(5391),
        XOR(5382);

        public final int value;

        private LogicOp(int value) {
            this.value = value;
        }
    }

    @OnlyIn(Dist.CLIENT)
    static class PolygonOffsetState {
        public final GlStateManager.BooleanState fill = new GlStateManager.BooleanState(32823);
        public final GlStateManager.BooleanState line = new GlStateManager.BooleanState(10754);
        public float factor;
        public float units;
    }

    @OnlyIn(Dist.CLIENT)
    static class ScissorState {
        public final GlStateManager.BooleanState mode = new GlStateManager.BooleanState(3089);
    }

    @OnlyIn(Dist.CLIENT)
    @DontObfuscate
    public static enum SourceFactor {
        CONSTANT_ALPHA(32771),
        CONSTANT_COLOR(32769),
        DST_ALPHA(772),
        DST_COLOR(774),
        ONE(1),
        ONE_MINUS_CONSTANT_ALPHA(32772),
        ONE_MINUS_CONSTANT_COLOR(32770),
        ONE_MINUS_DST_ALPHA(773),
        ONE_MINUS_DST_COLOR(775),
        ONE_MINUS_SRC_ALPHA(771),
        ONE_MINUS_SRC_COLOR(769),
        SRC_ALPHA(770),
        SRC_ALPHA_SATURATE(776),
        SRC_COLOR(768),
        ZERO(0);

        public final int value;

        private SourceFactor(int value) {
            this.value = value;
        }
    }

    @OnlyIn(Dist.CLIENT)
    static class StencilFunc {
        public int func = 519;
        public int ref;
        public int mask = -1;
    }

    @OnlyIn(Dist.CLIENT)
    static class StencilState {
        public final GlStateManager.StencilFunc func = new GlStateManager.StencilFunc();
        public int mask = -1;
        public int fail = 7680;
        public int zfail = 7680;
        public int zpass = 7680;
    }

    @OnlyIn(Dist.CLIENT)
    static class TextureState {
        public int binding;
    }

    @OnlyIn(Dist.CLIENT)
    public static enum Viewport {
        INSTANCE;

        protected int x;
        protected int y;
        protected int width;
        protected int height;

        public static int x() {
            return INSTANCE.x;
        }

        public static int y() {
            return INSTANCE.y;
        }

        public static int width() {
            return INSTANCE.width;
        }

        public static int height() {
            return INSTANCE.height;
        }
    }

    public static void _backupGlState(net.neoforged.neoforge.client.GlStateBackup state) {
        state.blendEnabled = BLEND.mode.enabled;
        state.blendSrcRgb = BLEND.srcRgb;
        state.blendDestRgb = BLEND.dstRgb;
        state.blendSrcAlpha = BLEND.srcAlpha;
        state.blendDestAlpha = BLEND.dstAlpha;
        state.depthEnabled = DEPTH.mode.enabled;
        state.depthMask = DEPTH.mask;
        state.depthFunc = DEPTH.func;
        state.cullEnabled = CULL.enable.enabled;
        state.polyOffsetFillEnabled = POLY_OFFSET.fill.enabled;
        state.polyOffsetLineEnabled = POLY_OFFSET.line.enabled;
        state.polyOffsetFactor = POLY_OFFSET.factor;
        state.polyOffsetUnits = POLY_OFFSET.units;
        state.colorLogicEnabled = COLOR_LOGIC.enable.enabled;
        state.colorLogicOp = COLOR_LOGIC.op;
        state.stencilFuncFunc = STENCIL.func.func;
        state.stencilFuncRef = STENCIL.func.ref;
        state.stencilFuncMask = STENCIL.func.mask;
        state.stencilMask = STENCIL.mask;
        state.stencilFail = STENCIL.fail;
        state.stencilZFail = STENCIL.zfail;
        state.stencilZPass = STENCIL.zpass;
        state.scissorEnabled = SCISSOR.mode.enabled;
        state.colorMaskRed = COLOR_MASK.red;
        state.colorMaskGreen = COLOR_MASK.green;
        state.colorMaskBlue = COLOR_MASK.blue;
        state.colorMaskAlpha = COLOR_MASK.alpha;
    }

    public static void _restoreGlState(net.neoforged.neoforge.client.GlStateBackup state) {
        BLEND.mode.setEnabled(state.blendEnabled);
        _blendFuncSeparate(state.blendSrcRgb, state.blendDestRgb, state.blendSrcAlpha, state.blendDestAlpha);
        DEPTH.mode.setEnabled(state.depthEnabled);
        _depthMask(state.depthMask);
        _depthFunc(state.depthFunc);
        CULL.enable.setEnabled(state.cullEnabled);
        POLY_OFFSET.fill.setEnabled(state.polyOffsetFillEnabled);
        POLY_OFFSET.line.setEnabled(state.polyOffsetLineEnabled);
        _polygonOffset(state.polyOffsetFactor, state.polyOffsetUnits);
        COLOR_LOGIC.enable.setEnabled(state.colorLogicEnabled);
        _logicOp(state.colorLogicOp);
        _stencilFunc(state.stencilFuncFunc, state.stencilFuncRef, state.stencilFuncMask);
        _stencilMask(state.stencilMask);
        _stencilOp(state.stencilFail, state.stencilZFail, state.stencilZPass);
        SCISSOR.mode.setEnabled(state.scissorEnabled);
        _colorMask(state.colorMaskRed, state.colorMaskGreen, state.colorMaskBlue, state.colorMaskAlpha);
    }
}
