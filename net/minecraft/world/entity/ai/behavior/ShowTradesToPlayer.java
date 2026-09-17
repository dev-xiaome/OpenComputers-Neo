package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

public class ShowTradesToPlayer extends Behavior<Villager> {
    private static final int MAX_LOOK_TIME = 900;
    private static final int STARTING_LOOK_TIME = 40;
    @Nullable
    private ItemStack playerItemStack;
    private final List<ItemStack> displayItems = Lists.newArrayList();
    private int cycleCounter;
    private int displayIndex;
    private int lookTime;

    public ShowTradesToPlayer(int minDuration, int maxDuration) {
        super(ImmutableMap.of(MemoryModuleType.INTERACTION_TARGET, MemoryStatus.VALUE_PRESENT), minDuration, maxDuration);
    }

    public boolean checkExtraStartConditions(ServerLevel level, Villager owner) {
        Brain<?> brain = owner.getBrain();
        if (brain.getMemory(MemoryModuleType.INTERACTION_TARGET).isEmpty()) {
            return false;
        } else {
            LivingEntity livingentity = brain.getMemory(MemoryModuleType.INTERACTION_TARGET).get();
            return livingentity.getType() == EntityType.PLAYER
                && owner.isAlive()
                && livingentity.isAlive()
                && !owner.isBaby()
                && owner.distanceToSqr(livingentity) <= 17.0;
        }
    }

    public boolean canStillUse(ServerLevel level, Villager entity, long gameTime) {
        return this.checkExtraStartConditions(level, entity)
            && this.lookTime > 0
            && entity.getBrain().getMemory(MemoryModuleType.INTERACTION_TARGET).isPresent();
    }

    public void start(ServerLevel level, Villager entity, long gameTime) {
        super.start(level, entity, gameTime);
        this.lookAtTarget(entity);
        this.cycleCounter = 0;
        this.displayIndex = 0;
        this.lookTime = 40;
    }

    public void tick(ServerLevel level, Villager owner, long gameTime) {
        LivingEntity livingentity = this.lookAtTarget(owner);
        this.findItemsToDisplay(livingentity, owner);
        if (!this.displayItems.isEmpty()) {
            this.displayCyclingItems(owner);
        } else {
            clearHeldItem(owner);
            this.lookTime = Math.min(this.lookTime, 40);
        }

        this.lookTime--;
    }

    public void stop(ServerLevel level, Villager entity, long gameTime) {
        super.stop(level, entity, gameTime);
        entity.getBrain().eraseMemory(MemoryModuleType.INTERACTION_TARGET);
        clearHeldItem(entity);
        this.playerItemStack = null;
    }

    private void findItemsToDisplay(LivingEntity entity, Villager villager) {
        boolean flag = false;
        ItemStack itemstack = entity.getMainHandItem();
        if (this.playerItemStack == null || !ItemStack.isSameItem(this.playerItemStack, itemstack)) {
            this.playerItemStack = itemstack;
            flag = true;
            this.displayItems.clear();
        }

        if (flag && !this.playerItemStack.isEmpty()) {
            this.updateDisplayItems(villager);
            if (!this.displayItems.isEmpty()) {
                this.lookTime = 900;
                this.displayFirstItem(villager);
            }
        }
    }

    private void displayFirstItem(Villager villager) {
        displayAsHeldItem(villager, this.displayItems.get(0));
    }

    private void updateDisplayItems(Villager villager) {
        for (MerchantOffer merchantoffer : villager.getOffers()) {
            if (!merchantoffer.isOutOfStock() && this.playerItemStackMatchesCostOfOffer(merchantoffer)) {
                this.displayItems.add(merchantoffer.assemble());
            }
        }
    }

    private boolean playerItemStackMatchesCostOfOffer(MerchantOffer offer) {
        return ItemStack.isSameItem(this.playerItemStack, offer.getCostA()) || ItemStack.isSameItem(this.playerItemStack, offer.getCostB());
    }

    private static void clearHeldItem(Villager villager) {
        villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        villager.setDropChance(EquipmentSlot.MAINHAND, 0.085F);
    }

    private static void displayAsHeldItem(Villager villager, ItemStack item) {
        villager.setItemSlot(EquipmentSlot.MAINHAND, item);
        villager.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    private LivingEntity lookAtTarget(Villager villager) {
        Brain<?> brain = villager.getBrain();
        LivingEntity livingentity = brain.getMemory(MemoryModuleType.INTERACTION_TARGET).get();
        brain.setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(livingentity, true));
        return livingentity;
    }

    private void displayCyclingItems(Villager villager) {
        if (this.displayItems.size() >= 2 && ++this.cycleCounter >= 40) {
            this.displayIndex++;
            this.cycleCounter = 0;
            if (this.displayIndex > this.displayItems.size() - 1) {
                this.displayIndex = 0;
            }

            displayAsHeldItem(villager, this.displayItems.get(this.displayIndex));
        }
    }
}
