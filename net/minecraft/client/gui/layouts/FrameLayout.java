package net.minecraft.client.gui.layouts;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.Util;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class FrameLayout extends AbstractLayout {
    private final List<FrameLayout.ChildContainer> children = new ArrayList<>();
    private int minWidth;
    private int minHeight;
    private final LayoutSettings defaultChildLayoutSettings = LayoutSettings.defaults().align(0.5F, 0.5F);

    public FrameLayout() {
        this(0, 0, 0, 0);
    }

    public FrameLayout(int width, int height) {
        this(0, 0, width, height);
    }

    public FrameLayout(int x, int y, int width, int height) {
        super(x, y, width, height);
        this.setMinDimensions(width, height);
    }

    public FrameLayout setMinDimensions(int minWidth, int minHeight) {
        return this.setMinWidth(minWidth).setMinHeight(minHeight);
    }

    public FrameLayout setMinHeight(int minHeight) {
        this.minHeight = minHeight;
        return this;
    }

    public FrameLayout setMinWidth(int minWidth) {
        this.minWidth = minWidth;
        return this;
    }

    public LayoutSettings newChildLayoutSettings() {
        return this.defaultChildLayoutSettings.copy();
    }

    public LayoutSettings defaultChildLayoutSetting() {
        return this.defaultChildLayoutSettings;
    }

    @Override
    public void arrangeElements() {
        super.arrangeElements();
        int i = this.minWidth;
        int j = this.minHeight;

        for (FrameLayout.ChildContainer framelayout$childcontainer : this.children) {
            i = Math.max(i, framelayout$childcontainer.getWidth());
            j = Math.max(j, framelayout$childcontainer.getHeight());
        }

        for (FrameLayout.ChildContainer framelayout$childcontainer1 : this.children) {
            framelayout$childcontainer1.setX(this.getX(), i);
            framelayout$childcontainer1.setY(this.getY(), j);
        }

        this.width = i;
        this.height = j;
    }

    public <T extends LayoutElement> T addChild(T child) {
        return this.addChild(child, this.newChildLayoutSettings());
    }

    public <T extends LayoutElement> T addChild(T child, LayoutSettings layoutSettings) {
        this.children.add(new FrameLayout.ChildContainer(child, layoutSettings));
        return child;
    }

    public <T extends LayoutElement> T addChild(T child, Consumer<LayoutSettings> layoutSettingsFactory) {
        return this.addChild(child, Util.make(this.newChildLayoutSettings(), layoutSettingsFactory));
    }

    @Override
    public void visitChildren(Consumer<LayoutElement> visitor) {
        this.children.forEach(p_265653_ -> visitor.accept(p_265653_.child));
    }

    public static void centerInRectangle(LayoutElement child, int x, int y, int width, int height) {
        alignInRectangle(child, x, y, width, height, 0.5F, 0.5F);
    }

    public static void centerInRectangle(LayoutElement child, ScreenRectangle rectangle) {
        centerInRectangle(child, rectangle.position().x(), rectangle.position().y(), rectangle.width(), rectangle.height());
    }

    public static void alignInRectangle(LayoutElement child, ScreenRectangle rectangle, float deltaX, float deltaY) {
        alignInRectangle(child, rectangle.left(), rectangle.top(), rectangle.width(), rectangle.height(), deltaX, deltaY);
    }

    public static void alignInRectangle(LayoutElement child, int x, int y, int width, int height, float deltaX, float deltaY) {
        alignInDimension(x, width, child.getWidth(), child::setX, deltaX);
        alignInDimension(y, height, child.getHeight(), child::setY, deltaY);
    }

    public static void alignInDimension(int position, int rectangleLength, int childLength, Consumer<Integer> setter, float delta) {
        int i = (int)Mth.lerp(delta, 0.0F, (float)(rectangleLength - childLength));
        setter.accept(position + i);
    }

    @OnlyIn(Dist.CLIENT)
    static class ChildContainer extends AbstractLayout.AbstractChildWrapper {
        protected ChildContainer(LayoutElement p_265667_, LayoutSettings p_265430_) {
            super(p_265667_, p_265430_);
        }
    }
}
