package net.minecraft.client.gui.screens.inventory.tooltip;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.tooltip.BundleTooltip;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public interface ClientTooltipComponent {
    static ClientTooltipComponent create(FormattedCharSequence text) {
        return new ClientTextTooltip(text);
    }

    static ClientTooltipComponent create(TooltipComponent visualTooltipComponent) {
        if (visualTooltipComponent instanceof BundleTooltip bundletooltip) {
            return new ClientBundleTooltip(bundletooltip.contents());
        } else if (visualTooltipComponent instanceof ClientActivePlayersTooltip.ActivePlayersTooltip clientactiveplayerstooltip$activeplayerstooltip) {
            return new ClientActivePlayersTooltip(clientactiveplayerstooltip$activeplayerstooltip);
        } else {
            ClientTooltipComponent result = net.neoforged.neoforge.client.gui.ClientTooltipComponentManager.createClientTooltipComponent(visualTooltipComponent);
            if (result != null) return result;
            throw new IllegalArgumentException("Unknown TooltipComponent");
        }
    }

    int getHeight();

    int getWidth(Font font);

    default void renderText(Font font, int mouseX, int mouseY, Matrix4f matrix, MultiBufferSource.BufferSource bufferSource) {
    }

    default void renderImage(Font font, int x, int y, GuiGraphics guiGraphics) {
    }
}
