package net.minecraft.client.gui.layouts;

import com.mojang.math.Divisor;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class EqualSpacingLayout extends AbstractLayout {
    private final EqualSpacingLayout.Orientation orientation;
    private final List<EqualSpacingLayout.ChildContainer> children = new ArrayList<>();
    private final LayoutSettings defaultChildLayoutSettings = LayoutSettings.defaults();

    public EqualSpacingLayout(int width, int height, EqualSpacingLayout.Orientation orientation) {
        this(0, 0, width, height, orientation);
    }

    public EqualSpacingLayout(int x, int y, int width, int height, EqualSpacingLayout.Orientation orientation) {
        super(x, y, width, height);
        this.orientation = orientation;
    }

    @Override
    public void arrangeElements() {
        super.arrangeElements();
        if (!this.children.isEmpty()) {
            int i = 0;
            int j = this.orientation.getSecondaryLength(this);

            for (EqualSpacingLayout.ChildContainer equalspacinglayout$childcontainer : this.children) {
                i += this.orientation.getPrimaryLength(equalspacinglayout$childcontainer);
                j = Math.max(j, this.orientation.getSecondaryLength(equalspacinglayout$childcontainer));
            }

            int k = this.orientation.getPrimaryLength(this) - i;
            int l = this.orientation.getPrimaryPosition(this);
            Iterator<EqualSpacingLayout.ChildContainer> iterator = this.children.iterator();
            EqualSpacingLayout.ChildContainer equalspacinglayout$childcontainer1 = iterator.next();
            this.orientation.setPrimaryPosition(equalspacinglayout$childcontainer1, l);
            l += this.orientation.getPrimaryLength(equalspacinglayout$childcontainer1);
            if (this.children.size() >= 2) {
                Divisor divisor = new Divisor(k, this.children.size() - 1);

                while (divisor.hasNext()) {
                    l += divisor.nextInt();
                    EqualSpacingLayout.ChildContainer equalspacinglayout$childcontainer2 = iterator.next();
                    this.orientation.setPrimaryPosition(equalspacinglayout$childcontainer2, l);
                    l += this.orientation.getPrimaryLength(equalspacinglayout$childcontainer2);
                }
            }

            int i1 = this.orientation.getSecondaryPosition(this);

            for (EqualSpacingLayout.ChildContainer equalspacinglayout$childcontainer3 : this.children) {
                this.orientation.setSecondaryPosition(equalspacinglayout$childcontainer3, i1, j);
            }

            switch (this.orientation) {
                case HORIZONTAL:
                    this.height = j;
                    break;
                case VERTICAL:
                    this.width = j;
            }
        }
    }

    @Override
    public void visitChildren(Consumer<LayoutElement> visitor) {
        this.children.forEach(p_296421_ -> visitor.accept(p_296421_.child));
    }

    public LayoutSettings newChildLayoutSettings() {
        return this.defaultChildLayoutSettings.copy();
    }

    public LayoutSettings defaultChildLayoutSetting() {
        return this.defaultChildLayoutSettings;
    }

    public <T extends LayoutElement> T addChild(T child) {
        return this.addChild(child, this.newChildLayoutSettings());
    }

    public <T extends LayoutElement> T addChild(T child, LayoutSettings layoutSettings) {
        this.children.add(new EqualSpacingLayout.ChildContainer(child, layoutSettings));
        return child;
    }

    public <T extends LayoutElement> T addChild(T child, Consumer<LayoutSettings> layoutSettingsCreator) {
        return this.addChild(child, Util.make(this.newChildLayoutSettings(), layoutSettingsCreator));
    }

    @OnlyIn(Dist.CLIENT)
    static class ChildContainer extends AbstractLayout.AbstractChildWrapper {
        protected ChildContainer(LayoutElement p_295358_, LayoutSettings p_295638_) {
            super(p_295358_, p_295638_);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static enum Orientation {
        HORIZONTAL,
        VERTICAL;

        int getPrimaryLength(LayoutElement element) {
            return switch (this) {
                case HORIZONTAL -> element.getWidth();
                case VERTICAL -> element.getHeight();
            };
        }

        int getPrimaryLength(EqualSpacingLayout.ChildContainer container) {
            return switch (this) {
                case HORIZONTAL -> container.getWidth();
                case VERTICAL -> container.getHeight();
            };
        }

        int getSecondaryLength(LayoutElement element) {
            return switch (this) {
                case HORIZONTAL -> element.getHeight();
                case VERTICAL -> element.getWidth();
            };
        }

        int getSecondaryLength(EqualSpacingLayout.ChildContainer container) {
            return switch (this) {
                case HORIZONTAL -> container.getHeight();
                case VERTICAL -> container.getWidth();
            };
        }

        void setPrimaryPosition(EqualSpacingLayout.ChildContainer container, int position) {
            switch (this) {
                case HORIZONTAL:
                    container.setX(position, container.getWidth());
                    break;
                case VERTICAL:
                    container.setY(position, container.getHeight());
            }
        }

        void setSecondaryPosition(EqualSpacingLayout.ChildContainer container, int position, int length) {
            switch (this) {
                case HORIZONTAL:
                    container.setY(position, length);
                    break;
                case VERTICAL:
                    container.setX(position, length);
            }
        }

        int getPrimaryPosition(LayoutElement element) {
            return switch (this) {
                case HORIZONTAL -> element.getX();
                case VERTICAL -> element.getY();
            };
        }

        int getSecondaryPosition(LayoutElement element) {
            return switch (this) {
                case HORIZONTAL -> element.getY();
                case VERTICAL -> element.getX();
            };
        }
    }
}
