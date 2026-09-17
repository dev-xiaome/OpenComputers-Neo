package net.minecraft.world.entity.animal.armadillo;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.AnimalMakeLove;
import net.minecraft.world.entity.ai.behavior.AnimalPanic;
import net.minecraft.world.entity.ai.behavior.BabyFollowAdult;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.CountDownCooldownTicks;
import net.minecraft.world.entity.ai.behavior.DoNothing;
import net.minecraft.world.entity.ai.behavior.FollowTemptation;
import net.minecraft.world.entity.ai.behavior.LookAtTargetSink;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.behavior.RandomLookAround;
import net.minecraft.world.entity.ai.behavior.RandomStroll;
import net.minecraft.world.entity.ai.behavior.RunOne;
import net.minecraft.world.entity.ai.behavior.SetEntityLookTargetSometimes;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromLookTarget;
import net.minecraft.world.entity.ai.behavior.Swim;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.behavior.declarative.MemoryAccessor;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;

public class ArmadilloAi {
    private static final float SPEED_MULTIPLIER_WHEN_PANICKING = 2.0F;
    private static final float SPEED_MULTIPLIER_WHEN_IDLING = 1.0F;
    private static final float SPEED_MULTIPLIER_WHEN_TEMPTED = 1.25F;
    private static final float SPEED_MULTIPLIER_WHEN_FOLLOWING_ADULT = 1.25F;
    private static final float SPEED_MULTIPLIER_WHEN_MAKING_LOVE = 1.0F;
    private static final double DEFAULT_CLOSE_ENOUGH_DIST = 2.0;
    private static final double BABY_CLOSE_ENOUGH_DIST = 1.0;
    private static final UniformInt ADULT_FOLLOW_RANGE = UniformInt.of(5, 16);
    private static final ImmutableList<SensorType<? extends Sensor<? super Armadillo>>> SENSOR_TYPES = ImmutableList.of(
        SensorType.NEAREST_LIVING_ENTITIES, SensorType.HURT_BY, SensorType.ARMADILLO_TEMPTATIONS, SensorType.NEAREST_ADULT, SensorType.ARMADILLO_SCARE_DETECTED
    );
    private static final ImmutableList<MemoryModuleType<?>> MEMORY_TYPES = ImmutableList.of(
        MemoryModuleType.IS_PANICKING,
        MemoryModuleType.HURT_BY,
        MemoryModuleType.HURT_BY_ENTITY,
        MemoryModuleType.WALK_TARGET,
        MemoryModuleType.LOOK_TARGET,
        MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
        MemoryModuleType.PATH,
        MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
        MemoryModuleType.TEMPTING_PLAYER,
        MemoryModuleType.TEMPTATION_COOLDOWN_TICKS,
        MemoryModuleType.GAZE_COOLDOWN_TICKS,
        MemoryModuleType.IS_TEMPTED,
        MemoryModuleType.BREED_TARGET,
        MemoryModuleType.NEAREST_VISIBLE_ADULT,
        MemoryModuleType.DANGER_DETECTED_RECENTLY
    );
    private static final OneShot<Armadillo> ARMADILLO_ROLLING_OUT = BehaviorBuilder.create(
        p_316587_ -> p_316587_.group(p_316587_.absent(MemoryModuleType.DANGER_DETECTED_RECENTLY))
                .apply(p_316587_, p_316348_ -> (p_319679_, p_319680_, p_319681_) -> {
                        if (p_319680_.isScared()) {
                            p_319680_.rollOut();
                            return true;
                        } else {
                            return false;
                        }
                    })
    );

    public static Brain.Provider<Armadillo> brainProvider() {
        return Brain.provider(MEMORY_TYPES, SENSOR_TYPES);
    }

    protected static Brain<?> makeBrain(Brain<Armadillo> brain) {
        initCoreActivity(brain);
        initIdleActivity(brain);
        initScaredActivity(brain);
        brain.setCoreActivities(Set.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.useDefaultActivity();
        return brain;
    }

    private static void initCoreActivity(Brain<Armadillo> brain) {
        brain.addActivity(
            Activity.CORE,
            0,
            ImmutableList.of(
                new Swim(0.8F),
                new ArmadilloAi.ArmadilloPanic(2.0F),
                new LookAtTargetSink(45, 90),
                new MoveToTargetSink() {
                    @Override
                    protected boolean checkExtraStartConditions(ServerLevel p_316506_, Mob p_316710_) {
                        if (p_316710_ instanceof Armadillo armadillo && armadillo.isScared()) {
                            return false;
                        }

                        return super.checkExtraStartConditions(p_316506_, p_316710_);
                    }
                },
                new CountDownCooldownTicks(MemoryModuleType.TEMPTATION_COOLDOWN_TICKS),
                new CountDownCooldownTicks(MemoryModuleType.GAZE_COOLDOWN_TICKS),
                ARMADILLO_ROLLING_OUT
            )
        );
    }

    private static void initIdleActivity(Brain<Armadillo> brain) {
        brain.addActivity(
            Activity.IDLE,
            ImmutableList.of(
                Pair.of(0, SetEntityLookTargetSometimes.create(EntityType.PLAYER, 6.0F, UniformInt.of(30, 60))),
                Pair.of(1, new AnimalMakeLove(EntityType.ARMADILLO, 1.0F, 1)),
                Pair.of(
                    2,
                    new RunOne<>(
                        ImmutableList.of(
                            Pair.of(new FollowTemptation(p_316818_ -> 1.25F, p_319682_ -> p_319682_.isBaby() ? 1.0 : 2.0), 1),
                            Pair.of(BabyFollowAdult.create(ADULT_FOLLOW_RANGE, 1.25F), 1)
                        )
                    )
                ),
                Pair.of(3, new RandomLookAround(UniformInt.of(150, 250), 30.0F, 0.0F, 0.0F)),
                Pair.of(
                    4,
                    new RunOne<>(
                        ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT),
                        ImmutableList.of(
                            Pair.of(RandomStroll.stroll(1.0F), 1), Pair.of(SetWalkTargetFromLookTarget.create(1.0F, 3), 1), Pair.of(new DoNothing(30, 60), 1)
                        )
                    )
                )
            )
        );
    }

