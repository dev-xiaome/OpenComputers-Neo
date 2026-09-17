package net.minecraft.world.level.block.entity;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.apache.commons.lang3.mutable.MutableInt;

public class BellBlockEntity extends BlockEntity {
    private static final int DURATION = 50;
    private static final int GLOW_DURATION = 60;
    private static final int MIN_TICKS_BETWEEN_SEARCHES = 60;
    private static final int MAX_RESONATION_TICKS = 40;
    private static final int TICKS_BEFORE_RESONATION = 5;
    private static final int SEARCH_RADIUS = 48;
    private static final int HEAR_BELL_RADIUS = 32;
    private static final int HIGHLIGHT_RAIDERS_RADIUS = 48;
    private long lastRingTimestamp;
    /**
     * How many ticks the bell has been ringing.
     */
    public int ticks;
    public boolean shaking;
    public Direction clickDirection;
    private List<LivingEntity> nearbyEntities;
    private boolean resonating;
    /**
     * A tick counter before raiders are revealed. At {@link #TICKS_BEFORE_RESONATION} ticks, the resonation sound is played, and at {@link #MAX_RESONATION_TICKS}, nearby raiders are revealed.
     */
    private int resonationTicks;

    public BellBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityType.BELL, pos, blockState);
    }

    @Override
    public boolean triggerEvent(int id, int type) {
        if (id == 1) {
            this.updateEntities();
            this.resonationTicks = 0;
            this.clickDirection = Direction.from3DDataValue(type);
            this.ticks = 0;
            this.shaking = true;
            return true;
        } else {
            return super.triggerEvent(id, type);
        }
    }

    private static void tick(
        Level level, BlockPos pos, BlockState state, BellBlockEntity blockEntity, BellBlockEntity.ResonationEndAction resonationEndAction
    ) {
        if (blockEntity.shaking) {
            blockEntity.ticks++;
        }

        if (blockEntity.ticks >= 50) {
            blockEntity.shaking = false;
            blockEntity.ticks = 0;
        }

        if (blockEntity.ticks >= 5 && blockEntity.resonationTicks == 0 && areRaidersNearby(pos, blockEntity.nearbyEntities)) {
            blockEntity.resonating = true;
            level.playSound(null, pos, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }

        if (blockEntity.resonating) {
            if (blockEntity.resonationTicks < 40) {
                blockEntity.resonationTicks++;
            } else {
                resonationEndAction.run(level, pos, blockEntity.nearbyEntities);
                blockEntity.resonating = false;
            }
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, BellBlockEntity blockEntity) {
        tick(level, pos, state, blockEntity, BellBlockEntity::showBellParticles);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BellBlockEntity blockEntity) {
        tick(level, pos, state, blockEntity, BellBlockEntity::makeRaidersGlow);
    }

    public void onHit(Direction direction) {
        BlockPos blockpos = this.getBlockPos();
        this.clickDirection = direction;
        if (this.shaking) {
            this.ticks = 0;
        } else {
            this.shaking = true;
        }

        this.level.blockEvent(blockpos, this.getBlockState().getBlock(), 1, direction.get3DDataValue());
    }

    private void updateEntities() {
        BlockPos blockpos = this.getBlockPos();
        if (this.level.getGameTime() > this.lastRingTimestamp + 60L || this.nearbyEntities == null) {
            this.lastRingTimestamp = this.level.getGameTime();
            AABB aabb = new AABB(blockpos).inflate(48.0);
            this.nearbyEntities = this.level.getEntitiesOfClass(LivingEntity.class, aabb);
        }

        if (!this.level.isClientSide) {
            for (LivingEntity livingentity : this.nearbyEntities) {
                if (livingentity.isAlive() && !livingentity.isRemoved() && blockpos.closerToCenterThan(livingentity.position(), 32.0)) {
                    livingentity.getBrain().setMemory(MemoryModuleType.HEARD_BELL_TIME, this.level.getGameTime());
                }
            }
        }
    }

    private static boolean areRaidersNearby(BlockPos pos, List<LivingEntity> raiders) {
        for (LivingEntity livingentity : raiders) {
            if (livingentity.isAlive()
                && !livingentity.isRemoved()
                && pos.closerToCenterThan(livingentity.position(), 32.0)
                && livingentity.getType().is(EntityTypeTags.RAIDERS)) {
                return true;
            }
        }

        return false;
    }

    private static void makeRaidersGlow(Level level, BlockPos pos, List<LivingEntity> raiders) {
        raiders.stream().filter(p_155219_ -> isRaiderWithinRange(pos, p_155219_)).forEach(BellBlockEntity::glow);
    }

    private static void showBellParticles(Level level, BlockPos pos, List<LivingEntity> raiders) {
        MutableInt mutableint = new MutableInt(16700985);
        int i = (int)raiders.stream().filter(p_352878_ -> pos.closerToCenterThan(p_352878_.position(), 48.0)).count();
        raiders.stream()
            .filter(p_155213_ -> isRaiderWithinRange(pos, p_155213_))
            .forEach(
                p_333683_ -> {
                    float f = 1.0F;
                    double d0 = Math.sqrt(
                        (p_333683_.getX() - (double)pos.getX()) * (p_333683_.getX() - (double)pos.getX())
                            + (p_333683_.getZ() - (double)pos.getZ()) * (p_333683_.getZ() - (double)pos.getZ())
                    );
                    double d1 = (double)((float)pos.getX() + 0.5F) + 1.0 / d0 * (p_333683_.getX() - (double)pos.getX());
                    double d2 = (double)((float)pos.getZ() + 0.5F) + 1.0 / d0 * (p_333683_.getZ() - (double)pos.getZ());
                    int j = Mth.clamp((i - 21) / -2, 3, 15);

                    for (int k = 0; k < j; k++) {
                        int l = mutableint.addAndGet(5);
                        level.addParticle(
                            ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, l), d1, (double)((float)pos.getY() + 0.5F), d2, 0.0, 0.0, 0.0
                        );
                    }
                }
            );
    }

    private static boolean isRaiderWithinRange(BlockPos pos, LivingEntity raider) {
        return raider.isAlive()
            && !raider.isRemoved()
            && pos.closerToCenterThan(raider.position(), 48.0)
            && raider.getType().is(EntityTypeTags.RAIDERS);
    }

    private static void glow(LivingEntity entity) {
        entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60));
    }

    @FunctionalInterface
    interface ResonationEndAction {
        void run(Level level, BlockPos pos, List<LivingEntity> raiders);
    }
}
