package net.minecraft.world.entity.projectile;

import java.util.Optional;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class ProjectileUtil {
    private static final float DEFAULT_ENTITY_HIT_RESULT_MARGIN = 0.3F;

    public static HitResult getHitResultOnMoveVector(Entity projectile, Predicate<Entity> filter) {
        Vec3 vec3 = projectile.getDeltaMovement();
        Level level = projectile.level();
        Vec3 vec31 = projectile.position();
        return getHitResult(vec31, projectile, filter, vec3, level, 0.3F, ClipContext.Block.COLLIDER);
    }

    public static HitResult getHitResultOnMoveVector(Entity projectile, Predicate<Entity> filter, ClipContext.Block clipContext) {
        Vec3 vec3 = projectile.getDeltaMovement();
        Level level = projectile.level();
        Vec3 vec31 = projectile.position();
        return getHitResult(vec31, projectile, filter, vec3, level, 0.3F, clipContext);
    }

    public static HitResult getHitResultOnViewVector(Entity projectile, Predicate<Entity> filter, double scale) {
        Vec3 vec3 = projectile.getViewVector(0.0F).scale(scale);
        Level level = projectile.level();
        Vec3 vec31 = projectile.getEyePosition();
        return getHitResult(vec31, projectile, filter, vec3, level, 0.0F, ClipContext.Block.COLLIDER);
    }

    private static HitResult getHitResult(
        Vec3 pos, Entity projectile, Predicate<Entity> filter, Vec3 deltaMovement, Level level, float margin, ClipContext.Block clipContext
    ) {
        Vec3 vec3 = pos.add(deltaMovement);
        HitResult hitresult = level.clip(new ClipContext(pos, vec3, clipContext, ClipContext.Fluid.NONE, projectile));
        if (hitresult.getType() != HitResult.Type.MISS) {
            vec3 = hitresult.getLocation();
        }

        HitResult hitresult1 = getEntityHitResult(
            level, projectile, pos, vec3, projectile.getBoundingBox().expandTowards(deltaMovement).inflate(1.0), filter, margin
        );
        if (hitresult1 != null) {
            hitresult = hitresult1;
        }

        return hitresult;
    }

    /**
     * Gets the EntityRayTraceResult representing the entity hit
     */
    @Nullable
    public static EntityHitResult getEntityHitResult(Entity shooter, Vec3 startVec, Vec3 endVec, AABB boundingBox, Predicate<Entity> filter, double distance) {
        Level level = shooter.level();
        double d0 = distance;
        Entity entity = null;
        Vec3 vec3 = null;

        for (Entity entity1 : level.getEntities(shooter, boundingBox, filter)) {
            AABB aabb = entity1.getBoundingBox().inflate((double)entity1.getPickRadius());
            Optional<Vec3> optional = aabb.clip(startVec, endVec);
            if (aabb.contains(startVec)) {
                if (d0 >= 0.0) {
                    entity = entity1;
                    vec3 = optional.orElse(startVec);
                    d0 = 0.0;
                }
            } else if (optional.isPresent()) {
                Vec3 vec31 = optional.get();
                double d1 = startVec.distanceToSqr(vec31);
                if (d1 < d0 || d0 == 0.0) {
                    if (entity1.getRootVehicle() == shooter.getRootVehicle() && !entity1.canRiderInteract()) {
                        if (d0 == 0.0) {
                            entity = entity1;
                            vec3 = vec31;
                        }
                    } else {
                        entity = entity1;
                        vec3 = vec31;
                        d0 = d1;
                    }
                }
            }
        }

        return entity == null ? null : new EntityHitResult(entity, vec3);
    }

    /**
     * Gets the EntityHitResult representing the entity hit
     */
    @Nullable
    public static EntityHitResult getEntityHitResult(Level level, Entity projectile, Vec3 startVec, Vec3 endVec, AABB boundingBox, Predicate<Entity> filter) {
        return getEntityHitResult(level, projectile, startVec, endVec, boundingBox, filter, 0.3F);
    }

    /**
     * Gets the EntityHitResult representing the entity hit
     */
    @Nullable
    public static EntityHitResult getEntityHitResult(
        Level level, Entity projectile, Vec3 startVec, Vec3 endVec, AABB boundingBox, Predicate<Entity> filter, float inflationAmount
    ) {
        double d0 = Double.MAX_VALUE;
        Entity entity = null;

        for (Entity entity1 : level.getEntities(projectile, boundingBox, filter)) {
            AABB aabb = entity1.getBoundingBox().inflate((double)inflationAmount);
            Optional<Vec3> optional = aabb.clip(startVec, endVec);
            if (optional.isPresent()) {
                double d1 = startVec.distanceToSqr(optional.get());
                if (d1 < d0) {
                    entity = entity1;
                    d0 = d1;
                }
            }
        }

        return entity == null ? null : new EntityHitResult(entity);
    }

    public static void rotateTowardsMovement(Entity projectile, float rotationSpeed) {
        Vec3 vec3 = projectile.getDeltaMovement();
        if (vec3.lengthSqr() != 0.0) {
            double d0 = vec3.horizontalDistance();
            projectile.setYRot((float)(Mth.atan2(vec3.z, vec3.x) * 180.0F / (float)Math.PI) + 90.0F);
            projectile.setXRot((float)(Mth.atan2(d0, vec3.y) * 180.0F / (float)Math.PI) - 90.0F);

            while (projectile.getXRot() - projectile.xRotO < -180.0F) {
                projectile.xRotO -= 360.0F;
            }

            while (projectile.getXRot() - projectile.xRotO >= 180.0F) {
                projectile.xRotO += 360.0F;
            }

            while (projectile.getYRot() - projectile.yRotO < -180.0F) {
                projectile.yRotO -= 360.0F;
            }

            while (projectile.getYRot() - projectile.yRotO >= 180.0F) {
                projectile.yRotO += 360.0F;
            }

            projectile.setXRot(Mth.lerp(rotationSpeed, projectile.xRotO, projectile.getXRot()));
            projectile.setYRot(Mth.lerp(rotationSpeed, projectile.yRotO, projectile.getYRot()));
        }
    }

    @Deprecated // Forge: Use the version below that takes in a Predicate<Item> instead of an Item
    public static InteractionHand getWeaponHoldingHand(LivingEntity shooter, Item weapon) {
        return shooter.getMainHandItem().is(weapon) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }

    public static InteractionHand getWeaponHoldingHand(LivingEntity livingEntity, Predicate<Item> itemPredicate) {
        return itemPredicate.test(livingEntity.getMainHandItem().getItem()) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }

    public static AbstractArrow getMobArrow(LivingEntity shooter, ItemStack arrow, float velocity, @Nullable ItemStack weapon) {
        ArrowItem arrowitem = (ArrowItem)(arrow.getItem() instanceof ArrowItem ? arrow.getItem() : Items.ARROW);
        AbstractArrow abstractarrow = arrowitem.createArrow(shooter.level(), arrow, shooter, weapon);
        abstractarrow.setBaseDamageFromMob(velocity);
        return abstractarrow;
    }
}
