package li.cil.oc.common.tileentity.traits.power

import net.minecraft.nbt.CompoundTag

/**
 * Factorization（电荷）能量集成 —— **未移植的降级占位实现**。
 *
 * ==原实现（1.7.10）==
 * 通过 `@Injectable.Interface`（ASM 注入）让本 trait 实现 `factorization.api.IChargeConductor`，
 * 内部持有一个 `factorization.api.Charge`，每 tick 调用 `Charge#update()`，
 * 每 `Settings.get.tickFrequency` 刻用 `Charge#deplete(amount)` 取电，
 * 并通过 `Power.fromCharge` / `Power.toCharge` 换算单位；
 * 存档时用 `Charge#readFromNBT/writeToNBT(nbt, "fzpower")`。
 *
 * ==为什么降级==
 *  - ASM 注入层（`li.cil.oc.common.asm.**`）在 1.21.1 已整体删除；
 *  - Factorization 未移植，`factorization.api.*`（`Charge` / `Coord` / `IChargeConductor`）
 *    全部不可用；
 *  - `li.cil.oc.integration.util.Power` 与 `li.cil.oc.common.EventHandler` 也未移植。
 *
 * ==恢复方式==
 * 恢复集成时请把 `Charge` 相关逻辑放回本 trait（或改为 NeoForge Capability 形式接入），
 * 对外方法名 [[getCharge]] / [[getInfo]] / [[getCoord]] 已保留。
 */
trait Factorization extends Common {
  // 注意：Scala 的自类型不会被继承，TileEntity 的每个子 trait 都必须重新声明。
  self: net.minecraft.world.level.block.entity.BlockEntity =>

  // TODO(integration.factorization): Mods.Factorization 集成未移植，恒为「未启用」。
  private def useFactorizationPower() = false

  // ----------------------------------------------------------------------- //

  override def tick(): Unit = {
    // TODO(integration.factorization): 原实现在此先 `getCharge.update()`，
    // 再每 `Settings.get.tickFrequency` 刻调用 updateEnergy()
    // （`Charge#deplete(demand)` + `Power.fromCharge` / `Power.toCharge`）。
    super.tick()
  }

  override def dispose(): Unit = {
    // TODO(integration.factorization): 原实现在 `invalidate()` 里调用 `getCharge.invalidate()`，
    // 在 `onChunkUnload()` 里（未 invalid 时）调用 `getCharge.remove()`。
    // 1.21.1 的 `dispose()` 同时承担这两个时机且可能被调用两次，恢复时需保证幂等。
    super.dispose()
  }

  // ----------------------------------------------------------------------- //

  override protected def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)
    // TODO(integration.factorization): 原实现为 `getCharge.readFromNBT(nbt, "fzpower")`。
  }

  override protected def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)
    // TODO(integration.factorization): 原实现为 `getCharge.writeToNBT(nbt, "fzpower")`。
  }

  // ----------------------------------------------------------------------- //

  /**
   * TODO(integration.factorization): 原返回 `factorization.api.Charge`；
   * 与原实现一致，Factorization 不可用时返回 `null`。
   */
  def getCharge: AnyRef = null

  /**
   * TODO(integration.factorization): 原实现恒返回空串（`IChargeConductor` 的调试信息）。
   */
  def getInfo: String = ""

  /**
   * TODO(integration.factorization): 原返回 `new factorization.api.Coord(this)`。
   */
  def getCoord: AnyRef = null
}
