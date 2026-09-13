package li.cil.oc.common.item

import li.cil.oc.Settings
import li.cil.oc.util.Tooltip
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

/**
 * 「已配对卡」（原 `li.cil.oc.common.item.LinkedCard`）：用于建立私有隧道。
 *
 * 1.21.1 迁移要点：
 *  - `stack.hasTagCompound` / `getTagCompound` → 隐式扩展 `hasTag()` / `getTag()`
 *    （底层是自定义数据组件 `opencomputers_neo:nbt`）
 *  - `CompoundTag#hasKey` → `contains`
 */
class LinkedCard(props: Item.Properties) extends Item(props) with traits.Delegate with traits.ItemTier {

  override def tooltipLines(stack: ItemStack, player: Player,
                            tooltip: java.util.List[String], advanced: Boolean): Unit = {
    if (stack.hasTag() && stack.getTag().contains(Settings.namespace + "data")) {
      val data = stack.getTag().getCompound(Settings.namespace + "data")
      if (data.contains(Settings.namespace + "tunnel")) {
        val channel = data.getString(Settings.namespace + "tunnel")
        if (channel.length > 13) {
          tooltip.addAll(Tooltip.get(unlocalizedName + "_Channel", channel.substring(0, 13) + "..."))
        }
        else {
          tooltip.addAll(Tooltip.get(unlocalizedName + "_Channel", channel))
        }
      }
    }
    super.tooltipLines(stack, player, tooltip, advanced)
  }
}
