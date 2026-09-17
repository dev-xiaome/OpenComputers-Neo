package net.minecraft.world.entity.ai;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.memory.ExpirableValue;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import org.apache.commons.lang3.mutable.MutableObject;
import org.slf4j.Logger;

public class Brain<E extends LivingEntity> {
    static final Logger LOGGER = LogUtils.getLogger();
    private final Supplier<Codec<Brain<E>>> codec;
    private static final int SCHEDULE_UPDATE_DELAY = 20;
    private final Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> memories = Maps.newHashMap();
    private final Map<SensorType<? extends Sensor<? super E>>, Sensor<? super E>> sensors = Maps.newLinkedHashMap();
    private final Map<Integer, Map<Activity, Set<BehaviorControl<? super E>>>> availableBehaviorsByPriority = Maps.newTreeMap();
    private Schedule schedule = Schedule.EMPTY;
    private final Map<Activity, Set<Pair<MemoryModuleType<?>, MemoryStatus>>> activityRequirements = Maps.newHashMap();
    private final Map<Activity, Set<MemoryModuleType<?>>> activityMemoriesToEraseWhenStopped = Maps.newHashMap();
    private Set<Activity> coreActivities = Sets.newHashSet();
    private final Set<Activity> activeActivities = Sets.newHashSet();
    private Activity defaultActivity = Activity.IDLE;
    private long lastScheduleUpdate = -9999L;

    public static <E extends LivingEntity> Brain.Provider<E> provider(
        Collection<? extends MemoryModuleType<?>> memoryTypes, Collection<? extends SensorType<? extends Sensor<? super E>>> sensorTypes
    ) {
        return new Brain.Provider<>(memoryTypes, sensorTypes);
    }

    public static <E extends LivingEntity> Codec<Brain<E>> codec(
        final Collection<? extends MemoryModuleType<?>> memoryTypes, final Collection<? extends SensorType<? extends Sensor<? super E>>> sensorTypes
    ) {
        final MutableObject<Codec<Brain<E>>> mutableobject = new MutableObject<>();
        mutableobject.setValue(
            (new MapCodec<Brain<E>>() {
                    @Override
                    public <T> Stream<T> keys(DynamicOps<T> ops) {
                        return memoryTypes.stream()
                            .flatMap(
                                p_22020_ -> p_22020_.getCodec()
                                        .map(p_258254_ -> BuiltInRegistries.MEMORY_MODULE_TYPE.getKey((MemoryModuleType<?>)p_22020_))
                                        .stream()
                            )
                            .map(p_22018_ -> ops.createString(p_22018_.toString()));
                    }

                    @Override
                    public <T> DataResult<Brain<E>> decode(DynamicOps<T> ops, MapLike<T> input) {
                        MutableObject<DataResult<Builder<Brain.MemoryValue<?>>>> mutableobject1 = new MutableObject<>(
                            DataResult.success(ImmutableList.builder())
                        );
                        input.entries()
                            .forEach(
                                p_344292_ -> {
                                    DataResult<MemoryModuleType<?>> dataresult = BuiltInRegistries.MEMORY_MODULE_TYPE
                                        .byNameCodec()
                                        .parse(ops, p_344292_.getFirst());
                                    DataResult<? extends Brain.MemoryValue<?>> dataresult1 = dataresult.flatMap(
                                        p_147350_ -> this.captureRead((MemoryModuleType<T>)p_147350_, ops, (T)p_344292_.getSecond())
                                    );
                                    mutableobject1.setValue(mutableobject1.getValue().apply2(Builder::add, dataresult1));
                                }
                            );
                        ImmutableList<Brain.MemoryValue<?>> immutablelist = mutableobject1.getValue()
                            .resultOrPartial(Brain.LOGGER::error)
                            .map(Builder::build)
                            .orElseGet(ImmutableList::of);
                        return DataResult.success(new Brain<>(memoryTypes, sensorTypes, immutablelist, mutableobject::getValue));
                    }

                    private <T, U> DataResult<Brain.MemoryValue<U>> captureRead(MemoryModuleType<U> p_21997_, DynamicOps<T> p_21998_, T p_21999_) {
                        return p_21997_.getCodec()
                            .map(DataResult::success)
                            .orElseGet(() -> DataResult.error(() -> "No codec for memory: " + p_21997_))
                            .<ExpirableValue<U>>flatMap(p_22011_ -> p_22011_.parse(p_21998_, p_21999_))
                            .map(p_21992_ -> new Brain.MemoryValue<>(p_21997_, Optional.of(p_21992_)));
                    }

                    public <T> RecordBuilder<T> encode(Brain<E> input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                        input.memories().forEach(p_22007_ -> p_22007_.serialize(ops, prefix));
                        return prefix;
                    }
                })
                .fieldOf("memories")
                .codec()
        );
        return mutableobject.getValue();
    }

