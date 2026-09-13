package li.cil.oc.common.item

import li.cil.oc.util.ItemStackNBTExtensions._

import java.util
import java.util.UUID

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.common.Tier
import li.cil.oc.common.item.data.TabletData
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.Rarity
import li.cil.oc.util.Tooltip
import li.cil.oc.util.TooltipKeyBindings
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.UseAnim
import net.minecraft.world.level.Level

/**
 * 「平板电脑（物品形态）」（原 `li.cil.oc.common.item.Tablet`）。
 *
 * 降级说明（依赖未移植内容）：
 *  - [[TabletWrapper]] / `Tablet.Cache` / `Tablet.Client` / `Tablet.Server`
 *    依赖 `common/inventory/ComponentInventory`、`server/component/Tablet`、
 *    `integration/opencomputers/DriverScreen` 等尚未移植的包，本文件暂不移植；
 *    平板的机器逻辑留到 `li.cil.oc.server` 阶段。
 *  - 因此 [[update]]（驱动机器 tick）、[[onPlayerStoppedUsing]] 的
 *    「分析方块 / 启动或停止机器 / 打开 GUI」与 `Tablet.get` 全部降级为空实现，
 *    只保留**物品自身的能量（充放电）语义与提示信息**。
 *  - `player.setItemInUse(stack, n)` → `Item#use` 返回 `consume` + [[getMaxItemUseDuration]]
 *  - `player.openGui(...)` → `player.openMenu(MenuProvider)`（`common/container` 未移植）
 *  - `registerIcons` / `icon(stack, pass)` 已删除（1.21.1 走模型 JSON）
 */
class Tablet(props: Item.Properties) extends Item(props) with traits.Delegate with traits.Chargeable {

  final val TimeToAnalyze = 10

  // Must be assembled to be usable so we hide it in the item list.
  showInItemList = false

  override protected def tooltipExtended(stack: ItemStack, tooltip: util.List[String]): Unit = {
    if (TooltipKeyBindings.showExtendedTooltips) {
      val info = new TabletData(stack)
      // Ignore/hide the screen.
      val components = info.items.drop(1)
      if (components.length > 1) {
        tooltip.addAll(Tooltip.get("Server.Components"))
        components.collect {
          case Some(component) if !component.isEmpty => tooltip.add("- " + component.getHoverName.getString)
        }
      }
    }
  }

  override def isDamageable(stack: ItemStack): Boolean = true

  override def getDamage(stack: ItemStack): Int = {
    val data = new TabletData(stack)
    (data.maxEnergy - data.energy).toInt
  }

  override def getMaxDamage(stack: ItemStack): Int = {
    val data = new TabletData(stack)
    data.maxEnergy.toInt max 1
  }

  // ----------------------------------------------------------------------- //

  override def maxCharge(stack: ItemStack): Double = {
    val data = new TabletData(stack)
    data.maxEnergy
  }

  override def getCharge(stack: ItemStack): Double = new TabletData(stack).energy

  override def setCharge(stack: ItemStack, amount: Double): Unit = {
    val data = new TabletData(stack)
    data.energy = math.min(data.maxEnergy, math.max(0, amount))
    data.save(stack)
  }

  override def canCharge(stack: ItemStack): Boolean = true

  override def charge(stack: ItemStack, amount: Double, simulate: Boolean): Double = {
    if (amount < 0) amount
    else {
      val data = new TabletData(stack)
      val charge = math.min(data.maxEnergy - data.energy, amount)
      if (!simulate) {
        data.energy += charge
        data.save(stack)
      }
      amount - charge
    }
  }

  // ----------------------------------------------------------------------- //

  override def use(world: Level, player: Player, hand: InteractionHand): InteractionResultHolder[ItemStack] =
    InteractionResultHolder.consume(player.getItemInHand(hand))

  override def getUseAnimation(stack: ItemStack): UseAnim = UseAnim.NONE

  override def getMaxItemUseDuration(stack: ItemStack): Int = 72000

  override def onItemUse(stack: ItemStack, player: Player, position: BlockPosition,
                         side: Int, hitX: Float, hitY: Float, hitZ: Float): Boolean = {
    Tablet.currentlyAnalyzing = Some((position, side, hitX, hitY, hitZ))
    true
  }

  override def inventoryTick(stack: ItemStack, world: Level, entity: Entity,
                             slot: Int, selected: Boolean): Unit = {
    // TODO(服务器): 原实现会驱动 `Tablet.get(stack, player).update(...)`（平板机器 tick）、
    // 在客户端播放分析完成的音效；依赖 `TabletWrapper`（`ComponentInventory` +
    // `server/component/Tablet`），等 `li.cil.oc.server` 阶段一起移植。
  }

  override def releaseUsing(stack: ItemStack, world: Level, entity: net.minecraft.world.entity.LivingEntity,
                            timeLeft: Int): Unit = {
    entity match {
      case player: Player =>
        val didAnalyze = getMaxItemUseDuration(stack) - timeLeft >= TimeToAnalyze
        if (didAnalyze) {
          // TODO(服务器): 原实现把分析结果通过 `tablet.use` 消息发给平板机器。
        }
        else if (!player.isShiftKeyDown) {
          // TODO(菜单): 原实现在客户端 `player.openMenu(...)` 打开平板 GUI；
          // 服务端则启动平板机器（`Tablet.get(stack, player).machine.start()`）。
        }
      case _ =>
    }
  }
}

object Tablet {
  // This is super-hacky, but since it's only used on the client we get away
  // with storing context information for analyzing a block in the singleton.
  var currentlyAnalyzing: Option[(BlockPosition, Int, Float, Float, Float)] = None

  /** 平板堆叠的唯一 id（原 `Tablet.getId(stack)`）。 */
  def getId(stack: ItemStack): String = {
    if (!stack.hasTag()) {
      stack.setTag(new CompoundTag())
    }
    if (!stack.getTag().contains(Settings.namespace + "tablet")) {
      stack.getTag().putString(Settings.namespace + "tablet", UUID.randomUUID().toString)
    }
    stack.getTag().getString(Settings.namespace + "tablet")
  }

  /** 物品品质按平板内部记录的等级显示（原 `rarity(stack)`）。 */
  def rarityOf(stack: ItemStack): net.minecraft.world.item.Rarity =
    Rarity.byTier(new TabletData(stack).tier)

  /** 创造模式标签页里的预配置平板（原 `Items.createConfiguredTablet`）。 */
  def createConfiguredTablet(): ItemStack = {
    val data = new TabletData()
    data.tier = Tier.Four
    data.maxEnergy = Settings.get.bufferTablet
    data.energy = data.maxEnergy
    val stack = data.createItemStack()
    stack
  }
}
