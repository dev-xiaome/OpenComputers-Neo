package li.cil.oc.common.tileentity.traits.power

import net.minecraft.core.Direction

/**
 * Galacticraft（GC 能量）集成 —— **未移植的降级占位实现**。
 *
 * ==原实现（1.7.10）==
 * 通过 `@Injectable.InterfaceList`（ASM 注入）让本 trait 实现
 * `micdoodle8.mods.galacticraft.api.power.IEnergyHandlerGC` 与
 * `micdoodle8.mods.galacticraft.api.transmission.tile.IConnector`，
 * 把 `EnergySource` 隐式转换成方向后用 `Power.fromGC` / `Power.toGC` 换算并注入 OC 缓冲。
 *
 * ==为什么降级==
 *  - ASM 注入层（`li.cil.oc.common.asm.**`）在 1.21.1 已整体删除；
 *  - Galacticraft 未移植，`micdoodle8.mods.galacticraft.api.*`
 *    （`EnergySource` / `NetworkType` / `IEnergyHandlerGC` / `IConnector`）全部不可用；
 *  - `li.cil.oc.integration.util.Power` 与 `li.cil.oc.integration.Mods` 也未移植。
 *
 * ==类型适配说明==
 * 原签名里的 `EnergySource`（抽象能量来源，可能是相邻方块或线缆）在 1.21.1 没有等价物，
 * 这里统一退化为 [[net.minecraft.core.Direction]]；`NetworkType` 退化为 `AnyRef`。
 * 方法名与参数个数保持不变，恢复时替换回 GC 的类型即可。
 */
trait Galacticraft extends Common {
  // 注意：Scala 的自类型不会被继承，TileEntity 的每个子 trait 都必须重新声明。
  self: net.minecraft.world.level.block.entity.BlockEntity =>

  /**
   * TODO(integration.galacticraft): 原为
   * `Mods.Galacticraft.isAvailable && canConnectPower(from)`；集成未移植，恒为 `false`。
   */
  def nodeAvailable(from: Direction): Boolean = false

  /**
   * TODO(integration.galacticraft): 原为
   * `Power.toGC(tryChangeBuffer(from, Power.fromGC(amount), !simulate))`。
   */
  def receiveEnergyGC(from: Direction, amount: Float, simulate: Boolean): Float = 0f

  /**
   * TODO(integration.galacticraft): 原为 `Power.toGC(globalBuffer(from))`。
   */
  def getEnergyStoredGC(from: Direction): Float = 0f

  /**
   * TODO(integration.galacticraft): 原为 `Power.toGC(globalBufferSize(from))`。
   */
  def getMaxEnergyStoredGC(from: Direction): Float = 0f

  /**
   * TODO(integration.galacticraft): 原实现就恒返回 `0f`（GC 集成只做输入）。
   */
  def extractEnergyGC(from: Direction, amount: Float, simulate: Boolean): Float = 0f

  /**
   * TODO(integration.galacticraft): 原为
   * `networkType == NetworkType.POWER && canConnectPower(from)`；`NetworkType` 不可用，
   * 这里第二个参数退化为 `AnyRef`。
   */
  def canConnect(from: Direction, networkType: AnyRef): Boolean = false
}
