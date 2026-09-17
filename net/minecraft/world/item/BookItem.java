package net.minecraft.world.item;

public class BookItem extends Item {
    public BookItem(Item.Properties properties) {
        super(properties);
    }

    /**
     * Checks isDamagable and if it cannot be stacked
     */
    @Override
    public boolean isEnchantable(ItemStack stack) {
        return stack.getCount() == 1;
    }

    @Override
    public int getEnchantmentValue() {
        return 1;
    }
}
