package li.cil.oc.common.item

import li.cil.oc.Settings
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

/**
 * 「硬盘」（原 `li.cil.oc.common.item.HardDiskDrive`）。
 *
 * 1.21.1 迁移要点：
 *  - 删除 `parent: Delegator`；`unlocalizedName` 由基类按 `tier` 自动拼出。
 *  - `parent.internalGetItemStackDisplayName(stack)` 原本是 `Delegator` 提供的基础显示名，
 *    1.21.1 直接退化为 `Item#getName`（即语言文件里的名字）。
 *  - `displayName` 的返回类型从 `Option[String]` 变为 `Option[Component]`。
 */
class HardDiskDrive(props: Item.Properties, override val tier: Int)
  extends Item(props) with traits.Delegate with traits.ItemTier with traits.FileSystemLike {

  val kiloBytes: Int = Settings.get.hddSizes(tier)
  val platterCount: Int = Settings.get.hddPlatterCounts(tier)

  override def displayName(stack: ItemStack): Option[Component] = {
    val localizedName = super.getName(stack).getString
    Some(Component.literal(if (kiloBytes >= 1024) {
      localizedName + s" (${kiloBytes / 1024}MB)"
    }
    else {
      localizedName + s" (${kiloBytes}KB)"
    }))
  }
}
