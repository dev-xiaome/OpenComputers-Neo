package net.minecraft.client.gui.components;

import java.util.function.Consumer;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MultiLineEditBox extends AbstractScrollWidget {
    private static final int CURSOR_INSERT_WIDTH = 1;
    private static final int CURSOR_INSERT_COLOR = -3092272;
    private static final String CURSOR_APPEND_CHARACTER = "_";
    private static final int TEXT_COLOR = -2039584;
    private static final int PLACEHOLDER_TEXT_COLOR = -857677600;
    private static final int CURSOR_BLINK_INTERVAL_MS = 300;
    private final Font font;
    private final Component placeholder;
    private final MultilineTextField textField;
    private long focusedTime = Util.getMillis();

    public MultiLineEditBox(Font font, int x, int y, int width, int height, Component placeholder, Component message) {
        super(x, y, width, height, message);
        this.font = font;
        this.placeholder = placeholder;
        this.textField = new MultilineTextField(font, width - this.totalInnerPadding());
        this.textField.setCursorListener(this::scrollToCursor);
    }

    public void setCharacterLimit(int characterLimit) {
        this.textField.setCharacterLimit(characterLimit);
    }

    public void setValueListener(Consumer<String> valueListener) {
        this.textField.setValueListener(valueListener);
    }

    public void setValue(String fullText) {
        this.textField.setValue(fullText);
    }

    public String getValue() {
        return this.textField.value();
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        narrationElementOutput.add(NarratedElementType.TITLE, Component.translatable("gui.narrate.editBox", this.getMessage(), this.getValue()));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.withinContentAreaPoint(mouseX, mouseY) && button == 0) {
            this.textField.setSelecting(Screen.hasShiftDown());
            this.seekCursorScreen(mouseX, mouseY);
            return true;
        } else {
            return super.mouseClicked(mouseX, mouseY, button);
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (super.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        } else if (this.withinContentAreaPoint(mouseX, mouseY) && button == 0) {
            this.textField.setSelecting(true);
            this.seekCursorScreen(mouseX, mouseY);
            this.textField.setSelecting(Screen.hasShiftDown());
            return true;
        } else {
            return false;
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
        return this.textField.keyPressed(keyCode);
    }

    /**
     * Called when a character is typed within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param codePoint the code point of the typed character.
     * @param modifiers the keyboard modifiers.
     */
    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.visible && this.isFocused() && StringUtil.isAllowedChatCharacter(codePoint)) {
            this.textField.insertText(Character.toString(codePoint));
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected void renderContents(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        String s = this.textField.value();
        if (s.isEmpty() && !this.isFocused()) {
            guiGraphics.drawWordWrap(
                this.font,
                this.placeholder,
                this.getX() + this.innerPadding(),
                this.getY() + this.innerPadding(),
                this.width - this.totalInnerPadding(),
                -857677600
            );
        } else {
            int i = this.textField.cursor();
            boolean flag = this.isFocused() && (Util.getMillis() - this.focusedTime) / 300L % 2L == 0L;
            boolean flag1 = i < s.length();
            int j = 0;
            int k = 0;
            int l = this.getY() + this.innerPadding();

            for (MultilineTextField.StringView multilinetextfield$stringview : this.textField.iterateLines()) {
                boolean flag2 = this.withinContentAreaTopBottom(l, l + 9);
                if (flag && flag1 && i >= multilinetextfield$stringview.beginIndex() && i <= multilinetextfield$stringview.endIndex()) {
                    if (flag2) {
                        j = guiGraphics.drawString(
                                this.font, s.substring(multilinetextfield$stringview.beginIndex(), i), this.getX() + this.innerPadding(), l, -2039584
                            )
                            - 1;
                        guiGraphics.fill(j, l - 1, j + 1, l + 1 + 9, -3092272);
                        guiGraphics.drawString(this.font, s.substring(i, multilinetextfield$stringview.endIndex()), j, l, -2039584);
                    }
                } else {
                    if (flag2) {
                        j = guiGraphics.drawString(
                                this.font,
                                s.substring(multilinetextfield$stringview.beginIndex(), multilinetextfield$stringview.endIndex()),
                                this.getX() + this.innerPadding(),
                                l,
                                -2039584
                            )
                            - 1;
                    }

                    k = l;
                }

                l += 9;
            }

            if (flag && !flag1 && this.withinContentAreaTopBottom(k, k + 9)) {
                guiGraphics.drawString(this.font, "_", j, k, -3092272);
            }

            if (this.textField.hasSelection()) {
                MultilineTextField.StringView multilinetextfield$stringview2 = this.textField.getSelected();
                int k1 = this.getX() + this.innerPadding();
                l = this.getY() + this.innerPadding();

                for (MultilineTextField.StringView multilinetextfield$stringview1 : this.textField.iterateLines()) {
                    if (multilinetextfield$stringview2.beginIndex() > multilinetextfield$stringview1.endIndex()) {
                        l += 9;
                    } else {
                        if (multilinetextfield$stringview1.beginIndex() > multilinetextfield$stringview2.endIndex()) {
                            break;
                        }

                        if (this.withinContentAreaTopBottom(l, l + 9)) {
                            int i1 = this.font
                                .width(
                                    s.substring(
                                        multilinetextfield$stringview1.beginIndex(),
                                        Math.max(multilinetextfield$stringview2.beginIndex(), multilinetextfield$stringview1.beginIndex())
                                    )
                                );
                            int j1;
                            if (multilinetextfield$stringview2.endIndex() > multilinetextfield$stringview1.endIndex()) {
                                j1 = this.width - this.innerPadding();
                            } else {
                                j1 = this.font.width(s.substring(multilinetextfield$stringview1.beginIndex(), multilinetextfield$stringview2.endIndex()));
                            }

                            this.renderHighlight(guiGraphics, k1 + i1, l, k1 + j1, l + 9);
                        }

                        l += 9;
                    }
                }
            }
        }
    }

    @Override
    protected void renderDecorations(GuiGraphics guiGraphics) {
        super.renderDecorations(guiGraphics);
        if (this.textField.hasCharacterLimit()) {
            int i = this.textField.characterLimit();
            Component component = Component.translatable("gui.multiLineEditBox.character_limit", this.textField.value().length(), i);
            guiGraphics.drawString(this.font, component, this.getX() + this.width - this.font.width(component), this.getY() + this.height + 4, 10526880);
        }
    }

    @Override
    public int getInnerHeight() {
        return 9 * this.textField.getLineCount();
    }

    @Override
    protected boolean scrollbarVisible() {
        return (double)this.textField.getLineCount() > this.getDisplayableLineCount();
    }

    @Override
    protected double scrollRate() {
        return 9.0 / 2.0;
    }

    private void renderHighlight(GuiGraphics guiGraphics, int minX, int minY, int maxX, int maxY) {
        guiGraphics.fill(RenderType.guiTextHighlight(), minX, minY, maxX, maxY, -16776961);
    }

    private void scrollToCursor() {
        double d0 = this.scrollAmount();
        MultilineTextField.StringView multilinetextfield$stringview = this.textField.getLineView((int)(d0 / 9.0));
        if (this.textField.cursor() <= multilinetextfield$stringview.beginIndex()) {
            d0 = (double)(this.textField.getLineAtCursor() * 9);
        } else {
            MultilineTextField.StringView multilinetextfield$stringview1 = this.textField.getLineView((int)((d0 + (double)this.height) / 9.0) - 1);
            if (this.textField.cursor() > multilinetextfield$stringview1.endIndex()) {
                d0 = (double)(this.textField.getLineAtCursor() * 9 - this.height + 9 + this.totalInnerPadding());
            }
        }

        this.setScrollAmount(d0);
    }

    private double getDisplayableLineCount() {
        return (double)(this.height - this.totalInnerPadding()) / 9.0;
    }

    private void seekCursorScreen(double mouseX, double mouseY) {
        double d0 = mouseX - (double)this.getX() - (double)this.innerPadding();
        double d1 = mouseY - (double)this.getY() - (double)this.innerPadding() + this.scrollAmount();
        this.textField.seekCursorToPoint(d0, d1);
    }

    /**
     * Sets the focus state of the GUI element.
     *
     * @param focused {@code true} to apply focus, {@code false} to remove focus
     */
    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (focused) {
            this.focusedTime = Util.getMillis();
        }
    }
}
