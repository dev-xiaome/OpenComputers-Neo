package li.cil.oc.common.item.traits

import java.util

import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.driver.item.MutableProcessor
import li.cil.oc.util.Tooltip
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

import scala.jdk.CollectionConverters._
import scala.language.existentials

/**
 * CPU 类物品（原 1.7.10 的 `CPULike`）。
 *
 * 1.21.1 迁移要点：
 *  - `onItemRightClick(stack, world, player)` → [[net.minecraft.world.item.Item#use]]，
 *    返回值从 `ItemStack` 变为 `InteractionResultHolder[ItemStack]`
 *  - `player.addChatMessage(...)` → `player.displayClientMessage(Component, actionBar)`
 *  - `player.swingItem()` → `player.swing(hand)`
 *  - `world.isRemote` → `world.isClientSide`
 *  - 原版用 `integration.opencomputers.DriverCPU.architecture(stack)` 读架构。该集成包
 *    属于后续阶段，为避免这里依赖未移植的包，改为**反射调用驱动的 `architecture` 方法**
 *    （等价语义：取当前处理器栈上的架构；取不到时返回 `null`，
 *    `api.Machine.getArchitectureName(null)` 会退化为原始类名）。
 */
trait CPULike extends Delegate {

  /** CPU 等级（0 起）。 */
  def cpuTier: Int

  override protected def tooltipData: Seq[Any] = Seq(Settings.get.cpuComponentSupport(cpuTier))

  override protected def tooltipExtended(stack: ItemStack, tooltip: util.List[String]): Unit = {
    // 不能把 null 传给 `Tooltip.get`：它内部会对每个参数做 `_.toString`，null 直接抛 NPE
    // （打开创造模式物品栏、鼠标悬停到 CPU 上时即崩客户端）。
    // 架构名很可能取不到：`api.Machine` 尚未接线（`server/machine` 未移植），
    // `api.Machine.getArchitectureName` 会返回 null。这里按原版 `Machine.getArchitectureName`
    // 的兜底语义退回类名，取不到架构时显示占位符。
    val architecture = CPULike.architectureOf(stack)
    val name =
      if (architecture == null) "<unknown>"
      else {
        val resolved = api.Machine.getArchitectureName(architecture)
        if (resolved != null) resolved else architecture.getSimpleName
      }
    tooltip.addAll(Tooltip.get("CPU.Architecture", name))
  }

  override def use(world: Level, player: Player, hand: InteractionHand): InteractionResultHolder[ItemStack] = {
    val stack = player.getItemInHand(hand)
    if (player.isShiftKeyDown) {
      if (!world.isClientSide) {
        api.Driver.driverFor(stack) match {
          case driver: MutableProcessor =>
            val architectures = driver.allArchitectures.asScala.toList
            if (architectures.nonEmpty) {
              val currentIndex = architectures.indexOf(driver.architecture(stack))
              val newIndex = (currentIndex + 1) % architectures.length
              val archClass = architectures(newIndex)
              val archName = api.Machine.getArchitectureName(archClass)
              driver.setArchitecture(stack, archClass)
              player.displayClientMessage(
                Component.translatable(Settings.namespace + "tooltip.CPU.Architecture", archName), false)
            }
            player.swing(hand)
          case _ => // 该处理器没有已知驱动。
        }
      }
    }
    InteractionResultHolder.sidedSuccess(stack, world.isClientSide)
  }
}

object CPULike {
  /**
   * 读取处理器栈当前使用的架构类。
   *
   * 原实现是 `integration.opencomputers.DriverCPU.architecture(stack)`；
   * 这里对驱动做反射调用，从而不依赖尚未移植的集成包。
   */
  def architectureOf(stack: ItemStack): Class[_ <: api.machine.Architecture] = {
    api.Driver.driverFor(stack) match {
      case null => null
      case driver =>
        try {
          val method = driver.getClass.getMethod("architecture", classOf[ItemStack])
          method.invoke(driver, stack).asInstanceOf[Class[_ <: api.machine.Architecture]]
        }
        catch {
          case _: Throwable => null
        }
    }
  }
}
