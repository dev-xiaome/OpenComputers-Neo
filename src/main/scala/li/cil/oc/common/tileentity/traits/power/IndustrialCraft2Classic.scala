package li.cil.oc.common.tileentity.traits.power

import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag

/**
 * IC2 Classic（工业时代 2 经典版）能量集成 —— **未移植的降级占位实现**。
 *
 * ==原实现（1.7.10）==
 * 通过 `@Injectable.Interface`（ASM 注入）让本 trait 实现 `ic2classic.api.energy.tile.IEnergySink`：
 * 外部把 EU 通过 `injectEnergy` 灌进 `conversionBuffer`，每 `Settings.get.tickFrequency` 刻
 * 由 `updateEnergy()` 用 `Power.fromEU` / `Power.toEU` 换算后 `tryAllSides` 注入 OC 缓冲；
 * 加入/移出 IC2 能量网走 `EventHandler.scheduleIC2Add` 与 `EnergyTileUnloadEvent` 反射投递。
 *
 * ==为什么降级==
 *  - ASM 注入层（`li.cil.oc.common.asm.**`）在 1.21.1 已整体删除；
 *  - IC2 Classic 未移植，`ic2classic.api.*` 全部不可用；
 *  - `li.cil.oc.integration.util.Power`、`li.cil.oc.common.EventHandler` 未移植；
 *  - `MinecraftForge.EVENT_BUS` / `net.minecraftforge.common.MinecraftForge` 在 NeoForge 1.21.1
 *    已改为 `NeoForge.EVENT_BUS`，且 IC2 的事件类本身也不存在。
 *
 * ==类型适配说明==
 * 原签名使用的 `ic2classic.api.Direction` 在 1.21.1 没有等价物，这里的 `direction` 参数
 * 统一退化为 [[net.minecraft.core.Direction]]（`toForgeDirection` 也随之删除）。
 * 方法名与参数个数保持不变。
 *
 * 注意：[[addedToIC2PowerGrid]]（来自 [[IndustrialCraft2Common]]）与
 * `conversionBuffer` 的存档键 `oc:ic2cpower` 均按原样保留，恢复集成时可直接复用存档。
 */
trait IndustrialCraft2Classic extends Common with IndustrialCraft2Common {
  // 注意：Scala 的自类型不会被继承，TileEntity 的每个子 trait 都必须重新声明。
  self: net.minecraft.world.level.block.entity.BlockEntity =>

  private var conversionBuffer = 0.0

  // TODO(integration.ic2classic): Mods.IndustrialCraft2Classic 集成未移植，恒为「未启用」。
  private def useIndustrialCraft2ClassicPower() = false

  // ----------------------------------------------------------------------- //

  override def tick(): Unit = {
    super.tick()
    // TODO(integration.ic2classic): 原实现每 `Settings.get.tickFrequency` 刻调用 updateEnergy()：
    // 以 `conversionBuffer` 为源，`Power.fromEU` / `Power.toEU` 换算后 tryAllSides 注入 OC 缓冲。
  }

  override protected def initialize(): Unit = {
    super.initialize()
    // TODO(integration.ic2classic): 原实现为
    // `if (!addedToIC2PowerGrid) EventHandler.scheduleIC2Add(this)`，
    // 延迟一 tick 后投递 `EnergyTileLoadEvent` 把本方块加入 IC2 能量网。
  }

  override def dispose(): Unit = {
    super.dispose()
    // TODO(integration.ic2classic): 原实现在 `invalidate()` / `onChunkUnload()` 里，
    // 若 `addedToIC2PowerGrid` 则反射投递 `EnergyTileUnloadEvent` 移出能量网。
    // 1.21.1 的 `dispose()` 同时承担这两个时机且可能被调用两次，恢复时需保证幂等。
  }

  // ----------------------------------------------------------------------- //

  override protected def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)
    conversionBuffer = nbt.getDouble(li.cil.oc.Settings.namespace + "ic2cpower")
  }

  override protected def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)
    nbt.putDouble(li.cil.oc.Settings.namespace + "ic2cpower", conversionBuffer)
  }

  // ----------------------------------------------------------------------- //

  /** TODO(integration.ic2classic): 原为 `addedToIC2PowerGrid`。 */
  def isAddedToEnergyNet: Boolean = addedToIC2PowerGrid

  /** TODO(integration.ic2classic): 原实现恒返回 `Int.MaxValue`（不限电压）。 */
  def getMaxSafeInput: Int = Int.MaxValue

  /**
   * TODO(integration.ic2classic): 原为
   * `useIndustrialCraft2ClassicPower && canConnectPower(direction.toForgeDirection)`。
   */
  def acceptsEnergyFrom(emitter: net.minecraft.world.level.block.entity.BlockEntity, direction: Direction): Boolean = false

  /**
   * TODO(integration.ic2classic): 原实现把 `amount` 累加进 `conversionBuffer` 并返回 `true`。
   * 集成未启用时不应真的收下能量，因此这里返回 `false` 且不改变缓冲。
   */
  def injectEnergy(directionFrom: Direction, amount: Int): Boolean = false

  /**
   * TODO(integration.ic2classic): 原为
   * `min(所有侧的 globalDemand 最大值, Power.toEU(energyThroughput))`（缓冲满时返回 0）。
   */
  def demandsEnergy: Int = 0
}
