package net.minecraft.client.gui.font;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class TextFieldHelper {
    private final Supplier<String> getMessageFn;
    private final Consumer<String> setMessageFn;
    private final Supplier<String> getClipboardFn;
    private final Consumer<String> setClipboardFn;
    private final Predicate<String> stringValidator;
    private int cursorPos;
    private int selectionPos;

    public TextFieldHelper(
        Supplier<String> getMessage, Consumer<String> setMessage, Supplier<String> getClipboard, Consumer<String> setClipboard, Predicate<String> stringValidator
    ) {
        this.getMessageFn = getMessage;
        this.setMessageFn = setMessage;
        this.getClipboardFn = getClipboard;
        this.setClipboardFn = setClipboard;
        this.stringValidator = stringValidator;
        this.setCursorToEnd();
    }

    public static Supplier<String> createClipboardGetter(Minecraft minecraft) {
        return () -> getClipboardContents(minecraft);
    }

    public static String getClipboardContents(Minecraft minecraft) {
        return ChatFormatting.stripFormatting(minecraft.keyboardHandler.getClipboard().replaceAll("\\r", ""));
    }

    public static Consumer<String> createClipboardSetter(Minecraft minecraft) {
        return p_95173_ -> setClipboardContents(minecraft, p_95173_);
    }

    public static void setClipboardContents(Minecraft minecraft, String text) {
        minecraft.keyboardHandler.setClipboard(text);
    }

    public boolean charTyped(char character) {
        if (StringUtil.isAllowedChatCharacter(character)) {
            this.insertText(this.getMessageFn.get(), Character.toString(character));
        }

        return true;
    }

    public boolean keyPressed(int key) {
        if (Screen.isSelectAll(key)) {
            this.selectAll();
            return true;
        } else if (Screen.isCopy(key)) {
            this.copy();
            return true;
        } else if (Screen.isPaste(key)) {
            this.paste();
            return true;
        } else if (Screen.isCut(key)) {
            this.cut();
            return true;
        } else {
            TextFieldHelper.CursorStep textfieldhelper$cursorstep = Screen.hasControlDown()
                ? TextFieldHelper.CursorStep.WORD
                : TextFieldHelper.CursorStep.CHARACTER;
            if (key == 259) {
                this.removeFromCursor(-1, textfieldhelper$cursorstep);
                return true;
            } else {
                if (key == 261) {
                    this.removeFromCursor(1, textfieldhelper$cursorstep);
                } else {
                    if (key == 263) {
                        this.moveBy(-1, Screen.hasShiftDown(), textfieldhelper$cursorstep);
                        return true;
                    }

                    if (key == 262) {
                        this.moveBy(1, Screen.hasShiftDown(), textfieldhelper$cursorstep);
                        return true;
                    }

                    if (key == 268) {
                        this.setCursorToStart(Screen.hasShiftDown());
                        return true;
                    }

                    if (key == 269) {
                        this.setCursorToEnd(Screen.hasShiftDown());
                        return true;
                    }
                }

                return false;
            }
        }
    }

    private int clampToMsgLength(int textIndex) {
        return Mth.clamp(textIndex, 0, this.getMessageFn.get().length());
    }

    private void insertText(String text, String clipboardText) {
        if (this.selectionPos != this.cursorPos) {
            text = this.deleteSelection(text);
        }

        this.cursorPos = Mth.clamp(this.cursorPos, 0, text.length());
        String s = new StringBuilder(text).insert(this.cursorPos, clipboardText).toString();
        if (this.stringValidator.test(s)) {
            this.setMessageFn.accept(s);
            this.selectionPos = this.cursorPos = Math.min(s.length(), this.cursorPos + clipboardText.length());
        }
    }

    public void insertText(String text) {
        this.insertText(this.getMessageFn.get(), text);
    }

    private void resetSelectionIfNeeded(boolean keepSelection) {
        if (!keepSelection) {
            this.selectionPos = this.cursorPos;
        }
    }

    public void moveBy(int direction, boolean keepSelection, TextFieldHelper.CursorStep cursorStep) {
        switch (cursorStep) {
            case CHARACTER:
                this.moveByChars(direction, keepSelection);
                break;
            case WORD:
                this.moveByWords(direction, keepSelection);
        }
    }

    public void moveByChars(int direction) {
        this.moveByChars(direction, false);
    }

    public void moveByChars(int direction, boolean keepSelection) {
        this.cursorPos = Util.offsetByCodepoints(this.getMessageFn.get(), this.cursorPos, direction);
        this.resetSelectionIfNeeded(keepSelection);
    }

    public void moveByWords(int direction) {
        this.moveByWords(direction, false);
    }

    public void moveByWords(int direction, boolean keepSelection) {
        this.cursorPos = StringSplitter.getWordPosition(this.getMessageFn.get(), direction, this.cursorPos, true);
        this.resetSelectionIfNeeded(keepSelection);
    }

    public void removeFromCursor(int direction, TextFieldHelper.CursorStep step) {
        switch (step) {
            case CHARACTER:
                this.removeCharsFromCursor(direction);
                break;
            case WORD:
                this.removeWordsFromCursor(direction);
        }
    }

    public void removeWordsFromCursor(int direction) {
        int i = StringSplitter.getWordPosition(this.getMessageFn.get(), direction, this.cursorPos, true);
        this.removeCharsFromCursor(i - this.cursorPos);
    }

    public void removeCharsFromCursor(int direction) {
        String s = this.getMessageFn.get();
        if (!s.isEmpty()) {
            String s1;
            if (this.selectionPos != this.cursorPos) {
                s1 = this.deleteSelection(s);
            } else {
                int i = Util.offsetByCodepoints(s, this.cursorPos, direction);
                int j = Math.min(i, this.cursorPos);
                int k = Math.max(i, this.cursorPos);
                s1 = new StringBuilder(s).delete(j, k).toString();
                if (direction < 0) {
                    this.selectionPos = this.cursorPos = j;
                }
            }

            this.setMessageFn.accept(s1);
        }
    }

    public void cut() {
        String s = this.getMessageFn.get();
        this.setClipboardFn.accept(this.getSelected(s));
        this.setMessageFn.accept(this.deleteSelection(s));
    }

    public void paste() {
        this.insertText(this.getMessageFn.get(), this.getClipboardFn.get());
        this.selectionPos = this.cursorPos;
    }

    public void copy() {
        this.setClipboardFn.accept(this.getSelected(this.getMessageFn.get()));
    }

    public void selectAll() {
        this.selectionPos = 0;
        this.cursorPos = this.getMessageFn.get().length();
    }

    private String getSelected(String text) {
        int i = Math.min(this.cursorPos, this.selectionPos);
        int j = Math.max(this.cursorPos, this.selectionPos);
        return text.substring(i, j);
    }

    private String deleteSelection(String text) {
        if (this.selectionPos == this.cursorPos) {
            return text;
        } else {
            int i = Math.min(this.cursorPos, this.selectionPos);
            int j = Math.max(this.cursorPos, this.selectionPos);
            String s = text.substring(0, i) + text.substring(j);
            this.selectionPos = this.cursorPos = i;
            return s;
        }
    }

    public void setCursorToStart() {
        this.setCursorToStart(false);
    }

    public void setCursorToStart(boolean keepSelection) {
        this.cursorPos = 0;
        this.resetSelectionIfNeeded(keepSelection);
    }

    public void setCursorToEnd() {
        this.setCursorToEnd(false);
    }

    public void setCursorToEnd(boolean keepSelection) {
        this.cursorPos = this.getMessageFn.get().length();
        this.resetSelectionIfNeeded(keepSelection);
    }

    public int getCursorPos() {
        return this.cursorPos;
    }

    public void setCursorPos(int textIndex) {
        this.setCursorPos(textIndex, true);
    }

    public void setCursorPos(int textIndex, boolean keepSelection) {
        this.cursorPos = this.clampToMsgLength(textIndex);
        this.resetSelectionIfNeeded(keepSelection);
    }

    public int getSelectionPos() {
        return this.selectionPos;
    }

    public void setSelectionPos(int textIndex) {
        this.selectionPos = this.clampToMsgLength(textIndex);
    }

    public void setSelectionRange(int selectionStart, int selectionEnd) {
        int i = this.getMessageFn.get().length();
        this.cursorPos = Mth.clamp(selectionStart, 0, i);
        this.selectionPos = Mth.clamp(selectionEnd, 0, i);
    }

    public boolean isSelecting() {
        return this.cursorPos != this.selectionPos;
    }

    @OnlyIn(Dist.CLIENT)
    public static enum CursorStep {
        CHARACTER,
        WORD;
    }
}
