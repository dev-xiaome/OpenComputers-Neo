package net.minecraft.world.entity.npc;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

public interface InventoryCarrier {
    String TAG_INVENTORY = "Inventory";

    SimpleContainer getInventory();

    static void pickUpItem(Mob mob, InventoryCarrier carrier, ItemEntity itemEntity) {
        ItemStack itemstack = itemEntity.getItem();
        if (mob.wantsToPickUp(itemstack)) {
            SimpleContainer simplecontainer = carrier.getInventory();
            boolean flag = simplecontainer.canAddItem(itemstack);
            if (!flag) {
                return;
            }

            mob.onItemPickup(itemEntity);
            int i = itemstack.getCount();
            ItemStack itemstack1 = simplecontainer.addItem(itemstack);
            mob.take(itemEntity, i - itemstack1.getCount());
            if (itemstack1.isEmpty()) {
                itemEntity.discard();
            } else {
                itemstack.setCount(itemstack1.getCount());
            }
        }
    }

    default void readInventoryFromTag(CompoundTag tag, HolderLookup.Provider levelRegistry) {
        if (tag.contains("Inventory", 9)) {
            this.getInventory().fromTag(tag.getList("Inventory", 10), levelRegistry);
        }
    }

    default void writeInventoryToTag(CompoundTag tag, HolderLookup.Provider levelRegistry) {
        tag.put("Inventory", this.getInventory().createTag(levelRegistry));
    }
}
