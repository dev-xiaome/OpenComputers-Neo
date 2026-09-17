package li.cil.oc.common.item.traits

import li.cil.oc.Settings
import net.minecraft.world.item.ItemStack

/**
 * 可充电物品（原 1.7.10 的 `Chargeable`）。
 *
 * 1.21.1 迁移要点：
 *  - 原文件通过 ASM（`@Injectable.InterfaceList`）给物品注入 AE2 / IC2 / Mekanism /
 *    CoFH RF 的接口。1.21.1 已移除 coremod（见 docs/PORTING.md「架构层」），
 *    这些第三方模组也不在本项目移植范围内，因此这里只保留 OC 自身的充电语义。
 *  - 若将来要对接 NeoForge 的能量能力，应改为实现 `IEnergyStorage` 并在
 *    `RegisterCapabilitiesEvent` 里注册，而不是给物品加接口。
 *  - TODO(集成): `getEnergyStored` / `getEnergy` / `getMaxTransfer` 等外部能量单位的
 *    换算率原本来自 `li.cil.oc.integration.util.Power`（集成层，未移植），
 *    目前统一按 1:1 处理；集成层移植后请换回正确比率。
 */
trait Chargeable extends li.cil.oc.api.driver.item.Chargeable {

  /** 最大可存储能量（单位与 `getCharge` 一致，即 OC 自身能量单位）。 */
  def maxCharge(stack: ItemStack): Double

  /** 当前已存储能量。 */
  def getCharge(stack: ItemStack): Double

  /** 设置已存储能量。 */
  def setCharge(stack: ItemStack, amount: Double): Unit

  /**
   * 是否需要按 metadata 区分子类型。
   * 1.21.1 不再使用 damage 派发，恒为 `false`；保留以兼容旧调用点。
   */
  def isMetadataSpecific(stack: ItemStack): Boolean = false

  // 以下为原先对外部能量体系的适配表面。1.21.1 下不再有对应的注入接口，
  // 但部分转换代码（如充电器）仍会调用，因此保留。

  /** 原 CoFH RF `getEnergyStored`。 */
  def getEnergyStored(stack: ItemStack): Int = getCharge(stack).toInt

  /** 原 CoFH RF `getMaxEnergyStored`。 */
  def getMaxEnergyStored(stack: ItemStack): Int = maxCharge(stack).toInt

  /** 原 CoFH RF `receiveEnergy`。 */
  def receiveEnergy(stack: ItemStack, maxReceive: Int, simulate: Boolean): Int =
    maxReceive - charge(stack, maxReceive, simulate).toInt

  /** 原 CoFH RF `extractEnergy`。 */
  def extractEnergy(stack: ItemStack, maxExtract: Int, simulate: Boolean): Int =
    maxExtract - charge(stack, -maxExtract, simulate).toInt

  /** 原 Mekanism `getEnergy`。 */
  def getEnergy(stack: ItemStack): Double = getCharge(stack)

  /** 原 Mekanism `setEnergy`。 */
  def setEnergy(stack: ItemStack, amount: Double): Unit = setCharge(stack, amount)

  /** 原 Mekanism `getMaxEnergy`。 */
  def getMaxEnergy(stack: ItemStack): Double = maxCharge(stack)

  /** 原 Mekanism `canSend`。 */
  def canSend(stack: ItemStack): Boolean = false

  /** 原 Mekanism `canReceive`。 */
  def canReceive(stack: ItemStack): Boolean = true

  /** 原 Mekanism `getMaxTransfer`。 */
  def getMaxTransfer(stack: ItemStack): Double = Settings.get.chargeRateTablet
}
