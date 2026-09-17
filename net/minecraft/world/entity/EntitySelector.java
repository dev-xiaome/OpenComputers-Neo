package net.minecraft.world.entity;

import com.google.common.base.Predicates;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.Team;

public final class EntitySelector {
    /**
     * Selects only entities which are alive
     */
    public static final Predicate<Entity> ENTITY_STILL_ALIVE = Entity::isAlive;
    /**
     * Selects only entities which are LivingEntities and alive
     */
    public static final Predicate<Entity> LIVING_ENTITY_STILL_ALIVE = p_20442_ -> p_20442_.isAlive() && p_20442_ instanceof LivingEntity;
    /**
     * Selects only entities which are neither ridden by anything nor ride on anything
     */
    public static final Predicate<Entity> ENTITY_NOT_BEING_RIDDEN = p_20440_ -> p_20440_.isAlive() && !p_20440_.isVehicle() && !p_20440_.isPassenger();
    /**
     * Selects only entities which are container entities
     */
    public static final Predicate<Entity> CONTAINER_ENTITY_SELECTOR = p_20438_ -> p_20438_ instanceof Container && p_20438_.isAlive();
    /**
     * Selects entities which are neither creative-mode players nor spectator-players
     */
    public static final Predicate<Entity> NO_CREATIVE_OR_SPECTATOR = p_20436_ -> !(p_20436_ instanceof Player)
            || !p_20436_.isSpectator() && !((Player)p_20436_).isCreative();
    /**
     * Selects entities which are either not players or players that are not spectating
     */
    public static final Predicate<Entity> NO_SPECTATORS = p_20434_ -> !p_20434_.isSpectator();
    /**
     * Selects entities which are collidable with and aren't spectators
     */
    public static final Predicate<Entity> CAN_BE_COLLIDED_WITH = NO_SPECTATORS.and(Entity::canBeCollidedWith);

    private EntitySelector() {
    }

    public static Predicate<Entity> withinDistance(double x, double y, double z, double range) {
        double d0 = range * range;
        return p_20420_ -> p_20420_ != null && p_20420_.distanceToSqr(x, y, z) <= d0;
    }

    public static Predicate<Entity> pushableBy(Entity entity) {
        Team team = entity.getTeam();
        Team.CollisionRule team$collisionrule = team == null ? Team.CollisionRule.ALWAYS : team.getCollisionRule();
        return (Predicate<Entity>)(team$collisionrule == Team.CollisionRule.NEVER
            ? Predicates.alwaysFalse()
            : NO_SPECTATORS.and(
                p_20430_ -> {
                    if (!p_20430_.isPushable()) {
                        return false;
                    } else if (!entity.level().isClientSide || p_20430_ instanceof Player && ((Player)p_20430_).isLocalPlayer()) {
                        Team team1 = p_20430_.getTeam();
                        Team.CollisionRule team$collisionrule1 = team1 == null ? Team.CollisionRule.ALWAYS : team1.getCollisionRule();
                        if (team$collisionrule1 == Team.CollisionRule.NEVER) {
                            return false;
                        } else {
                            boolean flag = team != null && team.isAlliedTo(team1);
                            return (team$collisionrule == Team.CollisionRule.PUSH_OWN_TEAM || team$collisionrule1 == Team.CollisionRule.PUSH_OWN_TEAM) && flag
                                ? false
                                : team$collisionrule != Team.CollisionRule.PUSH_OTHER_TEAMS && team$collisionrule1 != Team.CollisionRule.PUSH_OTHER_TEAMS
                                    || flag;
                        }
                    } else {
                        return false;
                    }
                }
            ));
    }

    public static Predicate<Entity> notRiding(Entity entity) {
        return p_20425_ -> {
            while (p_20425_.isPassenger()) {
                p_20425_ = p_20425_.getVehicle();
                if (p_20425_ == entity) {
                    return false;
                }
            }

            return true;
        };
    }

    public static class MobCanWearArmorEntitySelector implements Predicate<Entity> {
        private final ItemStack itemStack;

        public MobCanWearArmorEntitySelector(ItemStack stack) {
            this.itemStack = stack;
        }

        public boolean test(@Nullable Entity entity) {
            if (!entity.isAlive()) {
                return false;
            } else {
                return !(entity instanceof LivingEntity livingentity) ? false : livingentity.canTakeItem(this.itemStack);
            }
        }
    }
}
