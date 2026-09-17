package net.minecraft.client.gui.components;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MultilineTextField {
    public static final int NO_CHARACTER_LIMIT = Integer.MAX_VALUE;
    private static final int LINE_SEEK_PIXEL_BIAS = 2;
    private final Font font;
    private final List<MultilineTextField.StringView> displayLines = Lists.newArrayList();
    private String value;
    private int cursor;
    private int selectCursor;
    private boolean selecting;
    private int characterLimit = Integer.MAX_VALUE;
    private final int width;
    private Consumer<String> valueListener = p_239235_ -> {
    };
    private Runnable cursorListener = () -> {
    };

    public MultilineTextField(Font font, int width) {
        this.font = font;
        this.width = width;
        this.setValue("");
    }

    public int characterLimit() {
        return this.characterLimit;
    }

    public void setCharacterLimit(int characterLimit) {
        if (characterLimit < 0) {
            throw new IllegalArgumentException("Character limit cannot be negative");
        } else {
            this.characterLimit = characterLimit;
        }
    }

    public boolean hasCharacterLimit() {
        return this.characterLimit != Integer.MAX_VALUE;
    }

    public void setValueListener(Consumer<String> valueListener) {
        this.valueListener = valueListener;
    }

    public void setCursorListener(Runnable cursorListener) {
        this.cursorListener = cursorListener;
    }

    public void setValue(String fullText) {
        this.value = this.truncateFullText(fullText);
        this.cursor = this.value.length();
        this.selectCursor = this.cursor;
        this.onValueChange();
    }

    public String value() {
        return this.value;
    }

    public void insertText(String text) {
        if (!text.isEmpty() || this.hasSelection()) {
            String s = this.truncateInsertionText(StringUtil.filterText(text, true));
            MultilineTextField.StringView multilinetextfield$stringview = this.getSelected();
            this.value = new StringBuilder(this.value).replace(multilinetextfield$stringview.beginIndex, multilinetextfield$stringview.endIndex, s).toString();
            this.cursor = multilinetextfield$stringview.beginIndex + s.length();
            this.selectCursor = this.cursor;
            this.onValueChange();
        }
    }

    public void deleteText(int length) {
        if (!this.hasSelection()) {
            this.selectCursor = Mth.clamp(this.cursor + length, 0, this.value.length());
        }

        this.insertText("");
    }

    public int cursor() {
        return this.cursor;
    }

    public void setSelecting(boolean selecting) {
        this.selecting = selecting;
    }

    public MultilineTextField.StringView getSelected() {
        return new MultilineTextField.StringView(Math.min(this.selectCursor, this.cursor), Math.max(this.selectCursor, this.cursor));
    }

    public int getLineCount() {
        return this.displayLines.size();
    }

    public int getLineAtCursor() {
        for (int i = 0; i < this.displayLines.size(); i++) {
            MultilineTextField.StringView multilinetextfield$stringview = this.displayLines.get(i);
            if (this.cursor >= multilinetextfield$stringview.beginIndex && this.cursor <= multilinetextfield$stringview.endIndex) {
                return i;
            }
        }

        return -1;
    }

    public MultilineTextField.StringView getLineView(int lineNumber) {
        return this.displayLines.get(Mth.clamp(lineNumber, 0, this.displayLines.size() - 1));
    }

    public void seekCursor(Whence whence, int position) {
        switch (whence) {
            case ABSOLUTE:
                this.cursor = position;
                break;
            case RELATIVE:
                this.cursor += position;
                break;
            case END:
                this.cursor = this.value.length() + position;
        }

        this.cursor = Mth.clamp(this.cursor, 0, this.value.length());
        this.cursorListener.run();
        if (!this.selecting) {
            this.selectCursor = this.cursor;
        }
    }

    public void seekCursorLine(int offset) {
        if (offset != 0) {
            int i = this.font.width(this.value.substring(this.getCursorLineView().beginIndex, this.cursor)) + 2;
            MultilineTextField.StringView multilinetextfield$stringview = this.getCursorLineView(offset);
            int j = this.font
                .plainSubstrByWidth(this.value.substring(multilinetextfield$stringview.beginIndex, multilinetextfield$stringview.endIndex), i)
                .length();
            this.seekCursor(Whence.ABSOLUTE, multilinetextfield$stringview.beginIndex + j);
        }
    }

    public void seekCursorToPoint(double x, double y) {
        int i = Mth.floor(x);
        int j = Mth.floor(y / 9.0);
        MultilineTextField.StringView multilinetextfield$stringview = this.displayLines.get(Mth.clamp(j, 0, this.displayLines.size() - 1));
        int k = this.font
            .plainSubstrByWidth(this.value.substring(multilinetextfield$stringview.beginIndex, multilinetextfield$stringview.endIndex), i)
            .length();
        this.seekCursor(Whence.ABSOLUTE, multilinetextfield$stringview.beginIndex + k);
    }

    public boolean keyPressed(int keyCode) {
        this.selecting = Screen.hasShiftDown();
        if (Screen.isSelectAll(keyCode)) {
            this.cursor = this.value.length();
            this.selectCursor = 0;
            return true;
        } else if (Screen.isCopy(keyCode)) {
            Minecraft.getInstance().keyboardHandler.setClipboard(this.getSelectedText());
            return true;
        } else if (Screen.isPaste(keyCode)) {
            this.insertText(Minecraft.getInstance().keyboardHandler.getClipboard());
            return true;
        } else if (Screen.isCut(keyCode)) {
            Minecraft.getInstance().keyboardHandler.setClipboard(this.getSelectedText());
            this.insertText("");
            return true;
        } else {
            switch (keyCode) {
                case 257:
                case 335:
                    this.insertText("\n");
                    return true;
                case 259:
                    if (Screen.hasControlDown()) {
                        MultilineTextField.StringView multilinetextfield$stringview3 = this.getPreviousWord();
                        this.deleteText(multilinetextfield$stringview3.beginIndex - this.cursor);
                    } else {
                        this.deleteText(-1);
                    }

                    return true;
                case 261:
                    if (Screen.hasControlDown()) {
                        MultilineTextField.StringView multilinetextfield$stringview2 = this.getNextWord();
                        this.deleteText(multilinetextfield$stringview2.beginIndex - this.cursor);
                    } else {
                        this.deleteText(1);
                    }

                    return true;
                case 262:
                    if (Screen.hasControlDown()) {
                        MultilineTextField.StringView multilinetextfield$stringview1 = this.getNextWord();
                        this.seekCursor(Whence.ABSOLUTE, multilinetextfield$stringview1.beginIndex);
                    } else {
                        this.seekCursor(Whence.RELATIVE, 1);
                    }

                    return true;
                case 263:
                    if (Screen.hasControlDown()) {
                        MultilineTextField.StringView multilinetextfield$stringview = this.getPreviousWord();
                        this.seekCursor(Whence.ABSOLUTE, multilinetextfield$stringview.beginIndex);
                    } else {
                        this.seekCursor(Whence.RELATIVE, -1);
                    }

                    return true;
                case 264:
                    if (!Screen.hasControlDown()) {
                        this.seekCursorLine(1);
                    }

                    return true;
                case 265:
                    if (!Screen.hasControlDown()) {
                        this.seekCursorLine(-1);
                    }

                    return true;
                case 266:
                    this.seekCursor(Whence.ABSOLUTE, 0);
                    return true;
                case 267:
                    this.seekCursor(Whence.END, 0);
                    return true;
                case 268:
                    if (Screen.hasControlDown()) {
                        this.seekCursor(Whence.ABSOLUTE, 0);
                    } else {
                        this.seekCursor(Whence.ABSOLUTE, this.getCursorLineView().beginIndex);
                    }

                    return true;
                case 269:
                    if (Screen.hasControlDown()) {
                        this.seekCursor(Whence.END, 0);
                    } else {
                        this.seekCursor(Whence.ABSOLUTE, this.getCursorLineView().endIndex);
                    }

                    return true;
                default:
                    return false;
            }
        }
    }

    public Iterable<MultilineTextField.StringView> iterateLines() {
        return this.displayLines;
    }

    public boolean hasSelection() {
        return this.selectCursor != this.cursor;
    }

    @VisibleForTesting
    public String getSelectedText() {
        MultilineTextField.StringView multilinetextfield$stringview = this.getSelected();
        return this.value.substring(multilinetextfield$stringview.beginIndex, multilinetextfield$stringview.endIndex);
    }

    private MultilineTextField.StringView getCursorLineView() {
        return this.getCursorLineView(0);
    }

    private MultilineTextField.StringView getCursorLineView(int offset) {
        int i = this.getLineAtCursor();
        if (i < 0) {
            throw new IllegalStateException("Cursor is not within text (cursor = " + this.cursor + ", length = " + this.value.length() + ")");
        } else {
            return this.displayLines.get(Mth.clamp(i + offset, 0, this.displayLines.size() - 1));
        }
    }

    @VisibleForTesting
    public MultilineTextField.StringView getPreviousWord() {
        if (this.value.isEmpty()) {
            return MultilineTextField.StringView.EMPTY;
        } else {
            int i = Mth.clamp(this.cursor, 0, this.value.length() - 1);

            while (i > 0 && Character.isWhitespace(this.value.charAt(i - 1))) {
                i--;
            }

            while (i > 0 && !Character.isWhitespace(this.value.charAt(i - 1))) {
                i--;
            }

            return new MultilineTextField.StringView(i, this.getWordEndPosition(i));
        }
    }

    @VisibleForTesting
    public MultilineTextField.StringView getNextWord() {
        if (this.value.isEmpty()) {
            return MultilineTextField.StringView.EMPTY;
        } else {
            int i = Mth.clamp(this.cursor, 0, this.value.length() - 1);

            while (i < this.value.length() && !Character.isWhitespace(this.value.charAt(i))) {
                i++;
            }

            while (i < this.value.length() && Character.isWhitespace(this.value.charAt(i))) {
                i++;
            }

            return new MultilineTextField.StringView(i, this.getWordEndPosition(i));
        }
    }

    private int getWordEndPosition(int cursor) {
        int i = cursor;

        while (i < this.value.length() && !Character.isWhitespace(this.value.charAt(i))) {
            i++;
        }

        return i;
    }

    private void onValueChange() {
        this.reflowDisplayLines();
        this.valueListener.accept(this.value);
        this.cursorListener.run();
    }

    private void reflowDisplayLines() {
        this.displayLines.clear();
        if (this.value.isEmpty()) {
            this.displayLines.add(MultilineTextField.StringView.EMPTY);
        } else {
            this.font
                .getSplitter()
                .splitLines(
                    this.value,
                    this.width,
                    Style.EMPTY,
                    false,
                    (p_239846_, p_239847_, p_239848_) -> this.displayLines.add(new MultilineTextField.StringView(p_239847_, p_239848_))
                );
            if (this.value.charAt(this.value.length() - 1) == '\n') {
                this.displayLines.add(new MultilineTextField.StringView(this.value.length(), this.value.length()));
            }
        }
    }

    private String truncateFullText(String fullText) {
        return this.hasCharacterLimit() ? StringUtil.truncateStringIfNecessary(fullText, this.characterLimit, false) : fullText;
    }

    private String truncateInsertionText(String text) {
        if (this.hasCharacterLimit()) {
            int i = this.characterLimit - this.value.length();
            return StringUtil.truncateStringIfNecessary(text, i, false);
        } else {
            return text;
        }
    }

    @OnlyIn(Dist.CLIENT)
    protected static record StringView(int beginIndex, int endIndex) {
        static final MultilineTextField.StringView EMPTY = new MultilineTextField.StringView(0, 0);
    }
}
