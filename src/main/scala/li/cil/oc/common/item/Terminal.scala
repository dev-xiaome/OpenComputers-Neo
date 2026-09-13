package li.cil.oc.common.item

import java.util

import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.common.GuiType
import li.cil.oc.server.{PacketSender => ServerPacketSender}
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

class Terminal(val parent: Delegator) extends traits.Delegate {
  override def maxStackSize = 1

  private var iconOn: Option[Icon] = None
  private var iconOff: Option[Icon] = None

  def hasServer(stack: ItemStack) = stack.hasTagCompound && stack.getTagCompound.contains(Settings.namespace + "server")

  @SideOnly(Dist.CLIENT)
  override def tooltipLines(stack: ItemStack, player: Player, tooltip: util.List[String], advanced: Boolean): Unit = {
    super.tooltipLines(stack, player, tooltip, advanced)
    if (hasServer(stack)) {
      val server = stack.getTagCompound.getString(Settings.namespace + "server")
      tooltip.add("§8" + server.substring(0, 13) + "...§7")
    }
  }

  // TODO check if server is in range and running
  // Unlike in the GUI handler the result should definitely be cached here.
  @SideOnly(Dist.CLIENT)
  override def icon(stack: ItemStack, pass: Int) = if (hasServer(stack)) iconOn else iconOff

  override def registerIcons(iconRegister: IconRegister) = {
    super.registerIcons(iconRegister)

    iconOn = Option(iconRegister.registerIcon(Settings.resourceDomain + ":TerminalOn"))
    iconOff = Option(iconRegister.registerIcon(Settings.resourceDomain + ":TerminalOff"))
  }

  override def onItemRightClick(stack: ItemStack, world: Level, player: Player) = {
    if (!player.isSneaking && stack.hasTagCompound) {
      val key = stack.getTagCompound.getString(Settings.namespace + "key")
      val server = stack.getTagCompound.getString(Settings.namespace + "server")
      if (key != null && !key.isEmpty && server != null && !server.isEmpty) {
        if (world.isRemote) {
          player.openGui(OpenComputers, GuiType.Terminal.id, world, 0, 0, 0)
        }
        player.swingItem()
      }
    }
    super.onItemRightClick(stack, world, player)
  }
}
