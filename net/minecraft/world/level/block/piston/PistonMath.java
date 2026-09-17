package net.minecraft.world.level.block.piston;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

public class PistonMath {
    public static AABB getMovementArea(AABB bounds, Direction dir, double delta) {
        double d0 = delta * (double)dir.getAxisDirection().getStep();
        double d1 = Math.min(d0, 0.0);
        double d2 = Math.max(d0, 0.0);
        switch (dir) {
            case WEST:
                return new AABB(bounds.minX + d1, bounds.minY, bounds.minZ, bounds.minX + d2, bounds.maxY, bounds.maxZ);
            case EAST:
                return new AABB(bounds.maxX + d1, bounds.minY, bounds.minZ, bounds.maxX + d2, bounds.maxY, bounds.maxZ);
            case DOWN:
                return new AABB(bounds.minX, bounds.minY + d1, bounds.minZ, bounds.maxX, bounds.minY + d2, bounds.maxZ);
            case UP:
            default:
                return new AABB(bounds.minX, bounds.maxY + d1, bounds.minZ, bounds.maxX, bounds.maxY + d2, bounds.maxZ);
            case NORTH:
                return new AABB(bounds.minX, bounds.minY, bounds.minZ + d1, bounds.maxX, bounds.maxY, bounds.minZ + d2);
            case SOUTH:
                return new AABB(bounds.minX, bounds.minY, bounds.maxZ + d1, bounds.maxX, bounds.maxY, bounds.maxZ + d2);
        }
    }
}
