package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.Sets;
import com.mojang.datafixers.kinds.OptionalBox.Mu;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.behavior.declarative.MemoryAccessor;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.mutable.MutableObject;

public class InteractWithDoor {
    private static final int COOLDOWN_BEFORE_RERUNNING_IN_SAME_NODE = 20;
    private static final double SKIP_CLOSING_DOOR_IF_FURTHER_AWAY_THAN = 3.0;
    private static final double MAX_DISTANCE_TO_HOLD_DOOR_OPEN_FOR_OTHER_MOBS = 2.0;

    public static BehaviorControl<LivingEntity> create() {
        MutableObject<Node> mutableobject = new MutableObject<>(null);
        MutableInt mutableint = new MutableInt(0);
        return BehaviorBuilder.create(
            p_258474_ -> p_258474_.group(
                        p_258474_.present(MemoryModuleType.PATH),
                        p_258474_.registered(MemoryModuleType.DOORS_TO_CLOSE),
                        p_258474_.registered(MemoryModuleType.NEAREST_LIVING_ENTITIES)
                    )
                    .apply(
                        p_258474_,
                        (p_258460_, p_258461_, p_258462_) -> (p_258469_, p_258470_, p_258471_) -> {
                                Path path = p_258474_.get(p_258460_);
                                Optional<Set<GlobalPos>> optional = p_258474_.tryGet(p_258461_);
                                if (!path.notStarted() && !path.isDone()) {
                                    if (Objects.equals(mutableobject.getValue(), path.getNextNode())) {
                                        mutableint.setValue(20);
                                    } else if (mutableint.decrementAndGet() > 0) {
                                        return false;
                                    }

                                    mutableobject.setValue(path.getNextNode());
                                    Node node = path.getPreviousNode();
                                    Node node1 = path.getNextNode();
                                    BlockPos blockpos = node.asBlockPos();
                                    BlockState blockstate = p_258469_.getBlockState(blockpos);
                                    if (blockstate.is(BlockTags.MOB_INTERACTABLE_DOORS, p_201959_ -> p_201959_.getBlock() instanceof DoorBlock)) {
                                        DoorBlock doorblock = (DoorBlock)blockstate.getBlock();
                                        if (!doorblock.isOpen(blockstate)) {
                                            doorblock.setOpen(p_258470_, p_258469_, blockstate, blockpos, true);
                                        }

                                        optional = rememberDoorToClose(p_258461_, optional, p_258469_, blockpos);
                                    }

                                    BlockPos blockpos1 = node1.asBlockPos();
                                    BlockState blockstate1 = p_258469_.getBlockState(blockpos1);
                                    if (blockstate1.is(BlockTags.MOB_INTERACTABLE_DOORS, p_201957_ -> p_201957_.getBlock() instanceof DoorBlock)) {
                                        DoorBlock doorblock1 = (DoorBlock)blockstate1.getBlock();
                                        if (!doorblock1.isOpen(blockstate1)) {
                                            doorblock1.setOpen(p_258470_, p_258469_, blockstate1, blockpos1, true);
                                            optional = rememberDoorToClose(p_258461_, optional, p_258469_, blockpos1);
                                        }
                                    }

                                    optional.ifPresent(
                                        p_258452_ -> closeDoorsThatIHaveOpenedOrPassedThrough(
                                                p_258469_, p_258470_, node, node1, (Set<GlobalPos>)p_258452_, p_258474_.tryGet(p_258462_)
                                            )
                                    );
                                    return true;
                                } else {
                                    return false;
                                }
                            }
                    )
        );
    }

    public static void closeDoorsThatIHaveOpenedOrPassedThrough(
        ServerLevel level,
        LivingEntity entity,
        @Nullable Node previous,
        @Nullable Node next,
        Set<GlobalPos> doorPositions,
        Optional<List<LivingEntity>> nearestLivingEntities
    ) {
        Iterator<GlobalPos> iterator = doorPositions.iterator();

        while (iterator.hasNext()) {
            GlobalPos globalpos = iterator.next();
            BlockPos blockpos = globalpos.pos();
            if ((previous == null || !previous.asBlockPos().equals(blockpos)) && (next == null || !next.asBlockPos().equals(blockpos))) {
                if (isDoorTooFarAway(level, entity, globalpos)) {
                    iterator.remove();
                } else {
                    BlockState blockstate = level.getBlockState(blockpos);
                    if (!blockstate.is(BlockTags.MOB_INTERACTABLE_DOORS, p_201952_ -> p_201952_.getBlock() instanceof DoorBlock)) {
                        iterator.remove();
                    } else {
                        DoorBlock doorblock = (DoorBlock)blockstate.getBlock();
                        if (!doorblock.isOpen(blockstate)) {
                            iterator.remove();
                        } else if (areOtherMobsComingThroughDoor(entity, blockpos, nearestLivingEntities)) {
                            iterator.remove();
                        } else {
                            doorblock.setOpen(entity, level, blockstate, blockpos, false);
                            iterator.remove();
                        }
                    }
                }
            }
        }
    }

    private static boolean areOtherMobsComingThroughDoor(LivingEntity entity, BlockPos pos, Optional<List<LivingEntity>> nearestLivingEntities) {
        return nearestLivingEntities.isEmpty()
            ? false
            : nearestLivingEntities.get()
                .stream()
                .filter(p_348184_ -> p_348184_.getType() == entity.getType())
                .filter(p_352722_ -> pos.closerToCenterThan(p_352722_.position(), 2.0))
                .anyMatch(p_258454_ -> isMobComingThroughDoor(p_258454_.getBrain(), pos));
    }

    private static boolean isMobComingThroughDoor(Brain<?> brain, BlockPos pos) {
        if (!brain.hasMemoryValue(MemoryModuleType.PATH)) {
            return false;
        } else {
            Path path = brain.getMemory(MemoryModuleType.PATH).get();
            if (path.isDone()) {
                return false;
            } else {
                Node node = path.getPreviousNode();
                if (node == null) {
                    return false;
                } else {
                    Node node1 = path.getNextNode();
                    return pos.equals(node.asBlockPos()) || pos.equals(node1.asBlockPos());
                }
            }
        }
    }

    private static boolean isDoorTooFarAway(ServerLevel level, LivingEntity entity, GlobalPos pos) {
        return pos.dimension() != level.dimension() || !pos.pos().closerToCenterThan(entity.position(), 3.0);
    }

    private static Optional<Set<GlobalPos>> rememberDoorToClose(
        MemoryAccessor<Mu, Set<GlobalPos>> doorsToClose, Optional<Set<GlobalPos>> doorPositions, ServerLevel level, BlockPos pos
    ) {
        GlobalPos globalpos = GlobalPos.of(level.dimension(), pos);
        return Optional.of(doorPositions.<Set<GlobalPos>>map(p_261437_ -> {
            p_261437_.add(globalpos);
            return p_261437_;
        }).orElseGet(() -> {
            Set<GlobalPos> set = Sets.newHashSet(globalpos);
            doorsToClose.set(set);
            return set;
        }));
    }
}
