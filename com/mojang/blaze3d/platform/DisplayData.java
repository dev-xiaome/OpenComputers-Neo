package com.mojang.blaze3d.platform;

import java.util.OptionalInt;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class DisplayData {
    public final int width;
    public final int height;
    public final OptionalInt fullscreenWidth;
    public final OptionalInt fullscreenHeight;
    public final boolean isFullscreen;

    public DisplayData(int width, int height, OptionalInt fullscreenWidth, OptionalInt fullscreenHeight, boolean isFullscreen) {
        this.width = width;
        this.height = height;
        this.fullscreenWidth = fullscreenWidth;
        this.fullscreenHeight = fullscreenHeight;
        this.isFullscreen = isFullscreen;
    }
}
