package net.minecraft.world.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;

public interface EquipmentUser {
    void setItemSlot(EquipmentSlot slot, ItemStack stack);

    ItemStack getItemBySlot(EquipmentSlot slot);

    void setDropChance(EquipmentSlot slot, float dropChance);

    default void equip(EquipmentTable equipmentTable, LootParams params) {
        this.equip(equipmentTable.lootTable(), params, equipmentTable.slotDropChances());
    }

    default void equip(ResourceKey<LootTable> equipmentLootTable, LootParams params, Map<EquipmentSlot, Float> slotDropChances) {
        this.equip(equipmentLootTable, params, 0L, slotDropChances);
    }

    default void equip(ResourceKey<LootTable> equipmentLootTable, LootParams params, long seed, Map<EquipmentSlot, Float> slotDropChances) {
        if (!equipmentLootTable.equals(BuiltInLootTables.EMPTY)) {
            LootTable loottable = params.getLevel().getServer().reloadableRegistries().getLootTable(equipmentLootTable);
            if (loottable != LootTable.EMPTY) {
                List<ItemStack> list = loottable.getRandomItems(params, seed);
                List<EquipmentSlot> list1 = new ArrayList<>();

                for (ItemStack itemstack : list) {
                    EquipmentSlot equipmentslot = this.resolveSlot(itemstack, list1);
                    if (equipmentslot != null) {
                        ItemStack itemstack1 = equipmentslot.limit(itemstack);
                        this.setItemSlot(equipmentslot, itemstack1);
                        Float f = slotDropChances.get(equipmentslot);
                        if (f != null) {
                            this.setDropChance(equipmentslot, f);
                        }

                        list1.add(equipmentslot);
                    }
                }
            }
        }
    }

    @Nullable
    default EquipmentSlot resolveSlot(ItemStack stack, List<EquipmentSlot> excludedSlots) {
        if (stack.isEmpty()) {
            return null;
        } else {
            Equipable equipable = Equipable.get(stack);
            if (equipable != null) {
                EquipmentSlot equipmentslot = equipable.getEquipmentSlot();
                if (!excludedSlots.contains(equipmentslot)) {
                    return equipmentslot;
                }
            } else if (!excludedSlots.contains(EquipmentSlot.MAINHAND)) {
                return EquipmentSlot.MAINHAND;
            }

            return null;
        }
    }
}
