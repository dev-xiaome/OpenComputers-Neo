package net.minecraft.client.gui.components.events;

import javax.annotation.Nullable;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class AbstractContainerEventHandler implements ContainerEventHandler {
    @Nullable
    private GuiEventListener focused;
    private boolean isDragging;

    @Override
    public final boolean isDragging() {
        return this.isDragging;
    }

    /**
     * Sets if the GUI element is dragging or not.
     *
     * @param dragging the dragging state of the GUI element.
     */
    @Override
    public final void setDragging(boolean dragging) {
        this.isDragging = dragging;
    }

    @Nullable
    @Override
    public GuiEventListener getFocused() {
        return this.focused;
    }

    /**
     * Sets the focus state of the GUI element.
     *
     * @param listener the focused GUI element.
     */
    @Override
    public void setFocused(@Nullable GuiEventListener listener) {
        if (this.focused != null) {
            this.focused.setFocused(false);
        }

        if (listener != null) {
            listener.setFocused(true);
        }

        this.focused = listener;
    }
}
