package li.cil.oc.server.component

import java.util

import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.nbt.CompoundTag
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction
import net.neoforged.neoforge.fluids.capability.templates.FluidTank

import scala.jdk.CollectionConverters._

/**
 * 「储罐升级」组件（1.7.10 的 `server.component.UpgradeTank`）。
 *
 * 1.21.1 迁移要点：
 *  - 1.7.10 的 `IFluidTank` 在 NeoForge 1.21.1 已不存在，单罐统一用 [[IFluidHandler]] 表达
 *    （`getTanks` / `getFluidInTank(0)` / `getTankCapacity(0)` / `fill` / `drain`）。
 *    `Robot` / `Drone` 的 `MultiTank` 正是按 [[IFluidHandler]] 收集组件，因此这里的接口选择
 *    与宿主侧的期望一致（见 `common/tileentity/Robot.scala`、`common/entity/Drone.scala`）。
 *  - `fill(stack, doFill: Boolean)` / `drain(n, doDrain: Boolean)` → `FluidAction.EXECUTE / SIMULATE`。
 *  - `FluidTank#readFromNBT(nbt)` / `writeToNBT(nbt)` 在 1.21.1 需要
 *    `HolderLookup.Provider`，且 `writeToNBT` 会把内容写进传入的 `CompoundTag`；
 *    这里用一个子标签承载，避免与组件自身 NBT 冲突。
 *  - 旧的 `getFluid` / `getFluidAmount` / `getCapacity` 保留为普通方法（语义等价于
 *    `getFluidInTank(0)` / `getFluidInTank(0).getAmount` / `getTankCapacity(0)`），
 *    供尚未改成 `IFluidHandler` 的调用方（如 `traits` 批次）继续使用。
 */
class UpgradeTank(val owner: EnvironmentHost, val capacity: Int) extends prefab.ManagedEnvironment with IFluidHandler with DeviceInfo {
  override val node = Network.newNode(this, Visibility.None).create()

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Generic,
    DeviceAttribute.Description -> "Tank upgrade",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "Superblubb V10",
    DeviceAttribute.Capacity -> capacity.toString
  )

  // 1.21.1：Scala `Map` → `java.util.Map` 需要显式 `asJava`。
  override def getDeviceInfo: util.Map[String, String] = deviceInfo.asJava

  // ----------------------------------------------------------------------- //

  val tank = new FluidTank(capacity)

  /** 承载储罐内容的子标签名。 */
  private final val TankTag = "tank"

  override def load(nbt: CompoundTag): Unit = {
    super.load(nbt)
    // 1.21.1：`FluidTank#readFromNBT` 需要注册表访问器；这里用项目统一的退化注册表
    // （与 `util.ExtendedNBT.fallbackRegistry` / `StackSerializer` 的策略一致）。
    tank.readFromNBT(li.cil.oc.util.ExtendedNBT.fallbackRegistry, nbt.getCompound(TankTag))
  }

  override def save(nbt: CompoundTag): Unit = {
    super.save(nbt)
    nbt.put(TankTag, tank.writeToNBT(li.cil.oc.util.ExtendedNBT.fallbackRegistry, new CompoundTag()))
  }

  // ----------------------------------------------------------------------- //
  // IFluidHandler

  override def getTanks: Int = 1

  override def getFluidInTank(tankIndex: Int): FluidStack = tank.getFluid

  override def getTankCapacity(tankIndex: Int): Int = tank.getCapacity

  override def isFluidValid(tankIndex: Int, stack: FluidStack): Boolean = tank.isFluidValid(stack)

  override def fill(resource: FluidStack, action: FluidAction): Int = {
    val amount = tank.fill(resource, action)
    if (action.execute() && amount > 0) {
      node.sendToVisible("computer.signal", "tank_changed", Int.box(tankIndex), Int.box(amount))
    }
    amount
  }

  override def drain(resource: FluidStack, action: FluidAction): FluidStack = {
    val amount = tank.drain(resource, action)
    if (action.execute() && amount != null && amount.getAmount > 0) {
      node.sendToVisible("computer.signal", "tank_changed", Int.box(tankIndex), Int.box(-amount.getAmount))
    }
    amount
  }

  override def drain(maxDrain: Int, action: FluidAction): FluidStack = {
    val amount = tank.drain(maxDrain, action)
    if (action.execute() && amount != null && amount.getAmount > 0) {
      node.sendToVisible("computer.signal", "tank_changed", Int.box(tankIndex), Int.box(-amount.getAmount))
    }
    amount
  }

  // ----------------------------------------------------------------------- //
  // 旧 `IFluidTank` 的只读访问器（1.21.1 用 `IFluidHandler` 的按索引访问替代）。

  def getFluid: FluidStack = tank.getFluid

  def getFluidAmount: Int = tank.getFluidAmount

  def getCapacity: Int = tank.getCapacity

  // ----------------------------------------------------------------------- //

  /**
   * 本罐在宿主储罐列表中的 1 基编号（供 `tank_changed` 信号使用）。
   *
   * 1.21.1：`MultiTank#getFluidTank` 通常直接返回组件的 `IFluidHandler`（机器人即本对象），
   * 但无人机侧会再包一层匿名适配器，因此这里同时接受「引用相等」与「内容相等」两种匹配，
   * 都匹配不上时退回 1，与原实现的 `(index max 0) + 1` 兜底一致。
   */
  private def tankIndex: Int = {
    owner match {
      case agent: li.cil.oc.api.internal.Agent if agent.tank != null =>
        val count = agent.tank.tankCount
        var index = -1
        var i = 0
        while (index < 0 && i < count) {
          val handler = agent.tank.getFluidTank(i)
          if (handler eq this) index = i
          else if (handler != null && (handler.getFluidInTank(0).isFluidEqual(tank.getFluid)) &&
            handler.getTankCapacity(0) == tank.getCapacity) index = i
          i += 1
        }
        (index max 0) + 1
      case _ => 1
    }
  }
}
