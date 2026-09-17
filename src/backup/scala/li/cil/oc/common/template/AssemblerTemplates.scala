package li.cil.oc.common.template

import java.lang.reflect.Method

import com.google.common.base.Strings
import li.cil.oc.OpenComputers
import li.cil.oc.api
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.Reflection
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.nbt.{CompoundTag, Tag}
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.items.IItemHandler

import scala.collection.mutable
import scala.language.existentials

/**
 * 装配机模板注册表（对应 1.7.10 的 `common.template.AssemblerTemplates`）。
 *
 * 1.21.1 迁移要点：
 *  - 静态回调仍按「全限定方法名 + 反射」解析（见 [[li.cil.oc.common.Reflection]]），
 *    但回调签名里的物品栏参数由 `IInventory`（已随 1.7.10 一起消失）改为
 *    `net.neoforged.neoforge.items.IItemHandler`。也就是说第三方通过 IMC 注册模板时，
 *    `validate` / 槽位 `validate` 回调必须声明为
 *    `Object[] validate(IItemHandler)` 与 `boolean validate(IItemHandler, int, int, ItemStack)`；
 *  - `NBT.TAG_COMPOUND` → [[net.minecraft.nbt.Tag.TAG_COMPOUND]]，`getInteger` → `getInt`；
 *  - `Int#underlying`（Scala 2.11 的 `AnyVal` 装箱语法）已被移除，改用 `Int.box`。
 */
object AssemblerTemplates {
  val NoSlot = new Slot(Slot.None, Tier.None, None, None)

  private val templates = mutable.ArrayBuffer.empty[Template]

  private val templateFilters = mutable.ArrayBuffer.empty[Method]

  def add(template: CompoundTag): Unit = {
    val selector = Reflection.getStaticMethod(template.getString("select"), classOf[ItemStack])
    val validator = Reflection.getStaticMethod(template.getString("validate"), classOf[IItemHandler])
    val assembler = Reflection.getStaticMethod(template.getString("assemble"), classOf[IItemHandler])
    val hostClass = tryGetHostClass(template.getString("hostClass"))
    val containerSlots = template.getList("containerSlots", Tag.TAG_COMPOUND).map((tag: CompoundTag) => parseSlot(tag, Some(Slot.Container), hostClass)).take(3).padTo(3, NoSlot).toArray
    val upgradeSlots = template.getList("upgradeSlots", Tag.TAG_COMPOUND).map((tag: CompoundTag) => parseSlot(tag, Some(Slot.Upgrade), hostClass)).take(9).padTo(9, NoSlot).toArray
    val componentSlots = template.getList("componentSlots", Tag.TAG_COMPOUND).map((tag: CompoundTag) => parseSlot(tag, None, hostClass)).take(9).padTo(9, NoSlot).toArray

    templates += new Template(selector, validator, assembler, containerSlots, upgradeSlots, componentSlots)
  }

  def addFilter(method: String): Unit = {
    templateFilters += Reflection.getStaticMethod(method, classOf[ItemStack])
  }

  def select(stack: ItemStack) = {
    if (stack != null && !stack.isEmpty && templateFilters.forall(Reflection.tryInvokeStatic(_, stack)(true)))
      templates.find(_.select(stack))
    else
      None
  }

  class Template(val selector: Method,
                 val validator: Method,
                 val assembler: Method,
                 val containerSlots: Array[Slot],
                 val upgradeSlots: Array[Slot],
                 val componentSlots: Array[Slot]) {
    def select(stack: ItemStack) = Reflection.tryInvokeStatic(selector, stack)(false)

    def validate(inventory: IItemHandler) = Reflection.tryInvokeStatic(validator, inventory)(null: Array[AnyRef]) match {
      case Array(valid: java.lang.Boolean, progress: Component, warnings: Array[Component]) => (valid: Boolean, progress, warnings)
      case Array(valid: java.lang.Boolean, progress: Component) => (valid: Boolean, progress, Array.empty[Component])
      case Array(valid: java.lang.Boolean) => (valid: Boolean, null, Array.empty[Component])
      case _ => (false, null, Array.empty[Component])
    }

    def assemble(inventory: IItemHandler) = Reflection.tryInvokeStatic(assembler, inventory)(null: Array[AnyRef]) match {
      case Array(stack: ItemStack, energy: java.lang.Number) => (stack, energy.doubleValue(): Double)
      case Array(stack: ItemStack) => (stack, 0.0)
      case _ => (null, 0.0)
    }
  }

  class Slot(val kind: String, val tier: Int, val validator: Option[Method], val hostClass: Option[Class[_ <: EnvironmentHost]]) {
    def validate(inventory: IItemHandler, slot: Int, stack: ItemStack) = validator match {
      case Some(method) => Reflection.tryInvokeStatic(method, inventory, Int.box(slot), Int.box(tier), stack)(false)
      case _ => Option(hostClass.fold(api.Driver.driverFor(stack))(api.Driver.driverFor(stack, _))) match {
        case Some(driver) => try driver.slot(stack) == kind && driver.tier(stack) <= tier catch {
          case t: AbstractMethodError =>
            OpenComputers.log.warn(s"Error trying to query driver '${driver.getClass.getName}' for slot and/or tier information. Probably their fault. Yell at them before coming to OpenComputers for support. :P")
            false
        }
        case _ => false
      }
    }
  }

  private def parseSlot(nbt: CompoundTag, kindOverride: Option[String], hostClass: Option[Class[_ <: EnvironmentHost]]) = {
    val kind = kindOverride.getOrElse(if (nbt.contains("type")) nbt.getString("type") else Slot.None)
    val tier = if (nbt.contains("tier")) nbt.getInt("tier") else Tier.Any
    val validator = if (nbt.contains("validate")) Option(Reflection.getStaticMethod(nbt.getString("validate"), classOf[IItemHandler], classOf[Int], classOf[Int], classOf[ItemStack])) else None
    new Slot(kind, tier, validator, hostClass)
  }

  private def tryGetHostClass(name: String) =
    if (Strings.isNullOrEmpty(name)) None
    else Option(Class.forName(name).asSubclass(classOf[EnvironmentHost]))
}
