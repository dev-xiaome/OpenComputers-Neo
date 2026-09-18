package li.cil.oc.common.item

import li.cil.oc.common.datacomponents.OCComponents
import li.cil.oc.util.ExtendedDataComponentHolder._
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item.Properties
import net.minecraft.world.item.{Item, ItemStack}
import net.minecraft.world.level.LevelReader
import net.neoforged.neoforge.common.extensions.IItemExtension

class EEPROM(props: Properties) extends Item(props) with traits.SimpleItem with IItemExtension {
  override def getName(stack: ItemStack): Component = {
    stack.getComponent(OCComponents.LABEL).foreach(label => return Component.literal(label))
    super.getName(stack)
  }

  override def doesSneakBypassUse(stack: ItemStack, world: LevelReader, pos: BlockPos, player: Player): Boolean = true
}
