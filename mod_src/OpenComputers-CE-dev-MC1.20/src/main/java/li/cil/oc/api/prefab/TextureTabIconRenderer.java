package li.cil.oc.api.prefab;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import li.cil.oc.api.manual.TabIconRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Simple implementation of a tab icon renderer using a full texture as its graphic.
 */
@SuppressWarnings("UnusedDeclaration")
public class TextureTabIconRenderer implements TabIconRenderer {
    private final ResourceLocation location;

    public TextureTabIconRenderer(ResourceLocation location) {
        this.location = location;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void render(GuiGraphics graphics) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        graphics.blit(location, 0, 0, 0, 0, 16, 16, 16, 16);
    }
}
