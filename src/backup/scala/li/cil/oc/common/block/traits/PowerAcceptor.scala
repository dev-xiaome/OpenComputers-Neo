package li.cil.oc.common.block.traits

import li.cil.oc.common.block.SimpleBlockHooks
import li.cil.oc.util.Tooltip
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

/** 接受能量输入的方块：提示里额外显示最大输入功率（对应 1.7.10 的 `block.traits.PowerAcceptor`）。 */
trait PowerAcceptor extends SimpleBlockHooks {
  // 注意：Scala 的自类型不会被继承，[[SimpleBlockHooks]] 的子 trait 必须重新声明 `self: Block`。
  self: net.minecraft.world.level.block.Block =>

  def energyThroughput: Double

  // ----------------------------------------------------------------------- //

  override protected def tooltipTail(stack: ItemStack, player: Player, tooltip: java.util.List[String], advanced: Boolean): Unit = {
    super.tooltipTail(stack, player, tooltip, advanced)
    tooltip.addAll(Tooltip.extended("PowerAcceptor", energyThroughput.toInt))
  }
}
