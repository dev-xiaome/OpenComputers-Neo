package net.minecraft.world.entity;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

public class Interaction extends Entity implements Attackable, Targeting {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final EntityDataAccessor<Float> DATA_WIDTH_ID = SynchedEntityData.defineId(Interaction.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HEIGHT_ID = SynchedEntityData.defineId(Interaction.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_RESPONSE_ID = SynchedEntityData.defineId(Interaction.class, EntityDataSerializers.BOOLEAN);
    private static final String TAG_WIDTH = "width";
    private static final String TAG_HEIGHT = "height";
    private static final String TAG_ATTACK = "attack";
    private static final String TAG_INTERACTION = "interaction";
    private static final String TAG_RESPONSE = "response";
    @Nullable
    private Interaction.PlayerAction attack;
    @Nullable
    private Interaction.PlayerAction interaction;

    public Interaction(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_WIDTH_ID, 1.0F);
        builder.define(DATA_HEIGHT_ID, 1.0F);
        builder.define(DATA_RESPONSE_ID, false);
    }

    /**
     * (abstract) Protected helper method to read subclass entity data from NBT.
     */
    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        if (compound.contains("width", 99)) {
            this.setWidth(compound.getFloat("width"));
        }

        if (compound.contains("height", 99)) {
            this.setHeight(compound.getFloat("height"));
        }

        if (compound.contains("attack")) {
            Interaction.PlayerAction.CODEC
                .decode(NbtOps.INSTANCE, compound.get("attack"))
                .resultOrPartial(Util.prefix("Interaction entity", LOGGER::error))
                .ifPresent(p_273699_ -> this.attack = p_273699_.getFirst());
        } else {
            this.attack = null;
        }

        if (compound.contains("interaction")) {
            Interaction.PlayerAction.CODEC
                .decode(NbtOps.INSTANCE, compound.get("interaction"))
                .resultOrPartial(Util.prefix("Interaction entity", LOGGER::error))
                .ifPresent(p_273686_ -> this.interaction = p_273686_.getFirst());
        } else {
            this.interaction = null;
        }

        this.setResponse(compound.getBoolean("response"));
        this.setBoundingBox(this.makeBoundingBox());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        compound.putFloat("width", this.getWidth());
        compound.putFloat("height", this.getHeight());
        if (this.attack != null) {
            Interaction.PlayerAction.CODEC.encodeStart(NbtOps.INSTANCE, this.attack).ifSuccess(p_272806_ -> compound.put("attack", p_272806_));
        }

        if (this.interaction != null) {
            Interaction.PlayerAction.CODEC.encodeStart(NbtOps.INSTANCE, this.interaction).ifSuccess(p_272925_ -> compound.put("interaction", p_272925_));
        }

        compound.putBoolean("response", this.getResponse());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_HEIGHT_ID.equals(key) || DATA_WIDTH_ID.equals(key)) {
            this.setBoundingBox(this.makeBoundingBox());
        }
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    /**
     * Called when a player attacks an entity. If this returns true the attack will not happen.
     */
    @Override
    public boolean skipAttackInteraction(Entity entity) {
        if (entity instanceof Player player) {
            this.attack = new Interaction.PlayerAction(player.getUUID(), this.level().getGameTime());
            if (player instanceof ServerPlayer serverplayer) {
                CriteriaTriggers.PLAYER_HURT_ENTITY.trigger(serverplayer, this, player.damageSources().generic(), 1.0F, 1.0F, false);
            }

            return !this.getResponse();
        } else {
            return false;
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (this.level().isClientSide) {
            return this.getResponse() ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
        } else {
            this.interaction = new Interaction.PlayerAction(player.getUUID(), this.level().getGameTime());
            return InteractionResult.CONSUME;
        }
    }

    @Override
    public void tick() {
    }

    @Nullable
    @Override
    public LivingEntity getLastAttacker() {
        return this.attack != null ? this.level().getPlayerByUUID(this.attack.player()) : null;
    }

    @Nullable
    @Override
    public LivingEntity getTarget() {
        return this.interaction != null ? this.level().getPlayerByUUID(this.interaction.player()) : null;
    }

    private void setWidth(float width) {
        this.entityData.set(DATA_WIDTH_ID, width);
    }

    private float getWidth() {
        return this.entityData.get(DATA_WIDTH_ID);
    }

    private void setHeight(float height) {
        this.entityData.set(DATA_HEIGHT_ID, height);
    }

    private float getHeight() {
        return this.entityData.get(DATA_HEIGHT_ID);
    }

    private void setResponse(boolean response) {
        this.entityData.set(DATA_RESPONSE_ID, response);
    }

    private boolean getResponse() {
        return this.entityData.get(DATA_RESPONSE_ID);
    }

    private EntityDimensions getDimensions() {
        return EntityDimensions.scalable(this.getWidth(), this.getHeight());
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return this.getDimensions();
    }

    @Override
    protected AABB makeBoundingBox() {
        return this.getDimensions().makeBoundingBox(this.position());
    }

    static record PlayerAction(UUID player, long timestamp) {
        public static final Codec<Interaction.PlayerAction> CODEC = RecordCodecBuilder.create(
            p_273237_ -> p_273237_.group(
                        UUIDUtil.CODEC.fieldOf("player").forGetter(Interaction.PlayerAction::player),
                        Codec.LONG.fieldOf("timestamp").forGetter(Interaction.PlayerAction::timestamp)
                    )
                    .apply(p_273237_, Interaction.PlayerAction::new)
        );
    }
}
