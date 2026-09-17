package com.mojang.realmsclient.gui.screens;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.realms.RealmsScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RealmsConfirmScreen extends RealmsScreen {
    protected BooleanConsumer callback;
    private final Component title1;
    private final Component title2;

    public RealmsConfirmScreen(BooleanConsumer callback, Component title1, Component title2) {
        super(GameNarrator.NO_TITLE);
        this.callback = callback;
        this.title1 = title1;
        this.title2 = title2;
    }

    @Override
    public void init() {
        this.addRenderableWidget(
            Button.builder(CommonComponents.GUI_YES, p_88562_ -> this.callback.accept(true)).bounds(this.width / 2 - 105, row(9), 100, 20).build()
        );
        this.addRenderableWidget(
            Button.builder(CommonComponents.GUI_NO, p_88559_ -> this.callback.accept(false)).bounds(this.width / 2 + 5, row(9), 100, 20).build()
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
        guiGraphics.drawCenteredString(this.font, this.title1, this.width / 2, row(3), -1);
        guiGraphics.drawCenteredString(this.font, this.title2, this.width / 2, row(5), -1);
    }
}
