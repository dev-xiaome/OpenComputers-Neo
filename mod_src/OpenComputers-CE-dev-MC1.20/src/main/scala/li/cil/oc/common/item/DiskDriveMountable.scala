package li.cil.oc.common.item

import li.cil.oc.OpenComputers
import li.cil.oc.common.menu.MenuTypes
import li.cil.oc.common.container.DiskDriveMountableInventory
import net.minecraft.world.item.Item
import net.minecraft.world.item.Item.Properties
import net.minecraft.world.item.ItemStack
import net.minecraftforge.common.extensions.IForgeItem
import net.minecraft.world.level.Level
import net.minecraft.world.entity.player.Player
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder

class DiskDriveMountable(props: Properties) extends Item(props) with IForgeItem with traits.SimpleItem {
  override def use(stack: ItemStack, level: Level, player: Player) = {
    if (!level.isClientSide) player match {
      case srvPlr: ServerPlayer => MenuTypes.openDiskDriveGui(srvPlr, new DiskDriveMountableInventory {
        override def container: ItemStack = stack

        override def stillValid(player: Player) = player == srvPlr
      })
      case _ =>
    }
    player.swing(InteractionHand.MAIN_HAND)
    new InteractionResultHolder(InteractionResult.sidedSuccess(level.isClientSide), stack)
  }
}
