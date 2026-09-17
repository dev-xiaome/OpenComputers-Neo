package net.minecraft.world.entity.ai.memory;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.phys.Vec3;

public class WalkTarget {
    private final PositionTracker target;
    private final float speedModifier;
    private final int closeEnoughDist;

    /**
     * Constructs a walk target that tracks a position
     */
    public WalkTarget(BlockPos pos, float speedModifier, int closeEnoughDist) {
        this(new BlockPosTracker(pos), speedModifier, closeEnoughDist);
    }

    /**
     * Constructs a walk target using a vector that's directly converted to a BlockPos.
     */
    public WalkTarget(Vec3 vectorPos, float speedModifier, int closeEnoughDist) {
        this(new BlockPosTracker(BlockPos.containing(vectorPos)), speedModifier, closeEnoughDist);
    }

    /**
     * Constructs a walk target that tracks an entity's position
     */
    public WalkTarget(Entity targetEntity, float speedModifier, int closeEnoughDist) {
        this(new EntityTracker(targetEntity, false), speedModifier, closeEnoughDist);
    }

    public WalkTarget(PositionTracker target, float speedModifier, int closeEnoughDist) {
        this.target = target;
        this.speedModifier = speedModifier;
        this.closeEnoughDist = closeEnoughDist;
    }

    public PositionTracker getTarget() {
        return this.target;
    }

    public float getSpeedModifier() {
        return this.speedModifier;
    }

    public int getCloseEnoughDist() {
        return this.closeEnoughDist;
    }
}
