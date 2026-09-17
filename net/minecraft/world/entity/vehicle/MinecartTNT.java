package net.minecraft.world.entity.vehicle;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public class MinecartTNT extends AbstractMinecart {
    private static final byte EVENT_PRIME = 10;
    private int fuse = -1;

    public MinecartTNT(EntityType<? extends MinecartTNT> entityType, Level level) {
        super(entityType, level);
    }

    public MinecartTNT(Level level, double x, double y, double z) {
        super(EntityType.TNT_MINECART, level, x, y, z);
    }

    @Override
    public AbstractMinecart.Type getMinecartType() {
        return AbstractMinecart.Type.TNT;
    }

    @Override
    public BlockState getDefaultDisplayBlockState() {
        return Blocks.TNT.defaultBlockState();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.fuse > 0) {
            this.fuse--;
            this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.5, this.getZ(), 0.0, 0.0, 0.0);
        } else if (this.fuse == 0) {
            this.explode(this.getDeltaMovement().horizontalDistanceSqr());
        }

        if (this.horizontalCollision) {
            double d0 = this.getDeltaMovement().horizontalDistanceSqr();
            if (d0 >= 0.01F) {
                this.explode(d0);
            }
        }
    }

    /**
     * Called when the entity is attacked.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getDirectEntity() instanceof AbstractArrow abstractarrow && abstractarrow.isOnFire()) {
            DamageSource damagesource = this.damageSources().explosion(this, source.getEntity());
            this.explode(damagesource, abstractarrow.getDeltaMovement().lengthSqr());
        }

        return super.hurt(source, amount);
    }

    @Override
    public void destroy(DamageSource source) {
        double d0 = this.getDeltaMovement().horizontalDistanceSqr();
        if (!damageSourceIgnitesTnt(source) && !(d0 >= 0.01F)) {
            this.destroy(this.getDropItem());
        } else {
            if (this.fuse < 0) {
                this.primeFuse();
                this.fuse = this.random.nextInt(20) + this.random.nextInt(20);
            }
        }
    }

    @Override
    protected Item getDropItem() {
        return Items.TNT_MINECART;
    }

    /**
     * Makes the minecart explode.
     */
    protected void explode(double radiusModifier) {
        this.explode(null, radiusModifier);
    }

    protected void explode(@Nullable DamageSource damageSource, double radiusModifier) {
        if (!this.level().isClientSide) {
            double d0 = Math.sqrt(radiusModifier);
            if (d0 > 5.0) {
                d0 = 5.0;
            }

            this.level()
                .explode(
                    this,
                    damageSource,
                    null,
                    this.getX(),
                    this.getY(),
                    this.getZ(),
                    (float)(4.0 + this.random.nextDouble() * 1.5 * d0),
                    false,
                    Level.ExplosionInteraction.TNT
                );
            this.discard();
        }
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        if (fallDistance >= 3.0F) {
            float f = fallDistance / 10.0F;
            this.explode((double)(f * f));
        }

        return super.causeFallDamage(fallDistance, multiplier, source);
    }

    /**
     * Called every tick the minecart is on an activator rail.
     */
    @Override
    public void activateMinecart(int x, int y, int z, boolean receivingPower) {
        if (receivingPower && this.fuse < 0) {
            this.primeFuse();
        }
    }

    /**
     * Handles an entity event received from a {@link net.minecraft.network.protocol.game.ClientboundEntityEventPacket}.
     */
    @Override
    public void handleEntityEvent(byte id) {
        if (id == 10) {
            this.primeFuse();
        } else {
            super.handleEntityEvent(id);
        }
    }

    public void primeFuse() {
        this.fuse = 80;
        if (!this.level().isClientSide) {
            this.level().broadcastEntityEvent(this, (byte)10);
            if (!this.isSilent()) {
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
        }
    }

    public int getFuse() {
        return this.fuse;
    }

    public boolean isPrimed() {
        return this.fuse > -1;
    }

    /**
     * Explosion resistance of a block relative to this entity
     */
    @Override
    public float getBlockExplosionResistance(
        Explosion explosion, BlockGetter level, BlockPos pos, BlockState blockState, FluidState fluidState, float explosionPower
    ) {
        return !this.isPrimed() || !blockState.is(BlockTags.RAILS) && !level.getBlockState(pos.above()).is(BlockTags.RAILS)
            ? super.getBlockExplosionResistance(explosion, level, pos, blockState, fluidState, explosionPower)
            : 0.0F;
    }

    @Override
    public boolean shouldBlockExplode(Explosion explosion, BlockGetter level, BlockPos pos, BlockState blockState, float explosionPower) {
        return !this.isPrimed() || !blockState.is(BlockTags.RAILS) && !level.getBlockState(pos.above()).is(BlockTags.RAILS)
            ? super.shouldBlockExplode(explosion, level, pos, blockState, explosionPower)
            : false;
    }

    /**
     * (abstract) Protected helper method to read subclass entity data from NBT.
     */
    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("TNTFuse", 99)) {
            this.fuse = compound.getInt("TNTFuse");
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("TNTFuse", this.fuse);
    }

    @Override
    boolean shouldSourceDestroy(DamageSource source) {
        return damageSourceIgnitesTnt(source);
    }

    private static boolean damageSourceIgnitesTnt(DamageSource source) {
        return source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION);
    }
}
