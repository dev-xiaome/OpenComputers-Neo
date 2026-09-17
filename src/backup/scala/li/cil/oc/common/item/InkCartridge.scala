package li.cil.oc.common.item

import li.cil.oc.Constants
import li.cil.oc.api
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/**
 * 「墨盒」（已注墨，原 `li.cil.oc.common.item.InkCartridge`）。
 *
 * 1.7.10 → 1.21.1：
 *  - 1.7.10 用 `Delegator` 的 damage 值区分墨盒子类型；1.21.1 每个子类型是独立物品，
 *    因此容器物品直接按名称取描述符即可。
 *  - 1.21.1 的 `Item#getCraftingRemainingItem` / `hasCraftingRemainingItem` 是 `final`，
 *    无法覆写；合成剩余物改为在注册期用 `Item.Properties#craftRemainder` 声明
 *    （见 `Registry.Items.initItems`）。这里保留 `getContainerItem` / `hasContainerItem`
 *    两个公开方法，供旧的调用点继续使用。
 */
class InkCartridge(props: Item.Properties) extends Item(props) with traits.Delegate {

  /** 原 `getContainerItem(stack)`：返回一个空墨盒。 */
  override def getContainerItem(stack: ItemStack): ItemStack = {
    val empty = api.Items.get(Constants.ItemName.InkCartridgeEmpty)
    if (empty == null) null else empty.createItemStack(1)
  }

  override def hasContainerItem(stack: ItemStack): Boolean = true

  override def use(world: Level, player: Player, hand: InteractionHand): InteractionResultHolder[ItemStack] = {
    val stack = player.getItemInHand(hand)
    // TODO(打印机): 原版的右键行为与尚未移植的打印机组件的油墨数据结构有关，暂不实现。
    InteractionResultHolder.sidedSuccess(stack, world.isClientSide)
  }
}
