package net.minecraft.world.entity.monster;

import java.util.EnumSet;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public abstract class PatrollingMonster extends Monster {
    @Nullable
    private BlockPos patrolTarget;
    private boolean patrolLeader;
    private boolean patrolling;

    protected PatrollingMonster(EntityType<? extends PatrollingMonster> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(4, new PatrollingMonster.LongDistancePatrolGoal<>(this, 0.7, 0.595));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        if (this.patrolTarget != null) {
            compound.put("patrol_target", NbtUtils.writeBlockPos(this.patrolTarget));
        }

        compound.putBoolean("PatrolLeader", this.patrolLeader);
        compound.putBoolean("Patrolling", this.patrolling);
    }

    /**
     * (abstract) Protected helper method to read subclass entity data from NBT.
     */
    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        NbtUtils.readBlockPos(compound, "patrol_target").ifPresent(p_326234_ -> this.patrolTarget = p_326234_);
        this.patrolLeader = compound.getBoolean("PatrolLeader");
        this.patrolling = compound.getBoolean("Patrolling");
    }

    public boolean canBeLeader() {
        return true;
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData) {
        if (spawnType != MobSpawnType.PATROL
            && spawnType != MobSpawnType.EVENT
            && spawnType != MobSpawnType.STRUCTURE
            && level.getRandom().nextFloat() < 0.06F
            && this.canBeLeader()) {
            this.patrolLeader = true;
        }

        if (this.isPatrolLeader()) {
            this.setItemSlot(EquipmentSlot.HEAD, Raid.getLeaderBannerInstance(this.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN)));
            this.setDropChance(EquipmentSlot.HEAD, 2.0F);
        }

        if (spawnType == MobSpawnType.PATROL) {
            this.patrolling = true;
        }

        return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
    }

    public static boolean checkPatrollingMonsterSpawnRules(
        EntityType<? extends PatrollingMonster> patrollingMonster, LevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random
    ) {
        return level.getBrightness(LightLayer.BLOCK, pos) > 8
            ? false
            : checkAnyLightMonsterSpawnRules(patrollingMonster, level, spawnType, pos, random);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return !this.patrolling || distanceToClosestPlayer > 16384.0;
    }

    public void setPatrolTarget(BlockPos patrolTarget) {
        this.patrolTarget = patrolTarget;
        this.patrolling = true;
    }

    public BlockPos getPatrolTarget() {
        return this.patrolTarget;
    }

    public boolean hasPatrolTarget() {
        return this.patrolTarget != null;
    }

    public void setPatrolLeader(boolean patrolLeader) {
        this.patrolLeader = patrolLeader;
        this.patrolling = true;
    }

    public boolean isPatrolLeader() {
        return this.patrolLeader;
    }

    public boolean canJoinPatrol() {
        return true;
    }

    public void findPatrolTarget() {
        this.patrolTarget = this.blockPosition().offset(-500 + this.random.nextInt(1000), 0, -500 + this.random.nextInt(1000));
        this.patrolling = true;
    }

    protected boolean isPatrolling() {
        return this.patrolling;
    }

    protected void setPatrolling(boolean patrolling) {
        this.patrolling = patrolling;
    }

    public static class LongDistancePatrolGoal<T extends PatrollingMonster> extends Goal {
        private static final int NAVIGATION_FAILED_COOLDOWN = 200;
        private final T mob;
        private final double speedModifier;
        private final double leaderSpeedModifier;
        private long cooldownUntil;

        public LongDistancePatrolGoal(T mob, double speedModifier, double leaderSpeedModifier) {
            this.mob = mob;
            this.speedModifier = speedModifier;
            this.leaderSpeedModifier = leaderSpeedModifier;
            this.cooldownUntil = -1L;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            boolean flag = this.mob.level().getGameTime() < this.cooldownUntil;
            return this.mob.isPatrolling() && this.mob.getTarget() == null && !this.mob.hasControllingPassenger() && this.mob.hasPatrolTarget() && !flag;
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public void tick() {
            boolean flag = this.mob.isPatrolLeader();
            PathNavigation pathnavigation = this.mob.getNavigation();
            if (pathnavigation.isDone()) {
                List<PatrollingMonster> list = this.findPatrolCompanions();
                if (this.mob.isPatrolling() && list.isEmpty()) {
                    this.mob.setPatrolling(false);
                } else if (flag && this.mob.getPatrolTarget().closerToCenterThan(this.mob.position(), 10.0)) {
                    this.mob.findPatrolTarget();
                } else {
                    Vec3 vec3 = Vec3.atBottomCenterOf(this.mob.getPatrolTarget());
                    Vec3 vec31 = this.mob.position();
                    Vec3 vec32 = vec31.subtract(vec3);
                    vec3 = vec32.yRot(90.0F).scale(0.4).add(vec3);
                    Vec3 vec33 = vec3.subtract(vec31).normalize().scale(10.0).add(vec31);
                    BlockPos blockpos = BlockPos.containing(vec33);
                    blockpos = this.mob.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockpos);
                    if (!pathnavigation.moveTo(
                        (double)blockpos.getX(), (double)blockpos.getY(), (double)blockpos.getZ(), flag ? this.leaderSpeedModifier : this.speedModifier
                    )) {
                        this.moveRandomly();
                        this.cooldownUntil = this.mob.level().getGameTime() + 200L;
                    } else if (flag) {
                        for (PatrollingMonster patrollingmonster : list) {
                            patrollingmonster.setPatrolTarget(blockpos);
                        }
                    }
                }
            }
        }

        private List<PatrollingMonster> findPatrolCompanions() {
            return this.mob
                .level()
                .getEntitiesOfClass(
                    PatrollingMonster.class, this.mob.getBoundingBox().inflate(16.0), p_325813_ -> p_325813_.canJoinPatrol() && !p_325813_.is(this.mob)
                );
        }

        private boolean moveRandomly() {
            RandomSource randomsource = this.mob.getRandom();
            BlockPos blockpos = this.mob
                .level()
                .getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, this.mob.blockPosition().offset(-8 + randomsource.nextInt(16), 0, -8 + randomsource.nextInt(16))
                );
            return this.mob.getNavigation().moveTo((double)blockpos.getX(), (double)blockpos.getY(), (double)blockpos.getZ(), this.speedModifier);
        }
    }
}
