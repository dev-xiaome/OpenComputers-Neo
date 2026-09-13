package li.cil.oc.common.item

import li.cil.oc.Settings
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

class EEPROM extends traits.SimpleItem {
  override def doesSneakBypassUse(world: Level, x: Int, y: Int, z: Int, player: Player): Boolean = true

  override def getItemStackDisplayName(stack: ItemStack): String = {
    if (stack.hasTagCompound) {
      val tag = stack.getTagCompound
      if (tag.contains(Settings.namespace + "data")) {
        val data = tag.getCompound(Settings.namespace + "data")
        if (data.contains(Settings.namespace + "label")) {
          return data.getString(Settings.namespace + "label")
        }
      }
    }
    super.getItemStackDisplayName(stack)
  }
}
