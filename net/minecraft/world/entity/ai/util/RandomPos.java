package net.minecraft.world.entity.ai.util;

import com.google.common.annotations.VisibleForTesting;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;

public class RandomPos {
    private static final int RANDOM_POS_ATTEMPTS = 10;

    /**
     * Gets a random position within a certain distance.
     */
    public static BlockPos generateRandomDirection(RandomSource random, int horizontalDistance, int verticalDistance) {
        int i = random.nextInt(2 * horizontalDistance + 1) - horizontalDistance;
        int j = random.nextInt(2 * verticalDistance + 1) - verticalDistance;
        int k = random.nextInt(2 * horizontalDistance + 1) - horizontalDistance;
        return new BlockPos(i, j, k);
    }

    /**
     * @return a random (x, y, z) coordinate by picking a point (x, z), adding a random angle, up to a difference of {@code maxAngleDelta}. The y position is randomly chosen from the range {@code [y - yRange, y + yRange]}. Will be {@code null} if the chosen coordinate is outside a distance of {@code maxHorizontalDistance} from the origin.
     *
     * @param maxHorizontalDifference The maximum value in x and z, in absolute value,
     *                                that could be returned.
     * @param yRange                  The range plus or minus the y position to be
     *                                chosen
     * @param y                       The target y position
     * @param x                       The x offset to the target position
     * @param z                       The z offset to the target position
     * @param maxAngleDelta           The maximum variance of the returned angle, from
     *                                the base angle being a vector from (0, 0) to (x
     *                                , z).
     */
    @Nullable
    public static BlockPos generateRandomDirectionWithinRadians(
        RandomSource random, int maxHorizontalDifference, int yRange, int y, double x, double z, double maxAngleDelta
    ) {
        double d0 = Mth.atan2(z, x) - (float) (Math.PI / 2);
        double d1 = d0 + (double)(2.0F * random.nextFloat() - 1.0F) * maxAngleDelta;
        double d2 = Math.sqrt(random.nextDouble()) * (double)Mth.SQRT_OF_TWO * (double)maxHorizontalDifference;
        double d3 = -d2 * Math.sin(d1);
        double d4 = d2 * Math.cos(d1);
        if (!(Math.abs(d3) > (double)maxHorizontalDifference) && !(Math.abs(d4) > (double)maxHorizontalDifference)) {
            int i = random.nextInt(2 * yRange + 1) - yRange + y;
            return BlockPos.containing(d3, (double)i, d4);
        } else {
            return null;
        }
    }

    /**
     * @return the highest above position that is within the provided conditions
     */
    @VisibleForTesting
    public static BlockPos moveUpOutOfSolid(BlockPos pos, int maxY, Predicate<BlockPos> posPredicate) {
        if (!posPredicate.test(pos)) {
            return pos;
        } else {
            BlockPos blockpos = pos.above();

            while (blockpos.getY() < maxY && posPredicate.test(blockpos)) {
                blockpos = blockpos.above();
            }

            return blockpos;
        }
    }

    /**
     * Finds a position above based on the conditions.
     *
     * After it finds the position once, it will continue to move up until aboveSolidAmount is reached or the position is no longer valid
     */
    @VisibleForTesting
    public static BlockPos moveUpToAboveSolid(BlockPos pos, int aboveSolidAmount, int maxY, Predicate<BlockPos> posPredicate) {
        if (aboveSolidAmount < 0) {
            throw new IllegalArgumentException("aboveSolidAmount was " + aboveSolidAmount + ", expected >= 0");
        } else if (!posPredicate.test(pos)) {
            return pos;
        } else {
            BlockPos blockpos = pos.above();

            while (blockpos.getY() < maxY && posPredicate.test(blockpos)) {
                blockpos = blockpos.above();
            }

            BlockPos blockpos1 = blockpos;

            while (blockpos1.getY() < maxY && blockpos1.getY() - blockpos.getY() < aboveSolidAmount) {
                BlockPos blockpos2 = blockpos1.above();
                if (posPredicate.test(blockpos2)) {
                    break;
                }

                blockpos1 = blockpos2;
            }

            return blockpos1;
        }
    }

    @Nullable
    public static Vec3 generateRandomPos(PathfinderMob mob, Supplier<BlockPos> posSupplier) {
        return generateRandomPos(posSupplier, mob::getWalkTargetValue);
    }

    /**
     * Tries 10 times to maximize the return value of the position to double function based on the supplied position
     */
    @Nullable
    public static Vec3 generateRandomPos(Supplier<BlockPos> posSupplier, ToDoubleFunction<BlockPos> toDoubleFunction) {
        double d0 = Double.NEGATIVE_INFINITY;
        BlockPos blockpos = null;

        for (int i = 0; i < 10; i++) {
            BlockPos blockpos1 = posSupplier.get();
            if (blockpos1 != null) {
                double d1 = toDoubleFunction.applyAsDouble(blockpos1);
                if (d1 > d0) {
                    d0 = d1;
                    blockpos = blockpos1;
                }
            }
        }

        return blockpos != null ? Vec3.atBottomCenterOf(blockpos) : null;
    }

    /**
     * @return a random position within range, only if the mob is currently restricted
     */
    public static BlockPos generateRandomPosTowardDirection(PathfinderMob mob, int range, RandomSource random, BlockPos pos) {
        int i = pos.getX();
        int j = pos.getZ();
        if (mob.hasRestriction() && range > 1) {
            BlockPos blockpos = mob.getRestrictCenter();
            if (mob.getX() > (double)blockpos.getX()) {
                i -= random.nextInt(range / 2);
            } else {
                i += random.nextInt(range / 2);
            }

            if (mob.getZ() > (double)blockpos.getZ()) {
                j -= random.nextInt(range / 2);
            } else {
                j += random.nextInt(range / 2);
            }
        }

        return BlockPos.containing((double)i + mob.getX(), (double)pos.getY() + mob.getY(), (double)j + mob.getZ());
    }
}
