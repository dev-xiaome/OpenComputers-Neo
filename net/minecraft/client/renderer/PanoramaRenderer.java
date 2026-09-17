package net.minecraft.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class PanoramaRenderer {
    public static final ResourceLocation PANORAMA_OVERLAY = ResourceLocation.withDefaultNamespace("textures/gui/title/background/panorama_overlay.png");
    private final Minecraft minecraft;
    private final CubeMap cubeMap;
    private float spin;
    private float bob;

    public PanoramaRenderer(CubeMap cubeMap) {
        this.cubeMap = cubeMap;
        this.minecraft = Minecraft.getInstance();
    }

    public void render(GuiGraphics guiGraphics, int width, int height, float fade, float partialTick) {
        // Neo: as we fix MC-273464, the partial tick passed into the method is now based on game time rather than
        // real time causing the panorama to flicker in speed. See https://github.com/neoforged/NeoForge/issues/2362
        // We however preserve partial tick values of zero as the AccesibilityOnboardingScreen renders a static panorama
        partialTick = partialTick == 0F ? 0F : this.minecraft.getTimer().getRealtimeDeltaTicks();
        float f = (float)((double)partialTick * this.minecraft.options.panoramaSpeed().get());
        this.spin = wrap(this.spin + f * 0.1F, 360.0F);
        this.bob = wrap(this.bob + f * 0.001F, (float) (Math.PI * 2));
        this.cubeMap.render(this.minecraft, 10.0F, -this.spin, fade);
        RenderSystem.enableBlend();
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, fade);
        guiGraphics.blit(PANORAMA_OVERLAY, 0, 0, width, height, 0.0F, 0.0F, 16, 128, 16, 128);
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        // Neo: disable depth test again to prevent issues with extended far plane values for screen layers and HUD layers
        RenderSystem.disableDepthTest();
    }

    private static float wrap(float value, float max) {
        return value > max ? value - max : value;
    }
}
