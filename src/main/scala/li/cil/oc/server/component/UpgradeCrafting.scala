package li.cil.oc.server.component

import java.util
import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.internal
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network._
import li.cil.oc.api.prefab.AbstractManagedEnvironment
import li.cil.oc.util.InventoryUtils

import scala.collection.convert.ImplicitConversionsToJava._
import net.minecraft.world.inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ResultContainer
import net.minecraft.world.inventory.ResultSlot
import net.minecraft.world.item.crafting.CraftingInput
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.entity.player.Player
import net.minecraft.world.Container
import net.minecraft.world.item.ItemStack

import scala.collection.mutable

class UpgradeCrafting(val host: EnvironmentHost with internal.Robot) extends AbstractManagedEnvironment with DeviceInfo {
  override val node = Network.newNode(this, Visibility.Network).
    withComponent("crafting").
    create()

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Generic,
    DeviceAttribute.Description -> "Assembly controller",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "MultiCombinator-9S"
  )

  override def getDeviceInfo: util.Map[String, String] = deviceInfo

  @Callback(doc = """function([count:number]):number -- Tries to craft the specified number of items in the top left area of the inventory.""")
  def craft(context: Context, args: Arguments): Array[AnyRef] = {
    val count = args.optInteger(0, 64) max 0 min 64
    result(CraftingContainer.craft(count): _*)
  }

  private object CraftingContainer extends inventory.TransientCraftingContainer(new AbstractContainerMenu(null, 0) {
    override def stillValid(player: Player) = true
    override def quickMoveStack(player: Player, i: Int) = ItemStack.EMPTY
  }, 3, 3) {
    def craft(wantedCount: Int): Seq[_] = {
      val player = host.player
      copyItemsFromHost(player.inventory)
      var countCrafted = 0
      // Do not let crafted results occupy an empty slot in the crafting grid
      // before all requested rounds have been attempted.
      val craftedStacks = mutable.ArrayBuffer.empty[ItemStack]
      val manager = host.getEnvironmentLevel.getRecipeManager
      val initialCraft = manager.getRecipeFor(RecipeType.CRAFTING, this.asCraftInput(), host.getEnvironmentLevel)
      if (initialCraft.isPresent) {
        def tryCraft() : Boolean = {
          val craft = manager.getRecipeFor(RecipeType.CRAFTING, this.asCraftInput(), host.getEnvironmentLevel)
          if (craft != initialCraft) {
            return false
          }

          val craftResult = new ResultContainer
          val craftingSlot = new ResultSlot(player, CraftingContainer, craftResult, 0, 0, 0)
          val craftedResult = craft.get.value().assemble(this.asCraftInput(), player.registryAccess())
          craftResult.setItem(0, craftedResult)
          if (!craftingSlot.hasItem)
            return false

          val stack = craftingSlot.remove(1)
          if (stack.isEmpty)
            return false
          countCrafted += stack.getCount
          craftingSlot.onTake(player, stack)
          copyItemsToHost(player.inventory)
          copyItemsFromHost(player.inventory)
          craftedStacks += stack
          true
        }
        while (countCrafted < wantedCount && tryCraft()) {
          //
        }
      }
      val craftingSlots = (0 until getContainerSize).map(toParentSlot).toSet
      val outputSlots = (0 until player.inventory.getContainerSize).filterNot(craftingSlots)
      craftedStacks.foreach(addCraftedStack(_, player, outputSlots))
      Seq(countCrafted > 0, countCrafted)
    }

    private def addCraftedStack(stack: ItemStack, player: Player, outputSlots: Iterable[Int]): Unit = {
      if (!stack.isEmpty) {
        val inventory = player.inventory
        InventoryUtils.insertIntoInventory(stack, InventoryUtils.asItemHandler(inventory), slots = Some(outputSlots))
        if (stack.getCount > 0)
          player.drop(stack, false, false)
        inventory.setChanged()
        if (player.containerMenu != null)
          player.containerMenu.broadcastChanges()
      }
    }

    def copyItemsFromHost(inventory: Container): Unit = {
      for (slot <- 0 until getContainerSize) {
        val stack = inventory.getItem(toParentSlot(slot))
        setItem(slot, stack)
      }
    }

    def copyItemsToHost(inventory: Container): Unit = {
      for (slot <- 0 until getContainerSize) {
        inventory.setItem(toParentSlot(slot), getItem(slot))
      }
    }

    private def toParentSlot(slot: Int) = {
      val col = slot % 3
      val row = slot / 3
      row * 4 + col
    }
  }
}
