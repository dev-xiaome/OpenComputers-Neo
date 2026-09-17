package net.minecraft.world.entity.ai.behavior;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.behavior.declarative.MemoryAccessor;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

public class PoiCompetitorScan {
    public static BehaviorControl<Villager> create() {
        return BehaviorBuilder.create(
            p_258576_ -> p_258576_.group(p_258576_.present(MemoryModuleType.JOB_SITE), p_258576_.present(MemoryModuleType.NEAREST_LIVING_ENTITIES))
                    .apply(
                        p_258576_,
                        (p_258590_, p_258591_) -> (p_258580_, p_258581_, p_258582_) -> {
                                GlobalPos globalpos = p_258576_.get(p_258590_);
                                p_258580_.getPoiManager()
                                    .getType(globalpos.pos())
                                    .ifPresent(
                                        p_258588_ -> p_258576_.<List<LivingEntity>>get(p_258591_)
                                                .stream()
                                                .filter(p_258593_ -> p_258593_ instanceof Villager && p_258593_ != p_258581_)
                                                .map(p_258583_ -> (Villager)p_258583_)
                                                .filter(LivingEntity::isAlive)
                                                .filter(p_258596_ -> competesForSameJobsite(globalpos, p_258588_, p_258596_))
                                                .reduce(p_258581_, PoiCompetitorScan::selectWinner)
                                    );
                                return true;
                            }
                    )
        );
    }

    private static Villager selectWinner(Villager villagerA, Villager villagerB) {
        Villager villager;
        Villager villager1;
        if (villagerA.getVillagerXp() > villagerB.getVillagerXp()) {
            villager = villagerA;
            villager1 = villagerB;
        } else {
            villager = villagerB;
            villager1 = villagerA;
        }

        villager1.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
        return villager;
    }

    private static boolean competesForSameJobsite(GlobalPos jobSitePos, Holder<PoiType> poi, Villager poiType) {
        Optional<GlobalPos> optional = poiType.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        return optional.isPresent() && jobSitePos.equals(optional.get()) && hasMatchingProfession(poi, poiType.getVillagerData().getProfession());
    }

    private static boolean hasMatchingProfession(Holder<PoiType> poi, VillagerProfession poiType) {
        return poiType.heldJobSite().test(poi);
    }
}