    public Brain(
        Collection<? extends MemoryModuleType<?>> memoryModuleTypes,
        Collection<? extends SensorType<? extends Sensor<? super E>>> sensorTypes,
        ImmutableList<Brain.MemoryValue<?>> memoryValues,
        Supplier<Codec<Brain<E>>> codec
    ) {
        this.codec = codec;

        for (MemoryModuleType<?> memorymoduletype : memoryModuleTypes) {
            this.memories.put(memorymoduletype, Optional.empty());
        }

        for (SensorType<? extends Sensor<? super E>> sensortype : sensorTypes) {
            this.sensors.put(sensortype, (Sensor<? super E>)sensortype.create());
        }

        for (Sensor<? super E> sensor : this.sensors.values()) {
            for (MemoryModuleType<?> memorymoduletype1 : sensor.requires()) {
                this.memories.put(memorymoduletype1, Optional.empty());
            }
        }

        for (Brain.MemoryValue<?> memoryvalue : memoryValues) {
            memoryvalue.setMemoryInternal(this);
        }
    }

    public <T> DataResult<T> serializeStart(DynamicOps<T> ops) {
        return this.codec.get().encodeStart(ops, this);
    }

    Stream<Brain.MemoryValue<?>> memories() {
        return this.memories.entrySet().stream().map(p_21929_ -> Brain.MemoryValue.createUnchecked(p_21929_.getKey(), p_21929_.getValue()));
    }

    public boolean hasMemoryValue(MemoryModuleType<?> type) {
        return this.checkMemory(type, MemoryStatus.VALUE_PRESENT);
    }

    public void clearMemories() {
        this.memories.keySet().forEach(p_276103_ -> this.memories.put((MemoryModuleType<?>)p_276103_, Optional.empty()));
    }

    public <U> void eraseMemory(MemoryModuleType<U> type) {
        this.setMemory(type, Optional.empty());
    }

    public <U> void setMemory(MemoryModuleType<U> memoryType, @Nullable U memory) {
        this.setMemory(memoryType, Optional.ofNullable(memory));
    }

    public <U> void setMemoryWithExpiry(MemoryModuleType<U> memoryType, U memory, long timeToLive) {
        this.setMemoryInternal(memoryType, Optional.of(ExpirableValue.of(memory, timeToLive)));
    }

    public <U> void setMemory(MemoryModuleType<U> memoryType, Optional<? extends U> memory) {
        this.setMemoryInternal(memoryType, memory.map(ExpirableValue::of));
    }

    <U> void setMemoryInternal(MemoryModuleType<U> memoryType, Optional<? extends ExpirableValue<?>> memory) {
        if (this.memories.containsKey(memoryType)) {
            if (memory.isPresent() && this.isEmptyCollection(memory.get().getValue())) {
                this.eraseMemory(memoryType);
            } else {
                this.memories.put(memoryType, memory);
            }
        }
    }

    public <U> Optional<U> getMemory(MemoryModuleType<U> type) {
        Optional<? extends ExpirableValue<?>> optional = this.memories.get(type);
        if (optional == null) {
            throw new IllegalStateException("Unregistered memory fetched: " + type);
        } else {
            return (Optional<U>)optional.map(ExpirableValue::getValue);
        }
    }

    @Nullable
    public <U> Optional<U> getMemoryInternal(MemoryModuleType<U> type) {
        Optional<? extends ExpirableValue<?>> optional = this.memories.get(type);
        return optional == null ? null : (Optional<U>)optional.map(ExpirableValue::getValue);
    }

