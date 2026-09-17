package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.Maps;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.behavior.declarative.MemoryAccessor;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

public class PlayTagWithOtherKids {
    private static final int MAX_FLEE_XZ_DIST = 20;
    private static final int MAX_FLEE_Y_DIST = 8;
    private static final float FLEE_SPEED_MODIFIER = 0.6F;
    private static final float CHASE_SPEED_MODIFIER = 0.6F;
    private static final int MAX_CHASERS_PER_TARGET = 5;
    private static final int AVERAGE_WAIT_TIME_BETWEEN_RUNS = 10;

    public static BehaviorControl<PathfinderMob> create() {
        return BehaviorBuilder.create(
            p_258563_ -> p_258563_.group(
                        p_258563_.present(MemoryModuleType.VISIBLE_VILLAGER_BABIES),
                        p_258563_.absent(MemoryModuleType.WALK_TARGET),
                        p_258563_.registered(MemoryModuleType.LOOK_TARGET),
                        p_258563_.registered(MemoryModuleType.INTERACTION_TARGET)
                    )
                    .apply(p_258563_, (p_258559_, p_258560_, p_258561_, p_258562_) -> (p_275028_, p_275029_, p_275030_) -> {
                            if (p_275028_.getRandom().nextInt(10) != 0) {
                                return false;
                            } else {
                                List<LivingEntity> list = p_258563_.get(p_258559_);
                                Optional<LivingEntity> optional = list.stream().filter(p_258575_ -> isFriendChasingMe(p_275029_, p_258575_)).findAny();
                                if (!optional.isPresent()) {
                                    Optional<LivingEntity> optional1 = findSomeoneBeingChased(list);
                                    if (optional1.isPresent()) {
                                        chaseKid(p_258562_, p_258561_, p_258560_, optional1.get());
                                        return true;
                                    } else {
                                        list.stream().findAny().ifPresent(p_258557_ -> chaseKid(p_258562_, p_258561_, p_258560_, p_258557_));
                                        return true;
                                    }
                                } else {
                                    for (int i = 0; i < 10; i++) {
                                        Vec3 vec3 = LandRandomPos.getPos(p_275029_, 20, 8);
                                        if (vec3 != null && p_275028_.isVillage(BlockPos.containing(vec3))) {
                                            p_258560_.set(new WalkTarget(vec3, 0.6F, 0));
                                            break;
                                        }
                                    }

                                    return true;
                                }
                            }
                        })
        );
    }

    private static void chaseKid(
        MemoryAccessor<?, LivingEntity> interactionTarget,
        MemoryAccessor<?, PositionTracker> lookTarget,
        MemoryAccessor<?, WalkTarget> walkTarget,
        LivingEntity kid
    ) {
        interactionTarget.set(kid);
        lookTarget.set(new EntityTracker(kid, true));
        walkTarget.set(new WalkTarget(new EntityTracker(kid, false), 0.6F, 1));
    }

    private static Optional<LivingEntity> findSomeoneBeingChased(List<LivingEntity> kids) {
        Map<LivingEntity, Integer> map = checkHowManyChasersEachFriendHas(kids);
        return map.entrySet()
            .stream()
            .sorted(Comparator.comparingInt(Entry::getValue))
            .filter(p_23653_ -> p_23653_.getValue() > 0 && p_23653_.getValue() <= 5)
            .map(Entry::getKey)
            .findFirst();
    }

    private static Map<LivingEntity, Integer> checkHowManyChasersEachFriendHas(List<LivingEntity> kids) {
        Map<LivingEntity, Integer> map = Maps.newHashMap();
        kids.stream()
            .filter(PlayTagWithOtherKids::isChasingSomeone)
            .forEach(p_258565_ -> map.compute(whoAreYouChasing(p_258565_), (p_147707_, p_147708_) -> p_147708_ == null ? 1 : p_147708_ + 1));
        return map;
    }

    private static LivingEntity whoAreYouChasing(LivingEntity kid) {
        return kid.getBrain().getMemory(MemoryModuleType.INTERACTION_TARGET).get();
    }

    private static boolean isChasingSomeone(LivingEntity kid) {
        return kid.getBrain().getMemory(MemoryModuleType.INTERACTION_TARGET).isPresent();
    }

    private static boolean isFriendChasingMe(LivingEntity entity, LivingEntity kid) {
        return kid.getBrain().getMemory(MemoryModuleType.INTERACTION_TARGET).filter(p_23661_ -> p_23661_ == entity).isPresent();
    }
}
