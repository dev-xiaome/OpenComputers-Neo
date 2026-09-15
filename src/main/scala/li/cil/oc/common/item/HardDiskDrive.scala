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
    // 不能写 `super.getName(stack)`：`traits.Delegate` 覆写了 `getName` 并回调 `displayName`，
    // 而本类的线性化里 `super.getName` 解析到的正是那个 trait 实现，
    // 于是 `getName` ↔ `displayName` 无限递归（StackOverflowError，悬停硬盘即崩客户端）。
    // 这里直接用与基类 `Item#getName` 完全等价的实现：
    // `Item#getName(ItemStack)` 就是 `Component.translatable(getDescriptionId(stack))`。
    val localizedName = Component.translatable(getDescriptionId(stack)).getString
    Some(Component.literal(if (kiloBytes >= 1024) {
      localizedName + s" (${kiloBytes / 1024}MB)"
    }
    else {
      localizedName + s" (${kiloBytes}KB)"
    }))
  }
}