    public <U> long getTimeUntilExpiry(MemoryModuleType<U> memoryType) {
        Optional<? extends ExpirableValue<?>> optional = this.memories.get(memoryType);
        return optional.map(ExpirableValue::getTimeToLive).orElse(0L);
    }

    @Deprecated
    @VisibleForDebug
    public Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> getMemories() {
        return this.memories;
    }

    public <U> boolean isMemoryValue(MemoryModuleType<U> memoryType, U memory) {
        return !this.hasMemoryValue(memoryType) ? false : this.getMemory(memoryType).filter(p_21922_ -> p_21922_.equals(memory)).isPresent();
    }

    public boolean checkMemory(MemoryModuleType<?> memoryType, MemoryStatus memoryStatus) {
        Optional<? extends ExpirableValue<?>> optional = this.memories.get(memoryType);
        return optional == null
            ? false
            : memoryStatus == MemoryStatus.REGISTERED
                || memoryStatus == MemoryStatus.VALUE_PRESENT && optional.isPresent()
                || memoryStatus == MemoryStatus.VALUE_ABSENT && optional.isEmpty();
    }

    public Schedule getSchedule() {
        return this.schedule;
    }

    public void setSchedule(Schedule newSchedule) {
        this.schedule = newSchedule;
    }

    public void setCoreActivities(Set<Activity> newActivities) {
        this.coreActivities = newActivities;
    }

    @Deprecated
    @VisibleForDebug
    public Set<Activity> getActiveActivities() {
        return this.activeActivities;
    }

    @Deprecated
    @VisibleForDebug
    public List<BehaviorControl<? super E>> getRunningBehaviors() {
        List<BehaviorControl<? super E>> list = new ObjectArrayList<>();

        for (Map<Activity, Set<BehaviorControl<? super E>>> map : this.availableBehaviorsByPriority.values()) {
            for (Set<BehaviorControl<? super E>> set : map.values()) {
                for (BehaviorControl<? super E> behaviorcontrol : set) {
                    if (behaviorcontrol.getStatus() == Behavior.Status.RUNNING) {
                        list.add(behaviorcontrol);
                    }
                }
            }
        }

        return list;
    }

    public void useDefaultActivity() {
        this.setActiveActivity(this.defaultActivity);
    }

    public Optional<Activity> getActiveNonCoreActivity() {
        for (Activity activity : this.activeActivities) {
            if (!this.coreActivities.contains(activity)) {
                return Optional.of(activity);
            }
        }

        return Optional.empty();
    }

    public void setActiveActivityIfPossible(Activity activity) {
        if (this.activityRequirementsAreMet(activity)) {
            this.setActiveActivity(activity);
        } else {
            this.useDefaultActivity();
        }
    }

    private void setActiveActivity(Activity activity) {
        if (!this.isActive(activity)) {
            this.eraseMemoriesForOtherActivitesThan(activity);
            this.activeActivities.clear();
            this.activeActivities.addAll(this.coreActivities);
            this.activeActivities.add(activity);
        }
    }

    private void eraseMemoriesForOtherActivitesThan(Activity p_activity) {
        for (Activity activity : this.activeActivities) {
            if (activity != p_activity) {
                Set<MemoryModuleType<?>> set = this.activityMemoriesToEraseWhenStopped.get(activity);
                if (set != null) {
                    for (MemoryModuleType<?> memorymoduletype : set) {
                        this.eraseMemory(memorymoduletype);
                    }
                }
            }
        }
    }

    public void updateActivityFromSchedule(long dayTime, long gameTime) {
        if (gameTime - this.lastScheduleUpdate > 20L) {
            this.lastScheduleUpdate = gameTime;
            Activity activity = this.getSchedule().getActivityAt((int)(dayTime % 24000L));
            if (!this.activeActivities.contains(activity)) {
                this.setActiveActivityIfPossible(activity);
            }
        }
    }

    public void setActiveActivityToFirstValid(List<Activity> activities) {
        for (Activity activity : activities) {
            if (this.activityRequirementsAreMet(activity)) {
                this.setActiveActivity(activity);
                break;
            }
        }
    }

    public void setDefaultActivity(Activity newFallbackActivity) {
        this.defaultActivity = newFallbackActivity;
    }

    public void addActivity(Activity activity, int priorityStart, ImmutableList<? extends BehaviorControl<? super E>> tasks) {
        this.addActivity(activity, this.createPriorityPairs(priorityStart, tasks));
    }

