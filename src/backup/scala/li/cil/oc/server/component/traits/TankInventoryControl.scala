package li.cil.oc.server.component.traits

import li.cil.oc.Settings
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.server.component.result
import li.cil.oc.util.ExtendedArguments._
import li.cil.oc.util.InventoryUtils
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem

/**
 * 物品形式的流体容器（桶、罐等）与设备内部罐之间的转移。
 * 对应 1.7.10 的 `traits.TankInventoryControl`。
 *
 * 1.21.1 迁移要点：
 *  - `FluidContainerRegistry` / `IFluidContainerItem` 已被移除，物品的流体能力统一走
 *    `Capabilities.FluidHandler.ITEM.getCapability(stack, null)`（[[IFluidHandlerItem]]）。
 *    它同时覆盖旧版“已装满的容器”（`isFilledContainer`）与“可装填的容器”
 *    （`isEmptyContainer` / `IFluidContainerItem`）两种情况。
 *  - 转移成功后旧的容器物品由 `IFluidHandlerItem#getContainer` 给出（等价于旧版的
 *    `Item#getContainerItem`），再放回物品栏。
 */
trait TankInventoryControl extends WorldAware with InventoryAware with TankAware {
  @Callback(doc = """function([slot:number]):number -- Get the amount of fluid in the tank item in the specified slot or the selected slot.""")
  def getTankLevelInSlot(context: Context, args: Arguments): Array[AnyRef] =
    withFluidInfo(optSlot(args, 0), (fluid, _) => result(fluid.getAmount))

  @Callback(doc = """function([slot:number]):number -- Get the capacity of the tank item in the specified slot of the robot or the selected slot.""")
  def getTankCapacityInSlot(context: Context, args: Arguments): Array[AnyRef] =
    withFluidInfo(optSlot(args, 0), (_, capacity) => result(capacity))

  @Callback(doc = """function([slot:number]):table -- Get a description of the fluid in the tank item in the specified slot or the selected slot.""")
  def getFluidInTankInSlot(context: Context, args: Arguments): Array[AnyRef] = if (Settings.get.allowItemStackInspection) {
    withFluidInfo(optSlot(args, 0), (fluid, _) => result(if (fluid.isEmpty) FluidStack.EMPTY else fluid))
  }
  else result((), "not enabled in config")

  @Callback(doc = """function([tank:number]):table -- Get a description of the fluid in the tank in the specified slot or the selected slot.""")
  def getFluidInInternalTank(context: Context, args: Arguments): Array[AnyRef] = if (Settings.get.allowItemStackInspection) {
    result(getTank(optTank(args, 0)).map(internalTankFluid).filterNot(_.isEmpty).orNull)
  }
  else result((), "not enabled in config")

  @Callback(doc = """function([amount:number]):boolean -- Transfers fluid from a tank in the selected inventory slot to the selected tank.""")
  def drain(context: Context, args: Arguments): Array[AnyRef] = {
    val amount = args.optFluidCount(0)
    getTank(selectedTank) match {
      case Some(into) =>
        fluidHandlerFor(inventory.getStackInSlot(selectedSlot)) match {
          case Some(from) =>
            val contents = from.getFluidInTank(0)
            if (contents == null || contents.isEmpty) {
              result((), "item is empty or not a fluid container")
            }
            else {
              val space = internalTankCapacity(into) - internalTankAmount(into)
              val toMove = math.min(math.min(amount, contents.getAmount), space)
              if (toMove <= 0) {
                result((), "tank is full")
              }
              else {
                // 现在物品的流体能力可能直接改写传入的 ItemStack，也可能只在 getContainer 里
                // 体现结果，因此先在单件副本上执行，成功后再消耗原物品并把容器放回物品栏。
                val work = stackCopyOf(inventory.getStackInSlot(selectedSlot))
                val drained = fluidHandlerFor(work).
                  map(_.drain(contents.copyWithAmount(toMove), FluidAction.EXECUTE)).
                  getOrElse(FluidStack.EMPTY)
                if (drained == null || drained.isEmpty) {
                  result((), "incompatible or no fluid")
                }
                else {
                  val accepted = into.fill(drained, FluidAction.EXECUTE)
                  if (accepted <= 0) {
                    result((), "incompatible fluid")
                  }
                  else {
                    replaceSelectedWith(fluidHandlerFor(work).map(_.getContainer).orNull)
                    result(true, accepted)
                  }
                }
              }
            }
          case _ => result((), "item is empty or not a fluid container")
        }
      case _ => result((), "no tank")
    }
  }

  @Callback(doc = """function([amount:number]):boolean -- Transfers fluid from the selected tank to a tank in the selected inventory slot.""")
  def fill(context: Context, args: Arguments): Array[AnyRef] = {
    val amount = args.optFluidCount(0)
    getTank(selectedTank) match {
      case Some(from) =>
        fluidHandlerFor(inventory.getStackInSlot(selectedSlot)) match {
          case Some(_) =>
            val drained = from.drain(amount, FluidAction.SIMULATE)
            if (drained == null || drained.isEmpty) {
              result((), "tank is empty")
            }
            else {
              val work = stackCopyOf(inventory.getStackInSlot(selectedSlot))
              val transferred = fluidHandlerFor(work).map(_.fill(drained, FluidAction.EXECUTE)).getOrElse(0)
              if (transferred <= 0) {
                result((), "incompatible or no fluid")
              }
              else {
                from.drain(transferred, FluidAction.EXECUTE)
                replaceSelectedWith(fluidHandlerFor(work).map(_.getContainer).orNull)
                result(true, transferred)
              }
            }
          case _ => result((), "item is full or not a fluid container")
        }
      case _ => result((), "no tank")
    }
  }

  /**
   * 物品自带的流体容器能力。
   *
   * 1.21.1 用 `Capabilities.FluidHandler.ITEM` 取代了 1.7.10 的
   * `FluidContainerRegistry` 与 `IFluidContainerItem` 两套机制。
   */
  private def fluidHandlerFor(stack: ItemStack): Option[IFluidHandlerItem] = {
    if (stack == null || stack.isEmpty) None
    else Option(Capabilities.FluidHandler.ITEM.getCapability(stack, null))
  }

  /** 取出单件副本，供只作用于一个物品的流体能力使用。 */
  private def stackCopyOf(stack: ItemStack): ItemStack =
    if (stack == null || stack.isEmpty) ItemStack.EMPTY else stack.copyWithCount(1)

  /**
   * 消耗当前选中槽位里的 1 个物品，并把 `container` 放回物品栏。
   * 放不下的部分掉落到世界中（与 1.7.10 的行为一致）。
   */
  private def replaceSelectedWith(container: ItemStack): Unit = {
    InventorySlots.decrStackSize(inventory, selectedSlot, 1)
    if (container != null && !container.isEmpty) {
      val remaining = container.copy()
      InventoryUtils.insertIntoInventory(remaining, inventory, slots = Option(insertionSlots))
      if (!remaining.isEmpty) {
        InventoryUtils.spawnStackInWorld(position, remaining)
      }
    }
  }

  private def withFluidInfo(slot: Int, f: (FluidStack, Int) => Array[AnyRef]): Array[AnyRef] =
    fluidHandlerFor(inventory.getStackInSlot(slot)) match {
      case Some(handler) =>
        val fluid = handler.getFluidInTank(0)
        f(if (fluid == null) FluidStack.EMPTY else fluid, handler.getTankCapacity(0))
      case _ => result((), "item is not a fluid container")
    }
}
