package li.cil.oc.common.menu

import li.cil.oc.common.item.TabletWrapper
import li.cil.oc.integration.opencomputers.DriverScreen
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import net.minecraft.world.Container
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.DataSlot

class Tablet( id: Int, playerInventory: Inventory, val stack: ItemStack, tablet: Container, slot1: String, tier1: Int)
  extends AbstractMenu(MenuTypes.TABLET.get(), id, playerInventory, tablet) {

  override protected def getHostClass = classOf[TabletWrapper]

  addSlot(new StaticComponentSlot(this, otherInventory, otherInventory.getContainerSize - 1, 90, 35, getHostClass, slot1, tier1) {
    override def mayPlace(stack: ItemStack): Boolean = {
      if (DriverScreen.worksWith(stack, getHostClass)) return false
      super.mayPlace(stack)
    }
  })

  addPlayerInventorySlots(8, 84)

  private val runningData = tablet match {
    case wrapper: TabletWrapper =>
      addDataSlot(new DataSlot {
        override def get(): Int = if (wrapper.machine.isRunning) 1 else 0

        override def set(value: Int): Unit = ()
      })
    case _ => addDataSlot(DataSlot.standalone)
  }
  def isRunning: Boolean = runningData.get != 0

  override def stillValid(player: Player) = player == playerInventory.player
}
