package li.cil.oc.common.item

import li.cil.oc.Settings
import li.cil.oc.common.container.DatabaseInventory
import li.cil.oc.common.menu.MenuTypes
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item.Properties
import net.minecraft.world.item.{Item, ItemStack}
import net.minecraft.world.level.Level
import net.minecraft.world.{InteractionHand, InteractionResultHolder}
import net.minecraft.world.item.component.CustomData
import net.neoforged.neoforge.common.extensions.IItemExtension

class UpgradeDatabase(props: Properties, val tier: Int) extends Item(props) with traits.SimpleItem with traits.ItemTier with IItemExtension {
  @Deprecated
  override def getDescriptionId = super.getDescriptionId + tier

  override protected def tooltipData = Seq(Settings.get.databaseEntriesPerTier(tier))

  override def use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder[ItemStack] = {
    val stack = player.getItemInHand(hand)
    if (!player.isCrouching) {
      if (!level.isClientSide) player match {
        case srvPlr: ServerPlayer => MenuTypes.openDatabaseGui(srvPlr, new DatabaseInventory {
          override def container = stack

          override def stillValid(player: Player) = player == srvPlr
        })
        case _ =>
      }
    }
    else {
      CustomData.update(DataComponents.CUSTOM_DATA, stack, data => {
        data.remove(Settings.namespace + "items")
      })
    }
    InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
  }
}
