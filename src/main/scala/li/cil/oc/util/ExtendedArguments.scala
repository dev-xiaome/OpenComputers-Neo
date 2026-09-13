package li.cil.oc.util

import li.cil.oc.api.internal.MultiTank
import li.cil.oc.api.machine.Arguments
import net.minecraft.core.Direction
import net.neoforged.neoforge.fluids.capability.IFluidHandler

import scala.language.implicitConversions

/**
 * `Arguments` 的扩展（含各类参数校验便捷方法）。
 *
 * 1.21.1 迁移要点：
 *  - `IInventory` → `IItemHandler`：`getSizeInventory` → `getSlots`
 *  - `FluidContainerRegistry.BUCKET_VOLUME` → [[FluidUtils.BucketVolume]]
 *  - `Direction.VALID_DIRECTIONS` 与 `Direction.getOrientation(i)` 已移除，
 *    分别改为 `Direction.values` 与 `Direction.from3DDataValue(i)`
 *  - `FluidTankInfo` 已移除，改用 [[FluidUtils.TankInfo]]；
 *    `IFluidHandler#getTankInfo` → `getTanks()` / `getFluidInTank(i)` / `getTankCapacity(i)`
 *  - 所有 `side` 参数仅用于兼容旧签名：1.21.1 的面过滤由能力提供方完成
 */
object ExtendedArguments {

  implicit def extendedArguments(args: Arguments): ExtendedArguments = new ExtendedArguments(args)

  class ExtendedArguments(val args: Arguments) {
    def optItemCount(index: Int, default: Int = 64) =
      if (!isDefined(index) || !hasValue(index)) default
      else math.max(0, math.min(64, args.checkInteger(index)))

    def optFluidCount(index: Int, default: Int = FluidUtils.BucketVolume) =
      if (!isDefined(index) || !hasValue(index)) default
      else math.max(0, args.checkInteger(index))

    def checkSlot(inventory: net.neoforged.neoforge.items.IItemHandler, n: Int) = {
      val slot = args.checkInteger(n) - 1
      if (inventory == null || slot < 0 || slot >= inventory.getSlots) {
        throw new IllegalArgumentException("invalid slot")
      }
      slot
    }

    def optSlot(inventory: net.neoforged.neoforge.items.IItemHandler, index: Int, default: Int) = {
      if (!isDefined(index)) default
      else checkSlot(inventory, index)
    }

    def checkTank(multi: MultiTank, n: Int) = {
      val tank = args.checkInteger(n) - 1
      if (tank < 0 || tank >= multi.tankCount) {
        throw new IllegalArgumentException("invalid tank index")
      }
      tank
    }

    /** 校验并返回指定槽位的流体信息；槽位不存在时抛 `IllegalArgumentException`。 */
    def checkTankInfo(handler: IFluidHandler, side: Direction, n: Int): FluidUtils.TankInfo = {
      val tank = args.checkInteger(n) - 1
      if (handler == null || tank < 0 || tank >= handler.getTanks) {
        throw new IllegalArgumentException("invalid tank index")
      }
      FluidUtils.TankInfo(handler.getFluidInTank(tank), handler.getTankCapacity(tank))
    }

    def optTankInfo(handler: IFluidHandler, side: Direction, n: Int, default: FluidUtils.TankInfo): FluidUtils.TankInfo = {
      if (!isDefined(n)) default
      else checkTankInfo(handler, side, n)
    }

    def checkSideAny(index: Int) = checkSide(index, Direction.values.toSeq: _*)

    def optSideAny(index: Int, default: Direction) =
      if (!isDefined(index)) default
      else checkSideAny(index)

    def checkSideExcept(index: Int, invalid: Direction*) = checkSide(index, Direction.values.filterNot(invalid.contains).toSeq: _*)

    def optSideExcept(index: Int, default: Direction, invalid: Direction*) =
      if (!isDefined(index)) default
      else checkSideExcept(index, invalid.toSeq: _*)

    def checkSideForAction(index: Int) = checkSide(index, Direction.SOUTH, Direction.UP, Direction.DOWN)

    def optSideForAction(index: Int, default: Direction) =
      if (!isDefined(index)) default
      else checkSideForAction(index)

    def checkSideForMovement(index: Int) = checkSide(index, Direction.SOUTH, Direction.NORTH, Direction.UP, Direction.DOWN)

    def optSideForMovement(index: Int, default: Direction) =
      if (!isDefined(index)) default
      else checkSideForMovement(index)

    def checkSideForFace(index: Int, facing: Direction) = checkSideExcept(index, facing.getOpposite)

    def optSideForFace(index: Int, default: Direction) =
      if (!isDefined(index)) default
      else checkSideForAction(index)

    private def checkSide(index: Int, allowed: Direction*) = {
      val side = args.checkInteger(index)
      if (side < 0 || side > 5) {
        throw new IllegalArgumentException("invalid side")
      }
      val direction = Direction.from3DDataValue(side)
      if (allowed.isEmpty || (allowed contains direction)) direction
      else throw new IllegalArgumentException("unsupported side")
    }

    private def isDefined(index: Int) = index >= 0 && index < args.count()

    private def hasValue(index: Int) = args.checkAny(index) != null
  }

}
