package net.minecraft.world.level;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.core.BlockPos;

public class PotentialCalculator {
    private final List<PotentialCalculator.PointCharge> charges = Lists.newArrayList();

    public void addCharge(BlockPos pos, double charge) {
        if (charge != 0.0) {
            this.charges.add(new PotentialCalculator.PointCharge(pos, charge));
        }
    }

    public double getPotentialEnergyChange(BlockPos pos, double charge) {
        if (charge == 0.0) {
            return 0.0;
        } else {
            double d0 = 0.0;

            for (PotentialCalculator.PointCharge potentialcalculator$pointcharge : this.charges) {
                d0 += potentialcalculator$pointcharge.getPotentialChange(pos);
            }

            return d0 * charge;
        }
    }

    static class PointCharge {
        private final BlockPos pos;
        private final double charge;

        public PointCharge(BlockPos pos, double charge) {
            this.pos = pos;
            this.charge = charge;
        }

        public double getPotentialChange(BlockPos pos) {
            double d0 = this.pos.distSqr(pos);
            return d0 == 0.0 ? Double.POSITIVE_INFINITY : this.charge / Math.sqrt(d0);
        }
    }
}
