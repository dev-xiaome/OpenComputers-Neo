package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.pathfinder.Path;

public class VillagerMakeLove extends Behavior<Villager> {
    private long birthTimestamp;

    public VillagerMakeLove() {
        super(
            ImmutableMap.of(
                MemoryModuleType.BREED_TARGET, MemoryStatus.VALUE_PRESENT, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT
            ),
            350,
            350
        );
    }

    protected boolean checkExtraStartConditions(ServerLevel level, Villager owner) {
        return this.isBreedingPossible(owner);
    }

    protected boolean canStillUse(ServerLevel level, Villager entity, long gameTime) {
        return gameTime <= this.birthTimestamp && this.isBreedingPossible(entity);
    }

    protected void start(ServerLevel level, Villager entity, long gameTime) {
        AgeableMob ageablemob = entity.getBrain().getMemory(MemoryModuleType.BREED_TARGET).get();
        BehaviorUtils.lockGazeAndWalkToEachOther(entity, ageablemob, 0.5F, 2);
        level.broadcastEntityEvent(ageablemob, (byte)18);
        level.broadcastEntityEvent(entity, (byte)18);
        int i = 275 + entity.getRandom().nextInt(50);
        this.birthTimestamp = gameTime + (long)i;
    }

    protected void tick(ServerLevel level, Villager owner, long gameTime) {
        Villager villager = (Villager)owner.getBrain().getMemory(MemoryModuleType.BREED_TARGET).get();
        if (!(owner.distanceToSqr(villager) > 5.0)) {
            BehaviorUtils.lockGazeAndWalkToEachOther(owner, villager, 0.5F, 2);
            if (gameTime >= this.birthTimestamp) {
                owner.eatAndDigestFood();
                villager.eatAndDigestFood();
                this.tryToGiveBirth(level, owner, villager);
            } else if (owner.getRandom().nextInt(35) == 0) {
                level.broadcastEntityEvent(villager, (byte)12);
                level.broadcastEntityEvent(owner, (byte)12);
            }
        }
    }

    private void tryToGiveBirth(ServerLevel level, Villager parent, Villager partner) {
        Optional<BlockPos> optional = this.takeVacantBed(level, parent);
        if (optional.isEmpty()) {
            level.broadcastEntityEvent(partner, (byte)13);
            level.broadcastEntityEvent(parent, (byte)13);
        } else {
            Optional<Villager> optional1 = this.breed(level, parent, partner);
            if (optional1.isPresent()) {
                this.giveBedToChild(level, optional1.get(), optional.get());
            } else {
                level.getPoiManager().release(optional.get());
                DebugPackets.sendPoiTicketCountPacket(level, optional.get());
            }
        }
    }

    protected void stop(ServerLevel level, Villager entity, long gameTime) {
        entity.getBrain().eraseMemory(MemoryModuleType.BREED_TARGET);
    }

    private boolean isBreedingPossible(Villager villager) {
        Brain<Villager> brain = villager.getBrain();
        Optional<AgeableMob> optional = brain.getMemory(MemoryModuleType.BREED_TARGET).filter(p_348244_ -> p_348244_.getType() == EntityType.VILLAGER);
        return optional.isEmpty()
            ? false
            : BehaviorUtils.targetIsValid(brain, MemoryModuleType.BREED_TARGET, EntityType.VILLAGER) && villager.canBreed() && optional.get().canBreed();
    }

    private Optional<BlockPos> takeVacantBed(ServerLevel level, Villager villager) {
        return level.getPoiManager()
            .take(
                p_217509_ -> p_217509_.is(PoiTypes.HOME), (p_217506_, p_217507_) -> this.canReach(villager, p_217507_, p_217506_), villager.blockPosition(), 48
            );
    }

    private boolean canReach(Villager villager, BlockPos pos, Holder<PoiType> poiType) {
        Path path = villager.getNavigation().createPath(pos, poiType.value().validRange());
        return path != null && path.canReach();
    }

    private Optional<Villager> breed(ServerLevel level, Villager parent, Villager partner) {
        Villager villager = parent.getBreedOffspring(level, partner);
        if (villager == null) {
            return Optional.empty();
        } else {
            parent.setAge(6000);
            partner.setAge(6000);
            villager.setAge(-24000);
            villager.moveTo(parent.getX(), parent.getY(), parent.getZ(), 0.0F, 0.0F);
            level.addFreshEntityWithPassengers(villager);
            // Neo: If villager is blocked from spawning (e.g., FinalizeSpawnEvent), then breed should be unsuccessful
            if (!villager.isAddedToLevel()) return Optional.empty();
            level.broadcastEntityEvent(villager, (byte)12);
            return Optional.of(villager);
        }
    }

    private void giveBedToChild(ServerLevel level, Villager villager, BlockPos pos) {
        GlobalPos globalpos = GlobalPos.of(level.dimension(), pos);
        villager.getBrain().setMemory(MemoryModuleType.HOME, globalpos);
    }
}
