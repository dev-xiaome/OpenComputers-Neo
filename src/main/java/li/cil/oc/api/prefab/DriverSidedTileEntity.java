package li.cil.oc.api.prefab;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.core.Direction;

/**
 * To limit sidedness, I recommend overriding {@link #worksWith(Level, int, int, int, Direction)}
 * and calling <code>super.worksWith</code> in addition to the side check.
 */
public abstract class DriverSidedTileEntity implements li.cil.oc.api.driver.SidedBlock {
    public abstract Class<?> getTileEntityClass();

    @Override
    public boolean worksWith(final Level world, final int x, final int y, final int z, final Direction side) {
        final Class<?> filter = getTileEntityClass();
        if (filter == null) {
            // This can happen if filter classes are deduced by reflection and
            // the class in question is not present.
            return false;
        }
        final BlockEntity tileEntity = world.getTileEntity(x, y, z);
        return tileEntity != null && filter.isAssignableFrom(tileEntity.getClass());
    }
}