    private static void initScaredActivity(Brain<Armadillo> brain) {
        brain.addActivityWithConditions(
            Activity.PANIC,
            ImmutableList.of(Pair.of(0, new ArmadilloAi.ArmadilloBallUp())),
            Set.of(
                Pair.of(MemoryModuleType.DANGER_DETECTED_RECENTLY, MemoryStatus.VALUE_PRESENT),
                Pair.of(MemoryModuleType.IS_PANICKING, MemoryStatus.VALUE_ABSENT)
            )
        );
    }

    public static void updateActivity(Armadillo armadillo) {
        armadillo.getBrain().setActiveActivityToFirstValid(ImmutableList.of(Activity.PANIC, Activity.IDLE));
    }

    public static Predicate<ItemStack> getTemptations() {
        return p_335265_ -> p_335265_.is(ItemTags.ARMADILLO_FOOD);
    }

    public static class ArmadilloBallUp extends Behavior<Armadillo> {
        static final int BALL_UP_STAY_IN_STATE = 5 * TimeUtil.SECONDS_PER_MINUTE * 20;
        static final int TICKS_DELAY_TO_DETERMINE_IF_DANGER_IS_STILL_AROUND = 5;
        static final int DANGER_DETECTED_RECENTLY_DANGER_THRESHOLD = 75;
        int nextPeekTimer = 0;
        boolean dangerWasAround;

        public ArmadilloBallUp() {
            super(Map.of(), BALL_UP_STAY_IN_STATE);
        }

        protected void tick(ServerLevel level, Armadillo owner, long gameTime) {
            super.tick(level, owner, gameTime);
            if (this.nextPeekTimer > 0) {
                this.nextPeekTimer--;
            }

            if (owner.shouldSwitchToScaredState()) {
                owner.switchToState(Armadillo.ArmadilloState.SCARED);
                if (owner.onGround()) {
                    owner.playSound(SoundEvents.ARMADILLO_LAND);
                }
            } else {
                Armadillo.ArmadilloState armadillo$armadillostate = owner.getState();
                long i = owner.getBrain().getTimeUntilExpiry(MemoryModuleType.DANGER_DETECTED_RECENTLY);
                boolean flag = i > 75L;
                if (flag != this.dangerWasAround) {
                    this.nextPeekTimer = this.pickNextPeekTimer(owner);
                }

                this.dangerWasAround = flag;
                if (armadillo$armadillostate == Armadillo.ArmadilloState.SCARED) {
                    if (this.nextPeekTimer == 0 && owner.onGround() && flag) {
                        level.broadcastEntityEvent(owner, (byte)64);
                        this.nextPeekTimer = this.pickNextPeekTimer(owner);
                    }

                    if (i < (long)Armadillo.ArmadilloState.UNROLLING.animationDuration()) {
                        owner.playSound(SoundEvents.ARMADILLO_UNROLL_START);
                        owner.switchToState(Armadillo.ArmadilloState.UNROLLING);
                    }
                } else if (armadillo$armadillostate == Armadillo.ArmadilloState.UNROLLING && i > (long)Armadillo.ArmadilloState.UNROLLING.animationDuration()) {
                    owner.switchToState(Armadillo.ArmadilloState.SCARED);
                }
            }
        }

        private int pickNextPeekTimer(Armadillo armadillo) {
            return Armadillo.ArmadilloState.SCARED.animationDuration() + armadillo.getRandom().nextIntBetweenInclusive(100, 400);
        }

        protected boolean checkExtraStartConditions(ServerLevel level, Armadillo owner) {
            return owner.onGround();
        }

        protected boolean canStillUse(ServerLevel level, Armadillo entity, long gameTime) {
            return entity.getState().isThreatened();
        }

        protected void start(ServerLevel level, Armadillo entity, long gameTime) {
            entity.rollUp();
        }

        protected void stop(ServerLevel level, Armadillo entity, long gameTime) {
            if (!entity.canStayRolledUp()) {
                entity.rollOut();
            }
        }
    }

    public static class ArmadilloPanic extends AnimalPanic<Armadillo> {
        public ArmadilloPanic(float p_316413_) {
            super(p_316413_, p_350284_ -> DamageTypeTags.PANIC_ENVIRONMENTAL_CAUSES);
        }

        protected void start(ServerLevel p_326201_, Armadillo p_326188_, long p_325949_) {
            p_326188_.rollOut();
            super.start(p_326201_, p_326188_, p_325949_);
        }
    }
}
