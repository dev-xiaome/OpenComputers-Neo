package net.minecraft.world.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class ItemUtils {
    public static InteractionResultHolder<ItemStack> startUsingInstantly(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    public static ItemStack createFilledResult(ItemStack emptyStack, Player player, ItemStack filledStack, boolean preventDuplicates) {
        boolean flag = player.hasInfiniteMaterials();
        if (preventDuplicates && flag) {
            if (!player.getInventory().contains(filledStack)) {
                player.getInventory().add(filledStack);
            }

            return emptyStack;
        } else {
            emptyStack.consume(1, player);
            if (emptyStack.isEmpty()) {
                return filledStack;
            } else {
                if (!player.getInventory().add(filledStack)) {
                    player.drop(filledStack, false);
                }

                return emptyStack;
            }
        }
    }

    public static ItemStack createFilledResult(ItemStack emptyStack, Player player, ItemStack filledStack) {
        return createFilledResult(emptyStack, player, filledStack, true);
    }

    public static void onContainerDestroyed(ItemEntity container, Iterable<ItemStack> contents) {
        Level level = container.level();
        if (!level.isClientSide) {
            contents.forEach(p_352858_ -> level.addFreshEntity(new ItemEntity(level, container.getX(), container.getY(), container.getZ(), p_352858_)));
        }
    }
}
