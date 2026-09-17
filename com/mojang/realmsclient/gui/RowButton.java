package com.mojang.realmsclient.gui;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.realms.RealmsObjectSelectionList;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class RowButton {
    public final int width;
    public final int height;
    public final int xOffset;
    public final int yOffset;

    public RowButton(int width, int height, int xOffset, int yOffset) {
        this.width = width;
        this.height = height;
        this.xOffset = xOffset;
        this.yOffset = yOffset;
    }

    public void drawForRowAt(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        int i = x + this.xOffset;
        int j = y + this.yOffset;
        boolean flag = mouseX >= i && mouseX <= i + this.width && mouseY >= j && mouseY <= j + this.height;
        this.draw(guiGraphics, i, j, flag);
    }

    protected abstract void draw(GuiGraphics guiGraphics, int x, int y, boolean showTooltip);

    public int getRight() {
        return this.xOffset + this.width;
    }

    public int getBottom() {
        return this.yOffset + this.height;
    }

    public abstract void onClick(int index);

    public static void drawButtonsInRow(
        GuiGraphics guiGraphics, List<RowButton> buttons, RealmsObjectSelectionList<?> pendingInvitations, int x, int y, int mouseX, int mouseY
    ) {
        for (RowButton rowbutton : buttons) {
            if (pendingInvitations.getRowWidth() > rowbutton.getRight()) {
                rowbutton.drawForRowAt(guiGraphics, x, y, mouseX, mouseY);
            }
        }
    }

    public static void rowButtonMouseClicked(
        RealmsObjectSelectionList<?> list, ObjectSelectionList.Entry<?> entry, List<RowButton> buttons, int button, double mouseX, double mouseY
    ) {
        int i = list.children().indexOf(entry);
        if (i > -1) {
            list.selectItem(i);
            int j = list.getRowLeft();
            int k = list.getRowTop(i);
            int l = (int)(mouseX - (double)j);
            int i1 = (int)(mouseY - (double)k);

            for (RowButton rowbutton : buttons) {
                if (l >= rowbutton.xOffset && l <= rowbutton.getRight() && i1 >= rowbutton.yOffset && i1 <= rowbutton.getBottom()) {
                    rowbutton.onClick(i);
                }
            }
        }
    }
}
