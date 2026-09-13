package li.cil.oc.common.container

import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import li.cil.oc.common
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.util.IIcon

import scala.jdk.CollectionConverters._

trait ComponentSlot extends Slot {
  def container: Player

  def slot: String

  def tier: Int

  def tierIcon: IIcon

  var changeListener: Option[Slot => Unit] = None

  // ----------------------------------------------------------------------- //

  @SideOnly(Dist.CLIENT)
  override def func_111238_b() = slot != common.Slot.None && tier != common.Tier.None && super.func_111238_b()

  override def isItemValid(stack: ItemStack) = inventory.isItemValidForSlot(getSlotIndex, stack)

  override def onPickupFromSlot(player: Player, stack: ItemStack): Unit = {
    super.onPickupFromSlot(player, stack)
    for (slot <- container.inventorySlots) slot match {
      case dynamic: ComponentSlot => dynamic.clearIfInvalid(player)
      case _ =>
    }
  }

  override def putStack(stack: ItemStack): Unit = {
    super.putStack(stack)
    inventory match {
      case playerAware: common.tileentity.traits.PlayerInputAware =>
        playerAware.onSetInventorySlotContents(container.playerInventory.player, getSlotIndex, stack)
      case _ =>
    }
  }

  override def onSlotChanged(): Unit = {
    super.onSlotChanged()
    for (slot <- container.inventorySlots) slot match {
      case dynamic: ComponentSlot => dynamic.clearIfInvalid(container.playerInventory.player)
      case _ =>
    }
    changeListener.foreach(_(this))
  }

  protected def clearIfInvalid(player: Player) {}
}
