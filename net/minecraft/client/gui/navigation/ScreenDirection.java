package net.minecraft.client.gui.navigation;

import it.unimi.dsi.fastutil.ints.IntComparator;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public enum ScreenDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT;

    private final IntComparator coordinateValueComparator = (p_265081_, p_265641_) -> p_265081_ == p_265641_
            ? 0
            : (this.isBefore(p_265081_, p_265641_) ? -1 : 1);

    public ScreenAxis getAxis() {
        return switch (this) {
            case UP, DOWN -> ScreenAxis.VERTICAL;
            case LEFT, RIGHT -> ScreenAxis.HORIZONTAL;
        };
    }

    public ScreenDirection getOpposite() {
        return switch (this) {
            case UP -> DOWN;
            case DOWN -> UP;
            case LEFT -> RIGHT;
            case RIGHT -> LEFT;
        };
    }

    public boolean isPositive() {
        return switch (this) {
            case UP, LEFT -> false;
            case DOWN, RIGHT -> true;
        };
    }

    public boolean isAfter(int first, int second) {
        return this.isPositive() ? first > second : second > first;
    }

    public boolean isBefore(int first, int second) {
        return this.isPositive() ? first < second : second < first;
    }

    public IntComparator coordinateValueComparator() {
        return this.coordinateValueComparator;
    }
}
