package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

public class UseBonemeal extends Behavior<Villager> {
    private static final int BONEMEALING_DURATION = 80;
    private long nextWorkCycleTime;
    private long lastBonemealingSession;
    private int timeWorkedSoFar;
    private Optional<BlockPos> cropPos = Optional.empty();

    public UseBonemeal() {
        super(ImmutableMap.of(MemoryModuleType.LOOK_TARGET, MemoryStatus.VALUE_ABSENT, MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT));
    }

    protected boolean checkExtraStartConditions(ServerLevel level, Villager owner) {
        if (owner.tickCount % 10 == 0 && (this.lastBonemealingSession == 0L || this.lastBonemealingSession + 160L <= (long)owner.tickCount)) {
            if (owner.getInventory().countItem(Items.BONE_MEAL) <= 0) {
                return false;
            } else {
                this.cropPos = this.pickNextTarget(level, owner);
                return this.cropPos.isPresent();
            }
        } else {
            return false;
        }
    }

    protected boolean canStillUse(ServerLevel level, Villager entity, long gameTime) {
        return this.timeWorkedSoFar < 80 && this.cropPos.isPresent();
    }

    private Optional<BlockPos> pickNextTarget(ServerLevel level, Villager villager) {
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();
        Optional<BlockPos> optional = Optional.empty();
        int i = 0;

        for (int j = -1; j <= 1; j++) {
            for (int k = -1; k <= 1; k++) {
                for (int l = -1; l <= 1; l++) {
                    blockpos$mutableblockpos.setWithOffset(villager.blockPosition(), j, k, l);
                    if (this.validPos(blockpos$mutableblockpos, level)) {
                        if (level.random.nextInt(++i) == 0) {
                            optional = Optional.of(blockpos$mutableblockpos.immutable());
                        }
                    }
                }
            }
        }

        return optional;
    }

    private boolean validPos(BlockPos pos, ServerLevel level) {
        BlockState blockstate = level.getBlockState(pos);
        Block block = blockstate.getBlock();
        return block instanceof CropBlock && !((CropBlock)block).isMaxAge(blockstate);
    }

    protected void start(ServerLevel level, Villager entity, long gameTime) {
        this.setCurrentCropAsTarget(entity);
        entity.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BONE_MEAL));
        this.nextWorkCycleTime = gameTime;
        this.timeWorkedSoFar = 0;
    }

    private void setCurrentCropAsTarget(Villager villager) {
        this.cropPos.ifPresent(p_24484_ -> {
            BlockPosTracker blockpostracker = new BlockPosTracker(p_24484_);
            villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, blockpostracker);
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(blockpostracker, 0.5F, 1));
        });
    }

    protected void stop(ServerLevel level, Villager entity, long gameTime) {
        entity.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        this.lastBonemealingSession = (long)entity.tickCount;
    }

    protected void tick(ServerLevel level, Villager owner, long gameTime) {
        BlockPos blockpos = this.cropPos.get();
        if (gameTime >= this.nextWorkCycleTime && blockpos.closerToCenterThan(owner.position(), 1.0)) {
            ItemStack itemstack = ItemStack.EMPTY;
            SimpleContainer simplecontainer = owner.getInventory();
            int i = simplecontainer.getContainerSize();

            for (int j = 0; j < i; j++) {
                ItemStack itemstack1 = simplecontainer.getItem(j);
                if (itemstack1.is(Items.BONE_MEAL)) {
                    itemstack = itemstack1;
                    break;
                }
            }

            if (!itemstack.isEmpty() && BoneMealItem.growCrop(itemstack, level, blockpos)) {
                level.levelEvent(1505, blockpos, 15);
                this.cropPos = this.pickNextTarget(level, owner);
                this.setCurrentCropAsTarget(owner);
                this.nextWorkCycleTime = gameTime + 40L;
            }

            this.timeWorkedSoFar++;
        }
    }
}
