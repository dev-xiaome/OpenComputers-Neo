package net.minecraft.world.phys.shapes;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.doubles.DoubleLists;

public class IndirectMerger implements IndexMerger {
    private static final DoubleList EMPTY = DoubleLists.unmodifiable(DoubleArrayList.wrap(new double[]{0.0}));
    private final double[] result;
    private final int[] firstIndices;
    private final int[] secondIndices;
    private final int resultLength;

    public IndirectMerger(DoubleList lower, DoubleList upper, boolean excludeUpper, boolean excludeLower) {
        double d0 = Double.NaN;
        int i = lower.size();
        int j = upper.size();
        int k = i + j;
        this.result = new double[k];
        this.firstIndices = new int[k];
        this.secondIndices = new int[k];
        boolean flag = !excludeUpper;
        boolean flag1 = !excludeLower;
        int l = 0;
        int i1 = 0;
        int j1 = 0;

        while (true) {
            boolean flag2 = i1 >= i;
            boolean flag3 = j1 >= j;
            if (flag2 && flag3) {
                this.resultLength = Math.max(1, l);
                return;
            }

            boolean flag4 = !flag2 && (flag3 || lower.getDouble(i1) < upper.getDouble(j1) + 1.0E-7);
            if (flag4) {
                i1++;
                if (flag && (j1 == 0 || flag3)) {
                    continue;
                }
            } else {
                j1++;
                if (flag1 && (i1 == 0 || flag2)) {
                    continue;
                }
            }

            int k1 = i1 - 1;
            int l1 = j1 - 1;
            double d1 = flag4 ? lower.getDouble(k1) : upper.getDouble(l1);
            if (!(d0 >= d1 - 1.0E-7)) {
                this.firstIndices[l] = k1;
                this.secondIndices[l] = l1;
                this.result[l] = d1;
                l++;
                d0 = d1;
            } else {
                this.firstIndices[l - 1] = k1;
                this.secondIndices[l - 1] = l1;
            }
        }
    }

    @Override
    public boolean forMergedIndexes(IndexMerger.IndexConsumer consumer) {
        int i = this.resultLength - 1;

        for (int j = 0; j < i; j++) {
            if (!consumer.merge(this.firstIndices[j], this.secondIndices[j], j)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public int size() {
        return this.resultLength;
    }

    @Override
    public DoubleList getList() {
        return (DoubleList)(this.resultLength <= 1 ? EMPTY : DoubleArrayList.wrap(this.result, this.resultLength));
    }
}
