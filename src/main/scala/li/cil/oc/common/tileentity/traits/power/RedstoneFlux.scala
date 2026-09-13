package li.cil.oc.common.tileentity.traits.power

import net.minecraft.core.Direction

/**
 * RedstoneFlux（RF / CoFH 能量）集成 —— **未移植的降级占位实现**。
 *
 * ==原实现（1.7.10）==
 * 通过 `@Injectable.Interface`（ASM 注入）让本 trait 实现 `cofh.api.energy.IEnergyHandler`：
 * `receiveEnergy` 用 `Power.fromRF` / `Power.toRF` 换算后注入 OC 缓冲，
 * `getEnergyStored` / `getMaxEnergyStored` 报告缓冲余量，`extractEnergy` 恒为 0（只做输入）。
 *
 * ==为什么降级==
 *  - ASM 注入层（`li.cil.oc.common.asm.**`）在 1.21.1 已整体删除；
 *  - CoFH 的 RF API 未随本工程移植（`cofh.api.*` 不可用）；
 *  - `li.cil.oc.integration.util.Power` 与 `li.cil.oc.integration.Mods` 也未移植。
 *
 * 兼容提示：1.21.1 上跨模组能量统一走 NeoForge 的 `Capabilities.EnergyStorage`
 * （`IEnergyStorage`：`receiveEnergy` / `extractEnergy` / `getEnergyStored` / `getMaxEnergyStored` /
 * `canExtract` / `canReceive`）。恢复该集成时建议直接实现 `IEnergyStorage` 并注册
 * `Capabilities.EnergyStorage.BLOCK`（由 `Registry` 统一注册），方法名可与本文件保持一致。
 */
trait RedstoneFlux extends Common {
  // 注意：Scala 的自类型不会被继承，TileEntity 的每个子 trait 都必须重新声明。
  self: net.minecraft.world.level.block.entity.BlockEntity =>

  /**
   * TODO(integration.cofh): 原为 `Mods.CoFHEnergy.isAvailable && canConnectPower(from)`；
   * 集成未移植，恒为 `false`。
   */
  def canConnectEnergy(from: Direction): Boolean = false

  /**
   * TODO(integration.cofh): 原为
   * `Power.toRF(tryChangeBuffer(from, Power.fromRF(maxReceive), !simulate))`；这里返回 0。
   */
  def receiveEnergy(from: Direction, maxReceive: Int, simulate: Boolean): Int = 0

  /**
   * TODO(integration.cofh): 原为 `Power.toRF(globalBuffer(from))`。
   */
  def getEnergyStored(from: Direction): Int = 0

  /**
   * TODO(integration.cofh): 原为 `Power.toRF(globalBufferSize(from))`。
   */
  def getMaxEnergyStored(from: Direction): Int = 0

  /**
   * TODO(integration.cofh): 原实现就恒返回 0（RF 集成只做输入）。
   */
  def extractEnergy(from: Direction, maxExtract: Int, simulate: Boolean): Int = 0
}
