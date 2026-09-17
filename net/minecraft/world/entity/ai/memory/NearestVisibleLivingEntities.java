package net.minecraft.world.entity.ai.memory;

import com.google.common.collect.Iterables;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.Sensor;

public class NearestVisibleLivingEntities {
    private static final NearestVisibleLivingEntities EMPTY = new NearestVisibleLivingEntities();
    private final List<LivingEntity> nearbyEntities;
    private final Predicate<LivingEntity> lineOfSightTest;

    private NearestVisibleLivingEntities() {
        this.nearbyEntities = List.of();
        this.lineOfSightTest = p_186122_ -> false;
    }

    public NearestVisibleLivingEntities(LivingEntity livingEntity, List<LivingEntity> nearbyLivingEntities) {
        this.nearbyEntities = nearbyLivingEntities;
        Object2BooleanOpenHashMap<LivingEntity> object2booleanopenhashmap = new Object2BooleanOpenHashMap<>(nearbyLivingEntities.size());
        Predicate<LivingEntity> predicate = p_186111_ -> Sensor.isEntityTargetable(livingEntity, p_186111_);
        this.lineOfSightTest = p_186115_ -> object2booleanopenhashmap.computeIfAbsent(p_186115_, predicate);
    }

    public static NearestVisibleLivingEntities empty() {
        return EMPTY;
    }

    public Optional<LivingEntity> findClosest(Predicate<LivingEntity> predicate) {
        for (LivingEntity livingentity : this.nearbyEntities) {
            if (predicate.test(livingentity) && this.lineOfSightTest.test(livingentity)) {
                return Optional.of(livingentity);
            }
        }

        return Optional.empty();
    }

    public Iterable<LivingEntity> findAll(Predicate<LivingEntity> predicate) {
        return Iterables.filter(this.nearbyEntities, p_186127_ -> predicate.test(p_186127_) && this.lineOfSightTest.test(p_186127_));
    }

    public Stream<LivingEntity> find(Predicate<LivingEntity> predicate) {
        return this.nearbyEntities.stream().filter(p_186120_ -> predicate.test(p_186120_) && this.lineOfSightTest.test(p_186120_));
    }

    public boolean contains(LivingEntity entity) {
        return this.nearbyEntities.contains(entity) && this.lineOfSightTest.test(entity);
    }

    public boolean contains(Predicate<LivingEntity> predicate) {
        for (LivingEntity livingentity : this.nearbyEntities) {
            if (predicate.test(livingentity) && this.lineOfSightTest.test(livingentity)) {
                return true;
            }
        }

        return false;
    }
}
