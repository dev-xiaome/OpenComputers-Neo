package net.minecraft.world.entity.ai.behavior;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.behavior.declarative.MemoryAccessor;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class GoToTargetLocation {
    private static BlockPos getNearbyPos(Mob mob, BlockPos pos) {
        RandomSource randomsource = mob.level().random;
        return pos.offset(getRandomOffset(randomsource), 0, getRandomOffset(randomsource));
    }

    private static int getRandomOffset(RandomSource random) {
        return random.nextInt(3) - 1;
    }

    public static <E extends Mob> OneShot<E> create(MemoryModuleType<BlockPos> locationMemory, int closeEnoughDist, float speedModifier) {
        return BehaviorBuilder.create(
            p_259997_ -> p_259997_.group(
                        p_259997_.present(locationMemory),
                        p_259997_.absent(MemoryModuleType.ATTACK_TARGET),
                        p_259997_.absent(MemoryModuleType.WALK_TARGET),
                        p_259997_.registered(MemoryModuleType.LOOK_TARGET)
                    )
                    .apply(p_259997_, (p_259831_, p_259115_, p_259521_, p_259223_) -> (p_352718_, p_352719_, p_352720_) -> {
                            BlockPos blockpos = p_259997_.get(p_259831_);
                            boolean flag = blockpos.closerThan(p_352719_.blockPosition(), (double)closeEnoughDist);
                            if (!flag) {
                                BehaviorUtils.setWalkAndLookTargetMemories(p_352719_, getNearbyPos(p_352719_, blockpos), speedModifier, closeEnoughDist);
                            }

                            return true;
                        })
        );
    }
}
