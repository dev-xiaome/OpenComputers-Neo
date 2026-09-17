package li.cil.oc.integration.vanilla

import li.cil.oc.api.driver.SidedBlock
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.integration.ManagedTileEntityEnvironment
import li.cil.oc.util.{FluidUtils, ResultWrapper}
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.world.level.Level
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.fluids.capability.IFluidHandler

/**
 * 通用流体容器驱动（原 `DriverFluidHandler.java`）。
 *
 * 1.21.1 迁移要点（改动较大，因此由 Java 改写为 Scala）：
 *  - `net.minecraftforge.fluids.IFluidHandler` → `net.neoforged.neoforge.fluids.capability.IFluidHandler`，
 *    并且容器不再通过「方块实体实现接口」暴露，而是走 NeoForge 能力
 *    `Capabilities.FluidHandler.BLOCK`；因此改为直接实现 [[SidedBlock]]，
 *    在 `worksWith` 里查询能力。
 *  - `IFluidHandler#getTankInfo(side)` 已移除，改为
 *    `getTanks` / `getFluidInTank(i)` / `getTankCapacity(i)`，用 [[FluidUtils.TankInfo]] 承载等价信息。
 *  - 1.7.10 的 `IFluidTank` 接口在 1.21.1 已不存在（只剩模板类
 *    `net.neoforged.neoforge.fluids.capability.templates.FluidTank`），
 *    因此原 `DriverFluidTank.java`（组件名 `fluid_tank`）整体删除，
 *    其 `getInfo` 能力由本驱动的 `getTankInfo` 覆盖。
 *
 * TODO(port): `getTankInfo` 的 `side` 参数在 1.21.1 已无意义
 * （环境在创建时就绑定了面，能力查询已经把面解析掉了），这里仅为兼容旧 Lua 调用保留参数。
 */
object DriverFluidHandler extends SidedBlock {
  override def worksWith(world: Level, x: Int, y: Int, z: Int, side: Direction): Boolean =
    fluidHandlerAt(world, new BlockPos(x, y, z), side) != null

  override def createEnvironment(world: Level, x: Int, y: Int, z: Int, side: Direction): ManagedEnvironment = {
    val handler = fluidHandlerAt(world, new BlockPos(x, y, z), side)
    if (handler == null) null else new Environment(handler)
  }

  private def fluidHandlerAt(world: Level, pos: BlockPos, side: Direction): IFluidHandler = {
    if (world == null || !world.isLoaded(pos)) null
    else Capabilities.FluidHandler.BLOCK.getCapability(world, pos, world.getBlockState(pos), world.getBlockEntity(pos), side)
  }

  final class Environment(handler: IFluidHandler) extends ManagedTileEntityEnvironment[IFluidHandler](handler, "fluid_handler") {
    @Callback(doc = "function([side:number=6]):table -- Get some information about the tank accessible from the specified side.")
    def getTankInfo(context: Context, args: Arguments): Array[AnyRef] = {
      val tankCount = tileEntity.getTanks
      val tanks = (0 until tankCount).map(index =>
        FluidUtils.TankInfo(tileEntity.getFluidInTank(index), tileEntity.getTankCapacity(index))).toArray
      ResultWrapper.result(tanks: AnyRef)
    }
  }

}
