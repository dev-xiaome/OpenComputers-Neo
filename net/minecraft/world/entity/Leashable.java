package net.minecraft.world.entity;

import com.mojang.datafixers.util.Either;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.item.Items;

public interface Leashable {
    String LEASH_TAG = "leash";
    double LEASH_TOO_FAR_DIST = 10.0;
    double LEASH_ELASTIC_DIST = 6.0;

    @Nullable
    Leashable.LeashData getLeashData();

    void setLeashData(@Nullable Leashable.LeashData leashData);

    default boolean isLeashed() {
        return this.getLeashData() != null && this.getLeashData().leashHolder != null;
    }

    default boolean mayBeLeashed() {
        return this.getLeashData() != null;
    }

    default boolean canHaveALeashAttachedToIt() {
        return this.canBeLeashed() && !this.isLeashed();
    }

    default boolean canBeLeashed() {
        return true;
    }

    default void setDelayedLeashHolderId(int delayedLeashHolderId) {
        this.setLeashData(new Leashable.LeashData(delayedLeashHolderId));
        dropLeash((Entity & Leashable)this, false, false);
    }

    @Nullable
    default Leashable.LeashData readLeashData(CompoundTag tag) {
        if (tag.contains("leash", 10)) {
            return new Leashable.LeashData(Either.left(tag.getCompound("leash").getUUID("UUID")));
        } else {
            if (tag.contains("leash", 11)) {
                Either<UUID, BlockPos> either = NbtUtils.readBlockPos(tag, "leash").<Either<UUID, BlockPos>>map(Either::right).orElse(null);
                if (either != null) {
                    return new Leashable.LeashData(either);
                }
            }

            return null;
        }
    }

    default void writeLeashData(CompoundTag tag, @Nullable Leashable.LeashData leashData) {
        if (leashData != null) {
            Either<UUID, BlockPos> either = leashData.delayedLeashInfo;
            if (leashData.leashHolder instanceof LeashFenceKnotEntity leashfenceknotentity) {
                either = Either.right(leashfenceknotentity.getPos());
            } else if (leashData.leashHolder != null) {
                either = Either.left(leashData.leashHolder.getUUID());
            }

            if (either != null) {
                tag.put("leash", either.map(p_352326_ -> {
                    CompoundTag compoundtag = new CompoundTag();
                    compoundtag.putUUID("UUID", p_352326_);
                    return compoundtag;
                }, NbtUtils::writeBlockPos));
            }
        }
    }

    private static <E extends Entity & Leashable> void restoreLeashFromSave(E p_entity, Leashable.LeashData leashData) {
        if (leashData.delayedLeashInfo != null && p_entity.level() instanceof ServerLevel serverlevel) {
            Optional<UUID> optional1 = leashData.delayedLeashInfo.left();
            Optional<BlockPos> optional = leashData.delayedLeashInfo.right();
            if (optional1.isPresent()) {
                Entity entity = serverlevel.getEntity(optional1.get());
                if (entity != null) {
                    setLeashedTo(p_entity, entity, true);
                    return;
                }
            } else if (optional.isPresent()) {
                setLeashedTo(p_entity, LeashFenceKnotEntity.getOrCreateKnot(serverlevel, optional.get()), true);
                return;
            }

            if (p_entity.tickCount > 100) {
                p_entity.spawnAtLocation(Items.LEAD);
                p_entity.setLeashData(null);
            }
        }
    }

    default void dropLeash(boolean broadcastPacket, boolean dropItem) {
        dropLeash((Entity & Leashable)this, broadcastPacket, dropItem);
    }

    private static <E extends Entity & Leashable> void dropLeash(E entity, boolean broadcastPacket, boolean dropItem) {
        Leashable.LeashData leashable$leashdata = entity.getLeashData();
        if (leashable$leashdata != null && leashable$leashdata.leashHolder != null) {
            entity.setLeashData(null);
            if (!entity.level().isClientSide && dropItem) {
                entity.spawnAtLocation(Items.LEAD);
            }

            if (broadcastPacket && entity.level() instanceof ServerLevel serverlevel) {
                serverlevel.getChunkSource().broadcast(entity, new ClientboundSetEntityLinkPacket(entity, null));
            }
        }
    }

