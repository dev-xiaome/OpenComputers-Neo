package li.cil.oc.server.component.traits

import li.cil.oc.Settings
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.server.component.result
import li.cil.oc.util.ExtendedArguments._
import li.cil.oc.util.FluidUtils
import net.minecraft.core.Direction

/**
 * 方块侧流体信息检索（对应 1.7.10 的 `traits.WorldTankAnalytics`）。
 *
 * 1.21.1 迁移要点：
 *  - `FluidTankInfo` 与 `IFluidHandler#getTankInfo` 已移除，改用
 *    [[li.cil.oc.util.FluidUtils.TankInfo]]、`getTanks` / `getFluidInTank(i)` / `getTankCapacity(i)`。
 */
trait WorldTankAnalytics extends WorldAware with SideRestricted {
  @Callback(doc = """function(side:number [, tank:number]):number -- Get the amount of fluid in the specified tank on the specified side.""")
  def getTankLevel(context: Context, args: Arguments): Array[AnyRef] = {
    val facing = checkSideForAction(args, 0)

    FluidUtils.fluidHandlerAt(position.offset(facing)) match {
      case Some(handler) => args.optTankInfo(handler, facing.getOpposite, 1, null) match {
        case info: FluidUtils.TankInfo => result(Option(info.fluid).fold(0)(_.getAmount))
        case _ => result(allTankInfo(handler).map(info => Option(info.fluid).fold(0)(_.getAmount)).sum)
      }
      case _ => result((), "no tank")
    }
  }

  @Callback(doc = """function(side:number [, tank:number]):number -- Get the capacity of the specified tank on the specified side.""")
  def getTankCapacity(context: Context, args: Arguments): Array[AnyRef] = {
    val facing = checkSideForAction(args, 0)
    FluidUtils.fluidHandlerAt(position.offset(facing)) match {
      case Some(handler) => args.optTankInfo(handler, facing.getOpposite, 1, null) match {
        case info: FluidUtils.TankInfo => result(info.capacity)
        case _ => result(allTankInfo(handler).map(_.capacity).foldLeft(0)((max, capacity) => math.max(max, capacity)))
      }
      case _ => result((), "no tank")
    }
  }

  @Callback(doc = """function(side:number [, tank:number]):table -- Get a description of the fluid in the the specified tank on the specified side.""")
  def getFluidInTank(context: Context, args: Arguments): Array[AnyRef] = if (Settings.get.allowItemStackInspection) {
    val facing = checkSideForAction(args, 0)
    FluidUtils.fluidHandlerAt(position.offset(facing)) match {
      case Some(handler) => args.optTankInfo(handler, facing.getOpposite, 1, null) match {
        case info: FluidUtils.TankInfo => result(info)
        case _ =>
          val infos = allTankInfo(handler)
          if (infos.isEmpty) result((), "no tank") else result(infos)
      }
      case _ => result((), "no tank")
    }
  }
  else result((), "not enabled in config")

  @Callback(doc = """function(side:number):number -- Get the number of tanks available on the specified side.""")
  def getTankCount(context: Context, args: Arguments): Array[AnyRef] = {
    val facing = checkSideForAction(args, 0)
    FluidUtils.fluidHandlerAt(position.offset(facing)) match {
      case Some(handler) => result(handler.getTanks)
      case _ => result((), "no tank")
    }
  }

  /** 旧版 `getTankInfo(side)` 的等价物：把 `IFluidHandler` 的所有槽位转成 `TankInfo`。 */
  private def allTankInfo(handler: net.neoforged.neoforge.fluids.capability.IFluidHandler,
                          side: Direction = null): IndexedSeq[FluidUtils.TankInfo] = {
    if (handler == null) return IndexedSeq.empty
    (0 until handler.getTanks).map(tank => FluidUtils.TankInfo(handler.getFluidInTank(tank), handler.getTankCapacity(tank)))
  }
}
