package net.minecraft.client.gui.narration;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * An interface for providing narration information.
 */
@OnlyIn(Dist.CLIENT)
public interface NarrationSupplier {
    /**
     * Updates the narration output with the current narration information.
     *
     * @param narrationElementOutput the output to update with narration information.
     */
    void updateNarration(NarrationElementOutput narrationElementOutput);
}
