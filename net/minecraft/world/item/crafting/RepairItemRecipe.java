package net.minecraft.world.item.crafting;

import com.mojang.datafixers.util.Pair;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;

public class RepairItemRecipe extends CustomRecipe {
    public RepairItemRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Nullable
    private Pair<ItemStack, ItemStack> getItemsToCombine(CraftingInput input) {
        ItemStack itemstack = null;
        ItemStack itemstack1 = null;

        for (int i = 0; i < input.size(); i++) {
            ItemStack itemstack2 = input.getItem(i);
            if (!itemstack2.isEmpty()) {
                if (itemstack == null) {
                    itemstack = itemstack2;
                } else {
                    if (itemstack1 != null) {
                        return null;
                    }

                    itemstack1 = itemstack2;
                }
            }
        }

        return itemstack != null && itemstack1 != null && canCombine(itemstack, itemstack1) ? Pair.of(itemstack, itemstack1) : null;
    }

    private static boolean canCombine(ItemStack stack1, ItemStack stack2) {
        return stack2.is(stack1.getItem())
            && stack1.getCount() == 1
            && stack2.getCount() == 1
            && stack1.has(DataComponents.MAX_DAMAGE)
            && stack2.has(DataComponents.MAX_DAMAGE)
            && stack1.has(DataComponents.DAMAGE)
            && stack2.has(DataComponents.DAMAGE)
            && stack1.isRepairable()
            && stack2.isRepairable();
    }

    public boolean matches(CraftingInput input, Level level) {
        return this.getItemsToCombine(input) != null;
    }

    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        Pair<ItemStack, ItemStack> pair = this.getItemsToCombine(input);
        if (pair == null) {
            return ItemStack.EMPTY;
        } else {
            ItemStack itemstack = pair.getFirst();
            ItemStack itemstack1 = pair.getSecond();
            int i = Math.max(itemstack.getMaxDamage(), itemstack1.getMaxDamage());
            int j = itemstack.getMaxDamage() - itemstack.getDamageValue();
            int k = itemstack1.getMaxDamage() - itemstack1.getDamageValue();
            int l = j + k + i * 5 / 100;
            ItemStack itemstack2 = new ItemStack(itemstack.getItem());
            itemstack2.set(DataComponents.MAX_DAMAGE, i);
            itemstack2.setDamageValue(Math.max(i - l, 0));
            ItemEnchantments itemenchantments = EnchantmentHelper.getEnchantmentsForCrafting(itemstack);
            ItemEnchantments itemenchantments1 = EnchantmentHelper.getEnchantmentsForCrafting(itemstack1);
            EnchantmentHelper.updateEnchantments(
                itemstack2,
                p_344422_ -> registries.lookupOrThrow(Registries.ENCHANTMENT)
                        .listElements()
                        .filter(p_344414_ -> p_344414_.is(EnchantmentTags.CURSE))
                        .forEach(p_344418_ -> {
                            int i1 = Math.max(itemenchantments.getLevel(p_344418_), itemenchantments1.getLevel(p_344418_));
                            if (i1 > 0) {
                                p_344422_.upgrade(p_344418_, i1);
                            }
                        })
            );
            return itemstack2;
        }
    }

    /**
     * Used to determine if this recipe can fit in a grid of the given width/height
     */
    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RecipeSerializer.REPAIR_ITEM;
    }
}
