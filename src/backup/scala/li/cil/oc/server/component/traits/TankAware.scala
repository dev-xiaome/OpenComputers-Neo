package li.cil.oc.server.component.traits

import li.cil.oc.api.internal.MultiTank
import li.cil.oc.api.machine.Arguments
import li.cil.oc.util.ExtendedArguments._
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler

trait TankAware {
  def tank: MultiTank

  def selectedTank: Int

  def selectedTank_=(value: Int): Unit

  // ----------------------------------------------------------------------- //

  protected def optTank(args: Arguments, n: Int) =
    if (args.count > 0 && args.checkAny(0) != null) args.checkTank(tank, 0)
    else selectedTank

  protected def getTank(index: Int): Option[IFluidHandler] = Option(tank.getFluidTank(index))

  /**
   * 指定罐内的流体。
   *
   * 1.7.10 的 `IFluidTank#getFluid` 在空罐时返回 `null`；1.21.1 返回 `FluidStack.EMPTY`，
   * 这里统一归一化成 `None`，保持调用方原来的 `Some/None` 语义。
   */
  protected def fluidInTank(index: Int): Option[FluidStack] = getTank(index) match {
    case Some(handler) => Option(handler.getFluidInTank(0)).filterNot(_.isEmpty)
    case _ => None
  }

  protected def haveSameFluidType(stackA: FluidStack, stackB: FluidStack) = stackA.isFluidEqual(stackB)

  // ----------------------------------------------------------------------- //
  // 1.21.1 的 NeoForge 流体 API 里没有 1.7.10 的 `IFluidTank`：`MultiTank#getFluidTank`
  // 返回的 `IFluidHandler` 内部只有一个槽位。下面三个辅助方法把它当作旧版单槽罐使用，
  // 避免每个调用点都重复写 `getFluidInTank(0)` / `getTankCapacity(0)`。

  /** 罐内流体（等价于 1.7.10 的 `IFluidTank#getFluid`），空罐返回 `FluidStack.EMPTY`。 */
  protected def internalTankFluid(handler: IFluidHandler): FluidStack = {
    if (handler == null) FluidStack.EMPTY
    else Option(handler.getFluidInTank(0)).getOrElse(FluidStack.EMPTY)
  }

  /** 罐容量（等价于 1.7.10 的 `IFluidTank#getCapacity`）。 */
  protected def internalTankCapacity(handler: IFluidHandler): Int =
    if (handler == null) 0 else handler.getTankCapacity(0)

  /** 罐内流体量（等价于 1.7.10 的 `IFluidTank#getFluidAmount`）。 */
  protected def internalTankAmount(handler: IFluidHandler): Int =
    internalTankFluid(handler).getAmount
}
