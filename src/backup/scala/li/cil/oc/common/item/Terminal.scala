package li.cil.oc.common.item

import li.cil.oc.util.ItemStackNBTExtensions._

import java.util

import li.cil.oc.Localization
import li.cil.oc.Settings
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/**
 * 「终端」（原 `li.cil.oc.common.item.Terminal`）。
 *
 * 1.21.1 迁移要点：
 *  - `registerIcons` / `icon(stack, pass)` 已删除：1.21.1 的开关外观改由
 *    `ItemProperties` 模型谓词（`overrides`）或物品模型的 `custom_model_data` 表达，
 *    具体在客户端阶段实现（见 docs/PORTING.md 第 6 步）。
 *  - `player.openGui(OpenComputers, GuiType.Terminal.id, ...)` → `player.openMenu(MenuProvider)`；
 *    终端菜单属于 `common/container`，尚未移植，这里保留 TODO 占位。
 */
class Terminal(props: Item.Properties) extends Item(props) with traits.Delegate {

  def hasServer(stack: ItemStack): Boolean =
    stack.hasTag() && stack.getTag().contains(Settings.namespace + "server")

  override def tooltipLines(stack: ItemStack, player: Player,
                            tooltip: util.List[String], advanced: Boolean): Unit = {
    super.tooltipLines(stack, player, tooltip, advanced)
    if (hasServer(stack)) {
      val server = stack.getTag().getString(Settings.namespace + "server")
      if (server.length > 13) {
        tooltip.add("§8" + server.substring(0, 13) + "...§7")
      }
      else {
        tooltip.add("§8" + server + "§7")
      }
    }
  }

  override def onItemRightClick(stack: ItemStack, world: Level, player: Player): ItemStack = {
    if (!player.isShiftKeyDown && stack.hasTag()) {
      val key = stack.getTag().getString(Settings.namespace + "key")
      val server = stack.getTag().getString(Settings.namespace + "server")
      if (key != null && !key.isEmpty && server != null && !server.isEmpty) {
        // TODO(菜单): 1.21.1 改用 `player.openMenu(...)` 打开终端；
        // 需要 `common/container/Terminal` 与 `MenuType` 移植完成后接线。
        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND)
      }
    }
    super.onItemRightClick(stack, world, player)
  }
}
