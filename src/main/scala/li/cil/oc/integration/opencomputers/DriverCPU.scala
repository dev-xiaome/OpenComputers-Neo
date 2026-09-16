package li.cil.oc.integration.opencomputers
import li.cil.oc.util.ItemStackNBTExtensions._

import li.cil.oc.Constants
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.common.item
import li.cil.oc.common.item.Delegator
import li.cil.oc.server.component
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag

import scala.jdk.CollectionConverters._

object DriverCPU extends DriverCPU

abstract class DriverCPU extends Item with api.driver.item.MutableProcessor with api.driver.item.CallBudget {
  /**
   * 1.7.10 原生 Lua 架构的类名（`server.machine.luac`）。
   *
   * TODO(port): 原生 Lua 按 `docs/PROGRESS.md` 第 7 条暂不移植，`machine.luac` 包不存在，
   * 因此这里用**字符串字面量**代替 `classOf[NativeLuaArchitecture].getName`。
   * 老存档里 CPU 记录的正是这个类名，保留它才能继续把旧 CPU 迁移到默认架构。
   */
  private final val NativeLuaArchitectureName = "li.cil.oc.server.machine.luac.NativeLuaArchitecture"

  override def worksWith(stack: ItemStack) = isOneOf(stack,
    api.Items.get(Constants.ItemName.CPUTier1),
    api.Items.get(Constants.ItemName.CPUTier2),
    api.Items.get(Constants.ItemName.CPUTier3))

  override def createEnvironment(stack: ItemStack, host: api.network.EnvironmentHost): api.network.ManagedEnvironment = new component.CPU(tier(stack))

  override def slot(stack: ItemStack) = Slot.CPU

  override def tier(stack: ItemStack) = cpuTier(stack)

  def cpuTier(stack: ItemStack): Int =
    Delegator.subItem(stack) match {
      case Some(cpu: item.CPU) => cpu.cpuTier
      case _ => Tier.One
    }

  override def supportedComponents(stack: ItemStack) = Settings.get.cpuComponentSupport(cpuTier(stack))

  // 1.21.1：`api.Machine.architectures` 返回 `java.util.Collection`，要 `asScala` 才能用 Scala 集合操作。
  override def allArchitectures = api.Machine.architectures.asScala.toList

  override def architecture(stack: ItemStack): Class[_ <: api.machine.Architecture] = {
    if (stack.hasTag()) {
      val archClass = stack.getTag().getString(Settings.namespace + "archClass") match {
        case clazz if clazz == NativeLuaArchitectureName =>
          // Migrate old saved CPUs to new versions (since the class they refer still
          // exists, but is abstract, which would lead to issues).
          api.Machine.LuaArchitecture.getName
        case clazz => clazz
      }
      if (!archClass.isEmpty) try return Class.forName(archClass).asSubclass(classOf[api.machine.Architecture]) catch {
        case t: Throwable =>
          OpenComputers.log.warn("Failed getting class for CPU architecture. Resetting CPU to use the default.", t)
          stack.getTag().remove(Settings.namespace + "archClass")
          stack.getTag().remove(Settings.namespace + "archName")
      }
    }
    api.Machine.architectures.asScala.headOption.orNull
  }

  override def setArchitecture(stack: ItemStack, architecture: Class[_ <: api.machine.Architecture]): Unit = {
    if (!worksWith(stack)) throw new IllegalArgumentException("Unsupported processor type.")
    // 1.21.1：`ItemStack` 没有 `put`，NBT 走 `li.cil.oc` 的隐式类 → `setTag`。
    if (!stack.hasTag()) stack.setTag(new CompoundTag())
    stack.getTag().putString(Settings.namespace + "archClass", architecture.getName)
    stack.getTag().putString(Settings.namespace + "archName", api.Machine.getArchitectureName(architecture))
  }

  override def getCallBudget(stack: ItemStack): Double = Settings.get.callBudgets(tier(stack) max Tier.One min Tier.Three)
}
