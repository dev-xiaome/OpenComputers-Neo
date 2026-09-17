package net.minecraft.commands.arguments.selector;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class EntitySelector {
    public static final int INFINITE = Integer.MAX_VALUE;
    public static final BiConsumer<Vec3, List<? extends Entity>> ORDER_ARBITRARY = (p_261404_, p_261405_) -> {
    };
    private static final EntityTypeTest<Entity, ?> ANY_TYPE = new EntityTypeTest<Entity, Entity>() {
        public Entity tryCast(Entity p_175109_) {
            return p_175109_;
        }

        @Override
        public Class<? extends Entity> getBaseClass() {
            return Entity.class;
        }
    };
    private final int maxResults;
    private final boolean includesEntities;
    private final boolean worldLimited;
    private final List<Predicate<Entity>> contextFreePredicates;
    private final MinMaxBounds.Doubles range;
    private final Function<Vec3, Vec3> position;
    @Nullable
    private final AABB aabb;
    private final BiConsumer<Vec3, List<? extends Entity>> order;
    private final boolean currentEntity;
    @Nullable
    private final String playerName;
    @Nullable
    private final UUID entityUUID;
    private final EntityTypeTest<Entity, ?> type;
    private final boolean usesSelector;

    public EntitySelector(
        int maxResults,
        boolean includesEntities,
        boolean worldLimited,
        List<Predicate<Entity>> contextFreePredicates,
        MinMaxBounds.Doubles range,
        Function<Vec3, Vec3> position,
        @Nullable AABB aabb,
        BiConsumer<Vec3, List<? extends Entity>> order,
        boolean currentEntity,
        @Nullable String playerName,
        @Nullable UUID entityUUID,
        @Nullable EntityType<?> type,
        boolean usesSelector
    ) {
        this.maxResults = maxResults;
        this.includesEntities = includesEntities;
        this.worldLimited = worldLimited;
        this.contextFreePredicates = contextFreePredicates;
        this.range = range;
        this.position = position;
        this.aabb = aabb;
        this.order = order;
        this.currentEntity = currentEntity;
        this.playerName = playerName;
        this.entityUUID = entityUUID;
        this.type = (EntityTypeTest<Entity, ?>)(type == null ? ANY_TYPE : type);
        this.usesSelector = usesSelector;
    }

    public int getMaxResults() {
        return this.maxResults;
    }

    public boolean includesEntities() {
        return this.includesEntities;
    }

    public boolean isSelfSelector() {
        return this.currentEntity;
    }

    public boolean isWorldLimited() {
        return this.worldLimited;
    }

    public boolean usesSelector() {
        return this.usesSelector;
    }

    private void checkPermissions(CommandSourceStack source) throws CommandSyntaxException {
        if (this.usesSelector && !net.neoforged.neoforge.common.CommonHooks.canUseEntitySelectors(source)) {
            throw EntityArgument.ERROR_SELECTORS_NOT_ALLOWED.create();
        }
    }

    public Entity findSingleEntity(CommandSourceStack source) throws CommandSyntaxException {
        this.checkPermissions(source);
        List<? extends Entity> list = this.findEntities(source);
        if (list.isEmpty()) {
            throw EntityArgument.NO_ENTITIES_FOUND.create();
        } else if (list.size() > 1) {
            throw EntityArgument.ERROR_NOT_SINGLE_ENTITY.create();
        } else {
            return list.get(0);
        }
    }

    public List<? extends Entity> findEntities(CommandSourceStack source) throws CommandSyntaxException {
        this.checkPermissions(source);
        if (!this.includesEntities) {
            return this.findPlayers(source);
        } else if (this.playerName != null) {
            ServerPlayer serverplayer = source.getServer().getPlayerList().getPlayerByName(this.playerName);
            return serverplayer == null ? List.of() : List.of(serverplayer);
        } else if (this.entityUUID != null) {
            for (ServerLevel serverlevel1 : source.getServer().getAllLevels()) {
                Entity entity = serverlevel1.getEntity(this.entityUUID);
                if (entity != null) {
                    if (entity.getType().isEnabled(source.enabledFeatures())) {
                        return List.of(entity);
                    }
                    break;
                }
            }

            return List.of();
        } else {
            Vec3 vec3 = this.position.apply(source.getPosition());
            AABB aabb = this.getAbsoluteAabb(vec3);
            if (this.currentEntity) {
                Predicate<Entity> predicate1 = this.getPredicate(vec3, aabb, null);
                return source.getEntity() != null && predicate1.test(source.getEntity()) ? List.of(source.getEntity()) : List.of();
            } else {
                Predicate<Entity> predicate = this.getPredicate(vec3, aabb, source.enabledFeatures());
                List<Entity> list = new ObjectArrayList<>();
                if (this.isWorldLimited()) {
                    this.addEntities(list, source.getLevel(), aabb, predicate);
                } else {
                    for (ServerLevel serverlevel : source.getServer().getAllLevels()) {
                        this.addEntities(list, serverlevel, aabb, predicate);
                    }
                }

                return this.sortAndLimit(vec3, list);
            }
        }
    }

    private void addEntities(List<Entity> entities, ServerLevel level, @Nullable AABB box, Predicate<Entity> predicate) {
        int i = this.getResultLimit();
        if (entities.size() < i) {
            if (box != null) {
                level.getEntities(this.type, box, predicate, entities, i);
            } else {
                level.getEntities(this.type, predicate, entities, i);
            }
        }
    }

    private int getResultLimit() {
        return this.order == ORDER_ARBITRARY ? this.maxResults : Integer.MAX_VALUE;
    }

    public ServerPlayer findSinglePlayer(CommandSourceStack source) throws CommandSyntaxException {
        this.checkPermissions(source);
        List<ServerPlayer> list = this.findPlayers(source);
        if (list.size() != 1) {
            throw EntityArgument.NO_PLAYERS_FOUND.create();
        } else {
            return list.get(0);
        }
    }

    public List<ServerPlayer> findPlayers(CommandSourceStack source) throws CommandSyntaxException {
        this.checkPermissions(source);
        if (this.playerName != null) {
            ServerPlayer serverplayer2 = source.getServer().getPlayerList().getPlayerByName(this.playerName);
            return serverplayer2 == null ? List.of() : List.of(serverplayer2);
        } else if (this.entityUUID != null) {
            ServerPlayer serverplayer1 = source.getServer().getPlayerList().getPlayer(this.entityUUID);
            return serverplayer1 == null ? List.of() : List.of(serverplayer1);
        } else {
            Vec3 vec3 = this.position.apply(source.getPosition());
            AABB aabb = this.getAbsoluteAabb(vec3);
            Predicate<Entity> predicate = this.getPredicate(vec3, aabb, null);
            if (this.currentEntity) {
                if (source.getEntity() instanceof ServerPlayer serverplayer3 && predicate.test(serverplayer3)) {
                    return List.of(serverplayer3);
                }

                return List.of();
            } else {
                int i = this.getResultLimit();
                List<ServerPlayer> list;
                if (this.isWorldLimited()) {
                    list = source.getLevel().getPlayers(predicate, i);
                } else {
                    list = new ObjectArrayList<>();

                    for (ServerPlayer serverplayer : source.getServer().getPlayerList().getPlayers()) {
                        if (predicate.test(serverplayer)) {
                            list.add(serverplayer);
                            if (list.size() >= i) {
                                return list;
                            }
                        }
                    }
                }

                return this.sortAndLimit(vec3, list);
            }
        }
    }

    @Nullable
    private AABB getAbsoluteAabb(Vec3 pos) {
        return this.aabb != null ? this.aabb.move(pos) : null;
    }

    private Predicate<Entity> getPredicate(Vec3 pos, @Nullable AABB box, @Nullable FeatureFlagSet enabledFeatures) {
        boolean flag = enabledFeatures != null;
        boolean flag1 = box != null;
        boolean flag2 = !this.range.isAny();
        int i = (flag ? 1 : 0) + (flag1 ? 1 : 0) + (flag2 ? 1 : 0);
        List<Predicate<Entity>> list;
        if (i == 0) {
            list = this.contextFreePredicates;
        } else {
            List<Predicate<Entity>> list1 = new ObjectArrayList<>(this.contextFreePredicates.size() + i);
            list1.addAll(this.contextFreePredicates);
            if (flag) {
                list1.add(p_352684_ -> p_352684_.getType().isEnabled(enabledFeatures));
            }

            if (flag1) {
                list1.add(p_121143_ -> box.intersects(p_121143_.getBoundingBox()));
            }

            if (flag2) {
                list1.add(p_121148_ -> this.range.matchesSqr(p_121148_.distanceToSqr(pos)));
            }

            list = list1;
        }

        return Util.allOf(list);
    }

    private <T extends Entity> List<T> sortAndLimit(Vec3 pos, List<T> entities) {
        if (entities.size() > 1) {
            this.order.accept(pos, entities);
        }

        return entities.subList(0, Math.min(this.maxResults, entities.size()));
    }

    public static Component joinNames(List<? extends Entity> names) {
        return ComponentUtils.formatList(names, Entity::getDisplayName);
    }
}
