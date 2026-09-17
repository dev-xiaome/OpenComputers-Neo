package net.minecraft.client.gui.navigation;

import javax.annotation.Nullable;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record ScreenRectangle(ScreenPosition position, int width, int height) {
    private static final ScreenRectangle EMPTY = new ScreenRectangle(0, 0, 0, 0);

    public ScreenRectangle(int p_265721_, int p_265116_, int p_265225_, int p_265493_) {
        this(new ScreenPosition(p_265721_, p_265116_), p_265225_, p_265493_);
    }

    public static ScreenRectangle empty() {
        return EMPTY;
    }

    public static ScreenRectangle of(ScreenAxis axis, int primaryPosition, int secondaryPosition, int primaryLength, int secondaryLength) {
        return switch (axis) {
            case HORIZONTAL -> new ScreenRectangle(primaryPosition, secondaryPosition, primaryLength, secondaryLength);
            case VERTICAL -> new ScreenRectangle(secondaryPosition, primaryPosition, secondaryLength, primaryLength);
        };
    }

    public ScreenRectangle step(ScreenDirection direction) {
        return new ScreenRectangle(this.position.step(direction), this.width, this.height);
    }

    public int getLength(ScreenAxis axis) {
        return switch (axis) {
            case HORIZONTAL -> this.width;
            case VERTICAL -> this.height;
        };
    }

    public int getBoundInDirection(ScreenDirection direction) {
        ScreenAxis screenaxis = direction.getAxis();
        return direction.isPositive() ? this.position.getCoordinate(screenaxis) + this.getLength(screenaxis) - 1 : this.position.getCoordinate(screenaxis);
    }

    public ScreenRectangle getBorder(ScreenDirection direction) {
        int i = this.getBoundInDirection(direction);
        ScreenAxis screenaxis = direction.getAxis().orthogonal();
        int j = this.getBoundInDirection(screenaxis.getNegative());
        int k = this.getLength(screenaxis);
        return of(direction.getAxis(), i, j, 1, k).step(direction);
    }

    public boolean overlaps(ScreenRectangle rectangle) {
        return this.overlapsInAxis(rectangle, ScreenAxis.HORIZONTAL) && this.overlapsInAxis(rectangle, ScreenAxis.VERTICAL);
    }

    public boolean overlapsInAxis(ScreenRectangle rectangle, ScreenAxis axis) {
        int i = this.getBoundInDirection(axis.getNegative());
        int j = rectangle.getBoundInDirection(axis.getNegative());
        int k = this.getBoundInDirection(axis.getPositive());
        int l = rectangle.getBoundInDirection(axis.getPositive());
        return Math.max(i, j) <= Math.min(k, l);
    }

    public int getCenterInAxis(ScreenAxis axis) {
        return (this.getBoundInDirection(axis.getPositive()) + this.getBoundInDirection(axis.getNegative())) / 2;
    }

    @Nullable
    public ScreenRectangle intersection(ScreenRectangle rectangle) {
        int i = Math.max(this.left(), rectangle.left());
        int j = Math.max(this.top(), rectangle.top());
        int k = Math.min(this.right(), rectangle.right());
        int l = Math.min(this.bottom(), rectangle.bottom());
        return i < k && j < l ? new ScreenRectangle(i, j, k - i, l - j) : null;
    }

    public int top() {
        return this.position.y();
    }

    public int bottom() {
        return this.position.y() + this.height;
    }

    public int left() {
        return this.position.x();
    }

    public int right() {
        return this.position.x() + this.width;
    }

    public boolean containsPoint(int x, int y) {
        return x >= this.left() && x < this.right() && y >= this.top() && y < this.bottom();
    }
}
