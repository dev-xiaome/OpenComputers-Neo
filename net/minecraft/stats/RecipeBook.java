package net.minecraft.stats;

import com.google.common.collect.Sets;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.crafting.RecipeHolder;

public class RecipeBook {
    protected final Set<ResourceLocation> known = Sets.newHashSet();
    protected final Set<ResourceLocation> highlight = Sets.newHashSet();
    private final RecipeBookSettings bookSettings = new RecipeBookSettings();

    public void copyOverData(RecipeBook other) {
        this.known.clear();
        this.highlight.clear();
        this.bookSettings.replaceFrom(other.bookSettings);
        this.known.addAll(other.known);
        this.highlight.addAll(other.highlight);
    }

    public void add(RecipeHolder<?> recipe) {
        if (!recipe.value().isSpecial()) {
            this.add(recipe.id());
        }
    }

    protected void add(ResourceLocation recipeId) {
        this.known.add(recipeId);
    }

    public boolean contains(@Nullable RecipeHolder<?> recipe) {
        return recipe == null ? false : this.known.contains(recipe.id());
    }

    public boolean contains(ResourceLocation recipeId) {
        return this.known.contains(recipeId);
    }

    public void remove(RecipeHolder<?> recipe) {
        this.remove(recipe.id());
    }

    protected void remove(ResourceLocation recipeId) {
        this.known.remove(recipeId);
        this.highlight.remove(recipeId);
    }

    public boolean willHighlight(RecipeHolder<?> recipe) {
        return this.highlight.contains(recipe.id());
    }

    public void removeHighlight(RecipeHolder<?> recipe) {
        this.highlight.remove(recipe.id());
    }

    public void addHighlight(RecipeHolder<?> recipe) {
        this.addHighlight(recipe.id());
    }

    protected void addHighlight(ResourceLocation recipeId) {
        this.highlight.add(recipeId);
    }

    public boolean isOpen(RecipeBookType bookType) {
        return this.bookSettings.isOpen(bookType);
    }

    public void setOpen(RecipeBookType bookType, boolean open) {
        this.bookSettings.setOpen(bookType, open);
    }

    public boolean isFiltering(RecipeBookMenu<?, ?> bookMenu) {
        return this.isFiltering(bookMenu.getRecipeBookType());
    }

    public boolean isFiltering(RecipeBookType bookType) {
        return this.bookSettings.isFiltering(bookType);
    }

    public void setFiltering(RecipeBookType bookType, boolean filtering) {
        this.bookSettings.setFiltering(bookType, filtering);
    }

    public void setBookSettings(RecipeBookSettings settings) {
        this.bookSettings.replaceFrom(settings);
    }

    public RecipeBookSettings getBookSettings() {
        return this.bookSettings.copy();
    }

    public void setBookSetting(RecipeBookType bookType, boolean open, boolean filtering) {
        this.bookSettings.setOpen(bookType, open);
        this.bookSettings.setFiltering(bookType, filtering);
    }
}
