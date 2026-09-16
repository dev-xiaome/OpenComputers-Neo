package li.cil.oc.server.component.traits

import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.util.ExtendedArguments._
import li.cil.oc.util.FluidUtils
import li.cil.oc.util.ResultWrapper.result
import net.neoforged.neoforge.fluids.capability.IFluidHandler
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction

/**
 * 方块侧流体的控制（对应 1.7.10 的 `traits.TankWorldControl`）。
 *
 * 1.21.1 迁移要点：
 *  - `FluidTankInfo` 与 `IFluidHandler#getTankInfo` 已移除，改用
 *    [[li.cil.oc.util.FluidUtils.TankInfo]] 以及 `getTanks` / `getFluidInTank(i)` / `getTankCapacity(i)`。
 *  - `fill(stack, doFill)` / `drain(side, stack, doDrain)` / `drain(side, n, doDrain)` 统一成
 *    `fill(stack, FluidAction)` / `drain(stack, FluidAction)` / `drain(n, FluidAction)`；
 *    原来的 `side` 参数由 NeoForge 能力的 `Direction` 上下文承担，1.21.1 不再需要。
 */
trait TankWorldControl extends TankAware with WorldAware with SideRestricted {
  @Callback(doc = "function(side:number [, tank:number]):boolean -- Compare the fluid in the selected tank with the fluid in the specified tank on the specified side. Returns true if equal.")
  def compareFluid(context: Context, args: Arguments): Array[AnyRef] = {
    val side = checkSideForAction(args, 0)
    fluidInTank(selectedTank) match {
      case Some(stack) =>
        FluidUtils.fluidHandlerAt(position.offset(side)) match {
          case Some(handler) => args.optTankInfo(handler, side.getOpposite, 1, null) match {
            case info: FluidUtils.TankInfo => result(stack.isFluidEqual(info.fluid))
            case _ => result(allTankInfo(handler).exists(other => stack.isFluidEqual(other.fluid)))
          }
          case _ => result(false)
        }
      case _ => result(false)
    }
  }

  @Callback(doc = "function(side:boolean[, amount:number=1000]):boolean, number or string -- Drains the specified amount of fluid from the specified side. Returns the amount drained, or an error message.")
  def drain(context: Context, args: Arguments): Array[AnyRef] = {
    val facing = checkSideForAction(args, 0)
    val count = args.optFluidCount(1) max 0
    getTank(selectedTank) match {
      case Some(internalTank) =>
        val space = internalTankCapacity(internalTank) - internalTankAmount(internalTank)
        val amount = math.min(count, space)
        if (count < 1 || amount > 0) {
          FluidUtils.fluidHandlerAt(position.offset(facing)) match {
            case Some(handler) =>
              val stack = internalTankFluid(internalTank)
              if (!stack.isEmpty) {
                val drained = handler.drain(stack.copyWithAmount(amount), FluidAction.EXECUTE)
                if ((drained != null && drained.getAmount > 0) || amount == 0) {
                  val filled = internalTank.fill(drained, FluidAction.EXECUTE)
                  result(true, filled)
                }
                else result(Unit, "incompatible or no fluid")
              }
              else {
                val transferred = internalTank.fill(handler.drain(amount, FluidAction.EXECUTE), FluidAction.EXECUTE)
                result(transferred > 0, transferred)
              }
            case _ => result(Unit, "incompatible or no fluid")
          }
        }
        else result(Unit, "tank is full")
      case _ => result(Unit, "no tank selected")
    }
  }

  @Callback(doc = "function(side:number[, amount:number=1000]):boolean, number of string -- Eject the specified amount of fluid to the specified side. Returns the amount ejected or an error message.")
  def fill(context: Context, args: Arguments): Array[AnyRef] = {
    val facing = checkSideForAction(args, 0)
    val count = args.optFluidCount(1) max 0
    getTank(selectedTank) match {
      case Some(internalTank) =>
        val amount = math.min(count, internalTankAmount(internalTank))
        if (count < 1 || amount > 0) {
          FluidUtils.fluidHandlerAt(position.offset(facing)) match {
            case Some(handler) =>
              val stack = internalTankFluid(internalTank)
              if (!stack.isEmpty) {
                val filled = handler.fill(stack.copyWithAmount(amount), FluidAction.EXECUTE)
                if (filled > 0 || amount == 0) {
                  internalTank.drain(filled, FluidAction.EXECUTE)
                  result(true, filled)
                }
                else result(Unit, "incompatible or no fluid")
              }
              else result(Unit, "tank is empty")
            case _ => result(Unit, "no space")
          }
        }
        else result(Unit, "tank is empty")
      case _ => result(Unit, "no tank selected")
    }
  }

  /** 旧版 `getTankInfo(side)` 的等价物：把 `IFluidHandler` 的所有槽位转成 `TankInfo`。 */
  private def allTankInfo(handler: IFluidHandler): IndexedSeq[FluidUtils.TankInfo] = {
    if (handler == null) return IndexedSeq.empty
    (0 until handler.getTanks).map(tank => FluidUtils.TankInfo(handler.getFluidInTank(tank), handler.getTankCapacity(tank)))
  }
}
