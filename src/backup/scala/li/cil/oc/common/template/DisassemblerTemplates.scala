package li.cil.oc.common.template

import java.lang.reflect.Method

import li.cil.oc.OpenComputers
import li.cil.oc.common.Reflection
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack

import scala.collection.mutable

/**
 * 拆解机模板注册表（对应 1.7.10 的 `common.template.DisassemblerTemplates`）。
 *
 * 1.21.1 迁移要点：静态回调解析改用 [[li.cil.oc.common.Reflection]]（原为 `IMC` 里的私有实现）。
 * 回调签名与 1.7.10 一致：
 * {{{
 *   boolean select(ItemStack stack)
 *   Object  disassemble(ItemStack stack, ItemStack[] ingredients)
 * }}}
 */
object DisassemblerTemplates {
  private val templates = mutable.ArrayBuffer.empty[Template]

  def add(template: CompoundTag): Unit = try {
    val selector = Reflection.getStaticMethod(template.getString("select"), classOf[ItemStack])
    val disassembler = Reflection.getStaticMethod(template.getString("disassemble"), classOf[ItemStack], classOf[Array[ItemStack]])

    templates += new Template(selector, disassembler)
  }
  catch {
    case t: Throwable => OpenComputers.log.warn("Failed registering disassembler template.", t)
  }

  def select(stack: ItemStack) = if (stack == null || stack.isEmpty) None else templates.find(_.select(stack))

  class Template(val selector: Method,
                 val disassembler: Method) {
    def select(stack: ItemStack) = Reflection.tryInvokeStatic(selector, stack)(false)

    def disassemble(stack: ItemStack, ingredients: Array[ItemStack]) = Reflection.tryInvokeStatic(disassembler, stack, ingredients)(null: Array[_]) match {
      case Array(stacks: Array[ItemStack], drops: Array[ItemStack]) => (Some(stacks), Some(drops))
      case Array(stack: ItemStack, drops: Array[ItemStack]) => (Some(Array(stack)), Some(drops))
      case Array(stacks: Array[ItemStack], drop: ItemStack) => (Some(stacks), Some(Array(drop)))
      case stacks: Array[ItemStack] => (Some(stacks), None)
      case _ => (None, None)
    }
  }

}
