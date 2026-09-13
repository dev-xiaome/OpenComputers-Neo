package li.cil.oc.common.item

import java.util

import li.cil.oc.OpenComputers
import li.cil.oc.api
import li.cil.oc.util.BlockPosition
import net.minecraft.ChatFormatting
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/**
 * 「手册」（原 `li.cil.oc.common.item.Manual`）。
 *
 * 1.21.1 迁移要点：
 *  - `@SideOnly(Dist.CLIENT)` 已移除（NeoForge 的 `RuntimeDistCleaner` 会拒绝），
 *    `api.Manual.openFor` 内部自行处理侧别。
 *  - `player.getEntityWorld` → `player.level()`
 *  - `world.isRemote` → `world.isClientSide`；`player.isSneaking` → `player.isShiftKeyDown`
 */
class Manual(props: Item.Properties) extends Item(props) with traits.Delegate {

  override def tooltipLines(stack: ItemStack, player: Player,
                            tooltip: util.List[String], advanced: Boolean): Unit = {
    tooltip.add(ChatFormatting.DARK_GRAY.toString + "v" + OpenComputers.Version)
    super.tooltipLines(stack, player, tooltip, advanced)
  }

  override def onItemRightClick(stack: ItemStack, world: Level, player: Player): ItemStack = {
    if (world.isClientSide) {
      if (player.isShiftKeyDown) {
        api.Manual.reset()
      }
      api.Manual.openFor(player)
    }
    super.onItemRightClick(stack, world, player)
  }

  override def onItemUse(stack: ItemStack, player: Player, position: BlockPosition,
                         side: Int, hitX: Float, hitY: Float, hitZ: Float): Boolean = {
    val world = player.level()
    api.Manual.pathFor(world, position.x, position.y, position.z) match {
      case path: String =>
        if (world.isClientSide) {
          api.Manual.openFor(player)
          api.Manual.reset()
          api.Manual.navigate(path)
        }
        true
      case _ => super.onItemUse(stack, player, position, side, hitX, hitY, hitZ)
    }
  }
}
