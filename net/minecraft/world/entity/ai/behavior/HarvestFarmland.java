package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

public class HarvestFarmland extends Behavior<Villager> {
    private static final int HARVEST_DURATION = 200;
    public static final float SPEED_MODIFIER = 0.5F;
    @Nullable
    private BlockPos aboveFarmlandPos;
    private long nextOkStartTime;
    private int timeWorkedSoFar;
    private final List<BlockPos> validFarmlandAroundVillager = Lists.newArrayList();

    public HarvestFarmland() {
        super(
            ImmutableMap.of(
                MemoryModuleType.LOOK_TARGET,
                MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.WALK_TARGET,
                MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.SECONDARY_JOB_SITE,
                MemoryStatus.VALUE_PRESENT
            )
        );
    }

    protected boolean checkExtraStartConditions(ServerLevel level, Villager owner) {
        if (!net.neoforged.neoforge.event.EventHooks.canEntityGrief(level, owner)) {
            return false;
        } else if (owner.getVillagerData().getProfession() != VillagerProfession.FARMER) {
            return false;
        } else {
            BlockPos.MutableBlockPos blockpos$mutableblockpos = owner.blockPosition().mutable();
            this.validFarmlandAroundVillager.clear();

            for (int i = -1; i <= 1; i++) {
                for (int j = -1; j <= 1; j++) {
                    for (int k = -1; k <= 1; k++) {
                        blockpos$mutableblockpos.set(owner.getX() + (double)i, owner.getY() + (double)j, owner.getZ() + (double)k);
                        if (this.validPos(blockpos$mutableblockpos, level)) {
                            this.validFarmlandAroundVillager.add(new BlockPos(blockpos$mutableblockpos));
                        }
                    }
                }
            }

            this.aboveFarmlandPos = this.getValidFarmland(level);
            return this.aboveFarmlandPos != null;
        }
    }

    @Nullable
    private BlockPos getValidFarmland(ServerLevel serverLevel) {
        return this.validFarmlandAroundVillager.isEmpty()
            ? null
            : this.validFarmlandAroundVillager.get(serverLevel.getRandom().nextInt(this.validFarmlandAroundVillager.size()));
    }

    private boolean validPos(BlockPos pos, ServerLevel serverLevel) {
        BlockState blockstate = serverLevel.getBlockState(pos);
        Block block = blockstate.getBlock();
        Block block1 = serverLevel.getBlockState(pos.below()).getBlock();
        return block instanceof CropBlock && ((CropBlock)block).isMaxAge(blockstate) || blockstate.isAir() && (block1 instanceof FarmBlock || block1.builtInRegistryHolder().is(net.neoforged.neoforge.common.Tags.Blocks.VILLAGER_FARMLANDS));
    }

    protected void start(ServerLevel level, Villager entity, long gameTime) {
        if (gameTime > this.nextOkStartTime && this.aboveFarmlandPos != null) {
            entity.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(this.aboveFarmlandPos));
            entity.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new BlockPosTracker(this.aboveFarmlandPos), 0.5F, 1));
        }
    }

    protected void stop(ServerLevel level, Villager entity, long gameTime) {
        entity.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        entity.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        this.timeWorkedSoFar = 0;
        this.nextOkStartTime = gameTime + 40L;
    }

    protected void tick(ServerLevel level, Villager owner, long gameTime) {
        if (this.aboveFarmlandPos == null || this.aboveFarmlandPos.closerToCenterThan(owner.position(), 1.0)) {
            if (this.aboveFarmlandPos != null && gameTime > this.nextOkStartTime) {
                BlockState blockstate = level.getBlockState(this.aboveFarmlandPos);
                Block block = blockstate.getBlock();
                Block block1 = level.getBlockState(this.aboveFarmlandPos.below()).getBlock();
                if (block instanceof CropBlock && ((CropBlock)block).isMaxAge(blockstate)) {
                    level.destroyBlock(this.aboveFarmlandPos, true, owner);
                }

                if (blockstate.isAir() && (block1 instanceof FarmBlock || block1.builtInRegistryHolder().is(net.neoforged.neoforge.common.Tags.Blocks.VILLAGER_FARMLANDS)) && owner.hasFarmSeeds()) {
                    SimpleContainer simplecontainer = owner.getInventory();

                    for (int i = 0; i < simplecontainer.getContainerSize(); i++) {
                        ItemStack itemstack = simplecontainer.getItem(i);
                        boolean flag = false;
                        if (!itemstack.isEmpty() && itemstack.is(ItemTags.VILLAGER_PLANTABLE_SEEDS) && itemstack.getItem() instanceof BlockItem blockitem) {
                            BlockState blockstate1 = blockitem.getBlock().defaultBlockState();
                            level.setBlockAndUpdate(this.aboveFarmlandPos, blockstate1);
                            level.gameEvent(GameEvent.BLOCK_PLACE, this.aboveFarmlandPos, GameEvent.Context.of(owner, blockstate1));
                            flag = true;
                        } else if (itemstack.getItem() instanceof net.neoforged.neoforge.common.SpecialPlantable specialPlantable && specialPlantable.villagerCanPlantItem(owner)) {
                            if (specialPlantable.canPlacePlantAtPosition(itemstack, level, aboveFarmlandPos, net.minecraft.core.Direction.DOWN)) {
                                specialPlantable.spawnPlantAtPosition(itemstack, level, aboveFarmlandPos, net.minecraft.core.Direction.DOWN);
                                flag = true;
                            }
                        }

                        if (flag) {
                            level.playSound(
                                null,
                                (double)this.aboveFarmlandPos.getX(),
                                (double)this.aboveFarmlandPos.getY(),
                                (double)this.aboveFarmlandPos.getZ(),
                                SoundEvents.CROP_PLANTED,
                                SoundSource.BLOCKS,
                                1.0F,
                                1.0F
                            );
                            itemstack.shrink(1);
                            if (itemstack.isEmpty()) {
                                simplecontainer.setItem(i, ItemStack.EMPTY);
                            }
                            break;
                        }
                    }
                }

                if (block instanceof CropBlock && !((CropBlock)block).isMaxAge(blockstate)) {
                    this.validFarmlandAroundVillager.remove(this.aboveFarmlandPos);
                    this.aboveFarmlandPos = this.getValidFarmland(level);
                    if (this.aboveFarmlandPos != null) {
                        this.nextOkStartTime = gameTime + 20L;
                        owner.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new BlockPosTracker(this.aboveFarmlandPos), 0.5F, 1));
                        owner.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(this.aboveFarmlandPos));
                    }
                }
            }

            this.timeWorkedSoFar++;
        }
    }

    protected boolean canStillUse(ServerLevel level, Villager entity, long gameTime) {
        return this.timeWorkedSoFar < 200;
    }
}
