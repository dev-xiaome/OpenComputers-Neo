package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.inventory.SlotRange;
import net.minecraft.world.inventory.SlotRanges;

public record SlotsPredicate(Map<SlotRange, ItemPredicate> slots) {
    public static final Codec<SlotsPredicate> CODEC = Codec.unboundedMap(SlotRanges.CODEC, ItemPredicate.CODEC)
        .xmap(SlotsPredicate::new, SlotsPredicate::slots);

    public boolean matches(Entity entity) {
        for (Entry<SlotRange, ItemPredicate> entry : this.slots.entrySet()) {
            if (!matchSlots(entity, entry.getValue(), entry.getKey().slots())) {
                return false;
            }
        }

        return true;
    }

    private static boolean matchSlots(Entity entity, ItemPredicate predicate, IntList slots) {
        for (int i = 0; i < slots.size(); i++) {
            int j = slots.getInt(i);
            SlotAccess slotaccess = entity.getSlot(j);
            if (predicate.test(slotaccess.get())) {
                return true;
            }
        }

        return false;
    }
}
