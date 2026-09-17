package com.mojang.blaze3d.platform;

import com.google.common.base.Charsets;
import java.nio.ByteBuffer;
import net.minecraft.util.StringDecomposer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWErrorCallbackI;
import org.lwjgl.system.MemoryUtil;

@OnlyIn(Dist.CLIENT)
public class ClipboardManager {
    public static final int FORMAT_UNAVAILABLE = 65545;
    private final ByteBuffer clipboardScratchBuffer = BufferUtils.createByteBuffer(8192);

    public String getClipboard(long window, GLFWErrorCallbackI errorCallback) {
        GLFWErrorCallback glfwerrorcallback = GLFW.glfwSetErrorCallback(errorCallback);
        String s = GLFW.glfwGetClipboardString(window);
        s = s != null ? StringDecomposer.filterBrokenSurrogates(s) : "";
        GLFWErrorCallback glfwerrorcallback1 = GLFW.glfwSetErrorCallback(glfwerrorcallback);
        if (glfwerrorcallback1 != null) {
            glfwerrorcallback1.free();
        }

        return s;
    }

    private static void pushClipboard(long window, ByteBuffer buffer, byte[] clipboardContent) {
        buffer.clear();
        buffer.put(clipboardContent);
        buffer.put((byte)0);
        buffer.flip();
        GLFW.glfwSetClipboardString(window, buffer);
    }

    public void setClipboard(long window, String clipboardContent) {
        byte[] abyte = clipboardContent.getBytes(Charsets.UTF_8);
        int i = abyte.length + 1;
        if (i < this.clipboardScratchBuffer.capacity()) {
            pushClipboard(window, this.clipboardScratchBuffer, abyte);
        } else {
            ByteBuffer bytebuffer = MemoryUtil.memAlloc(i);

            try {
                pushClipboard(window, bytebuffer, abyte);
            } finally {
                MemoryUtil.memFree(bytebuffer);
            }
        }
    }
}
