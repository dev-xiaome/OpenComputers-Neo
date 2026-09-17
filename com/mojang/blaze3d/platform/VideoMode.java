package com.mojang.blaze3d.platform;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.glfw.GLFWVidMode.Buffer;

@OnlyIn(Dist.CLIENT)
public final class VideoMode {
    private final int width;
    private final int height;
    private final int redBits;
    private final int greenBits;
    private final int blueBits;
    private final int refreshRate;
    private static final Pattern PATTERN = Pattern.compile("(\\d+)x(\\d+)(?:@(\\d+)(?::(\\d+))?)?");

    public VideoMode(int width, int height, int redBits, int greenBits, int blueBits, int refreshRate) {
        this.width = width;
        this.height = height;
        this.redBits = redBits;
        this.greenBits = greenBits;
        this.blueBits = blueBits;
        this.refreshRate = refreshRate;
    }

    public VideoMode(Buffer bufferVideoMode) {
        this.width = bufferVideoMode.width();
        this.height = bufferVideoMode.height();
        this.redBits = bufferVideoMode.redBits();
        this.greenBits = bufferVideoMode.greenBits();
        this.blueBits = bufferVideoMode.blueBits();
        this.refreshRate = bufferVideoMode.refreshRate();
    }

    public VideoMode(GLFWVidMode glfwVideoMode) {
        this.width = glfwVideoMode.width();
        this.height = glfwVideoMode.height();
        this.redBits = glfwVideoMode.redBits();
        this.greenBits = glfwVideoMode.greenBits();
        this.blueBits = glfwVideoMode.blueBits();
        this.refreshRate = glfwVideoMode.refreshRate();
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public int getRedBits() {
        return this.redBits;
    }

    public int getGreenBits() {
        return this.greenBits;
    }

    public int getBlueBits() {
        return this.blueBits;
    }

    public int getRefreshRate() {
        return this.refreshRate;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (other != null && this.getClass() == other.getClass()) {
            VideoMode videomode = (VideoMode)other;
            return this.width == videomode.width
                && this.height == videomode.height
                && this.redBits == videomode.redBits
                && this.greenBits == videomode.greenBits
                && this.blueBits == videomode.blueBits
                && this.refreshRate == videomode.refreshRate;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.width, this.height, this.redBits, this.greenBits, this.blueBits, this.refreshRate);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%sx%s@%s (%sbit)", this.width, this.height, this.refreshRate, this.redBits + this.greenBits + this.blueBits);
    }

    public static Optional<VideoMode> read(@Nullable String videoMode) {
        if (videoMode == null) {
            return Optional.empty();
        } else {
            try {
                Matcher matcher = PATTERN.matcher(videoMode);
                if (matcher.matches()) {
                    int i = Integer.parseInt(matcher.group(1));
                    int j = Integer.parseInt(matcher.group(2));
                    String s = matcher.group(3);
                    int k;
                    if (s == null) {
                        k = 60;
                    } else {
                        k = Integer.parseInt(s);
                    }

                    String s1 = matcher.group(4);
                    int l;
                    if (s1 == null) {
                        l = 24;
                    } else {
                        l = Integer.parseInt(s1);
                    }

                    int i1 = l / 3;
                    return Optional.of(new VideoMode(i, j, i1, i1, i1, k));
                }
            } catch (Exception exception) {
            }

            return Optional.empty();
        }
    }

    public String write() {
        return String.format(Locale.ROOT, "%sx%s@%s:%s", this.width, this.height, this.refreshRate, this.redBits + this.greenBits + this.blueBits);
    }
}