    public void addActivityAndRemoveMemoryWhenStopped(
        Activity activity, int priorityStart, ImmutableList<? extends BehaviorControl<? super E>> tasks, MemoryModuleType<?> memoryType
    ) {
        Set<Pair<MemoryModuleType<?>, MemoryStatus>> set = ImmutableSet.of(Pair.of(memoryType, MemoryStatus.VALUE_PRESENT));
        Set<MemoryModuleType<?>> set1 = ImmutableSet.of(memoryType);
        this.addActivityAndRemoveMemoriesWhenStopped(activity, this.createPriorityPairs(priorityStart, tasks), set, set1);
    }

    public void addActivity(Activity activity, ImmutableList<? extends Pair<Integer, ? extends BehaviorControl<? super E>>> tasks) {
        this.addActivityAndRemoveMemoriesWhenStopped(activity, tasks, ImmutableSet.of(), Sets.newHashSet());
    }

    public void addActivityWithConditions(
        Activity activity,
        ImmutableList<? extends Pair<Integer, ? extends BehaviorControl<? super E>>> tasks,
        Set<Pair<MemoryModuleType<?>, MemoryStatus>> memoryStatuses
    ) {
        this.addActivityAndRemoveMemoriesWhenStopped(activity, tasks, memoryStatuses, Sets.newHashSet());
    }

    public void addActivityAndRemoveMemoriesWhenStopped(
        Activity activity,
        ImmutableList<? extends Pair<Integer, ? extends BehaviorControl<? super E>>> tasks,
        Set<Pair<MemoryModuleType<?>, MemoryStatus>> memorieStatuses,
        Set<MemoryModuleType<?>> memoryTypes
    ) {
        this.activityRequirements.put(activity, memorieStatuses);
        if (!memoryTypes.isEmpty()) {
            this.activityMemoriesToEraseWhenStopped.put(activity, memoryTypes);
        }

        for (Pair<Integer, ? extends BehaviorControl<? super E>> pair : tasks) {
            this.availableBehaviorsByPriority
                .computeIfAbsent(pair.getFirst(), p_21917_ -> Maps.newHashMap())
                .computeIfAbsent(activity, p_21972_ -> Sets.newLinkedHashSet())
                .add((BehaviorControl<? super E>)pair.getSecond());
        }
    }

    @VisibleForTesting
    public void removeAllBehaviors() {
        this.availableBehaviorsByPriority.clear();
    }

    public boolean isActive(Activity activity) {
        return this.activeActivities.contains(activity);
    }

    public Brain<E> copyWithoutBehaviors() {
        Brain<E> brain = new Brain<>(this.memories.keySet(), this.sensors.keySet(), ImmutableList.of(), this.codec);

        for (Entry<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> entry : this.memories.entrySet()) {
            MemoryModuleType<?> memorymoduletype = entry.getKey();
            if (entry.getValue().isPresent()) {
                brain.memories.put(memorymoduletype, entry.getValue());
            }
        }

        return brain;
    }

    public void tick(ServerLevel level, E entity) {
        this.forgetOutdatedMemories();
        this.tickSensors(level, entity);
        this.startEachNonRunningBehavior(level, entity);
        this.tickEachRunningBehavior(level, entity);
    }

    private void tickSensors(ServerLevel level, E brainHolder) {
        for (Sensor<? super E> sensor : this.sensors.values()) {
            sensor.tick(level, brainHolder);
        }
    }

    private void forgetOutdatedMemories() {
        for (Entry<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> entry : this.memories.entrySet()) {
            if (entry.getValue().isPresent()) {
                ExpirableValue<?> expirablevalue = (ExpirableValue<?>)entry.getValue().get();
                if (expirablevalue.hasExpired()) {
                    this.eraseMemory(entry.getKey());
                }

                expirablevalue.tick();
            }
        }
    }

    public void stopAll(ServerLevel level, E owner) {
        long i = owner.level().getGameTime();

        for (BehaviorControl<? super E> behaviorcontrol : this.getRunningBehaviors()) {
            behaviorcontrol.doStop(level, owner, i);
        }
    }

