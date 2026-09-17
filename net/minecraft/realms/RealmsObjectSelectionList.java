package net.minecraft.realms;

import java.util.Collection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class RealmsObjectSelectionList<E extends ObjectSelectionList.Entry<E>> extends ObjectSelectionList<E> {
    protected RealmsObjectSelectionList(int width, int height, int y, int itemHeight) {
        super(Minecraft.getInstance(), width, height, y, itemHeight);
    }

    public void setSelectedItem(int index) {
        if (index == -1) {
            this.setSelected(null);
        } else if (super.getItemCount() != 0) {
            this.setSelected(this.getEntry(index));
        }
    }

    public void selectItem(int index) {
        this.setSelectedItem(index);
    }

    @Override
    public int getMaxPosition() {
        return 0;
    }

    @Override
    public int getRowWidth() {
        return (int)((double)this.width * 0.6);
    }

    @Override
    public void replaceEntries(Collection<E> entries) {
        super.replaceEntries(entries);
    }

    @Override
    public int getItemCount() {
        return super.getItemCount();
    }

    @Override
    public int getRowTop(int index) {
        return super.getRowTop(index);
    }

    @Override
    public int getRowLeft() {
        return super.getRowLeft();
    }

    public int addEntry(E entry) {
        return super.addEntry(entry);
    }

    public void clear() {
        this.clearEntries();
    }
}
