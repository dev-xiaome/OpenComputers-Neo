package net.minecraft.client.gui.screens.packs;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class TransferableSelectionList extends ObjectSelectionList<TransferableSelectionList.PackEntry> {
    static final ResourceLocation SELECT_HIGHLIGHTED_SPRITE = ResourceLocation.withDefaultNamespace("transferable_list/select_highlighted");
    static final ResourceLocation SELECT_SPRITE = ResourceLocation.withDefaultNamespace("transferable_list/select");
    static final ResourceLocation UNSELECT_HIGHLIGHTED_SPRITE = ResourceLocation.withDefaultNamespace("transferable_list/unselect_highlighted");
    static final ResourceLocation UNSELECT_SPRITE = ResourceLocation.withDefaultNamespace("transferable_list/unselect");
    static final ResourceLocation MOVE_UP_HIGHLIGHTED_SPRITE = ResourceLocation.withDefaultNamespace("transferable_list/move_up_highlighted");
    static final ResourceLocation MOVE_UP_SPRITE = ResourceLocation.withDefaultNamespace("transferable_list/move_up");
    static final ResourceLocation MOVE_DOWN_HIGHLIGHTED_SPRITE = ResourceLocation.withDefaultNamespace("transferable_list/move_down_highlighted");
    static final ResourceLocation MOVE_DOWN_SPRITE = ResourceLocation.withDefaultNamespace("transferable_list/move_down");
    static final Component INCOMPATIBLE_TITLE = Component.translatable("pack.incompatible");
    static final Component INCOMPATIBLE_CONFIRM_TITLE = Component.translatable("pack.incompatible.confirm.title");
    private final Component title;
    final PackSelectionScreen screen;

    public TransferableSelectionList(Minecraft minecraft, PackSelectionScreen screen, int width, int height, Component title) {
        super(minecraft, width, height, 33, 36);
        this.screen = screen;
        this.title = title;
        this.centerListVertically = false;
        this.setRenderHeader(true, (int)(9.0F * 1.5F));
    }

    @Override
    protected void renderHeader(GuiGraphics guiGraphics, int x, int y) {
        Component component = Component.empty().append(this.title).withStyle(ChatFormatting.UNDERLINE, ChatFormatting.BOLD);
        guiGraphics.drawString(
            this.minecraft.font,
            component,
            x + this.width / 2 - this.minecraft.font.width(component) / 2,
            Math.min(this.getY() + 3, y),
            -1,
            false
        );
    }

    @Override
    public int getRowWidth() {
        return this.width;
    }

    @Override
    protected int getScrollbarPosition() {
        return this.getRight() - 6;
    }

    @Override
    protected void renderSelection(GuiGraphics guiGraphics, int top, int width, int height, int outerColor, int innerColor) {
        if (this.scrollbarVisible()) {
            int i = 2;
            int j = this.getRowLeft() - 2;
            int k = this.getRight() - 6 - 1;
            int l = top - 2;
            int i1 = top + height + 2;
            guiGraphics.fill(j, l, k, i1, outerColor);
            guiGraphics.fill(j + 1, l + 1, k - 1, i1 - 1, innerColor);
        } else {
            super.renderSelection(guiGraphics, top, width, height, outerColor, innerColor);
        }
    }

    /**
     * Called when a keyboard key is pressed within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param keyCode   the key code of the pressed key.
     * @param scanCode  the scan code of the pressed key.
     * @param modifiers the keyboard modifiers.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.getSelected() != null) {
            switch (keyCode) {
                case 32:
                case 257:
                    this.getSelected().keyboardSelection();
                    return true;
                default:
                    if (Screen.hasShiftDown()) {
                        switch (keyCode) {
                            case 264:
                                this.getSelected().keyboardMoveDown();
                                return true;
                            case 265:
                                this.getSelected().keyboardMoveUp();
                                return true;
                        }
                    }
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @OnlyIn(Dist.CLIENT)
    public static class PackEntry extends ObjectSelectionList.Entry<TransferableSelectionList.PackEntry> {
        private static final int MAX_DESCRIPTION_WIDTH_PIXELS = 157;
        private static final int MAX_NAME_WIDTH_PIXELS = 157;
        private static final String TOO_LONG_NAME_SUFFIX = "...";
        private final TransferableSelectionList parent;
        protected final Minecraft minecraft;
        private final PackSelectionModel.Entry pack;
        private final FormattedCharSequence nameDisplayCache;
        private final MultiLineLabel descriptionDisplayCache;
        private final FormattedCharSequence incompatibleNameDisplayCache;
        private final MultiLineLabel incompatibleDescriptionDisplayCache;

        public PackEntry(Minecraft minecraft, TransferableSelectionList parent, PackSelectionModel.Entry pack) {
            this.minecraft = minecraft;
            this.pack = pack;
            this.parent = parent;
            this.nameDisplayCache = cacheName(minecraft, pack.getTitle());
            this.descriptionDisplayCache = cacheDescription(minecraft, pack.getExtendedDescription());
            this.incompatibleNameDisplayCache = cacheName(minecraft, TransferableSelectionList.INCOMPATIBLE_TITLE);
            this.incompatibleDescriptionDisplayCache = cacheDescription(minecraft, pack.getCompatibility().getDescription());
        }

        private static FormattedCharSequence cacheName(Minecraft minecraft, Component name) {
            int i = minecraft.font.width(name);
            if (i > 157) {
                FormattedText formattedtext = FormattedText.composite(
                    minecraft.font.substrByWidth(name, 157 - minecraft.font.width("...")), FormattedText.of("...")
                );
                return Language.getInstance().getVisualOrder(formattedtext);
            } else {
                return name.getVisualOrderText();
            }
        }

        private static MultiLineLabel cacheDescription(Minecraft minecraft, Component text) {
            return MultiLineLabel.create(minecraft.font, 157, 2, text);
        }

        @Override
        public Component getNarration() {
            return Component.translatable("narrator.select", this.pack.getTitle());
        }

        @Override
        public void render(
            GuiGraphics guiGraphics,
            int index,
            int top,
            int left,
            int width,
            int height,
            int mouseX,
            int mouseY,
            boolean hovering,
            float partialTick
        ) {
            PackCompatibility packcompatibility = this.pack.getCompatibility();
            if (!packcompatibility.isCompatible()) {
                int i = left + width - 3 - (this.parent.scrollbarVisible() ? 7 : 0);
                guiGraphics.fill(left - 1, top - 1, i, top + height + 1, -8978432);
            }

            guiGraphics.blit(this.pack.getIconTexture(), left, top, 0.0F, 0.0F, 32, 32, 32, 32);
            FormattedCharSequence formattedcharsequence = this.nameDisplayCache;
            MultiLineLabel multilinelabel = this.descriptionDisplayCache;
            if (this.showHoverOverlay()
                && (this.minecraft.options.touchscreen().get() || hovering || this.parent.getSelected() == this && this.parent.isFocused())) {
                guiGraphics.fill(left, top, left + 32, top + 32, -1601138544);
                int j = mouseX - left;
                int k = mouseY - top;
                if (!this.pack.getCompatibility().isCompatible()) {
                    formattedcharsequence = this.incompatibleNameDisplayCache;
                    multilinelabel = this.incompatibleDescriptionDisplayCache;
                }

                if (this.pack.canSelect()) {
                    if (j < 32) {
                        guiGraphics.blitSprite(TransferableSelectionList.SELECT_HIGHLIGHTED_SPRITE, left, top, 32, 32);
                    } else {
                        guiGraphics.blitSprite(TransferableSelectionList.SELECT_SPRITE, left, top, 32, 32);
                    }
                } else {
                    if (this.pack.canUnselect()) {
                        if (j < 16) {
                            guiGraphics.blitSprite(TransferableSelectionList.UNSELECT_HIGHLIGHTED_SPRITE, left, top, 32, 32);
                        } else {
                            guiGraphics.blitSprite(TransferableSelectionList.UNSELECT_SPRITE, left, top, 32, 32);
                        }
                    }

                    if (this.pack.canMoveUp()) {
                        if (j < 32 && j > 16 && k < 16) {
                            guiGraphics.blitSprite(TransferableSelectionList.MOVE_UP_HIGHLIGHTED_SPRITE, left, top, 32, 32);
                        } else {
                            guiGraphics.blitSprite(TransferableSelectionList.MOVE_UP_SPRITE, left, top, 32, 32);
                        }
                    }

                    if (this.pack.canMoveDown()) {
                        if (j < 32 && j > 16 && k > 16) {
                            guiGraphics.blitSprite(TransferableSelectionList.MOVE_DOWN_HIGHLIGHTED_SPRITE, left, top, 32, 32);
                        } else {
                            guiGraphics.blitSprite(TransferableSelectionList.MOVE_DOWN_SPRITE, left, top, 32, 32);
                        }
                    }
                }
            }

            guiGraphics.drawString(this.minecraft.font, formattedcharsequence, left + 32 + 2, top + 1, 16777215);
            multilinelabel.renderLeftAligned(guiGraphics, left + 32 + 2, top + 12, 10, -8355712);
        }

        public String getPackId() {
            return this.pack.getId();
        }

        private boolean showHoverOverlay() {
            return !this.pack.isFixedPosition() || !this.pack.isRequired();
        }

        public void keyboardSelection() {
            if (this.pack.canSelect() && this.handlePackSelection()) {
                this.parent.screen.updateFocus(this.parent);
            } else if (this.pack.canUnselect()) {
                this.pack.unselect();
                this.parent.screen.updateFocus(this.parent);
            }
        }

        void keyboardMoveUp() {
            if (this.pack.canMoveUp()) {
                this.pack.moveUp();
            }
        }

        void keyboardMoveDown() {
            if (this.pack.canMoveDown()) {
                this.pack.moveDown();
            }
        }

        private boolean handlePackSelection() {
            if (this.pack.getCompatibility().isCompatible()) {
                this.pack.select();
                return true;
            } else {
                Component component = this.pack.getCompatibility().getConfirmation();
                this.minecraft.setScreen(new ConfirmScreen(p_264693_ -> {
                    this.minecraft.setScreen(this.parent.screen);
                    if (p_264693_) {
                        this.pack.select();
                    }
                }, TransferableSelectionList.INCOMPATIBLE_CONFIRM_TITLE, component));
                return false;
            }
        }

        /**
         * Called when a mouse button is clicked within the GUI element.
         * <p>
         * @return {@code true} if the event is consumed, {@code false} otherwise.
         *
         * @param mouseX the X coordinate of the mouse.
         * @param mouseY the Y coordinate of the mouse.
         * @param button the button that was clicked.
         */
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            double d0 = mouseX - (double)this.parent.getRowLeft();
            double d1 = mouseY - (double)this.parent.getRowTop(this.parent.children().indexOf(this));
            if (this.showHoverOverlay() && d0 <= 32.0) {
                this.parent.screen.clearSelected();
                if (this.pack.canSelect()) {
                    this.handlePackSelection();
                    return true;
                }

                if (d0 < 16.0 && this.pack.canUnselect()) {
                    this.pack.unselect();
                    return true;
                }

                if (d0 > 16.0 && d1 < 16.0 && this.pack.canMoveUp()) {
                    this.pack.moveUp();
                    return true;
                }

                if (d0 > 16.0 && d1 > 16.0 && this.pack.canMoveDown()) {
                    this.pack.moveDown();
                    return true;
                }
            }

            return super.mouseClicked(mouseX, mouseY, button);
        }
    }
}
