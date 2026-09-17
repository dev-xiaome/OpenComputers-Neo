package net.minecraft.world.entity.ai.behavior;

import java.util.List;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.behavior.declarative.MemoryAccessor;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import org.apache.commons.lang3.mutable.MutableLong;

public class StrollToPoiList {
    public static BehaviorControl<Villager> create(
        MemoryModuleType<List<GlobalPos>> poiListMemory, float speedModifier, int closeEnoughDist, int maxDistFromPoi, MemoryModuleType<GlobalPos> mustBeCloseToMemory
    ) {
        MutableLong mutablelong = new MutableLong(0L);
        return BehaviorBuilder.create(
            p_259612_ -> p_259612_.group(p_259612_.registered(MemoryModuleType.WALK_TARGET), p_259612_.present(poiListMemory), p_259612_.present(mustBeCloseToMemory))
                    .apply(
                        p_259612_,
                        (p_259574_, p_259801_, p_259116_) -> (p_259940_, p_259222_, p_260161_) -> {
                                List<GlobalPos> list = p_259612_.get(p_259801_);
                                GlobalPos globalpos = p_259612_.get(p_259116_);
                                if (list.isEmpty()) {
                                    return false;
                                } else {
                                    GlobalPos globalpos1 = list.get(p_259940_.getRandom().nextInt(list.size()));
                                    if (globalpos1 != null
                                        && p_259940_.dimension() == globalpos1.dimension()
                                        && globalpos.pos().closerToCenterThan(p_259222_.position(), (double)maxDistFromPoi)) {
                                        if (p_260161_ > mutablelong.getValue()) {
                                            p_259574_.set(new WalkTarget(globalpos1.pos(), speedModifier, closeEnoughDist));
                                            mutablelong.setValue(p_260161_ + 100L);
                                        }

                                        return true;
                                    } else {
                                        return false;
                                    }
                                }
                            }
                    )
        );
    }
}
