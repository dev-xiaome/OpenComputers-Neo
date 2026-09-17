package net.minecraft.client.gui.screens.inventory.tooltip;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector2i;
import org.joml.Vector2ic;

@OnlyIn(Dist.CLIENT)
public class BelowOrAboveWidgetTooltipPositioner implements ClientTooltipPositioner {
    private final ScreenRectangle screenRectangle;

    public BelowOrAboveWidgetTooltipPositioner(ScreenRectangle screenRectangle) {
        this.screenRectangle = screenRectangle;
    }

    @Override
    public Vector2ic positionTooltip(int screenWidth, int screenHeight, int mouseX, int mouseY, int tooltipWidth, int tooltipHeight) {
        Vector2i vector2i = new Vector2i();
        vector2i.x = this.screenRectangle.left() + 3;
        vector2i.y = this.screenRectangle.bottom() + 3 + 1;
        if (vector2i.y + tooltipHeight + 3 > screenHeight) {
            vector2i.y = this.screenRectangle.top() - tooltipHeight - 3 - 1;
        }

        if (vector2i.x + tooltipWidth > screenWidth) {
            vector2i.x = Math.max(this.screenRectangle.right() - tooltipWidth - 3, 4);
        }

        return vector2i;
    }
}
