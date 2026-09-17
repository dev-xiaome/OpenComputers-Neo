package net.minecraft.world.item;

import net.minecraft.world.item.enchantment.EnchantmentInstance;

public class EnchantedBookItem extends Item {
    public EnchantedBookItem(Item.Properties properties) {
        super(properties);
    }

    /**
     * Checks isDamagable and if it cannot be stacked
     */
    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    /**
     * Returns the ItemStack of an enchanted version of this item.
     */
    public static ItemStack createForEnchantment(EnchantmentInstance instance) {
        ItemStack itemstack = new ItemStack(Items.ENCHANTED_BOOK);
        itemstack.enchant(instance.enchantment, instance.level);
        return itemstack;
    }
}
