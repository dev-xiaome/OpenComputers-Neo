package li.cil.oc.common.tileentity.traits.power

import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag

/**
 * AE2（Applied Energistics 2）能量集成 —— **未移植的降级占位实现**。
 *
 * ==原实现（1.7.10）==
 * 通过 `@Injectable.Interface`（ASM 注入）让本 trait 实现 `appeng.api.networking.IGridHost`，
 * 用 `AEApi.instance.createGridNode(new AppliedEnergistics2GridBlock(this))` 建立电网节点，
 * 每 `Settings.get.tickFrequency` 刻用 `IEnergyGrid#extractAEPower` 抽能，
 * 并通过 `Power.fromAE` / `Power.toAE` 换算单位。
 *
 * ==为什么降级==
 *  - `li.cil.oc.common.asm.**`（ASM 注入层）在 1.21.1 已整体删除，不再能"注入式"实现第三方接口；
 *  - AE2 的 API（`appeng.*`）未随本工程移植，`AEApi` / `IGridNode` / `IEnergyGrid` /
 *    `DimensionalCoord` / `AECableType` 全部不可用；
 *  - `li.cil.oc.common.EventHandler#scheduleAE2Add` 与 `li.cil.oc.integration.util.Power` 也未移植。
 *
 * ==恢复方式==
 * AE2 移植后，把 `IGridHost` 相关实现放回本 trait（或改成 NeoForge Capability 形式接入），
 * 并补回 `Power` 单位换算与 `EventHandler.scheduleAE2Add` 的等价逻辑。
 * 现有的对外方法名（[[getGridNode]] / [[getCableConnectionType]] / [[securityBreak]]）
 * 已经保留，恢复时只需替换返回类型与方法体。
 */
trait AppliedEnergistics2 extends Common {
  // 注意：Scala 的自类型不会被继承，TileEntity 的每个子 trait 都必须重新声明。
  self: net.minecraft.world.level.block.entity.BlockEntity =>

  // TODO(integration.appeng): Mods.AppliedEnergistics2 集成未移植，恒为「未启用」，
  // 因此下面所有更新/存档钩子都不会做任何事。
  private def useAppliedEnergistics2Power() = false

  // ----------------------------------------------------------------------- //

  override def tick(): Unit = {
    super.tick()
    // TODO(integration.appeng): 原实现每 `Settings.get.tickFrequency` 刻调用 updateEnergy()，
    // 逐面通过 `IEnergyGrid#extractAEPower(demand, Actionable.MODULATE, PowerMultiplier.CONFIG)`
    // 抽能并 `Power.fromAE` / `Power.toAE` 换算。
  }

  override protected def initialize(): Unit = {
    super.initialize()
    // TODO(integration.appeng): 原实现为 `EventHandler.scheduleAE2Add(this)`，
    // 延迟一 tick 后 `getGridNode(UNKNOWN).updateState()` 把节点加入 AE 电网。
  }

  override def dispose(): Unit = {
    super.dispose()
    // TODO(integration.appeng): 原实现在 `invalidate()` / `onChunkUnload()` 里调用 securityBreak()
    // 销毁 AE 电网节点（`IGridNode#destroy()`）。注意 dispose 可能被调用两次，恢复时需保证幂等。
  }

  // ----------------------------------------------------------------------- //

  override protected def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)
    // TODO(integration.appeng): 原实现为 `getGridNode(UNKNOWN).loadFromNBT("oc:ae2power", nbt)`。
  }

  override protected def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)
    // TODO(integration.appeng): 原实现为 `getGridNode(UNKNOWN).saveToNBT("oc:ae2power", nbt)`。
  }

  // ----------------------------------------------------------------------- //

  /**
   * TODO(integration.appeng): 原返回 `appeng.api.networking.IGridNode`（服务端首次调用时创建并缓存）。
   * AE2 API 不可用时返回 `null`（与客户端分支的返回值一致）。
   */
  def getGridNode(side: Direction): AnyRef = null

  /**
   * TODO(integration.appeng): 原返回 `appeng.api.util.AECableType.SMART`。
   */
  def getCableConnectionType(side: Direction): AnyRef = null

  /**
   * TODO(integration.appeng): 原销毁 AE 电网节点（`getGridNode(UNKNOWN).destroy()`）。
   */
  def securityBreak(): Unit = {}
}
