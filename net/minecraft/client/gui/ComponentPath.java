package net.minecraft.client.gui;

import javax.annotation.Nullable;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Represents a path of components in a user interface hierarchy.
 * <p>
 * It provides methods to create and manipulate component paths.
 */
@OnlyIn(Dist.CLIENT)
public interface ComponentPath {
    /**
     * Creates a leaf component path with the specified {@code GuiEventListener} component.
     * <p>
     * @return a new leaf component path.
     *
     * @param component the component associated with the leaf path
     */
    static ComponentPath leaf(GuiEventListener component) {
        return new ComponentPath.Leaf(component);
    }

    /**
     * Creates a component path with the specified {@code ContainerEventHandler} component and an optional child path.
     * <p>
     * @return a new component path, or {@code null} if the child path is null
     *
     * @param component the component associated with the path
     * @param childPath the child path associated with the component
     */
    @Nullable
    static ComponentPath path(ContainerEventHandler component, @Nullable ComponentPath childPath) {
        return childPath == null ? null : new ComponentPath.Path(component, childPath);
    }

    /**
     * Creates a new {@code ComponentPath} leaf node with the specified {@code GuiEventListener} component and an array of {@code ContainerEventHandler} ancestors.
     * <p>
     * @return a new component path
     *
     * @param leafComponent      the new 'Leaf' component associated with the path
     * @param ancestorComponents the array of ancestor components associated with the
     *                           path, ordered in reverse ascending order towards root
     *                           .
     */
    static ComponentPath path(GuiEventListener leafComponent, ContainerEventHandler... ancestorComponents) {
        ComponentPath componentpath = leaf(leafComponent);

        for (ContainerEventHandler containereventhandler : ancestorComponents) {
            componentpath = path(containereventhandler, componentpath);
        }

        return componentpath;
    }

    GuiEventListener component();

    /**
     * Applies focus to or removes focus from the component associated with this component path.
     *
     * @param focused {@code true} to apply focus, {@code false} to remove focus.
     */
    void applyFocus(boolean focused);

    /**
     * The {@code Leaf} class represents a leaf component path in the hierarchy.
     */
    @OnlyIn(Dist.CLIENT)
    public static record Leaf(GuiEventListener component) implements ComponentPath {
        /**
         * Applies focus to or removes focus from the component associated with this leaf path.
         * focused {@code true} to apply focus, {@code false} to remove focus
         *
         * @param focused {@code true} to apply focus, {@code false} to remove focus.
         */
        @Override
        public void applyFocus(boolean focused) {
            this.component.setFocused(focused);
        }
    }

    /**
     * The {@code Path} class represents a non-leaf component path in the hierarchy.
     */
    @OnlyIn(Dist.CLIENT)
    public static record Path(ContainerEventHandler component, ComponentPath childPath) implements ComponentPath {
        /**
         * Applies focus to or removes focus from the component associated with this component path.
         * focused {@code true} to apply focus, {@code false} to remove focus
         *
         * @param focused {@code true} to apply focus, {@code false} to remove focus.
         */
        @Override
        public void applyFocus(boolean focused) {
            if (!focused) {
                this.component.setFocused(null);
            } else {
                this.component.setFocused(this.childPath.component());
            }

            this.childPath.applyFocus(focused);
        }
    }
}
