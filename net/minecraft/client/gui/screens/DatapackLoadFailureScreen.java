package net.minecraft.client.gui.screens;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class DatapackLoadFailureScreen extends Screen {
    private MultiLineLabel message = MultiLineLabel.EMPTY;
    private final Runnable cancelCallback;
    private final Runnable safeModeCallback;

    public DatapackLoadFailureScreen(Runnable cancelCallback, Runnable safeModeCallback) {
        super(Component.translatable("datapackFailure.title"));
        this.cancelCallback = cancelCallback;
        this.safeModeCallback = safeModeCallback;
    }

    @Override
    protected void init() {
        super.init();
        this.message = MultiLineLabel.create(this.font, this.getTitle(), this.width - 50);
        this.addRenderableWidget(
            Button.builder(Component.translatable("datapackFailure.safeMode"), p_307041_ -> this.safeModeCallback.run())
                .bounds(this.width / 2 - 155, this.height / 6 + 96, 150, 20)
                .build()
        );
        this.addRenderableWidget(
            Button.builder(CommonComponents.GUI_BACK, p_307042_ -> this.cancelCallback.run())
                .bounds(this.width / 2 - 155 + 160, this.height / 6 + 96, 150, 20)
                .build()
        );
    }

    /**
     * Renders the graphical user interface (GUI) element.
     *
     * @param guiGraphics the GuiGraphics object used for rendering.
     * @param mouseX      the x-coordinate of the mouse cursor.
     * @param mouseY      the y-coordinate of the mouse cursor.
     * @param partialTick the partial tick time.
     */
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.message.renderCentered(guiGraphics, this.width / 2, 70);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
