package net.minecraft.world.entity.ai.behavior;

import com.mojang.datafixers.util.Pair;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;

public class GateBehavior<E extends LivingEntity> implements BehaviorControl<E> {
    private final Map<MemoryModuleType<?>, MemoryStatus> entryCondition;
    private final Set<MemoryModuleType<?>> exitErasedMemories;
    private final GateBehavior.OrderPolicy orderPolicy;
    private final GateBehavior.RunningPolicy runningPolicy;
    private final ShufflingList<BehaviorControl<? super E>> behaviors = new ShufflingList<>();
    private Behavior.Status status = Behavior.Status.STOPPED;

    public GateBehavior(
        Map<MemoryModuleType<?>, MemoryStatus> entryCondition,
        Set<MemoryModuleType<?>> exitErasedMemories,
        GateBehavior.OrderPolicy orderPolicy,
        GateBehavior.RunningPolicy runningPolicy,
        List<Pair<? extends BehaviorControl<? super E>, Integer>> durations
    ) {
        this.entryCondition = entryCondition;
        this.exitErasedMemories = exitErasedMemories;
        this.orderPolicy = orderPolicy;
        this.runningPolicy = runningPolicy;
        durations.forEach(p_258332_ -> this.behaviors.add((BehaviorControl<? super E>)p_258332_.getFirst(), p_258332_.getSecond()));
    }

    @Override
    public Behavior.Status getStatus() {
        return this.status;
    }

    private boolean hasRequiredMemories(E entity) {
        for (Entry<MemoryModuleType<?>, MemoryStatus> entry : this.entryCondition.entrySet()) {
            MemoryModuleType<?> memorymoduletype = entry.getKey();
            MemoryStatus memorystatus = entry.getValue();
            if (!entity.getBrain().checkMemory(memorymoduletype, memorystatus)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public final boolean tryStart(ServerLevel level, E entity, long gameTime) {
        if (this.hasRequiredMemories(entity)) {
            this.status = Behavior.Status.RUNNING;
            this.orderPolicy.apply(this.behaviors);
            this.runningPolicy.apply(this.behaviors.stream(), level, entity, gameTime);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public final void tickOrStop(ServerLevel level, E entity, long gameTime) {
        this.behaviors
            .stream()
            .filter(p_258342_ -> p_258342_.getStatus() == Behavior.Status.RUNNING)
            .forEach(p_258336_ -> p_258336_.tickOrStop(level, entity, gameTime));
        if (this.behaviors.stream().noneMatch(p_258344_ -> p_258344_.getStatus() == Behavior.Status.RUNNING)) {
            this.doStop(level, entity, gameTime);
        }
    }

    @Override
    public final void doStop(ServerLevel level, E entity, long gameTime) {
        this.status = Behavior.Status.STOPPED;
        this.behaviors
            .stream()
            .filter(p_258337_ -> p_258337_.getStatus() == Behavior.Status.RUNNING)
            .forEach(p_258341_ -> p_258341_.doStop(level, entity, gameTime));
        this.exitErasedMemories.forEach(entity.getBrain()::eraseMemory);
    }

    @Override
    public String debugString() {
        return this.getClass().getSimpleName();
    }

    @Override
    public String toString() {
        Set<? extends BehaviorControl<? super E>> set = this.behaviors
            .stream()
            .filter(p_258343_ -> p_258343_.getStatus() == Behavior.Status.RUNNING)
            .collect(Collectors.toSet());
        return "(" + this.getClass().getSimpleName() + "): " + set;
    }

    public static enum OrderPolicy {
        ORDERED(p_147530_ -> {
        }),
        SHUFFLED(ShufflingList::shuffle);

        private final Consumer<ShufflingList<?>> consumer;

        private OrderPolicy(Consumer<ShufflingList<?>> consumer) {
            this.consumer = consumer;
        }

        public void apply(ShufflingList<?> list) {
            this.consumer.accept(list);
        }
    }

    public static enum RunningPolicy {
        RUN_ONE {
            @Override
            public <E extends LivingEntity> void apply(Stream<BehaviorControl<? super E>> behaviors, ServerLevel level, E owner, long gameTime) {
                behaviors.filter(p_258349_ -> p_258349_.getStatus() == Behavior.Status.STOPPED)
                    .filter(p_258348_ -> p_258348_.tryStart(level, owner, gameTime))
                    .findFirst();
            }
        },
        TRY_ALL {
            @Override
            public <E extends LivingEntity> void apply(Stream<BehaviorControl<? super E>> behaviors, ServerLevel level, E owner, long gameTime) {
                behaviors.filter(p_258350_ -> p_258350_.getStatus() == Behavior.Status.STOPPED)
                    .forEach(p_258354_ -> p_258354_.tryStart(level, owner, gameTime));
            }
        };

        public abstract <E extends LivingEntity> void apply(Stream<BehaviorControl<? super E>> behaviors, ServerLevel level, E owner, long gameTime);
    }
}
