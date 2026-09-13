package li.cil.oc.common.item

import li.cil.oc.Settings
import li.cil.oc.common.item.data.HoverBootsData
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ArmorItem
import net.minecraft.world.item.ArmorMaterials
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/**
 * 「悬浮靴」（原 `li.cil.oc.common.item.HoverBoots`）。
 *
 * 降级说明（依赖未移植内容）：
 *  - `ItemArmor(ItemArmor.ArmorMaterial.DIAMOND, 0, 3)` →
 *    [[ArmorItem]](`ArmorMaterials.DIAMOND`, `ArmorItem.Type.BOOTS`)。
 *  - `@SideOnly(Dist.CLIENT) getArmorModel` / `HoverBootRenderer`（`client.renderer.item`）
 *    与 `registerIcons` / `getIconFromDamageForRenderPass` 全部删除：
 *    1.21.1 的护甲外观走护甲模型 JSON + 装备纹理，染色走
 *    `RegisterColorHandlersEvent.Item` + `ItemColor`（客户端阶段实现）。
 *  - `getArmorTexture` / `getColorFromItemStack` / `onArmorTick` / `onEntityItemUpdate`
 *    这些 1.7.10 的护甲钩子在 1.21.1 已不存在：
 *    - 掉电减速改由 [[inventoryTick]] 在检测到玩家穿戴时施加（等价语义）；
 *    - 「放进炼药锅洗掉染色」依赖 `ItemEntity` 的特殊更新钩子，1.21.1 无对应入口，
 *      作为 TODO 保留。
 *  - 能量条：1.21.1 用 [[isBarVisible]] + [[getBarWidth]] 表达，代替
 *    `getDisplayDamage` / `getMaxDamage`。
 */
class HoverBoots(props: Item.Properties)
  extends ArmorItem(ArmorMaterials.DIAMOND, ArmorItem.Type.BOOTS, props)
    with traits.SimpleItem with traits.Chargeable {

  override def maxCharge(stack: ItemStack): Double = Settings.get.bufferHoverBoots

  override def getCharge(stack: ItemStack): Double = new HoverBootsData(stack).charge

  override def setCharge(stack: ItemStack, amount: Double): Unit = {
    val data = new HoverBootsData(stack)
    data.charge = math.min(maxCharge(stack), math.max(0, amount))
    data.save(stack)
  }

  override def canCharge(stack: ItemStack): Boolean = true

  override def charge(stack: ItemStack, amount: Double, simulate: Boolean): Double = {
    val data = new HoverBootsData(stack)
    if (amount < 0) {
      val remainder = math.min(0, data.charge + amount)
      if (!simulate) {
        data.charge = math.max(0, data.charge + amount)
        data.save(stack)
      }
      remainder
    }
    else {
      val remainder = -math.min(0, Settings.get.bufferHoverBoots - (data.charge + amount))
      if (!simulate) {
        data.charge = math.min(Settings.get.bufferHoverBoots, data.charge + amount)
        data.save(stack)
      }
      remainder
    }
  }

  // ----------------------------------------------------------------------- //
  // 能量条
  // ----------------------------------------------------------------------- //

  override def isBarVisible(stack: ItemStack): Boolean = true

  override def getBarWidth(stack: ItemStack): Int = {
    val max = Settings.get.bufferHoverBoots
    if (max <= 0) 0 else math.round(13 * (getCharge(stack) / max).toFloat) max 0 min 13
  }

  override def getBarColor(stack: ItemStack): Int = 0x66DD55

  // ----------------------------------------------------------------------- //
  // 行为
  // ----------------------------------------------------------------------- //

  override def inventoryTick(stack: ItemStack, world: Level, entity: Entity, slot: Int, selected: Boolean): Unit = {
    entity match {
      case player: Player if !world.isClientSide =>
        // 1.7.10 的 `onArmorTick` 语义：穿在身上且没电时给缓慢效果。
        if (!Settings.get.ignorePower && (player.getItemBySlot(EquipmentSlot.FEET) eq stack) &&
          getCharge(stack) <= 0.0 && !player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
          player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1))
        }
        // TODO(事件): 原版还有「把靴子丢进炼药锅洗掉染色」的行为，
        // 1.21.1 没有 `Item#onEntityItemUpdate`，需要改用 `ItemEntity` 的 tick 事件
        // （`common/event`）在移植后实现。
      case _ =>
    }
  }
}

object HoverBoots {
  /** 默认属性：唯一堆叠、不可修复（原 `setNoRepair()`）。 */
  def defaultProps(): Item.Properties =
    new net.minecraft.world.item.Item.Properties().stacksTo(1).setNoRepair()

  /** 充满电的悬浮靴（原 `Items.createChargedHoverBoots`）。 */
  def createChargedHoverBoots(): ItemStack = {
    val data = new HoverBootsData()
    data.charge = Settings.get.bufferHoverBoots
    data.createItemStack()
  }
}
