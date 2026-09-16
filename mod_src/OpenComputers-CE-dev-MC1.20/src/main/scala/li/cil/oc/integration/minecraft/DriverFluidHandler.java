package li.cil.oc.integration.minecraft;

import li.cil.oc.api.driver.DriverBlock;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.integration.ManagedBlockEntityEnvironment;
import li.cil.oc.util.ExtendedArguments.TankProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.capability.IFluidHandler;

public final class DriverFluidHandler implements DriverBlock {
    @Override
    public boolean worksWith(final Level level, final BlockPos pos, final Direction side) {
        final BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return false;
        }
        return blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, side).isPresent();
    }

    @Override
    public ManagedEnvironment createEnvironment(final Level level, final BlockPos pos, final Direction side) {
        return new Environment(level.getBlockEntity(pos).getCapability(ForgeCapabilities.FLUID_HANDLER, side).orElse(null));
    }

    public static final class Environment extends ManagedBlockEntityEnvironment<IFluidHandler> {
        public Environment(final IFluidHandler tileEntity) {
            super(tileEntity, "fluid_handler");
        }

        @Callback(doc = "function():table -- Get some information about the tank accessible from the specified side.")
        public Object[] getTankInfo(final Context context, final Arguments args) {
            TankProperties[] props = new TankProperties[blockEntity.getTanks()];
            for (int i = 0; i < props.length; i++) {
                props[i] = new TankProperties(blockEntity.getTankCapacity(i), blockEntity.getFluidInTank(i));
            }
            return props;
        }
    }
}
