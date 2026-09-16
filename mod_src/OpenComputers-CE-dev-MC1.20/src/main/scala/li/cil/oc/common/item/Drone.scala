package li.cil.oc.common.item

import li.cil.oc.client.KeyBindings
import li.cil.oc.common.entity
import li.cil.oc.common.item.data.DroneData
import li.cil.oc.server.agent
import li.cil.oc.util.{BlockPosition, Rarity, Tooltip}
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item.Properties
import net.minecraft.world.item.{Item, ItemStack}
import net.minecraftforge.common.extensions.IForgeItem

import java.util

class Drone(props: Properties) extends Item(props) with IForgeItem with traits.SimpleItem {
  override protected def tooltipExtended(stack: ItemStack, tooltip: util.List[Component]): Unit = {
    if (KeyBindings.showExtendedTooltips) {
      val info = new DroneData(stack)
      for (component <- info.components if !component.isEmpty) {
        tooltip.add(Component.literal("- " + component.getHoverName.getString).setStyle(Tooltip.DefaultStyle))
      }
    }
  }

  override def getRarity(stack: ItemStack) = {
    val data = new DroneData(stack)
    Rarity.byTier(data.tier)
  }

  override def onItemUse(stack: ItemStack, player: Player, position: BlockPosition, side: Direction, hitX: Float, hitY: Float, hitZ: Float) = {
    val world = position.world.get
    if (!world.isClientSide) {
      val drone = entity.EntityTypes.DRONE.get().create(world)
      player match {
        case fakePlayer: agent.Player =>
          drone.ownerName = fakePlayer.agent.ownerName
          drone.ownerUUID = fakePlayer.agent.ownerUUID
        case _ =>
          drone.ownerName = player.getName.getString
          drone.ownerUUID = player.getGameProfile.getId
      }
      drone.initializeAfterPlacement(stack, player, position.offset(hitX * 1.1f, hitY * 1.1f, hitZ * 1.1f))
      world.addFreshEntity(drone)
    }
    stack.shrink(1)
    true
  }
}
