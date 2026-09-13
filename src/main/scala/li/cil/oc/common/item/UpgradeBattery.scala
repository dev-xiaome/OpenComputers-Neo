package li.cil.oc.common.item

import li.cil.oc.Settings
import li.cil.oc.api.driver.item.Chargeable
import li.cil.oc.common.item.data.NodeData
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

/**
 * 「电池升级」（原 `li.cil.oc.common.item.UpgradeBattery`）。
 *
 * 1.21.1 迁移要点：
 *  - 删除 `parent: Delegator`，`unlocalizedName` 由基类按 `tier` 自动拼出。
 *  - 1.21.1 的 `Item` 没有可覆写的 `getDamage` / `setDamage`，耐久条改由
 *    [[Item#isBarVisible]] / [[Item#getBarWidth]] 控制，因此旧的 `damage` / `maxDamage`
 *    只作为 OC 内部语义保留（供 `ItemCosts` 等调用）。
 */
class UpgradeBattery(props: Item.Properties, val tier: Int)
  extends Item(props) with traits.Delegate with traits.ItemTier with Chargeable {

  override protected def tooltipName: Option[String] = Option(super.unlocalizedName)

  override protected def tooltipData: Seq[Any] = Seq(Settings.get.bufferCapacitorUpgrades(tier).toInt)

  override def isDamageable: Boolean = true

  override def damage(stack: ItemStack): Int = {
    val data = new NodeData(stack)
    ((1 - data.buffer.getOrElse(0.0) / Settings.get.bufferCapacitorUpgrades(tier)) * 100).toInt
  }

  override def maxDamage(stack: ItemStack): Int = 100

  // ----------------------------------------------------------------------- //

  override def canCharge(stack: ItemStack): Boolean = true

  override def charge(stack: ItemStack, amount: Double, simulate: Boolean): Double = {
    val data = new NodeData(stack)
    val buffer = data.buffer match {
      case Some(value) => value
      case _ => 0.0
    }
    if (amount < 0) amount // TODO support discharging
    else {
      val charge = math.min(amount, Settings.get.bufferCapacitorUpgrades(tier).toInt - buffer)
      if (!simulate) {
        data.buffer = Option(buffer + charge)
        data.save(stack)
      }
      amount - charge
    }
  }
}
