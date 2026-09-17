package li.cil.oc.common.template

import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.common.inventory.ServerInventory
import li.cil.oc.util.ItemUtils
import net.minecraft.world.item.ItemStack

import scala.language.postfixOps

/**
 * 服务器拆解模板（对应 1.7.10 的 `common.template.ServerTemplate`）。
 *
 * 1.21.1 迁移要点：`IInventory#getSizeInventory` → `IItemHandler#getSlots`，
 * 且 `getStackInSlot` 返回 `ItemStack.EMPTY` 而不是 `null`，因此空槽位要显式过滤。
 */
object ServerTemplate {
  def selectDisassembler(stack: ItemStack) =
    api.Items.get(stack) == api.Items.get(Constants.ItemName.ServerTier1) ||
      api.Items.get(stack) == api.Items.get(Constants.ItemName.ServerTier2) ||
      api.Items.get(stack) == api.Items.get(Constants.ItemName.ServerTier3)

  def disassemble(stack: ItemStack, ingredients: Array[ItemStack]) = {
    val info = new ServerInventory {
      override def container = stack
    }
    Array(ingredients, (0 until info.getSlots).map(info.getStackInSlot).filter(s => s != null && !s.isEmpty).toArray)
  }

  def register(): Unit = {
    // Disassembler
    api.IMC.registerDisassemblerTemplate("Server",
      "li.cil.oc.common.template.ServerTemplate.selectDisassembler",
      "li.cil.oc.common.template.ServerTemplate.disassemble")
  }
}
