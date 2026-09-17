package net.minecraft.client.gui.components;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class TabButton extends AbstractWidget {
    private static final WidgetSprites SPRITES = new WidgetSprites(
        ResourceLocation.withDefaultNamespace("widget/tab_selected"),
        ResourceLocation.withDefaultNamespace("widget/tab"),
        ResourceLocation.withDefaultNamespace("widget/tab_selected_highlighted"),
        ResourceLocation.withDefaultNamespace("widget/tab_highlighted")
    );
    private static final int SELECTED_OFFSET = 3;
    private static final int TEXT_MARGIN = 1;
    private static final int UNDERLINE_HEIGHT = 1;
    private static final int UNDERLINE_MARGIN_X = 4;
    private static final int UNDERLINE_MARGIN_BOTTOM = 2;
    private final TabManager tabManager;
    private final Tab tab;

    public TabButton(TabManager tabManager, Tab tab, int width, int height) {
        super(0, 0, width, height, tab.getTabTitle());
        this.tabManager = tabManager;
        this.tab = tab;
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        RenderSystem.enableBlend();
        guiGraphics.blitSprite(SPRITES.get(this.isSelected(), this.isHoveredOrFocused()), this.getX(), this.getY(), this.width, this.height);
        RenderSystem.disableBlend();
        Font font = Minecraft.getInstance().font;
        int i = this.active ? -1 : -6250336;
        this.renderString(guiGraphics, font, i);
        if (this.isSelected()) {
            this.renderMenuBackground(guiGraphics, this.getX() + 2, this.getY() + 2, this.getRight() - 2, this.getBottom());
            this.renderFocusUnderline(guiGraphics, font, i);
        }
    }

    protected void renderMenuBackground(GuiGraphics guiGraphics, int minX, int minY, int maxX, int maxY) {
        Screen.renderMenuBackgroundTexture(guiGraphics, Screen.MENU_BACKGROUND, minX, minY, 0.0F, 0.0F, maxX - minX, maxY - minY);
    }

    public void renderString(GuiGraphics guiGraphics, Font font, int color) {
        int i = this.getX() + 1;
        int j = this.getY() + (this.isSelected() ? 0 : 3);
        int k = this.getX() + this.getWidth() - 1;
        int l = this.getY() + this.getHeight();
        renderScrollingString(guiGraphics, font, this.getMessage(), i, j, k, l, color);
    }

    private void renderFocusUnderline(GuiGraphics guiGraphics, Font font, int color) {
        int i = Math.min(font.width(this.getMessage()), this.getWidth() - 4);
        int j = this.getX() + (this.getWidth() - i) / 2;
        int k = this.getY() + this.getHeight() - 2;
        guiGraphics.fill(j, k, j + i, k + 1, color);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        narrationElementOutput.add(NarratedElementType.TITLE, Component.translatable("gui.narrate.tab", this.tab.getTabTitle()));
    }

    @Override
    public void playDownSound(SoundManager handler) {
    }

    public Tab tab() {
        return this.tab;
    }

    public boolean isSelected() {
        return this.tabManager.getCurrentTab() == this.tab;
    }
}
