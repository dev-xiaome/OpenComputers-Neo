package net.minecraft.client.gui.components.events;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.navigation.ScreenAxis;
import net.minecraft.client.gui.navigation.ScreenDirection;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector2i;

@OnlyIn(Dist.CLIENT)
public interface ContainerEventHandler extends GuiEventListener {
    List<? extends GuiEventListener> children();

    /**
     * Returns the first event listener that intersects with the mouse coordinates.
     */
    default Optional<GuiEventListener> getChildAt(double mouseX, double mouseY) {
        for (GuiEventListener guieventlistener : this.children()) {
            if (guieventlistener.isMouseOver(mouseX, mouseY)) {
                return Optional.of(guieventlistener);
            }
        }

        return Optional.empty();
    }

    /**
     * Called when a mouse button is clicked within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param mouseX the X coordinate of the mouse.
     * @param mouseY the Y coordinate of the mouse.
     * @param button the button that was clicked.
     */
    @Override
    default boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (GuiEventListener guieventlistener : this.children()) {
            if (guieventlistener.mouseClicked(mouseX, mouseY, button)) {
                this.setFocused(guieventlistener);
                if (button == 0) {
                    this.setDragging(true);
                }

                return true;
            }
        }

        return false;
    }

    /**
     * Called when a mouse button is released within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param mouseX the X coordinate of the mouse.
     * @param mouseY the Y coordinate of the mouse.
     * @param button the button that was released.
     */
    @Override
    default boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.isDragging()) {
            this.setDragging(false);
            if (this.getFocused() != null) {
                return this.getFocused().mouseReleased(mouseX, mouseY, button);
            }
        }

        return this.getChildAt(mouseX, mouseY).filter(p_94708_ -> p_94708_.mouseReleased(mouseX, mouseY, button)).isPresent();
    }

    /**
     * Called when the mouse is dragged within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param mouseX the X coordinate of the mouse.
     * @param mouseY the Y coordinate of the mouse.
     * @param button the button that is being dragged.
     * @param dragX  the X distance of the drag.
     * @param dragY  the Y distance of the drag.
     */
    @Override
    default boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return this.getFocused() != null && this.isDragging() && button == 0
            ? this.getFocused().mouseDragged(mouseX, mouseY, button, dragX, dragY)
            : false;
    }

    boolean isDragging();

    /**
     * Sets if the GUI element is dragging or not.
     *
     * @param isDragging the dragging state of the GUI element.
     */
    void setDragging(boolean isDragging);

    @Override
    default boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return this.getChildAt(mouseX, mouseY).filter(p_293596_ -> p_293596_.mouseScrolled(mouseX, mouseY, scrollX, scrollY)).isPresent();
    }

    /**
     * Called when a keyboard key is pressed within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param keyCode   the key code of the pressed key.
     * @param scanCode  the scan code of the pressed key.
     * @param modifiers the keyboard modifiers.
     */
    @Override
    default boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return this.getFocused() != null && this.getFocused().keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Called when a keyboard key is released within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param keyCode   the key code of the released key.
     * @param scanCode  the scan code of the released key.
     * @param modifiers the keyboard modifiers.
     */
    @Override
    default boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return this.getFocused() != null && this.getFocused().keyReleased(keyCode, scanCode, modifiers);
    }

    /**
     * Called when a character is typed within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param codePoint the code point of the typed character.
     * @param modifiers the keyboard modifiers.
     */
    @Override
    default boolean charTyped(char codePoint, int modifiers) {
        return this.getFocused() != null && this.getFocused().charTyped(codePoint, modifiers);
    }

    @Nullable
    GuiEventListener getFocused();

    /**
     * Sets the focus state of the GUI element.
     *
     * @param focused the focused GUI element.
     */
    void setFocused(@Nullable GuiEventListener focused);

    /**
     * Sets the focus state of the GUI element.
     *
     * @param focused {@code true} to apply focus, {@code false} to remove focus
     */
    @Override
    default void setFocused(boolean focused) {
    }

    @Override
    default boolean isFocused() {
        return this.getFocused() != null;
    }

    @Nullable
    @Override
    default ComponentPath getCurrentFocusPath() {
        GuiEventListener guieventlistener = this.getFocused();
        return guieventlistener != null ? ComponentPath.path(this, guieventlistener.getCurrentFocusPath()) : null;
    }

    /**
     * Retrieves the next focus path based on the given focus navigation event.
     * <p>
     * @return The next focus path as a ComponentPath, or {@code null} if there is no next focus path.
     *
     * @param event the focus navigation event.
     */
    @Nullable
    @Override
    default ComponentPath nextFocusPath(FocusNavigationEvent event) {
        GuiEventListener guieventlistener = this.getFocused();
        if (guieventlistener != null) {
            ComponentPath componentpath = guieventlistener.nextFocusPath(event);
            if (componentpath != null) {
                return ComponentPath.path(this, componentpath);
            }
        }

        if (event instanceof FocusNavigationEvent.TabNavigation focusnavigationevent$tabnavigation) {
            return this.handleTabNavigation(focusnavigationevent$tabnavigation);
        } else {
            return event instanceof FocusNavigationEvent.ArrowNavigation focusnavigationevent$arrownavigation
                ? this.handleArrowNavigation(focusnavigationevent$arrownavigation)
                : null;
        }
    }

    /**
     * Handles tab-based navigation events.
     * <p>
     * @return The next focus path for tab navigation, or {@code null} if no suitable path is found.
     *
     * @param tabNavigation The tab navigation event.
     */
    @Nullable
    private ComponentPath handleTabNavigation(FocusNavigationEvent.TabNavigation tabNavigation) {
        boolean flag = tabNavigation.forward();
        GuiEventListener guieventlistener = this.getFocused();
        List<? extends GuiEventListener> list = new ArrayList<>(this.children());
        Collections.sort(list, Comparator.comparingInt(p_344153_ -> p_344153_.getTabOrderGroup()));
        int j = list.indexOf(guieventlistener);
        int i;
        if (guieventlistener != null && j >= 0) {
            i = j + (flag ? 1 : 0);
        } else if (flag) {
            i = 0;
        } else {
            i = list.size();
        }

        ListIterator<? extends GuiEventListener> listiterator = list.listIterator(i);
        BooleanSupplier booleansupplier = flag ? listiterator::hasNext : listiterator::hasPrevious;
        Supplier<? extends GuiEventListener> supplier = flag ? listiterator::next : listiterator::previous;

        while (booleansupplier.getAsBoolean()) {
            GuiEventListener guieventlistener1 = supplier.get();
            ComponentPath componentpath = guieventlistener1.nextFocusPath(tabNavigation);
            if (componentpath != null) {
                return ComponentPath.path(this, componentpath);
            }
        }

        return null;
    }

    /**
     * Handles arrow-based navigation events.
     * <p>
     * @return The next focus path for arrow navigation, or {@code null} if no suitable path is found.
     *
     * @param arrowNavigation The arrow navigation event.
     */
    @Nullable
    private ComponentPath handleArrowNavigation(FocusNavigationEvent.ArrowNavigation arrowNavigation) {
        GuiEventListener guieventlistener = this.getFocused();
        if (guieventlistener == null) {
            ScreenDirection screendirection = arrowNavigation.direction();
            ScreenRectangle screenrectangle1 = this.getRectangle().getBorder(screendirection.getOpposite());
            return ComponentPath.path(this, this.nextFocusPathInDirection(screenrectangle1, screendirection, null, arrowNavigation));
        } else {
            ScreenRectangle screenrectangle = guieventlistener.getRectangle();
            return ComponentPath.path(this, this.nextFocusPathInDirection(screenrectangle, arrowNavigation.direction(), guieventlistener, arrowNavigation));
        }
    }

    /**
     * Calculates the next focus path in a specific direction.
     * <p>
     * @return The next focus path in the specified direction, or {@code null} if no suitable path is found.
     *
     * @param rectangle The screen rectangle.
     * @param direction The direction of navigation.
     * @param listener  The currently focused GUI event listener.
     * @param event     The focus navigation event.
     */
    @Nullable
    private ComponentPath nextFocusPathInDirection(
        ScreenRectangle rectangle, ScreenDirection direction, @Nullable GuiEventListener listener, FocusNavigationEvent event
    ) {
        ScreenAxis screenaxis = direction.getAxis();
        ScreenAxis screenaxis1 = screenaxis.orthogonal();
        ScreenDirection screendirection = screenaxis1.getPositive();
        int i = rectangle.getBoundInDirection(direction.getOpposite());
        List<GuiEventListener> list = new ArrayList<>();

        for (GuiEventListener guieventlistener : this.children()) {
            if (guieventlistener != listener) {
                ScreenRectangle screenrectangle = guieventlistener.getRectangle();
                if (screenrectangle.overlapsInAxis(rectangle, screenaxis1)) {
                    int j = screenrectangle.getBoundInDirection(direction.getOpposite());
                    if (direction.isAfter(j, i)) {
                        list.add(guieventlistener);
                    } else if (j == i && direction.isAfter(screenrectangle.getBoundInDirection(direction), rectangle.getBoundInDirection(direction))) {
                        list.add(guieventlistener);
                    }
                }
            }
        }

        Comparator<GuiEventListener> comparator = Comparator.comparing(
            p_264674_ -> p_264674_.getRectangle().getBoundInDirection(direction.getOpposite()), direction.coordinateValueComparator()
        );
        Comparator<GuiEventListener> comparator1 = Comparator.comparing(
            p_264676_ -> p_264676_.getRectangle().getBoundInDirection(screendirection.getOpposite()), screendirection.coordinateValueComparator()
        );
        list.sort(comparator.thenComparing(comparator1));

        for (GuiEventListener guieventlistener1 : list) {
            ComponentPath componentpath = guieventlistener1.nextFocusPath(event);
            if (componentpath != null) {
                return componentpath;
            }
        }

        return this.nextFocusPathVaguelyInDirection(rectangle, direction, listener, event);
    }

    /**
     * Calculates the next focus path in a vague direction.
     * <p>
     * @return The next focus path in the vague direction, or {@code null} if no suitable path is found.
     *
     * @param rectangle The screen rectangle.
     * @param direction The direction of navigation.
     * @param listener  The currently focused GUI event listener.
     * @param event     The focus navigation event.
     */
    @Nullable
    private ComponentPath nextFocusPathVaguelyInDirection(
        ScreenRectangle rectangle, ScreenDirection direction, @Nullable GuiEventListener listener, FocusNavigationEvent event
    ) {
        ScreenAxis screenaxis = direction.getAxis();
        ScreenAxis screenaxis1 = screenaxis.orthogonal();
        List<Pair<GuiEventListener, Long>> list = new ArrayList<>();
        ScreenPosition screenposition = ScreenPosition.of(screenaxis, rectangle.getBoundInDirection(direction), rectangle.getCenterInAxis(screenaxis1));

        for (GuiEventListener guieventlistener : this.children()) {
            if (guieventlistener != listener) {
                ScreenRectangle screenrectangle = guieventlistener.getRectangle();
                ScreenPosition screenposition1 = ScreenPosition.of(
                    screenaxis, screenrectangle.getBoundInDirection(direction.getOpposite()), screenrectangle.getCenterInAxis(screenaxis1)
                );
                if (direction.isAfter(screenposition1.getCoordinate(screenaxis), screenposition.getCoordinate(screenaxis))) {
                    long i = Vector2i.distanceSquared(screenposition.x(), screenposition.y(), screenposition1.x(), screenposition1.y());
                    list.add(Pair.of(guieventlistener, i));
                }
            }
        }

        list.sort(Comparator.comparingDouble(Pair::getSecond));

        for (Pair<GuiEventListener, Long> pair : list) {
            ComponentPath componentpath = pair.getFirst().nextFocusPath(event);
            if (componentpath != null) {
                return componentpath;
            }
        }

        return null;
    }
}
