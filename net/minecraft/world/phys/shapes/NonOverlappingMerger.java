package net.minecraft.world.phys.shapes;

import it.unimi.dsi.fastutil.doubles.AbstractDoubleList;
import it.unimi.dsi.fastutil.doubles.DoubleList;

public class NonOverlappingMerger extends AbstractDoubleList implements IndexMerger {
    private final DoubleList lower;
    private final DoubleList upper;
    private final boolean swap;

    protected NonOverlappingMerger(DoubleList lower, DoubleList upper, boolean swap) {
        this.lower = lower;
        this.upper = upper;
        this.swap = swap;
    }

    @Override
    public int size() {
        return this.lower.size() + this.upper.size();
    }

    @Override
    public boolean forMergedIndexes(IndexMerger.IndexConsumer consumer) {
        return this.swap
            ? this.forNonSwappedIndexes((p_83020_, p_83021_, p_83022_) -> consumer.merge(p_83021_, p_83020_, p_83022_))
            : this.forNonSwappedIndexes(consumer);
    }

    private boolean forNonSwappedIndexes(IndexMerger.IndexConsumer consumer) {
        int i = this.lower.size();

        for (int j = 0; j < i; j++) {
            if (!consumer.merge(j, -1, j)) {
                return false;
            }
        }

        int l = this.upper.size() - 1;

        for (int k = 0; k < l; k++) {
            if (!consumer.merge(i - 1, k, i + k)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public double getDouble(int index) {
        return index < this.lower.size() ? this.lower.getDouble(index) : this.upper.getDouble(index - this.lower.size());
    }

    @Override
    public DoubleList getList() {
        return this;
    }
}
