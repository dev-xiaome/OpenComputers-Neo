package net.minecraft.world;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;

public class ContainerHelper {
    public static final String TAG_ITEMS = "Items";

    public static ItemStack removeItem(List<ItemStack> stacks, int index, int amount) {
        return index >= 0 && index < stacks.size() && !stacks.get(index).isEmpty() && amount > 0
            ? stacks.get(index).split(amount)
            : ItemStack.EMPTY;
    }

    public static ItemStack takeItem(List<ItemStack> stacks, int index) {
        return index >= 0 && index < stacks.size() ? stacks.set(index, ItemStack.EMPTY) : ItemStack.EMPTY;
    }

    public static CompoundTag saveAllItems(CompoundTag tag, NonNullList<ItemStack> items, HolderLookup.Provider levelRegistry) {
        return saveAllItems(tag, items, true, levelRegistry);
    }

    public static CompoundTag saveAllItems(CompoundTag tag, NonNullList<ItemStack> items, boolean alwaysPutTag, HolderLookup.Provider levelRegistry) {
        ListTag listtag = new ListTag();

        for (int i = 0; i < items.size(); i++) {
            ItemStack itemstack = items.get(i);
            if (!itemstack.isEmpty()) {
                CompoundTag compoundtag = new CompoundTag();
                compoundtag.putByte("Slot", (byte)i);
                listtag.add(itemstack.save(levelRegistry, compoundtag));
            }
        }

        if (!listtag.isEmpty() || alwaysPutTag) {
            tag.put("Items", listtag);
        }

        return tag;
    }

    public static void loadAllItems(CompoundTag tag, NonNullList<ItemStack> items, HolderLookup.Provider levelRegistry) {
        ListTag listtag = tag.getList("Items", 10);

        for (int i = 0; i < listtag.size(); i++) {
            CompoundTag compoundtag = listtag.getCompound(i);
            int j = compoundtag.getByte("Slot") & 255;
            if (j >= 0 && j < items.size()) {
                items.set(j, ItemStack.parse(levelRegistry, compoundtag).orElse(ItemStack.EMPTY));
            }
        }
    }

    /**
     * Clears items from the inventory matching a predicate.
     * @return The amount of items cleared
     *
     * @param maxItems The maximum amount of items to be cleared. A negative value
     *                 means unlimited and 0 means count how many items are found that
     *                 could be cleared.
     */
    public static int clearOrCountMatchingItems(Container container, Predicate<ItemStack> itemPredicate, int maxItems, boolean simulate) {
        int i = 0;

        for (int j = 0; j < container.getContainerSize(); j++) {
            ItemStack itemstack = container.getItem(j);
            int k = clearOrCountMatchingItems(itemstack, itemPredicate, maxItems - i, simulate);
            if (k > 0 && !simulate && itemstack.isEmpty()) {
                container.setItem(j, ItemStack.EMPTY);
            }

            i += k;
        }

        return i;
    }

    public static int clearOrCountMatchingItems(ItemStack stack, Predicate<ItemStack> itemPredicate, int maxItems, boolean simulate) {
        if (stack.isEmpty() || !itemPredicate.test(stack)) {
            return 0;
        } else if (simulate) {
            return stack.getCount();
        } else {
            int i = maxItems < 0 ? stack.getCount() : Math.min(maxItems, stack.getCount());
            stack.shrink(i);
            return i;
        }
    }
}
