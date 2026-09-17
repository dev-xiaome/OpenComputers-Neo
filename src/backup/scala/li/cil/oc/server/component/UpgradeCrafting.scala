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
import li.cil.oc.api.prefab
import li.cil.oc.util.InventoryUtils
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.{Inventory, Player}
import net.minecraft.world.inventory.TransientCraftingContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.{CraftingInput, CraftingRecipe, RecipeHolder, RecipeType}
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.{PlayerDestroyItemEvent, PlayerEvent}

import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.util.control.Breaks._

/**
 * 合成升级：让机器人在自己的物品栏左上角 3x3 区域合成物品。
 *
 * ==1.21.1 迁移要点==
 *  - `InventoryCrafting` + `CraftingManager.findMatchingRecipe` 已移除：
 *    改用 `CraftingInput` + `RecipeManager#getRecipeFor(RecipeType.CRAFTING, ...)`，
 *    产物由 `Recipe#assemble(input, registryAccess)` 计算。
 *  - `net.minecraft.inventory.IInventory` → 1.21.1 的 `Container`
 *    （`Player#getInventory` 仍是 `Container`，`getItem/setItem/removeItem` 一一对应旧方法）。
 *  - `FMLCommonHandler.firePlayerCraftingEvent` → `NeoForge.EVENT_BUS.post(PlayerEvent.ItemCraftedEvent)`。
 *  - `Item#hasContainerItem/getContainerItem` → `ItemStack#hasCraftingRemainingItem/getCraftingRemainingItem`。
 *  - `Item#doesContainerItemLeaveCraftingGrid` 已移除（Forge 的默认实现恒为 `true`），
 *    因此容器物品一律进入 surplus，语义与旧版默认行为一致。
 */
class UpgradeCrafting(val host: EnvironmentHost with internal.Robot) extends prefab.ManagedEnvironment with DeviceInfo {
  override val node = Network.newNode(this, Visibility.Network).
    withComponent("crafting").
    create()

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Generic,
    DeviceAttribute.Description -> "Assembly controller",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "MultiCombinator-9S"
  )

  override def getDeviceInfo: util.Map[String, String] = deviceInfo.asJava

  @Callback(doc = """function([count:number]):number -- Tries to craft the specified number of items in the top left area of the inventory.""")
  def craft(context: Context, args: Arguments): Array[AnyRef] = {
    val count = args.optInteger(0, 64) max 0 min 64
    result(CraftingInventory.craft(count).toSeq: _*)
  }

  /**
   * 3x3 合成网格。
   *
   * 复用 `TransientCraftingContainer` 以获得 `CraftingContainer#asCraftInput` 等实现；
   * 它只在 `stillValid` 中才会用到 `AbstractContainerMenu`，而合成升级从不需要该判定，
   * 因此传入 `null`（不构造假的 `MenuType`）。
   */
  private object CraftingInventory extends TransientCraftingContainer(null, 3, 3) {
    var amountPossible = 0

    def craft(wantedCount: Int): Seq[_] = {
      val player = host.player
      load(player.getInventory)
      val recipeManager = host.world.getRecipeManager
      val registry = host.world.registryAccess()
      var countCrafted = 0
      var originalResult: ItemStack = null
      breakable {
        while (countCrafted < wantedCount) {
          val input = asCraftInput
          val holder: RecipeHolder[CraftingRecipe] =
            recipeManager.getRecipeFor[CraftingInput, CraftingRecipe](RecipeType.CRAFTING, input, host.world).orElse(null)
          if (holder == null) break()
          val result = holder.value().assemble(input, registry)
          if (result == null || result.isEmpty || result.getCount < 1) break()
          if (originalResult == null) {
            originalResult = result
          } else if (!ItemStack.isSameItemSameComponents(originalResult, result)) {
            break()
          }
          countCrafted += result.getCount
          NeoForge.EVENT_BUS.post(new PlayerEvent.ItemCraftedEvent(player, result, this))
          val surplus = mutable.ArrayBuffer.empty[ItemStack]
          for (slot <- 0 until getContainerSize) {
            val stack = getItem(slot)
            if (stack != null && !stack.isEmpty) {
              // 1.7.10 的 `decrStackSize(slot, 1)`（返回值即被消耗的那一个）。
              val consumed = removeItem(slot, 1)
              val ingredient = if (consumed != null && !consumed.isEmpty) consumed else stack
              if (ingredient.hasCraftingRemainingItem) {
                val container = ingredient.getCraftingRemainingItem
                if (container.isDamageableItem && container.getDamageValue > container.getMaxDamage) {
                  NeoForge.EVENT_BUS.post(new PlayerDestroyItemEvent(player, container, InteractionHand.MAIN_HAND))
                }
                else surplus += container
              }
            }
          }
          save(player.getInventory)
          InventoryUtils.addToPlayerInventory(result, player)
          for (stack <- surplus) {
            InventoryUtils.addToPlayerInventory(stack, player)
          }
          load(player.getInventory)
        }
      }
      Seq(originalResult != null, countCrafted)
    }

    def load(inventory: Inventory): Unit = {
      amountPossible = Int.MaxValue
      for (slot <- 0 until getContainerSize) {
        val stack = inventory.getItem(toParentSlot(slot))
        // `NonNullList` 不接受 `null`，用空栈表示空槽位。
        setItem(slot, if (stack == null) ItemStack.EMPTY else stack)
        if (stack != null && !stack.isEmpty) {
          amountPossible = math.min(amountPossible, stack.getCount)
        }
      }
    }

    def save(inventory: Inventory): Unit = {
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