    private void startEachNonRunningBehavior(ServerLevel level, E entity) {
        long i = level.getGameTime();

        for (Map<Activity, Set<BehaviorControl<? super E>>> map : this.availableBehaviorsByPriority.values()) {
            for (Entry<Activity, Set<BehaviorControl<? super E>>> entry : map.entrySet()) {
                Activity activity = entry.getKey();
                if (this.activeActivities.contains(activity)) {
                    for (BehaviorControl<? super E> behaviorcontrol : entry.getValue()) {
                        if (behaviorcontrol.getStatus() == Behavior.Status.STOPPED) {
                            behaviorcontrol.tryStart(level, entity, i);
                        }
                    }
                }
            }
        }
    }

    private void tickEachRunningBehavior(ServerLevel level, E entity) {
        long i = level.getGameTime();

        for (BehaviorControl<? super E> behaviorcontrol : this.getRunningBehaviors()) {
            behaviorcontrol.tickOrStop(level, entity, i);
        }
    }

    private boolean activityRequirementsAreMet(Activity activity) {
        if (!this.activityRequirements.containsKey(activity)) {
            return false;
        } else {
            for (Pair<MemoryModuleType<?>, MemoryStatus> pair : this.activityRequirements.get(activity)) {
                MemoryModuleType<?> memorymoduletype = pair.getFirst();
                MemoryStatus memorystatus = pair.getSecond();
                if (!this.checkMemory(memorymoduletype, memorystatus)) {
                    return false;
                }
            }

            return true;
        }
    }

    private boolean isEmptyCollection(Object collection) {
        return collection instanceof Collection && ((Collection)collection).isEmpty();
    }

    ImmutableList<? extends Pair<Integer, ? extends BehaviorControl<? super E>>> createPriorityPairs(
        int priorityStart, ImmutableList<? extends BehaviorControl<? super E>> tasks
    ) {
        int i = priorityStart;
        Builder<Pair<Integer, ? extends BehaviorControl<? super E>>> builder = ImmutableList.builder();

        for (BehaviorControl<? super E> behaviorcontrol : tasks) {
            builder.add(Pair.of(i++, behaviorcontrol));
        }

        return builder.build();
    }

    static final class MemoryValue<U> {
        private final MemoryModuleType<U> type;
        private final Optional<? extends ExpirableValue<U>> value;

        static <U> Brain.MemoryValue<U> createUnchecked(MemoryModuleType<U> memoryType, Optional<? extends ExpirableValue<?>> memory) {
            return new Brain.MemoryValue<>(memoryType, (Optional<? extends ExpirableValue<U>>)memory);
        }

        MemoryValue(MemoryModuleType<U> type, Optional<? extends ExpirableValue<U>> value) {
            this.type = type;
            this.value = value;
        }

        void setMemoryInternal(Brain<?> brain) {
            brain.setMemoryInternal(this.type, this.value);
        }

        public <T> void serialize(DynamicOps<T> ops, RecordBuilder<T> builder) {
            this.type
                .getCodec()
                .ifPresent(
                    p_22053_ -> this.value
                            .ifPresent(
                                p_344296_ -> builder.add(
                                        BuiltInRegistries.MEMORY_MODULE_TYPE.byNameCodec().encodeStart(ops, this.type),
                                        p_22053_.encodeStart(ops, (ExpirableValue<U>)p_344296_)
                                    )
                            )
                );
        }
    }

    public static final class Provider<E extends LivingEntity> {
        private final Collection<? extends MemoryModuleType<?>> memoryTypes;
        private final Collection<? extends SensorType<? extends Sensor<? super E>>> sensorTypes;
        private final Codec<Brain<E>> codec;

        Provider(Collection<? extends MemoryModuleType<?>> memoryTypes, Collection<? extends SensorType<? extends Sensor<? super E>>> sensorTypes) {
            this.memoryTypes = memoryTypes;
            this.sensorTypes = sensorTypes;
            this.codec = Brain.codec(memoryTypes, sensorTypes);
        }

        public Brain<E> makeBrain(Dynamic<?> ops) {
            return this.codec
                .parse(ops)
                .resultOrPartial(Brain.LOGGER::error)
                .orElseGet(() -> new Brain<>(this.memoryTypes, this.sensorTypes, ImmutableList.of(), () -> this.codec));
        }
    }
}
