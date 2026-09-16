package li.cil.oc.common.item

import li.cil.oc.Settings
import li.cil.oc.util.BlockPosition
import net.minecraft.world.item.Item
import net.minecraft.world.item.Item.Properties
import net.minecraft.world.item.ItemStack
import net.minecraft.core.BlockPos
import net.minecraftforge.common.extensions.IForgeItem
import net.minecraft.world.level.LevelReader
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player

class EEPROM(props: Properties) extends Item(props) with IForgeItem with traits.SimpleItem {
  override def getName(stack: ItemStack): Component = {
    if (stack.hasTag) {
      val tag = stack.getTag
      if (tag.contains(Settings.namespace + "data")) {
        val data = tag.getCompound(Settings.namespace + "data")
        if (data.contains(Settings.namespace + "label")) {
          return Component.literal(data.getString(Settings.namespace + "label"))
        }
      }
    }
    super.getName(stack)
  }

  override def doesSneakBypassUse(stack: ItemStack, world: LevelReader, pos: BlockPos, player: Player): Boolean = true
}
