package net.minecraft.world.entity;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public interface SlotAccess {
    SlotAccess NULL = new SlotAccess() {
        @Override
        public ItemStack get() {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean set(ItemStack p_147314_) {
            return false;
        }
    };

    static SlotAccess of(final Supplier<ItemStack> getter, final Consumer<ItemStack> setter) {
        return new SlotAccess() {
            @Override
            public ItemStack get() {
                return getter.get();
            }

            @Override
            public boolean set(ItemStack p_147324_) {
                setter.accept(p_147324_);
                return true;
            }
        };
    }

    static SlotAccess forContainer(final Container inventory, final int slot, final Predicate<ItemStack> stackFilter) {
        return new SlotAccess() {
            @Override
            public ItemStack get() {
                return inventory.getItem(slot);
            }

            @Override
            public boolean set(ItemStack p_147334_) {
                if (!stackFilter.test(p_147334_)) {
                    return false;
                } else {
                    inventory.setItem(slot, p_147334_);
                    return true;
                }
            }
        };
    }

    static SlotAccess forContainer(Container inventory, int slot) {
        return forContainer(inventory, slot, p_147310_ -> true);
    }

    static SlotAccess forEquipmentSlot(final LivingEntity entity, final EquipmentSlot slot, final Predicate<ItemStack> stackFilter) {
        return new SlotAccess() {
            @Override
            public ItemStack get() {
                return entity.getItemBySlot(slot);
            }

            @Override
            public boolean set(ItemStack p_341038_) {
                if (!stackFilter.test(p_341038_)) {
                    return false;
                } else {
                    entity.setItemSlot(slot, p_341038_);
                    return true;
                }
            }
        };
    }

    static SlotAccess forEquipmentSlot(LivingEntity entity, EquipmentSlot slot) {
        return forEquipmentSlot(entity, slot, p_147308_ -> true);
    }

    ItemStack get();

    boolean set(ItemStack carried);
}
