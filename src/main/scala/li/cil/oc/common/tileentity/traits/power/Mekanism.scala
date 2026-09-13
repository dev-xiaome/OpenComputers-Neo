package li.cil.oc.common.tileentity.traits.power

import net.minecraft.core.Direction

/**
 * Mekanism（通用机械）能量集成 —— **未移植的降级占位实现**。
 *
 * ==原实现（1.7.10）==
 * 通过 `@Injectable.Interface`（ASM 注入）让本 trait 实现
 * `mekanism.api.energy.IStrictEnergyAcceptor`：`transferEnergyToAcceptor` 用
 * `Power.fromJoules` / `Power.toJoules` 换算后注入 OC 缓冲，
 * `getEnergy` / `getMaxEnergy` 报告缓冲余量。
 *
 * ==为什么降级==
 *  - ASM 注入层（`li.cil.oc.common.asm.**`）在 1.21.1 已整体删除；
 *  - Mekanism 未移植，`mekanism.api.*` 不可用；
 *  - `li.cil.oc.integration.util.Power` 与 `li.cil.oc.integration.Mods` 也未移植。
 */
trait Mekanism extends Common {
  // 注意：Scala 的自类型不会被继承，TileEntity 的每个子 trait 都必须重新声明。
  self: net.minecraft.world.level.block.entity.BlockEntity =>

  /**
   * TODO(integration.mekanism): 原为 `Mods.Mekanism.isAvailable && canConnectPower(side)`；
   * 集成未移植，恒为 `false`。
   */
  def canReceiveEnergy(side: Direction): Boolean = false

  /**
   * TODO(integration.mekanism): 原为
   * `Power.toJoules(tryChangeBuffer(side, Power.fromJoules(amount)))`；这里返回 0（未接收任何能量）。
   */
  def transferEnergyToAcceptor(side: Direction, amount: Double): Double = 0

  /**
   * TODO(integration.mekanism): 原为 `Power.toJoules(所有侧 globalBufferSize 的最大值)`。
   */
  def getMaxEnergy: Double = 0

  /**
   * TODO(integration.mekanism): 原为 `Power.toJoules(所有侧 globalBuffer 的最大值)`。
   */
  def getEnergy: Double = 0

  /**
   * TODO(integration.mekanism): 原实现为空方法（Mekanism 允许多方块共享能量，OC 侧不响应写入）。
   */
  def setEnergy(energy: Double): Unit = {}
}
