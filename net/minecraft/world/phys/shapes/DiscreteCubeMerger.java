package net.minecraft.world.phys.shapes;

import com.google.common.math.IntMath;
import it.unimi.dsi.fastutil.doubles.DoubleList;

public final class DiscreteCubeMerger implements IndexMerger {
    private final CubePointRange result;
    private final int firstDiv;
    private final int secondDiv;

    DiscreteCubeMerger(int aa, int bb) {
        this.result = new CubePointRange((int)Shapes.lcm(aa, bb));
        int i = IntMath.gcd(aa, bb);
        this.firstDiv = aa / i;
        this.secondDiv = bb / i;
    }

    @Override
    public boolean forMergedIndexes(IndexMerger.IndexConsumer consumer) {
        int i = this.result.size() - 1;

        for (int j = 0; j < i; j++) {
            if (!consumer.merge(j / this.secondDiv, j / this.firstDiv, j)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public int size() {
        return this.result.size();
    }

    @Override
    public DoubleList getList() {
        return this.result;
    }
}
