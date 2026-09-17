package li.cil.oc.common.item

import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/**
 * 「可挂载磁盘驱动器」（原 `li.cil.oc.common.item.DiskDriveMountable`）。
 *
 * 1.21.1 迁移要点：
 *  - 删除 `parent: Delegator`。
 *  - `player.openGui(OpenComputers, GuiType.DiskDriveMountable.id, ...)` 在 1.21.1 已不存在，
 *    改为 `player.openMenu(MenuProvider)`；菜单属于 `common/container`，尚未移植，
 *    这里保留 TODO 占位（客户端与服务端都要打开，避免槽位错位）。
 */
class DiskDriveMountable(props: Item.Properties) extends Item(props) with traits.Delegate {

  override def use(world: Level, player: Player, hand: InteractionHand): InteractionResultHolder[ItemStack] = {
    val stack = player.getItemInHand(hand)
    // TODO(菜单): 1.21.1 用 `player.openMenu(new SimpleMenuProvider(...))` 打开 GUI，
    // 需要 `li.cil.oc.common.container.DiskDriveMountable` 与 `MenuType`。
    // 旧的 `li.cil.oc.common.GuiType.DiskDriveMountable` 常量仍然保留，供移植容器时复用。
    player.swing(hand)
    InteractionResultHolder.sidedSuccess(stack, world.isClientSide)
  }
}
