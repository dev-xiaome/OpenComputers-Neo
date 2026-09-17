package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;

public class GoToPotentialJobSite extends Behavior<Villager> {
    private static final int TICKS_UNTIL_TIMEOUT = 1200;
    final float speedModifier;

    public GoToPotentialJobSite(float speedModifier) {
        super(ImmutableMap.of(MemoryModuleType.POTENTIAL_JOB_SITE, MemoryStatus.VALUE_PRESENT), 1200);
        this.speedModifier = speedModifier;
    }

    protected boolean checkExtraStartConditions(ServerLevel level, Villager owner) {
        return owner.getBrain()
            .getActiveNonCoreActivity()
            .map(p_23115_ -> p_23115_ == Activity.IDLE || p_23115_ == Activity.WORK || p_23115_ == Activity.PLAY)
            .orElse(true);
    }

    protected boolean canStillUse(ServerLevel level, Villager entity, long gameTime) {
        return entity.getBrain().hasMemoryValue(MemoryModuleType.POTENTIAL_JOB_SITE);
    }

    protected void tick(ServerLevel level, Villager owner, long gameTime) {
        BehaviorUtils.setWalkAndLookTargetMemories(
            owner, owner.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).get().pos(), this.speedModifier, 1
        );
    }

    protected void stop(ServerLevel level, Villager entity, long gameTime) {
        Optional<GlobalPos> optional = entity.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
        optional.ifPresent(p_23111_ -> {
            BlockPos blockpos = p_23111_.pos();
            ServerLevel serverlevel = level.getServer().getLevel(p_23111_.dimension());
            if (serverlevel != null) {
                PoiManager poimanager = serverlevel.getPoiManager();
                if (poimanager.exists(blockpos, p_217230_ -> true)) {
                    poimanager.release(blockpos);
                }

                DebugPackets.sendPoiTicketCountPacket(level, blockpos);
            }
        });
        entity.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
    }
}
