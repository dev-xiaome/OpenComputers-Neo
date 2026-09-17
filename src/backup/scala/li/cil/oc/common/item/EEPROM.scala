package li.cil.oc.common.item

import li.cil.oc.util.ItemStackNBTExtensions._

import li.cil.oc.Settings
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

/**
 * 「EEPROM」（原 `li.cil.oc.common.item.EEPROM`）。
 *
 * 1.21.1 迁移要点：
 *  - `getItemStackDisplayName(stack)` → [[Item#getName(ItemStack)]]（返回 [[Component]]）
 *  - `doesSneakBypassUse` 已删除（1.21.1 由方块/`BlockItem` 侧决定），这里改为无操作移除。
 */
class EEPROM(props: Item.Properties) extends Item(props) with traits.SimpleItem {

  override def getName(stack: ItemStack): Component = {
    if (stack.hasTag()) {
      val tag = stack.getTag()
      if (tag.contains(Settings.namespace + "data")) {
        val data = tag.getCompound(Settings.namespace + "data")
        if (data.contains(Settings.namespace + "label")) {
          return Component.literal(data.getString(Settings.namespace + "label"))
        }
      }
    }
    super.getName(stack)
  }
}
