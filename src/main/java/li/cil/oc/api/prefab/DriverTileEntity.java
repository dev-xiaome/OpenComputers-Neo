package li.cil.oc.api.prefab;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;

/**
 * @deprecated Use {@link DriverSidedTileEntity} instead.
 */
@Deprecated // TODO Remove in OC 1.7
public abstract class DriverTileEntity implements li.cil.oc.api.driver.Block {
    public abstract Class<?> getTileEntityClass();

    @Override
    public boolean worksWith(final Level world, final int x, final int y, final int z) {
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
