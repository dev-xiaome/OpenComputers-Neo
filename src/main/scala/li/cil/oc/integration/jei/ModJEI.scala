package li.cil.oc.integration.jei

import li.cil.oc.common.EventHandler
import mezz.jei.api.constants.VanillaTypes
import mezz.jei.api.runtime.{IIngredientManager, IJeiRuntime}
import net.minecraft.world.item.ItemStack

import scala.collection.JavaConverters.seqAsJavaList
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.collection.convert.ImplicitConversionsToScala._

object ModJEI {
  var runtime: Option[IJeiRuntime] = None
  var ingredientRegistry: Option[IIngredientManager] = None

  private val disksForRuntime: ArrayBuffer[ItemStack] = mutable.ArrayBuffer.empty
  private var scheduled = false

  def addDiskAtRuntime(stack: ItemStack): Unit = ingredientRegistry.foreach { registry =>
    if (!registry.getAllIngredients(VanillaTypes.ITEM_STACK).exists(ItemStack.matches(_, stack))) {
      disksForRuntime += stack
      if (!scheduled) {
        EventHandler.scheduleClient { () =>
          ingredientRegistry.foreach(_.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, seqAsJavaList(disksForRuntime)))
          disksForRuntime.clear()
          scheduled = false
        }
        scheduled = true
      }
    }
  }

  def addItemAtRuntime(stack: ItemStack): Unit = ingredientRegistry.foreach { registry =>
    if (!registry.getAllIngredients(VanillaTypes.ITEM_STACK).exists(ItemStack.matches(_, stack))) {
      EventHandler.scheduleClient { () =>
        ingredientRegistry.foreach { currentRegistry =>
          if (!currentRegistry.getAllIngredients(VanillaTypes.ITEM_STACK).exists(ItemStack.matches(_, stack))) {
            currentRegistry.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, seqAsJavaList(Seq(stack)))
          }
        }
      }
    }
  }
}
