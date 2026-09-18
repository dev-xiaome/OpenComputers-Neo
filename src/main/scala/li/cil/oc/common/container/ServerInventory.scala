package li.cil.oc.common.container

import li.cil.oc.api.Driver
import li.cil.oc.api.internal
import li.cil.oc.api.ImmutableItemStack
import li.cil.oc.common.InventorySlots
import li.cil.oc.common.datacomponents.OCComponents
import li.cil.oc.common.menu.{Server => ServerContainer}
import li.cil.oc.util.ItemUtils
import li.cil.oc.common.menu.MenuTypes
import net.minecraft.core.component.{DataComponentHolder, DataComponentType}
import net.neoforged.neoforge.common.MutableDataComponentHolder
import net.minecraft.world.item.ItemStack
import net.minecraft.world.entity.player.Player
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.player.Inventory

trait ServerInventory extends ItemStackInventory with MenuProvider {
  def rackSlot: Int

  override def component: DataComponentType[List[ImmutableItemStack]] =
    OCComponents.COMPONENTS.get()

  override def loadData(holder: DataComponentHolder): Unit = {
    val components = Option(holder.get(OCComponents.COMPONENTS.get()))
    val contents = Option(holder.get(OCComponents.CONTENTS.get()))

    components.filter(_.exists(stack => !stack.isEmpty)).orElse(contents).orElse(components).foreach(loadFrom)
  }

  override def saveData(holder: MutableDataComponentHolder): Unit = {
    super.saveData(holder)
    holder.remove(OCComponents.CONTENTS.get())
  }

  def tier: Int = ItemUtils.caseTier(container) max 0

  override def getContainerSize = InventorySlots.server(tier).length

  override protected def inventoryName = "server"

  override def getMaxStackSize = 1

  override def stillValid(player: Player) = false

  override def canPlaceItem(slot: Int, stack: ItemStack) =
    Option(Driver.driverFor(stack, classOf[internal.Server])).fold(false)(driver => {
      val provided = InventorySlots.server(tier)(slot)
      driver.slot(stack) == provided.slot && driver.tier(stack) <= provided.tier
    })

  override def createMenu(id: Int, playerInventory: Inventory, player: Player) =
    new ServerContainer(id, playerInventory, container, this, tier, rackSlot)
}
