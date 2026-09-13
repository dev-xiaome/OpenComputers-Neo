package li.cil.oc.common.tileentity.traits.power

import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag

/**
 * IC2 Experimental（工业时代 2 实验版）能量集成 —— **未移植的降级占位实现**。
 *
 * ==原实现（1.7.10）==
 * 通过 `@Injectable.Interface`（ASM 注入）让本 trait 实现 `ic2.api.energy.tile.IEnergySink`：
 * 外部把 EU 通过 `injectEnergy` 灌进 `conversionBuffer`，每 `Settings.get.tickFrequency` 刻
 * 由 `updateEnergy()` 用 `Power.fromEU` / `Power.toEU` 换算后 `tryAllSides` 注入 OC 缓冲；
 * 加入/移出 IC2 能量网走 `EventHandler.scheduleIC2Add` 与 `EnergyTileUnloadEvent` 反射投递。
 *
 * ==为什么降级==
 *  - ASM 注入层（`li.cil.oc.common.asm.**`）在 1.21.1 已整体删除；
 *  - IC2 未移植，`ic2.api.*` 全部不可用；
 *  - `li.cil.oc.integration.util.Power`、`li.cil.oc.common.EventHandler` 未移植；
 *  - `MinecraftForge.EVENT_BUS` 在 NeoForge 1.21.1 已改为 `NeoForge.EVENT_BUS`，
 *    且 IC2 的事件类本身也不存在。
 *
 * 注意：[[addedToIC2PowerGrid]]（来自 [[IndustrialCraft2Common]]）与
 * `conversionBuffer` 的存档键 `oc:ic2power` 均按原样保留，恢复集成时可直接复用存档。
 */
trait IndustrialCraft2Experimental extends Common with IndustrialCraft2Common {
  // 注意：Scala 的自类型不会被继承，TileEntity 的每个子 trait 都必须重新声明。
  self: net.minecraft.world.level.block.entity.BlockEntity =>

  private var conversionBuffer = 0.0

  // TODO(integration.ic2): Mods.IndustrialCraft2 集成未移植，恒为「未启用」。
  private def useIndustrialCraft2Power() = false

  // ----------------------------------------------------------------------- //

  override def tick(): Unit = {
    super.tick()
    // TODO(integration.ic2): 原实现每 `Settings.get.tickFrequency` 刻调用 updateEnergy()：
    // 以 `conversionBuffer` 为源，`Power.fromEU` / `Power.toEU` 换算后 tryAllSides 注入 OC 缓冲。
  }

  override protected def initialize(): Unit = {
    super.initialize()
    // TODO(integration.ic2): 原实现为
    // `if (!addedToIC2PowerGrid) EventHandler.scheduleIC2Add(this)`，
    // 延迟一 tick 后投递 `EnergyTileLoadEvent` 把本方块加入 IC2 能量网。
  }

  override def dispose(): Unit = {
    super.dispose()
    // TODO(integration.ic2): 原实现在 `invalidate()` / `onChunkUnload()` 里，
    // 若 `addedToIC2PowerGrid` 则反射投递 `EnergyTileUnloadEvent` 移出能量网。
    // 1.21.1 的 `dispose()` 同时承担这两个时机且可能被调用两次，恢复时需保证幂等。
  }

  // ----------------------------------------------------------------------- //

  override protected def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)
    conversionBuffer = nbt.getDouble(li.cil.oc.Settings.namespace + "ic2power")
  }

  override protected def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)
    nbt.putDouble(li.cil.oc.Settings.namespace + "ic2power", conversionBuffer)
  }

  // ----------------------------------------------------------------------- //

  /** TODO(integration.ic2): 原实现恒返回 `Int.MaxValue`（不限电压等级）。 */
  def getSinkTier: Int = Int.MaxValue

  /**
   * TODO(integration.ic2): 原为 `useIndustrialCraft2Power && canConnectPower(direction)`。
   */
  def acceptsEnergyFrom(emitter: net.minecraft.world.level.block.entity.BlockEntity, direction: Direction): Boolean = false

  /**
   * TODO(integration.ic2): 原实现把 `amount` 累加进 `conversionBuffer` 并返回 `0.0`。
   * 集成未启用时不应真的收下能量，因此这里直接返回 `0.0` 且不改变缓冲。
   */
  def injectEnergy(directionFrom: Direction, amount: Double, voltage: Double): Double = 0.0

  /**
   * TODO(integration.ic2): 原为
   * `min(所有侧的 globalDemand 最大值, Power.toEU(energyThroughput))`（缓冲满时返回 0）。
   */
  def getDemandedEnergy: Double = 0.0
}
