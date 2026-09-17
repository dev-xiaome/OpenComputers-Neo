package net.minecraft.client.gui.screens.recipebook;

import com.google.common.collect.Lists;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class GhostRecipe {
    @Nullable
    private RecipeHolder<?> recipe;
    private final List<GhostRecipe.GhostIngredient> ingredients = Lists.newArrayList();
    float time;

    public void clear() {
        this.recipe = null;
        this.ingredients.clear();
        this.time = 0.0F;
    }

    public void addIngredient(Ingredient ingredient, int x, int y) {
        this.ingredients.add(new GhostRecipe.GhostIngredient(ingredient, x, y));
    }

    public GhostRecipe.GhostIngredient get(int index) {
        return this.ingredients.get(index);
    }

    public int size() {
        return this.ingredients.size();
    }

    @Nullable
    public RecipeHolder<?> getRecipe() {
        return this.recipe;
    }

    public void setRecipe(RecipeHolder<?> recipe) {
        this.recipe = recipe;
    }

    public void render(GuiGraphics guiGraphics, Minecraft minecraft, int leftPos, int topPos, boolean offset, float partialTick) {
        if (!Screen.hasControlDown()) {
            this.time += partialTick;
        }

        for (int i = 0; i < this.ingredients.size(); i++) {
            GhostRecipe.GhostIngredient ghostrecipe$ghostingredient = this.ingredients.get(i);
            int j = ghostrecipe$ghostingredient.getX() + leftPos;
            int k = ghostrecipe$ghostingredient.getY() + topPos;
            if (i == 0 && offset) {
                guiGraphics.fill(j - 4, k - 4, j + 20, k + 20, 822018048);
            } else {
                guiGraphics.fill(j, k, j + 16, k + 16, 822018048);
            }

            ItemStack itemstack = ghostrecipe$ghostingredient.getItem();
            guiGraphics.renderFakeItem(itemstack, j, k);
            guiGraphics.fill(RenderType.guiGhostRecipeOverlay(), j, k, j + 16, k + 16, 822083583);
            if (i == 0) {
                guiGraphics.renderItemDecorations(minecraft.font, itemstack, j, k);
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    public class GhostIngredient {
        private final Ingredient ingredient;
        private final int x;
        private final int y;

        public GhostIngredient(Ingredient ingredient, int x, int y) {
            this.ingredient = ingredient;
            this.x = x;
            this.y = y;
        }

        public int getX() {
            return this.x;
        }

        public int getY() {
            return this.y;
        }

        public ItemStack getItem() {
            ItemStack[] aitemstack = this.ingredient.getItems();
            return aitemstack.length == 0 ? ItemStack.EMPTY : aitemstack[Mth.floor(GhostRecipe.this.time / 30.0F) % aitemstack.length];
        }
    }
}
