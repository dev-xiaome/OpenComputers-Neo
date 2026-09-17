package net.minecraft.client.gui.components;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.navigation.ScreenAxis;
import net.minecraft.client.gui.navigation.ScreenDirection;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class ContainerObjectSelectionList<E extends ContainerObjectSelectionList.Entry<E>> extends AbstractSelectionList<E> {
    public ContainerObjectSelectionList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
        super(minecraft, width, height, y, itemHeight);
    }

    /**
     * Retrieves the next focus path based on the given focus navigation event.
     * <p>
     * @return the next focus path as a ComponentPath, or {@code null} if there is no next focus path.
     *
     * @param event the focus navigation event.
     */
    @Nullable
    @Override
    public ComponentPath nextFocusPath(FocusNavigationEvent event) {
        if (this.getItemCount() == 0) {
            return null;
        } else if (!(event instanceof FocusNavigationEvent.ArrowNavigation focusnavigationevent$arrownavigation)) {
            return super.nextFocusPath(event);
        } else {
            E e = this.getFocused();
            if (focusnavigationevent$arrownavigation.direction().getAxis() == ScreenAxis.HORIZONTAL && e != null) {
                return ComponentPath.path(this, e.nextFocusPath(event));
            } else {
                int i = -1;
                ScreenDirection screendirection = focusnavigationevent$arrownavigation.direction();
                if (e != null) {
                    i = e.children().indexOf(e.getFocused());
                }

                if (i == -1) {
                    switch (screendirection) {
                        case LEFT:
                            i = Integer.MAX_VALUE;
                            screendirection = ScreenDirection.DOWN;
                            break;
                        case RIGHT:
                            i = 0;
                            screendirection = ScreenDirection.DOWN;
                            break;
                        default:
                            i = 0;
                    }
                }

                E e1 = e;

                ComponentPath componentpath;
                do {
                    e1 = this.nextEntry(screendirection, p_351636_ -> !p_351636_.children().isEmpty(), e1);
                    if (e1 == null) {
                        return null;
                    }

                    componentpath = e1.focusPathAtIndex(focusnavigationevent$arrownavigation, i);
                } while (componentpath == null);

                return ComponentPath.path(this, componentpath);
            }
        }
    }

    /**
     * Sets the focus state of the GUI element.
     *
     * @param focused the focused GUI element.
     */
    @Override
    public void setFocused(@Nullable GuiEventListener focused) {
        if (this.getFocused() != focused) {
            super.setFocused(focused);
            if (focused == null) {
                this.setSelected(null);
            }
        }
    }

    @Override
    public NarratableEntry.NarrationPriority narrationPriority() {
        return this.isFocused() ? NarratableEntry.NarrationPriority.FOCUSED : super.narrationPriority();
    }

    @Override
    protected boolean isSelectedItem(int index) {
        return false;
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        E e = this.getHovered();
        if (e != null) {
            e.updateNarration(narrationElementOutput.nest());
            this.narrateListElementPosition(narrationElementOutput, e);
        } else {
            E e1 = this.getFocused();
            if (e1 != null) {
                e1.updateNarration(narrationElementOutput.nest());
                this.narrateListElementPosition(narrationElementOutput, e1);
            }
        }

        narrationElementOutput.add(NarratedElementType.USAGE, Component.translatable("narration.component_list.usage"));
    }

    @OnlyIn(Dist.CLIENT)
    public abstract static class Entry<E extends ContainerObjectSelectionList.Entry<E>> extends AbstractSelectionList.Entry<E> implements ContainerEventHandler {
        @Nullable
        private GuiEventListener focused;
        @Nullable
        private NarratableEntry lastNarratable;
        private boolean dragging;

        @Override
        public boolean isDragging() {
            return this.dragging;
        }

        /**
         * Sets if the GUI element is dragging or not.
         *
         * @param dragging the dragging state of the GUI element.
         */
        @Override
        public void setDragging(boolean dragging) {
            this.dragging = dragging;
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
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return ContainerEventHandler.super.mouseClicked(mouseX, mouseY, button);
        }

        /**
         * Sets the focus state of the GUI element.
         *
         * @param listener the focused GUI element.
         */
        @Override
        public void setFocused(@Nullable GuiEventListener listener) {
            if (this.focused != null) {
                this.focused.setFocused(false);
            }

            if (listener != null) {
                listener.setFocused(true);
            }

            this.focused = listener;
        }

        @Nullable
        @Override
        public GuiEventListener getFocused() {
            return this.focused;
        }

        @Nullable
        public ComponentPath focusPathAtIndex(FocusNavigationEvent event, int index) {
            if (this.children().isEmpty()) {
                return null;
            } else {
                ComponentPath componentpath = this.children().get(Math.min(index, this.children().size() - 1)).nextFocusPath(event);
                return ComponentPath.path(this, componentpath);
            }
        }

        /**
         * Retrieves the next focus path based on the given focus navigation event.
         * <p>
         * @return the next focus path as a ComponentPath, or {@code null} if there is no next focus path.
         *
         * @param event the focus navigation event.
         */
        @Nullable
        @Override
        public ComponentPath nextFocusPath(FocusNavigationEvent event) {
            if (event instanceof FocusNavigationEvent.ArrowNavigation focusnavigationevent$arrownavigation) {
                int i = switch (focusnavigationevent$arrownavigation.direction()) {
                    case LEFT -> -1;
                    case RIGHT -> 1;
                    case UP, DOWN -> 0;
                };
                if (i == 0) {
                    return null;
                }

                int j = Mth.clamp(i + this.children().indexOf(this.getFocused()), 0, this.children().size() - 1);

                for (int k = j; k >= 0 && k < this.children().size(); k += i) {
                    GuiEventListener guieventlistener = this.children().get(k);
                    ComponentPath componentpath = guieventlistener.nextFocusPath(event);
                    if (componentpath != null) {
                        return ComponentPath.path(this, componentpath);
                    }
                }
            }

            return ContainerEventHandler.super.nextFocusPath(event);
        }

        public abstract List<? extends NarratableEntry> narratables();

        void updateNarration(NarrationElementOutput narrationElementOutput) {
            List<? extends NarratableEntry> list = this.narratables();
            Screen.NarratableSearchResult screen$narratablesearchresult = Screen.findNarratableWidget(list, this.lastNarratable);
            if (screen$narratablesearchresult != null) {
                if (screen$narratablesearchresult.priority.isTerminal()) {
                    this.lastNarratable = screen$narratablesearchresult.entry;
                }

                if (list.size() > 1) {
                    narrationElementOutput.add(
                        NarratedElementType.POSITION,
                        Component.translatable("narrator.position.object_list", screen$narratablesearchresult.index + 1, list.size())
                    );
                    if (screen$narratablesearchresult.priority == NarratableEntry.NarrationPriority.FOCUSED) {
                        narrationElementOutput.add(NarratedElementType.USAGE, Component.translatable("narration.component_list.usage"));
                    }
                }

                screen$narratablesearchresult.entry.updateNarration(narrationElementOutput.nest());
            }
        }
    }
}
