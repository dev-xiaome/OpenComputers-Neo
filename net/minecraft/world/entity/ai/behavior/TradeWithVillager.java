package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class TradeWithVillager extends Behavior<Villager> {
    private Set<Item> trades = ImmutableSet.of();

    public TradeWithVillager() {
        super(
            ImmutableMap.of(
                MemoryModuleType.INTERACTION_TARGET, MemoryStatus.VALUE_PRESENT, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT
            )
        );
    }

    protected boolean checkExtraStartConditions(ServerLevel level, Villager owner) {
        return BehaviorUtils.targetIsValid(owner.getBrain(), MemoryModuleType.INTERACTION_TARGET, EntityType.VILLAGER);
    }

    protected boolean canStillUse(ServerLevel level, Villager entity, long gameTime) {
        return this.checkExtraStartConditions(level, entity);
    }

    protected void start(ServerLevel level, Villager entity, long gameTime) {
        Villager villager = (Villager)entity.getBrain().getMemory(MemoryModuleType.INTERACTION_TARGET).get();
        BehaviorUtils.lockGazeAndWalkToEachOther(entity, villager, 0.5F, 2);
        this.trades = figureOutWhatIAmWillingToTrade(entity, villager);
    }

    protected void tick(ServerLevel level, Villager owner, long gameTime) {
        Villager villager = (Villager)owner.getBrain().getMemory(MemoryModuleType.INTERACTION_TARGET).get();
        if (!(owner.distanceToSqr(villager) > 5.0)) {
            BehaviorUtils.lockGazeAndWalkToEachOther(owner, villager, 0.5F, 2);
            owner.gossip(level, villager, gameTime);
            if (owner.hasExcessFood() && (owner.getVillagerData().getProfession() == VillagerProfession.FARMER || villager.wantsMoreFood())) {
                throwHalfStack(owner, Villager.FOOD_POINTS.keySet(), villager);
            }

            if (villager.getVillagerData().getProfession() == VillagerProfession.FARMER
                && owner.getInventory().countItem(Items.WHEAT) > Items.WHEAT.getDefaultMaxStackSize() / 2) {
                throwHalfStack(owner, ImmutableSet.of(Items.WHEAT), villager);
            }

            if (!this.trades.isEmpty() && owner.getInventory().hasAnyOf(this.trades)) {
                throwHalfStack(owner, this.trades, villager);
            }
        }
    }

    protected void stop(ServerLevel level, Villager entity, long gameTime) {
        entity.getBrain().eraseMemory(MemoryModuleType.INTERACTION_TARGET);
    }

    private static Set<Item> figureOutWhatIAmWillingToTrade(Villager villager, Villager other) {
        ImmutableSet<Item> immutableset = other.getVillagerData().getProfession().requestedItems();
        ImmutableSet<Item> immutableset1 = villager.getVillagerData().getProfession().requestedItems();
        return immutableset.stream().filter(p_24431_ -> !immutableset1.contains(p_24431_)).collect(Collectors.toSet());
    }

    private static void throwHalfStack(Villager villager, Set<Item> stack, LivingEntity entity) {
        SimpleContainer simplecontainer = villager.getInventory();
        ItemStack itemstack = ItemStack.EMPTY;
        int i = 0;

        while (i < simplecontainer.getContainerSize()) {
            ItemStack itemstack1;
            Item item;
            int j;
            label28: {
                itemstack1 = simplecontainer.getItem(i);
                if (!itemstack1.isEmpty()) {
                    item = itemstack1.getItem();
                    if (stack.contains(item)) {
                        if (itemstack1.getCount() > itemstack1.getMaxStackSize() / 2) {
                            j = itemstack1.getCount() / 2;
                            break label28;
                        }

                        if (itemstack1.getCount() > 24) {
                            j = itemstack1.getCount() - 24;
                            break label28;
                        }
                    }
                }

                i++;
                continue;
            }

            itemstack1.shrink(j);
            itemstack = new ItemStack(item, j);
            break;
        }

        if (!itemstack.isEmpty()) {
            BehaviorUtils.throwItem(villager, itemstack, entity.position());
        }
    }
}
