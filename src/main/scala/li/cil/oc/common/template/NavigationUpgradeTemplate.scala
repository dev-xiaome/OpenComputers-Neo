package li.cil.oc.common.template

import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.common.item.data.NavigationUpgradeData
import net.minecraft.world.item.{ItemStack, Items}

import scala.language.postfixOps

/**
 * 导航升级拆解模板（对应 1.7.10 的 `common.template.NavigationUpgradeTemplate`）。
 *
 * 1.21.1 迁移要点：`net.minecraft.init.Items.filled_map` → `net.minecraft.world.item.Items.FILLED_MAP`
 * （1.21.1 里物品与方块共用同一个 `Items` 注册表）。
 */
object NavigationUpgradeTemplate {
  def selectDisassembler(stack: ItemStack) = api.Items.get(stack) == api.Items.get(Constants.ItemName.NavigationUpgrade)

  def disassemble(stack: ItemStack, ingredients: Array[ItemStack]) = {
    val info = new NavigationUpgradeData(stack)
    ingredients.map {
      case part if part != null && part.getItem == Items.FILLED_MAP => info.map
      case part => part
    }
  }

  def register(): Unit = {
    // Disassembler
    api.IMC.registerDisassemblerTemplate(
      "Navigation Upgrade",
      "li.cil.oc.common.template.NavigationUpgradeTemplate.selectDisassembler",
      "li.cil.oc.common.template.NavigationUpgradeTemplate.disassemble")
  }
}
