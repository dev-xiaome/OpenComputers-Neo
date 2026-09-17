package net.minecraft.world.entity.ai.behavior;

import com.mojang.datafixers.util.Pair;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.behavior.declarative.Trigger;

public class TriggerGate {
    public static <E extends LivingEntity> OneShot<E> triggerOneShuffled(List<Pair<? extends Trigger<? super E>, Integer>> triggers) {
        return triggerGate(triggers, GateBehavior.OrderPolicy.SHUFFLED, GateBehavior.RunningPolicy.RUN_ONE);
    }

    public static <E extends LivingEntity> OneShot<E> triggerGate(
        List<Pair<? extends Trigger<? super E>, Integer>> triggers, GateBehavior.OrderPolicy orderPolicy, GateBehavior.RunningPolicy runningPolicy
    ) {
        ShufflingList<Trigger<? super E>> shufflinglist = new ShufflingList<>();
        triggers.forEach(p_260333_ -> shufflinglist.add((Trigger<? super E>)p_260333_.getFirst(), p_260333_.getSecond()));
        return BehaviorBuilder.create(p_259457_ -> p_259457_.point((p_260107_, p_259505_, p_259999_) -> {
                if (orderPolicy == GateBehavior.OrderPolicy.SHUFFLED) {
                    shufflinglist.shuffle();
                }

                for (Trigger<? super E> trigger : shufflinglist) {
                    if (trigger.trigger(p_260107_, p_259505_, p_259999_) && runningPolicy == GateBehavior.RunningPolicy.RUN_ONE) {
                        break;
                    }
                }

                return true;
            }));
    }
}
