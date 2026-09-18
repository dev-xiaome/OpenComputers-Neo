package li.cil.oc.common.capabilities

import li.cil.oc.api.ImmutableFluidStack
import li.cil.oc.common.datacomponents.OCComponents
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.fluids.{FluidStack, FluidType}
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction
import net.neoforged.neoforge.fluids.capability.{IFluidHandlerItem, templates}

/** NeoForge item-fluid capability for a tank upgrade.
  *
  * The fluid is stored in the same data component used by an installed tank
  * upgrade, so removing an upgrade and handling it as an item share storage.
  */
final class TankUpgradeFluidHandler(private val stack: ItemStack) extends IFluidHandlerItem {
  private val tank = new templates.FluidTank(16 * FluidType.BUCKET_VOLUME)

  tank.setFluid(Option(stack.get(OCComponents.TANK.get()))
    .map(_.mutableCopy())
    .getOrElse(FluidStack.EMPTY))

  private def save(): Unit =
    stack.set(OCComponents.TANK.get(), ImmutableFluidStack.copyOf(tank.getFluid))

  override def getContainer: ItemStack = stack

  override def getTanks: Int = tank.getTanks

  override def getFluidInTank(index: Int): FluidStack = tank.getFluidInTank(index)

  override def getTankCapacity(index: Int): Int = tank.getTankCapacity(index)

  override def isFluidValid(index: Int, fluid: FluidStack): Boolean = tank.isFluidValid(index, fluid)

  override def fill(fluid: FluidStack, action: FluidAction): Int = {
    val amount = tank.fill(fluid, action)
    if (action.execute && amount > 0) save()
    amount
  }

  override def drain(fluid: FluidStack, action: FluidAction): FluidStack = {
    val drained = tank.drain(fluid, action)
    if (action.execute && drained != null && !drained.isEmpty) save()
    drained
  }

  override def drain(amount: Int, action: FluidAction): FluidStack = {
    val drained = tank.drain(amount, action)
    if (action.execute && drained != null && !drained.isEmpty) save()
    drained
  }
}
