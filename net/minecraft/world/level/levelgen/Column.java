package net.minecraft.world.level.levelgen;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A representation of an integer valued interval, either bounded or unbounded.
 * While the class itself does not imply any coordinate in particular, this is practically used to represent a column in the Y direction.
 */
public abstract class Column {
    /**
     * @return A column of the closed interval [floor, ceiling].
     */
    public static Column.Range around(int floor, int ceiling) {
        return new Column.Range(floor - 1, ceiling + 1);
    }

    /**
     * @return A column of the open interval (floor, ceiling).
     */
    public static Column.Range inside(int floor, int ceiling) {
        return new Column.Range(floor, ceiling);
    }

    /**
     * @return A column of the unbounded interval (-infinity, ceiling).
     */
    public static Column below(int ceiling) {
        return new Column.Ray(ceiling, false);
    }

    /**
     * @return A column of the unbounded interval (-infinity, ceiling].
     */
    public static Column fromHighest(int ceiling) {
        return new Column.Ray(ceiling + 1, false);
    }

    /**
     * @return A column of the unbounded interval (floor, infinity).
     */
    public static Column above(int floor) {
        return new Column.Ray(floor, true);
    }

    /**
     * @return A column of the unbounded interval [floor, infinity).
     */
    public static Column fromLowest(int floor) {
        return new Column.Ray(floor - 1, true);
    }

    public static Column line() {
        return Column.Line.INSTANCE;
    }

    public static Column create(OptionalInt floor, OptionalInt ceiling) {
        if (floor.isPresent() && ceiling.isPresent()) {
            return inside(floor.getAsInt(), ceiling.getAsInt());
        } else if (floor.isPresent()) {
            return above(floor.getAsInt());
        } else {
            return ceiling.isPresent() ? below(ceiling.getAsInt()) : line();
        }
    }

    public abstract OptionalInt getCeiling();

    public abstract OptionalInt getFloor();

    public abstract OptionalInt getHeight();

    public Column withFloor(OptionalInt floor) {
        return create(floor, this.getCeiling());
    }

    public Column withCeiling(OptionalInt ceiling) {
        return create(this.getFloor(), ceiling);
    }

    /**
     * Scans for a column of states satisfying {@code columnPredicate}, up to a length of {@code maxDistance} from the origin, and ending with a state which satisfies {@code tipPredicate}.
     * @return A column representing the tips found. The column will be bounded if a tip was reached in the given direction, unbounded otherwise.
     */
    public static Optional<Column> scan(
        LevelSimulatedReader level, BlockPos pos, int maxDistance, Predicate<BlockState> columnPredicate, Predicate<BlockState> tipPredicate
    ) {
        BlockPos.MutableBlockPos blockpos$mutableblockpos = pos.mutable();
        if (!level.isStateAtPosition(pos, columnPredicate)) {
            return Optional.empty();
        } else {
            int i = pos.getY();
            OptionalInt optionalint = scanDirection(level, maxDistance, columnPredicate, tipPredicate, blockpos$mutableblockpos, i, Direction.UP);
            OptionalInt optionalint1 = scanDirection(level, maxDistance, columnPredicate, tipPredicate, blockpos$mutableblockpos, i, Direction.DOWN);
            return Optional.of(create(optionalint1, optionalint));
        }
    }

    /**
     * Scans for a sequence of states in a given {@code direction}, up to a length of {@code maxDistance} which satisfy {@code columnPredicate}, and ending with a state which satisfies {@code tipPredicate}.
     * @return The y position of the tip, if found.
     */
    private static OptionalInt scanDirection(
        LevelSimulatedReader level,
        int maxDistance,
        Predicate<BlockState> columnPredicate,
        Predicate<BlockState> tipPredicate,
        BlockPos.MutableBlockPos mutablePos,
        int startY,
        Direction direction
    ) {
        mutablePos.setY(startY);

        for (int i = 1; i < maxDistance && level.isStateAtPosition(mutablePos, columnPredicate); i++) {
            mutablePos.move(direction);
        }

        return level.isStateAtPosition(mutablePos, tipPredicate) ? OptionalInt.of(mutablePos.getY()) : OptionalInt.empty();
    }

    public static final class Line extends Column {
        static final Column.Line INSTANCE = new Column.Line();

        private Line() {
        }

        @Override
        public OptionalInt getCeiling() {
            return OptionalInt.empty();
        }

        @Override
        public OptionalInt getFloor() {
            return OptionalInt.empty();
        }

        @Override
        public OptionalInt getHeight() {
            return OptionalInt.empty();
        }

        @Override
        public String toString() {
            return "C(-)";
        }
    }

    public static final class Range extends Column {
        private final int floor;
        private final int ceiling;

        protected Range(int floor, int ceiling) {
            this.floor = floor;
            this.ceiling = ceiling;
            if (this.height() < 0) {
                throw new IllegalArgumentException("Column of negative height: " + this);
            }
        }

        @Override
        public OptionalInt getCeiling() {
            return OptionalInt.of(this.ceiling);
        }

        @Override
        public OptionalInt getFloor() {
            return OptionalInt.of(this.floor);
        }

        @Override
        public OptionalInt getHeight() {
            return OptionalInt.of(this.height());
        }

        public int ceiling() {
            return this.ceiling;
        }

        public int floor() {
            return this.floor;
        }

        public int height() {
            return this.ceiling - this.floor - 1;
        }

        @Override
        public String toString() {
            return "C(" + this.ceiling + "-" + this.floor + ")";
        }
    }

    public static final class Ray extends Column {
        private final int edge;
        private final boolean pointingUp;

        public Ray(int edge, boolean pointingUp) {
            this.edge = edge;
            this.pointingUp = pointingUp;
        }

        @Override
        public OptionalInt getCeiling() {
            return this.pointingUp ? OptionalInt.empty() : OptionalInt.of(this.edge);
        }

        @Override
        public OptionalInt getFloor() {
            return this.pointingUp ? OptionalInt.of(this.edge) : OptionalInt.empty();
        }

        @Override
        public OptionalInt getHeight() {
            return OptionalInt.empty();
        }

        @Override
        public String toString() {
            return this.pointingUp ? "C(" + this.edge + "-)" : "C(-" + this.edge + ")";
        }
    }
}
