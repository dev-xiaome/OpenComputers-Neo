package net.minecraft.recipebook;

import java.util.Iterator;
import net.minecraft.util.Mth;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;

public interface PlaceRecipe<T> {
    default void placeRecipe(int width, int height, int outputSlot, RecipeHolder<?> recipe, Iterator<T> ingredients, int maxAmount) {
        int i = width;
        int j = height;
        if (recipe.value() instanceof ShapedRecipe shapedrecipe) {
            i = shapedrecipe.getWidth();
            j = shapedrecipe.getHeight();
        }

        int k1 = 0;

        for (int k = 0; k < height; k++) {
            if (k1 == outputSlot) {
                k1++;
            }

            boolean flag = (float)j < (float)height / 2.0F;
            int l = Mth.floor((float)height / 2.0F - (float)j / 2.0F);
            if (flag && l > k) {
                k1 += width;
                k++;
            }

            for (int i1 = 0; i1 < width; i1++) {
                if (!ingredients.hasNext()) {
                    return;
                }

                flag = (float)i < (float)width / 2.0F;
                l = Mth.floor((float)width / 2.0F - (float)i / 2.0F);
                int j1 = i;
                boolean flag1 = i1 < i;
                if (flag) {
                    j1 = l + i;
                    flag1 = l <= i1 && i1 < l + i;
                }

                if (flag1) {
                    this.addItemToSlot(ingredients.next(), k1, maxAmount, i1, k);
                } else if (j1 == i1) {
                    k1 += width - i1;
                    break;
                }

                k1++;
            }
        }
    }

    void addItemToSlot(T item, int slot, int maxAmount, int x, int y);
}
