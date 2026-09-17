package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.animal.Animal;

public class AnimalMakeLove extends Behavior<Animal> {
    private static final int BREED_RANGE = 3;
    private static final int MIN_DURATION = 60;
    private static final int MAX_DURATION = 110;
    private final EntityType<? extends Animal> partnerType;
    private final float speedModifier;
    private final int closeEnoughDistance;
    private static final int DEFAULT_CLOSE_ENOUGH_DISTANCE = 2;
    private long spawnChildAtTime;

    public AnimalMakeLove(EntityType<? extends Animal> partnerType) {
        this(partnerType, 1.0F, 2);
    }

    public AnimalMakeLove(EntityType<? extends Animal> partnerType, float speedModifier, int closeEnoughDistance) {
        super(
            ImmutableMap.of(
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.BREED_TARGET,
                MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.WALK_TARGET,
                MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET,
                MemoryStatus.REGISTERED,
                MemoryModuleType.IS_PANICKING,
                MemoryStatus.VALUE_ABSENT
            ),
            110
        );
        this.partnerType = partnerType;
        this.speedModifier = speedModifier;
        this.closeEnoughDistance = closeEnoughDistance;
    }

    protected boolean checkExtraStartConditions(ServerLevel level, Animal owner) {
        return owner.isInLove() && this.findValidBreedPartner(owner).isPresent();
    }

    protected void start(ServerLevel level, Animal entity, long gameTime) {
        Animal animal = this.findValidBreedPartner(entity).get();
        entity.getBrain().setMemory(MemoryModuleType.BREED_TARGET, animal);
        animal.getBrain().setMemory(MemoryModuleType.BREED_TARGET, entity);
        BehaviorUtils.lockGazeAndWalkToEachOther(entity, animal, this.speedModifier, this.closeEnoughDistance);
        int i = 60 + entity.getRandom().nextInt(50);
        this.spawnChildAtTime = gameTime + (long)i;
    }

    protected boolean canStillUse(ServerLevel level, Animal entity, long gameTime) {
        if (!this.hasBreedTargetOfRightType(entity)) {
            return false;
        } else {
            Animal animal = this.getBreedTarget(entity);
            return animal.isAlive()
                && entity.canMate(animal)
                && BehaviorUtils.entityIsVisible(entity.getBrain(), animal)
                && gameTime <= this.spawnChildAtTime
                && !entity.isPanicking()
                && !animal.isPanicking();
        }
    }

    protected void tick(ServerLevel level, Animal owner, long gameTime) {
        Animal animal = this.getBreedTarget(owner);
        BehaviorUtils.lockGazeAndWalkToEachOther(owner, animal, this.speedModifier, this.closeEnoughDistance);
        if (owner.closerThan(animal, 3.0)) {
            if (gameTime >= this.spawnChildAtTime) {
                owner.spawnChildFromBreeding(level, animal);
                owner.getBrain().eraseMemory(MemoryModuleType.BREED_TARGET);
                animal.getBrain().eraseMemory(MemoryModuleType.BREED_TARGET);
            }
        }
    }

    protected void stop(ServerLevel level, Animal entity, long gameTime) {
        entity.getBrain().eraseMemory(MemoryModuleType.BREED_TARGET);
        entity.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        entity.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        this.spawnChildAtTime = 0L;
    }

    private Animal getBreedTarget(Animal animal) {
        return (Animal)animal.getBrain().getMemory(MemoryModuleType.BREED_TARGET).get();
    }

    private boolean hasBreedTargetOfRightType(Animal animal) {
        Brain<?> brain = animal.getBrain();
        return brain.hasMemoryValue(MemoryModuleType.BREED_TARGET) && brain.getMemory(MemoryModuleType.BREED_TARGET).get().getType() == this.partnerType;
    }

    private Optional<? extends Animal> findValidBreedPartner(Animal p_animal) {
        return p_animal.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).get().findClosest(p_352711_ -> {
            if (p_352711_.getType() == this.partnerType && p_352711_ instanceof Animal animal && p_animal.canMate(animal) && !animal.isPanicking()) {
                return true;
            }

            return false;
        }).map(Animal.class::cast);
    }
}
