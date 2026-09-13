package li.cil.oc.common.tileentity.traits.power

import net.minecraft.core.Direction

/**
 * RotaryCraft（旋转工艺）轴功率集成 —— **未移植的降级占位实现**。
 *
 * ==原实现（1.7.10）==
 * 通过 `@Injectable.Interface`（ASM 注入）让本 trait 实现
 * `Reika.RotaryCraft.API.Power.ShaftPowerReceiver`（以及 `ShaftMachine` / `PowerAcceptor`
 * 两组方法）：外部通过 `setOmega` / `setTorque` / `setPower` 写入转速、扭矩与功率，
 * 每 `Settings.get.tickFrequency` 刻由 `updateEnergy()` 用 `Power.fromWA` / `Power.toWA`
 * 换算后 `tryAllSides` 注入 OC 缓冲。
 *
 * ==为什么降级==
 *  - ASM 注入层（`li.cil.oc.common.asm.**`）在 1.21.1 已整体删除；
 *  - RotaryCraft 未移植，`Reika.RotaryCraft.API.*` 不可用；
 *  - `li.cil.oc.integration.util.Power` 与 `li.cil.oc.integration.Mods` 也未移植。
 *
 * 由于没有任何外部调用者，本文件保留的 `omega` / `torque` / `power` / `alpha` 状态
 * 只作为占位（不会参与能量换算）；`OpenComputers.Name` 仍原样返回，便于恢复集成后复用。
 */
trait RotaryCraft extends Common {
  // 注意：Scala 的自类型不会被继承，TileEntity 的每个子 trait 都必须重新声明。
  self: net.minecraft.world.level.block.entity.BlockEntity =>

  // TODO(integration.rotarycraft): Mods.RotaryCraft 集成未移植，恒为「未启用」。
  private def useRotaryCraftPower() = false

  private var omega = 0
  private var torque = 0
  private var power = 0L
  private var alpha = 0

  // ----------------------------------------------------------------------- //

  override def tick(): Unit = {
    // TODO(integration.rotarycraft): 原实现在此调用 updateEnergy()：每 `Settings.get.tickFrequency`
    // 刻以 `power` 为源，`Power.fromWA` / `Power.toWA` 换算后 tryAllSides 注入 OC 缓冲。
    super.tick()
  }

  // ----------------------------------------------------------------------- //
  // ShaftMachine

  /** TODO(integration.rotarycraft): 原返回外部写入的转速。 */
  def getOmega: Int = omega

  /** TODO(integration.rotarycraft): 原返回外部写入的扭矩。 */
  def getTorque: Int = torque

  /** TODO(integration.rotarycraft): 原返回外部写入的功率。 */
  def getPower: Long = power

  /** TODO(integration.rotarycraft): 原返回 `OpenComputers.Name`，这里保持一致。 */
  def getName: String = li.cil.oc.OpenComputers.Name

  /** TODO(integration.rotarycraft): 原返回 I/O 渲染透明度。 */
  def getIORenderAlpha: Int = alpha

  /** TODO(integration.rotarycraft): 原写入 I/O 渲染透明度。 */
  def setIORenderAlpha(value: Int): Unit = alpha = value

  // ----------------------------------------------------------------------- //
  // ShaftPowerReceiver

  /** TODO(integration.rotarycraft): 原写入转速。 */
  def setOmega(value: Int): Unit = omega = value

  /** TODO(integration.rotarycraft): 原写入扭矩。 */
  def setTorque(value: Int): Unit = torque = value

  /** TODO(integration.rotarycraft): 原写入功率。 */
  def setPower(value: Long): Unit = power = value

  /** TODO(integration.rotarycraft): 原在无输入时把转速/扭矩/功率清零。 */
  def noInputMachine(): Unit = {
    omega = 0
    torque = 0
    power = 0
  }

  // ----------------------------------------------------------------------- //
  // PowerAcceptor

  /** TODO(integration.rotarycraft): 原实现恒返回 `true`。 */
  def canReadFrom(forgeDirection: Direction): Boolean = true

  /** TODO(integration.rotarycraft): 原实现恒返回 `true`。 */
  def isReceiving: Boolean = true

  /** TODO(integration.rotarycraft): 原实现恒返回 0。 */
  def getMinTorque(available: Int): Int = 0
}
