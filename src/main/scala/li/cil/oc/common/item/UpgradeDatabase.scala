package li.cil.oc.common.item

import li.cil.oc.util.ItemStackNBTExtensions._

import li.cil.oc.Settings
import li.cil.oc.util.Rarity
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/**
 * 「数据库升级」（原 `li.cil.oc.common.item.UpgradeDatabase`）。
 *
 * 1.21.1 迁移要点：
 *  - 删除 `parent: Delegator`；`unlocalizedName` 由基类按 `tier` 自动拼出。
 *  - 品质在注册期由 [[UpgradeDatabase.tier]] 工厂固定。
 *  - `player.openGui(...)` 在 1.21.1 已不存在，改为 `player.openMenu(MenuProvider)`；
 *    菜单类型属于 `common/container` + `common/GuiHandler`（尚未移植），此处保留 TODO 占位。
 *  - `stack.setTagCompound(null)` → 清空自定义数据组件。
 */
class UpgradeDatabase(props: Item.Properties, override val tier: Int)
  extends Item(props) with traits.Delegate with traits.ItemTier {

  override protected def tooltipName: Option[String] = Option(super.unlocalizedName)

  override protected def tooltipData: Seq[Any] = Seq(Settings.get.databaseEntriesPerTier(tier))

  override def use(world: Level, player: Player, hand: InteractionHand): InteractionResultHolder[ItemStack] = {
    val stack = player.getItemInHand(hand)
    if (!player.isShiftKeyDown) {
      // TODO(菜单): 1.21.1 用 `player.openMenu(new SimpleMenuProvider(...))` 打开数据库 GUI，
      // 需要 `li.cil.oc.common.container.Database` 与 `MenuType` 移植完成后接线。
      player.swing(hand)
    }
    else if (stack.hasTag() && stack.getTag().contains(Settings.namespace + "items")) {
      stack.setTag(null)
      player.swing(hand)
    }
    InteractionResultHolder.sidedSuccess(stack, world.isClientSide)
  }
}

object UpgradeDatabase {
  /** 按等级创建物品（`tier` 为 0 起的等级索引）。 */
  def tier(t: Int): UpgradeDatabase =
    new UpgradeDatabase(new Item.Properties().rarity(Rarity.byTier(t)), t)
}
