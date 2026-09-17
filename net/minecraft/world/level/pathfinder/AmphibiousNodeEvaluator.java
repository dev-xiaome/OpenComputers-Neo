package net.minecraft.world.level.pathfinder;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;

public class AmphibiousNodeEvaluator extends WalkNodeEvaluator {
    private final boolean prefersShallowSwimming;
    private float oldWalkableCost;
    private float oldWaterBorderCost;

    public AmphibiousNodeEvaluator(boolean prefersShallowSwimming) {
        this.prefersShallowSwimming = prefersShallowSwimming;
    }

    @Override
    public void prepare(PathNavigationRegion level, Mob mob) {
        super.prepare(level, mob);
        mob.setPathfindingMalus(PathType.WATER, 0.0F);
        this.oldWalkableCost = mob.getPathfindingMalus(PathType.WALKABLE);
        mob.setPathfindingMalus(PathType.WALKABLE, 6.0F);
        this.oldWaterBorderCost = mob.getPathfindingMalus(PathType.WATER_BORDER);
        mob.setPathfindingMalus(PathType.WATER_BORDER, 4.0F);
    }

    @Override
    public void done() {
        this.mob.setPathfindingMalus(PathType.WALKABLE, this.oldWalkableCost);
        this.mob.setPathfindingMalus(PathType.WATER_BORDER, this.oldWaterBorderCost);
        super.done();
    }

    @Override
    public Node getStart() {
        return !this.mob.isInWater()
            ? super.getStart()
            : this.getStartNode(
                new BlockPos(
                    Mth.floor(this.mob.getBoundingBox().minX), Mth.floor(this.mob.getBoundingBox().minY + 0.5), Mth.floor(this.mob.getBoundingBox().minZ)
                )
            );
    }

    @Override
    public Target getTarget(double x, double y, double z) {
        return this.getTargetNodeAt(x, y + 0.5, z);
    }

    @Override
    public int getNeighbors(Node[] outputArray, Node p_node) {
        int i = super.getNeighbors(outputArray, p_node);
        PathType pathtype = this.getCachedPathType(p_node.x, p_node.y + 1, p_node.z);
        PathType pathtype1 = this.getCachedPathType(p_node.x, p_node.y, p_node.z);
        int j;
        if (this.mob.getPathfindingMalus(pathtype) >= 0.0F && pathtype1 != PathType.STICKY_HONEY) {
            j = Mth.floor(Math.max(1.0F, this.mob.maxUpStep()));
        } else {
            j = 0;
        }

        double d0 = this.getFloorLevel(new BlockPos(p_node.x, p_node.y, p_node.z));
        Node node = this.findAcceptedNode(p_node.x, p_node.y + 1, p_node.z, Math.max(0, j - 1), d0, Direction.UP, pathtype1);
        Node node1 = this.findAcceptedNode(p_node.x, p_node.y - 1, p_node.z, j, d0, Direction.DOWN, pathtype1);
        if (this.isVerticalNeighborValid(node, p_node)) {
            outputArray[i++] = node;
        }

        if (this.isVerticalNeighborValid(node1, p_node) && pathtype1 != PathType.TRAPDOOR) {
            outputArray[i++] = node1;
        }

        for (int k = 0; k < i; k++) {
            Node node2 = outputArray[k];
            if (node2.type == PathType.WATER && this.prefersShallowSwimming && node2.y < this.mob.level().getSeaLevel() - 10) {
                node2.costMalus++;
            }
        }

        return i;
    }

    private boolean isVerticalNeighborValid(@Nullable Node neighbor, Node node) {
        return this.isNeighborValid(neighbor, node) && neighbor.type == PathType.WATER;
    }

    @Override
    protected boolean isAmphibious() {
        return true;
    }

    @Override
    public PathType getPathType(PathfindingContext context, int x, int y, int z) {
        PathType pathtype = context.getPathTypeFromState(x, y, z);
        if (pathtype == PathType.WATER) {
            BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

            for (Direction direction : Direction.values()) {
                blockpos$mutableblockpos.set(x, y, z).move(direction);
                PathType pathtype1 = context.getPathTypeFromState(
                    blockpos$mutableblockpos.getX(), blockpos$mutableblockpos.getY(), blockpos$mutableblockpos.getZ()
                );
                if (pathtype1 == PathType.BLOCKED) {
                    return PathType.WATER_BORDER;
                }
            }

            return PathType.WATER;
        } else {
            return super.getPathType(context, x, y, z);
        }
    }
}