    static <E extends Entity & Leashable> void tickLeash(E p_entity) {
        Leashable.LeashData leashable$leashdata = p_entity.getLeashData();
        if (leashable$leashdata != null && leashable$leashdata.delayedLeashInfo != null) {
            restoreLeashFromSave(p_entity, leashable$leashdata);
        }

        if (leashable$leashdata != null && leashable$leashdata.leashHolder != null) {
            if (!p_entity.isAlive() || !leashable$leashdata.leashHolder.isAlive()) {
                dropLeash(p_entity, true, true);
            }

            Entity entity = p_entity.getLeashHolder();
            if (entity != null && entity.level() == p_entity.level()) {
                float f = p_entity.distanceTo(entity);
                if (!p_entity.handleLeashAtDistance(entity, f)) {
                    return;
                }

                if ((double)f > 10.0) {
                    p_entity.leashTooFarBehaviour();
                } else if ((double)f > 6.0) {
                    p_entity.elasticRangeLeashBehaviour(entity, f);
                    p_entity.checkSlowFallDistance();
                } else {
                    p_entity.closeRangeLeashBehaviour(entity);
                }
            }
        }
    }

    default boolean handleLeashAtDistance(Entity leashHolder, float distance) {
        return true;
    }

    default void leashTooFarBehaviour() {
        this.dropLeash(true, true);
    }

    default void closeRangeLeashBehaviour(Entity entity) {
    }

    default void elasticRangeLeashBehaviour(Entity leashHolder, float distance) {
        legacyElasticRangeLeashBehaviour((Entity & Leashable)this, leashHolder, distance);
    }

    private static <E extends Entity & Leashable> void legacyElasticRangeLeashBehaviour(E entity, Entity leashHolder, float distance) {
        double d0 = (leashHolder.getX() - entity.getX()) / (double)distance;
        double d1 = (leashHolder.getY() - entity.getY()) / (double)distance;
        double d2 = (leashHolder.getZ() - entity.getZ()) / (double)distance;
        entity.setDeltaMovement(
            entity.getDeltaMovement().add(Math.copySign(d0 * d0 * 0.4, d0), Math.copySign(d1 * d1 * 0.4, d1), Math.copySign(d2 * d2 * 0.4, d2))
        );
    }

    default void setLeashedTo(Entity leashHolder, boolean broadcastPacket) {
        setLeashedTo((Entity & Leashable)this, leashHolder, broadcastPacket);
    }

    private static <E extends Entity & Leashable> void setLeashedTo(E entity, Entity leashHolder, boolean broadcastPacket) {
        Leashable.LeashData leashable$leashdata = entity.getLeashData();
        if (leashable$leashdata == null) {
            leashable$leashdata = new Leashable.LeashData(leashHolder);
            entity.setLeashData(leashable$leashdata);
        } else {
            leashable$leashdata.setLeashHolder(leashHolder);
        }

        if (broadcastPacket && entity.level() instanceof ServerLevel serverlevel) {
            serverlevel.getChunkSource().broadcast(entity, new ClientboundSetEntityLinkPacket(entity, leashHolder));
        }

        if (entity.isPassenger()) {
            entity.stopRiding();
        }
    }

    @Nullable
    default Entity getLeashHolder() {
        return getLeashHolder((Entity & Leashable)this);
    }

    @Nullable
    private static <E extends Entity & Leashable> Entity getLeashHolder(E p_entity) {
        Leashable.LeashData leashable$leashdata = p_entity.getLeashData();
        if (leashable$leashdata == null) {
            return null;
        } else {
            if (leashable$leashdata.delayedLeashHolderId != 0 && p_entity.level().isClientSide) {
                Entity entity = p_entity.level().getEntity(leashable$leashdata.delayedLeashHolderId);
                if (entity instanceof Entity) {
                    leashable$leashdata.setLeashHolder(entity);
                }
            }

            return leashable$leashdata.leashHolder;
        }
    }

    public static final class LeashData {
        int delayedLeashHolderId;
        @Nullable
        public Entity leashHolder;
        @Nullable
        public Either<UUID, BlockPos> delayedLeashInfo;

        LeashData(Either<UUID, BlockPos> delayedLeashInfo) {
            this.delayedLeashInfo = delayedLeashInfo;
        }

        LeashData(Entity leashHolder) {
            this.leashHolder = leashHolder;
        }

        LeashData(int delayedLeashInfoId) {
            this.delayedLeashHolderId = delayedLeashInfoId;
        }

        public void setLeashHolder(Entity leashHolder) {
            this.leashHolder = leashHolder;
            this.delayedLeashInfo = null;
            this.delayedLeashHolderId = 0;
        }
    }
}
