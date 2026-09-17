package net.minecraft.world.level;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.ImmutableList.Builder;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public interface EntityGetter {
    /**
     * Gets all entities within the specified AABB excluding the one passed into it.
     */
    List<Entity> getEntities(@Nullable Entity entity, AABB area, Predicate<? super Entity> predicate);

    <T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> entityTypeTest, AABB bounds, Predicate<? super T> predicate);

    default <T extends Entity> List<T> getEntitiesOfClass(Class<T> clazz, AABB area, Predicate<? super T> filter) {
        return this.getEntities(EntityTypeTest.forClass(clazz), area, filter);
    }

    List<? extends Player> players();

    /**
     * Will get all entities within the specified AABB excluding the one passed into it. Args: entityToExclude, aabb
     */
    default List<Entity> getEntities(@Nullable Entity entity, AABB area) {
        return this.getEntities(entity, area, EntitySelector.NO_SPECTATORS);
    }

    default boolean isUnobstructed(@Nullable Entity p_entity, VoxelShape shape) {
        if (shape.isEmpty()) {
            return true;
        } else {
            for (Entity entity : this.getEntities(p_entity, shape.bounds())) {
                if (!entity.isRemoved()
                    && entity.blocksBuilding
                    && (p_entity == null || !entity.isPassengerOfSameVehicle(p_entity))
                    && Shapes.joinIsNotEmpty(shape, Shapes.create(entity.getBoundingBox()), BooleanOp.AND)) {
                    return false;
                }
            }

            return true;
        }
    }

    default <T extends Entity> List<T> getEntitiesOfClass(Class<T> entityClass, AABB area) {
        return this.getEntitiesOfClass(entityClass, area, EntitySelector.NO_SPECTATORS);
    }

    default List<VoxelShape> getEntityCollisions(@Nullable Entity p_entity, AABB collisionBox) {
        if (collisionBox.getSize() < 1.0E-7) {
            return List.of();
        } else {
            Predicate<Entity> predicate = p_entity == null ? EntitySelector.CAN_BE_COLLIDED_WITH : EntitySelector.NO_SPECTATORS.and(p_entity::canCollideWith);
            List<Entity> list = this.getEntities(p_entity, collisionBox.inflate(1.0E-7), predicate);
            if (list.isEmpty()) {
                return List.of();
            } else {
                Builder<VoxelShape> builder = ImmutableList.builderWithExpectedSize(list.size());

                for (Entity entity : list) {
                    builder.add(Shapes.create(entity.getBoundingBox()));
                }

                return builder.build();
            }
        }
    }

    @Nullable
    default Player getNearestPlayer(double x, double y, double z, double distance, @Nullable Predicate<Entity> predicate) {
        double d0 = -1.0;
        Player player = null;

        for (Player player1 : this.players()) {
            if (predicate == null || predicate.test(player1)) {
                double d1 = player1.distanceToSqr(x, y, z);
                if ((distance < 0.0 || d1 < distance * distance) && (d0 == -1.0 || d1 < d0)) {
                    d0 = d1;
                    player = player1;
                }
            }
        }

        return player;
    }

    @Nullable
    default Player getNearestPlayer(Entity entity, double distance) {
        return this.getNearestPlayer(entity.getX(), entity.getY(), entity.getZ(), distance, false);
    }

    @Nullable
    default Player getNearestPlayer(double x, double y, double z, double distance, boolean creativePlayers) {
        Predicate<Entity> predicate = creativePlayers ? EntitySelector.NO_CREATIVE_OR_SPECTATOR : EntitySelector.NO_SPECTATORS;
        return this.getNearestPlayer(x, y, z, distance, predicate);
    }

    default boolean hasNearbyAlivePlayer(double x, double y, double z, double distance) {
        for (Player player : this.players()) {
            if (EntitySelector.NO_SPECTATORS.test(player) && EntitySelector.LIVING_ENTITY_STILL_ALIVE.test(player)) {
                double d0 = player.distanceToSqr(x, y, z);
                if (distance < 0.0 || d0 < distance * distance) {
                    return true;
                }
            }
        }

        return false;
    }

    @Nullable
    default Player getNearestPlayer(TargetingConditions predicate, LivingEntity target) {
        return this.getNearestEntity(this.players(), predicate, target, target.getX(), target.getY(), target.getZ());
    }

    @Nullable
    default Player getNearestPlayer(TargetingConditions predicate, LivingEntity target, double x, double y, double z) {
        return this.getNearestEntity(this.players(), predicate, target, x, y, z);
    }

    @Nullable
    default Player getNearestPlayer(TargetingConditions predicate, double x, double y, double z) {
        return this.getNearestEntity(this.players(), predicate, null, x, y, z);
    }

    @Nullable
    default <T extends LivingEntity> T getNearestEntity(
        Class<? extends T> entityClazz,
        TargetingConditions conditions,
        @Nullable LivingEntity target,
        double x,
        double y,
        double z,
        AABB boundingBox
    ) {
        return this.getNearestEntity(this.getEntitiesOfClass(entityClazz, boundingBox, p_186454_ -> true), conditions, target, x, y, z);
    }

    @Nullable
    default <T extends LivingEntity> T getNearestEntity(
        List<? extends T> entities, TargetingConditions predicate, @Nullable LivingEntity target, double x, double y, double z
    ) {
        double d0 = -1.0;
        T t = null;

        for (T t1 : entities) {
            if (predicate.test(target, t1)) {
                double d1 = t1.distanceToSqr(x, y, z);
                if (d0 == -1.0 || d1 < d0) {
                    d0 = d1;
                    t = t1;
                }
            }
        }

        return t;
    }

    default List<Player> getNearbyPlayers(TargetingConditions predicate, LivingEntity target, AABB area) {
        List<Player> list = Lists.newArrayList();

        for (Player player : this.players()) {
            if (area.contains(player.getX(), player.getY(), player.getZ()) && predicate.test(target, player)) {
                list.add(player);
            }
        }

        return list;
    }

    default <T extends LivingEntity> List<T> getNearbyEntities(Class<T> entityClazz, TargetingConditions entityPredicate, LivingEntity entity, AABB area) {
        List<T> list = this.getEntitiesOfClass(entityClazz, area, p_186450_ -> true);
        List<T> list1 = Lists.newArrayList();

        for (T t : list) {
            if (entityPredicate.test(entity, t)) {
                list1.add(t);
            }
        }

        return list1;
    }

    @Nullable
    default Player getPlayerByUUID(UUID uniqueId) {
        for (int i = 0; i < this.players().size(); i++) {
            Player player = this.players().get(i);
            if (uniqueId.equals(player.getUUID())) {
                return player;
            }
        }

        return null;
    }
}
