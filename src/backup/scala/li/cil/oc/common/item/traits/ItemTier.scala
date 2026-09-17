package li.cil.oc.common.item.traits

import java.util

import li.cil.oc.Localization
import scala.jdk.CollectionConverters._

import net.minecraft.network.chat.Component

import net.minecraft.world.item.ItemStack

/**
 * 带等级的组件物品（原 1.7.10 的 `ItemTier`）。
 *
 * 迁移说明：旧版 `tooltipLines(stack, player, tooltip: java.util.List[String], advanced)`
 * 在 1.21.1 变为 [[net.minecraft.world.item.Item#appendHoverText]]，`advanced` 由
 * `TooltipFlag#isAdvanced` 表达。这里保留旧签名的辅助方法，并在 `appendHoverText`
 * 里统一转接。
 */
trait ItemTier extends Delegate {

  override def appendHoverText(stack: ItemStack, context: net.minecraft.world.item.Item.TooltipContext,
                               tooltip: util.List[Component],
                               flag: net.minecraft.world.item.TooltipFlag): Unit = {
    val lines = new util.ArrayList[String]()
    tooltipLines(stack, null, lines, flag.isAdvanced)
    lines.asScala.foreach(line => tooltip.add(Component.literal(line)))
  }

  override def tooltipLines(stack: ItemStack, player: net.minecraft.world.entity.player.Player,
                            tooltip: util.List[String], advanced: Boolean): Unit = {
    super.tooltipLines(stack, player, tooltip, advanced)
    if (advanced) {
      tooltip.add(Localization.Tooltip.Tier(tierFromDriver(stack) + 1))
    }
  }
}
