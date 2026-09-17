package li.cil.oc.common.item

import li.cil.oc.util.ItemStackNBTExtensions._

import li.cil.oc.util.Rarity
import li.cil.oc.util.Tooltip
import li.cil.oc.util.TooltipKeyBindings
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

import scala.collection.mutable

/**
 * 「服务器」（原 `li.cil.oc.common.item.Server`）。
 *
 * 1.21.1 迁移要点：
 *  - 删除 `parent: Delegator`；`unlocalizedName` 由基类按 `tier` 自动拼出。
 *  - 品质在注册期由 [[Server.tier]] 工厂固定。
 *  - `player.openGui(OpenComputers, GuiType.Server.id, ...)` → `player.openMenu(MenuProvider)`，
 *    服务器菜单属于 `common/container`（尚未移植），这里保留 TODO 占位。
 *  - `common/inventory/ServerInventory` 尚未移植：组件列表改为直接用已移植的
 *    [[li.cil.oc.common.item.data.MicrocontrollerData]] 读取（服务器与单片机的组件存储
 *    格式一致，`ItemData` 的名字都是各自的 `Constants.ItemName.*`）。
 *  - `item.getDisplayName` → `stack.getHoverName.getString`
 */
class Server(props: Item.Properties, override val tier: Int) extends Item(props) with traits.Delegate {

  override protected def tooltipName: Option[String] = Option(super.unlocalizedName)

  override protected def tooltipExtended(stack: ItemStack, tooltip: java.util.List[String]): Unit = {
    super.tooltipExtended(stack, tooltip)
    if (TooltipKeyBindings.showExtendedTooltips) {
      // TODO(容器): 等 `common/inventory/ServerInventory` 移植完成后换成它的 `reinitialize`
      // 语义（它还会考虑容器槽位与 EEPROM 占位）。
      val data = new li.cil.oc.common.item.data.MicrocontrollerData(
        li.cil.oc.Constants.ItemName.ServerTier1)
      val tag = stack.getTag()
      if (tag != null) data.load(tag)
      val itemsByName = mutable.Map.empty[String, Int]
      for (component <- data.components if component != null && !component.isEmpty) {
        val itemName = component.getHoverName.getString
        itemsByName += itemName -> (if (itemsByName.contains(itemName)) itemsByName(itemName) + 1 else 1)
      }
      if (itemsByName.nonEmpty) {
        tooltip.addAll(Tooltip.get("Server.Components"))
        for (itemName <- itemsByName.keys.toArray.sorted) {
          tooltip.add("- " + itemsByName(itemName) + "x " + itemName)
        }
      }
    }
  }

  override def use(world: Level, player: Player, hand: InteractionHand): InteractionResultHolder[ItemStack] = {
    val stack = player.getItemInHand(hand)
    if (!player.isShiftKeyDown) {
      // TODO(菜单): 1.21.1 用 `player.openMenu(new SimpleMenuProvider(...))` 打开服务器 GUI，
      // 需要 `li.cil.oc.common.container.Server` 与 `MenuType` 移植完成后接线。
      player.swing(hand)
    }
    InteractionResultHolder.sidedSuccess(stack, world.isClientSide)
  }
}

object Server {
  /** 按等级创建物品（`tier` 为 0 起的等级索引）。 */
  def tier(t: Int): Server =
    new Server(new Item.Properties().rarity(Rarity.byTier(t)), t)
}
