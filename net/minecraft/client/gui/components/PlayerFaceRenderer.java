package net.minecraft.client.gui.components;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class PlayerFaceRenderer {
    public static final int SKIN_HEAD_U = 8;
    public static final int SKIN_HEAD_V = 8;
    public static final int SKIN_HEAD_WIDTH = 8;
    public static final int SKIN_HEAD_HEIGHT = 8;
    public static final int SKIN_HAT_U = 40;
    public static final int SKIN_HAT_V = 8;
    public static final int SKIN_HAT_WIDTH = 8;
    public static final int SKIN_HAT_HEIGHT = 8;
    public static final int SKIN_TEX_WIDTH = 64;
    public static final int SKIN_TEX_HEIGHT = 64;

    public static void draw(GuiGraphics guiGraphics, PlayerSkin skin, int x, int y, int size) {
        draw(guiGraphics, skin.texture(), x, y, size);
    }

    public static void draw(GuiGraphics guiGraphics, ResourceLocation atlasLocation, int x, int y, int size) {
        draw(guiGraphics, atlasLocation, x, y, size, true, false);
    }

    public static void draw(
        GuiGraphics guiGraphics, ResourceLocation atlasLocation, int x, int y, int size, boolean p_drawHat, boolean upsideDown
    ) {
        int i = 8 + (upsideDown ? 8 : 0);
        int j = 8 * (upsideDown ? -1 : 1);
        guiGraphics.blit(atlasLocation, x, y, size, size, 8.0F, (float)i, 8, j, 64, 64);
        if (p_drawHat) {
            drawHat(guiGraphics, atlasLocation, x, y, size, upsideDown);
        }
    }

    private static void drawHat(GuiGraphics guiGraphics, ResourceLocation atlasLocation, int x, int y, int size, boolean upsideDown) {
        int i = 8 + (upsideDown ? 8 : 0);
        int j = 8 * (upsideDown ? -1 : 1);
        RenderSystem.enableBlend();
        guiGraphics.blit(atlasLocation, x, y, size, size, 40.0F, (float)i, 8, j, 64, 64);
        RenderSystem.disableBlend();
    }
}
