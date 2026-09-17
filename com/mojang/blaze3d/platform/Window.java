package com.mojang.blaze3d.platform;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.SilentInitException;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.IoSupplier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.glfw.GLFWImage.Buffer;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public final class Window implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int BASE_WIDTH = 320;
    public static final int BASE_HEIGHT = 240;
    private final GLFWErrorCallback defaultErrorCallback = GLFWErrorCallback.create(this::defaultErrorCallback);
    private final WindowEventHandler eventHandler;
    private final ScreenManager screenManager;
    private final long window;
    private int windowedX;
    private int windowedY;
    private int windowedWidth;
    private int windowedHeight;
    private Optional<VideoMode> preferredFullscreenVideoMode;
    private boolean fullscreen;
    private boolean actuallyFullscreen;
    private int x;
    private int y;
    private int width;
    private int height;
    private int framebufferWidth;
    private int framebufferHeight;
    private int guiScaledWidth;
    private int guiScaledHeight;
    private double guiScale;
    private String errorSection = "";
    private boolean dirty;
    private int framerateLimit;
    private boolean vsync;

    public Window(WindowEventHandler eventHandler, ScreenManager screenManager, DisplayData displayData, @Nullable String preferredFullscreenVideoMode, String title) {
        this.screenManager = screenManager;
        this.setBootErrorCallback();
        this.setErrorSection("Pre startup");
        this.eventHandler = eventHandler;
        Optional<VideoMode> optional = VideoMode.read(preferredFullscreenVideoMode);
        if (optional.isPresent()) {
            this.preferredFullscreenVideoMode = optional;
        } else if (displayData.fullscreenWidth.isPresent() && displayData.fullscreenHeight.isPresent()) {
            this.preferredFullscreenVideoMode = Optional.of(
                new VideoMode(displayData.fullscreenWidth.getAsInt(), displayData.fullscreenHeight.getAsInt(), 8, 8, 8, 60)
            );
        } else {
            this.preferredFullscreenVideoMode = Optional.empty();
        }

        this.actuallyFullscreen = this.fullscreen = displayData.isFullscreen;
        Monitor monitor = screenManager.getMonitor(GLFW.glfwGetPrimaryMonitor());
        this.windowedWidth = this.width = displayData.width > 0 ? displayData.width : 1;
        this.windowedHeight = this.height = displayData.height > 0 ? displayData.height : 1;
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(139265, 196609);
        GLFW.glfwWindowHint(139275, 221185);
        GLFW.glfwWindowHint(139266, 3);
        GLFW.glfwWindowHint(139267, 2);
        GLFW.glfwWindowHint(139272, 204801);
        GLFW.glfwWindowHint(139270, 1);
        this.window = net.neoforged.fml.loading.ImmediateWindowHandler.setupMinecraftWindow(()->this.width, ()->this.height, ()->title, ()->this.fullscreen && monitor != null ? monitor.getMonitor() : 0L);
        if (!net.neoforged.fml.loading.ImmediateWindowHandler.positionWindow(Optional.ofNullable(monitor), w->this.width = this.windowedWidth = w, h->this.height = this.windowedHeight = h, x->this.x = this.windowedX = x, y->this.y = this.windowedY = y)) {
        if (monitor != null) {
            VideoMode videomode = monitor.getPreferredVidMode(this.fullscreen ? this.preferredFullscreenVideoMode : Optional.empty());
            this.windowedX = this.x = monitor.getX() + videomode.getWidth() / 2 - this.width / 2;
            this.windowedY = this.y = monitor.getY() + videomode.getHeight() / 2 - this.height / 2;
        } else {
            int[] aint1 = new int[1];
            int[] aint = new int[1];
            GLFW.glfwGetWindowPos(this.window, aint1, aint);
            this.windowedX = this.x = aint1[0];
            this.windowedY = this.y = aint[0];
        }
        }

        GLFW.glfwMakeContextCurrent(this.window);
        GL.createCapabilities();
        int i = RenderSystem.maxSupportedTextureSize();
        GLFW.glfwSetWindowSizeLimits(this.window, -1, -1, i, i);
        this.setMode();
        this.refreshFramebufferSize();
        GLFW.glfwSetFramebufferSizeCallback(this.window, this::onFramebufferResize);
        GLFW.glfwSetWindowPosCallback(this.window, this::onMove);
        GLFW.glfwSetWindowSizeCallback(this.window, this::onResize);
        GLFW.glfwSetWindowFocusCallback(this.window, this::onFocus);
        GLFW.glfwSetCursorEnterCallback(this.window, this::onEnter);
    }

    public static String getPlatform() {
        int i = GLFW.glfwGetPlatform();

        return switch (i) {
            case 0 -> "<error>";
            case 393217 -> "win32";
            case 393218 -> "cocoa";
            case 393219 -> "wayland";
            case 393220 -> "x11";
            case 393221 -> "null";
            default -> String.format(Locale.ROOT, "unknown (%08X)", i);
        };
    }

    public int getRefreshRate() {
        RenderSystem.assertOnRenderThread();
        return GLX._getRefreshRate(this);
    }

    public boolean shouldClose() {
        return GLX._shouldClose(this);
    }

    public static void checkGlfwError(BiConsumer<Integer, String> errorConsumer) {
        try (MemoryStack memorystack = MemoryStack.stackPush()) {
            PointerBuffer pointerbuffer = memorystack.mallocPointer(1);
            int i = GLFW.glfwGetError(pointerbuffer);
            if (i != 0) {
                long j = pointerbuffer.get();
                String s = j == 0L ? "" : MemoryUtil.memUTF8(j);
                errorConsumer.accept(i, s);
            }
        }
    }

    public void setIcon(PackResources packResources, IconSet iconSet) throws IOException {
        int i = GLFW.glfwGetPlatform();
        switch (i) {
            case 393217:
            case 393220:
                List<IoSupplier<InputStream>> list = iconSet.getStandardIcons(packResources);
                List<ByteBuffer> list1 = new ArrayList<>(list.size());

                try (MemoryStack memorystack = MemoryStack.stackPush()) {
                    Buffer buffer = GLFWImage.malloc(list.size(), memorystack);

                    for (int j = 0; j < list.size(); j++) {
                        try (NativeImage nativeimage = NativeImage.read(list.get(j).get())) {
                            ByteBuffer bytebuffer = MemoryUtil.memAlloc(nativeimage.getWidth() * nativeimage.getHeight() * 4);
                            list1.add(bytebuffer);
                            bytebuffer.asIntBuffer().put(nativeimage.getPixelsRGBA());
                            buffer.position(j);
                            buffer.width(nativeimage.getWidth());
                            buffer.height(nativeimage.getHeight());
                            buffer.pixels(bytebuffer);
                        }
                    }

                    GLFW.glfwSetWindowIcon(this.window, buffer.position(0));
                    break;
                } finally {
                    list1.forEach(MemoryUtil::memFree);
                }
            case 393218:
                MacosUtil.loadIcon(iconSet.getMacIcon(packResources));
            case 393219:
            case 393221:
                break;
            default:
                LOGGER.warn("Not setting icon for unrecognized platform: {}", i);
        }
    }

    public void setErrorSection(String errorSection) {
        this.errorSection = errorSection;
    }

    private void setBootErrorCallback() {
        GLFW.glfwSetErrorCallback(Window::bootCrash);
    }

    private static void bootCrash(int error, long description) {
        String s = "GLFW error " + error + ": " + MemoryUtil.memUTF8(description);
        TinyFileDialogs.tinyfd_messageBox(
            "Minecraft", s + ".\n\nPlease make sure you have up-to-date drivers (see aka.ms/mcdriver for instructions).", "ok", "error", false
        );
        throw new Window.WindowInitFailed(s);
    }

    public void defaultErrorCallback(int error, long description) {
        String s = MemoryUtil.memUTF8(description);
        if (!RenderSystem.isOnRenderThread()) {
            throw new IllegalStateException("Encountered GL error off-thread @ " + errorSection + ": " + error + ": " + s);
        }
        LOGGER.error("########## GL ERROR ##########");
        LOGGER.error("@ {}", this.errorSection);
        LOGGER.error("{}: {}", error, s);
    }

    public void setDefaultErrorCallback() {
        GLFWErrorCallback glfwerrorcallback = GLFW.glfwSetErrorCallback(this.defaultErrorCallback);
        if (glfwerrorcallback != null) {
            glfwerrorcallback.free();
        }
    }

    public void updateVsync(boolean vsync) {
        RenderSystem.assertOnRenderThreadOrInit();
        this.vsync = vsync;
        GLFW.glfwSwapInterval(vsync ? 1 : 0);
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        Callbacks.glfwFreeCallbacks(this.window);
        this.defaultErrorCallback.close();
        GLFW.glfwDestroyWindow(this.window);
        GLFW.glfwTerminate();
    }

    private void onMove(long window, int x, int y) {
        this.x = x;
        this.y = y;
    }

    private void onFramebufferResize(long window, int framebufferWidth, int framebufferHeight) {
        if (window == this.window) {
            int i = this.getWidth();
            int j = this.getHeight();
            if (framebufferWidth != 0 && framebufferHeight != 0) {
                this.framebufferWidth = framebufferWidth;
                this.framebufferHeight = framebufferHeight;
                if (this.getWidth() != i || this.getHeight() != j) {
                    this.eventHandler.resizeDisplay();
                }
            }
        }
    }

    private void refreshFramebufferSize() {
        int[] aint = new int[1];
        int[] aint1 = new int[1];
        GLFW.glfwGetFramebufferSize(this.window, aint, aint1);
        this.framebufferWidth = aint[0] > 0 ? aint[0] : 1;
        this.framebufferHeight = aint1[0] > 0 ? aint1[0] : 1;
        if (this.framebufferHeight == 0 || this.framebufferWidth==0) net.neoforged.fml.loading.ImmediateWindowHandler.updateFBSize(w->this.framebufferWidth=w, h->this.framebufferHeight=h);
    }

    private void onResize(long window, int width, int height) {
        this.width = width;
        this.height = height;
    }

    private void onFocus(long window, boolean hasFocus) {
        if (window == this.window) {
            this.eventHandler.setWindowActive(hasFocus);
        }
    }

    /**
     * @param cursorEntered {@code true} if the cursor entered the window, {@code
     *                      false} if the cursor left
     */
    private void onEnter(long window, boolean cursorEntered) {
        if (cursorEntered) {
            this.eventHandler.cursorEntered();
        }
    }

    public void setFramerateLimit(int limit) {
        this.framerateLimit = limit;
    }

    public int getFramerateLimit() {
        return this.framerateLimit;
    }

    public void updateDisplay() {
        RenderSystem.flipFrame(this.window);
        if (this.fullscreen != this.actuallyFullscreen) {
            this.actuallyFullscreen = this.fullscreen;
            this.updateFullscreen(this.vsync);
        }
    }

    public Optional<VideoMode> getPreferredFullscreenVideoMode() {
        return this.preferredFullscreenVideoMode;
    }

    public void setPreferredFullscreenVideoMode(Optional<VideoMode> preferredFullscreenVideoMode) {
        boolean flag = !preferredFullscreenVideoMode.equals(this.preferredFullscreenVideoMode);
        this.preferredFullscreenVideoMode = preferredFullscreenVideoMode;
        if (flag) {
            this.dirty = true;
        }
    }

    public void changeFullscreenVideoMode() {
        if (this.fullscreen && this.dirty) {
            this.dirty = false;
            this.setMode();
            this.eventHandler.resizeDisplay();
        }
    }

    private void setMode() {
        boolean flag = GLFW.glfwGetWindowMonitor(this.window) != 0L;
        if (this.fullscreen) {
            Monitor monitor = this.screenManager.findBestMonitor(this);
            if (monitor == null) {
                LOGGER.warn("Failed to find suitable monitor for fullscreen mode");
                this.fullscreen = false;
            } else {
                if (Minecraft.ON_OSX) {
                    MacosUtil.exitNativeFullscreen(this.window);
                }

                VideoMode videomode = monitor.getPreferredVidMode(this.preferredFullscreenVideoMode);
                if (!flag) {
                    this.windowedX = this.x;
                    this.windowedY = this.y;
                    this.windowedWidth = this.width;
                    this.windowedHeight = this.height;
                }

                this.x = 0;
                this.y = 0;
                this.width = videomode.getWidth();
                this.height = videomode.getHeight();
                GLFW.glfwSetWindowMonitor(this.window, monitor.getMonitor(), this.x, this.y, this.width, this.height, videomode.getRefreshRate());
                if (Minecraft.ON_OSX) {
                    MacosUtil.clearResizableBit(this.window);
                }
            }
        } else {
            this.x = this.windowedX;
            this.y = this.windowedY;
            this.width = this.windowedWidth;
            this.height = this.windowedHeight;
            GLFW.glfwSetWindowMonitor(this.window, 0L, this.x, this.y, this.width, this.height, -1);
        }
    }

    public void toggleFullScreen() {
        this.fullscreen = !this.fullscreen;
    }

    public void setWindowed(int windowedWidth, int windowedHeight) {
        this.windowedWidth = windowedWidth;
        this.windowedHeight = windowedHeight;
        this.fullscreen = false;
        this.setMode();
    }

    private void updateFullscreen(boolean vsyncEnabled) {
        RenderSystem.assertOnRenderThread();

        try {
            this.setMode();
            this.eventHandler.resizeDisplay();
            this.updateVsync(vsyncEnabled);
            this.updateDisplay();
        } catch (Exception exception) {
            LOGGER.error("Couldn't toggle fullscreen", (Throwable)exception);
        }
    }

    public int calculateScale(int guiScale, boolean forceUnicode) {
        int i = 1;

        while (
            i != guiScale
                && i < this.framebufferWidth
                && i < this.framebufferHeight
                && this.framebufferWidth / (i + 1) >= 320
                && this.framebufferHeight / (i + 1) >= 240
        ) {
            i++;
        }

        if (forceUnicode && i % 2 != 0) {
            i++;
        }

        return i;
    }

    public void setGuiScale(double scaleFactor) {
        this.guiScale = scaleFactor;
        int i = (int)((double)this.framebufferWidth / scaleFactor);
        this.guiScaledWidth = (double)this.framebufferWidth / scaleFactor > (double)i ? i + 1 : i;
        int j = (int)((double)this.framebufferHeight / scaleFactor);
        this.guiScaledHeight = (double)this.framebufferHeight / scaleFactor > (double)j ? j + 1 : j;
    }

    public void setTitle(String title) {
        GLFW.glfwSetWindowTitle(this.window, title);
    }

    public long getWindow() {
        return this.window;
    }

    public boolean isFullscreen() {
        return this.fullscreen;
    }

    public int getWidth() {
        return this.framebufferWidth;
    }

    public int getHeight() {
        return this.framebufferHeight;
    }

    public void setWidth(int framebufferWidth) {
        this.framebufferWidth = framebufferWidth;
    }

    public void setHeight(int framebufferHeight) {
        this.framebufferHeight = framebufferHeight;
    }

    public int getScreenWidth() {
        return this.width;
    }

    public int getScreenHeight() {
        return this.height;
    }

    public int getGuiScaledWidth() {
        return this.guiScaledWidth;
    }

    public int getGuiScaledHeight() {
        return this.guiScaledHeight;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public double getGuiScale() {
        return this.guiScale;
    }

    @Nullable
    public Monitor findBestMonitor() {
        return this.screenManager.findBestMonitor(this);
    }

    public void updateRawMouseInput(boolean enableRawMouseMotion) {
        InputConstants.updateRawMouseInput(this.window, enableRawMouseMotion);
    }

    @OnlyIn(Dist.CLIENT)
    public static class WindowInitFailed extends SilentInitException {
        WindowInitFailed(String p_85455_) {
            super(p_85455_);
        }
    }
}
