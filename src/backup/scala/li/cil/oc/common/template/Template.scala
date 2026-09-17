package li.cil.oc.common.template

import li.cil.oc.Constants
import li.cil.oc.Localization
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.items.IItemHandler
import org.apache.commons.lang3.tuple

import scala.collection.mutable

/**
 * 装配模板的公共基类（对应 1.7.10 的 `common.template.Template`）。
 *
 * 1.21.1 迁移要点：
 *  - 物品栏参数由 `net.minecraft.inventory.IInventory` 改为
 *    `net.neoforged.neoforge.items.IItemHandler`：
 *    `getSizeInventory` → `getSlots`；
 *  - `IItemHandler#getStackInSlot` 永远返回非 `null`，空槽位用 `ItemStack.EMPTY`
 *    表示（而不是 1.7.10 的 `null`），因此统一通过 [[stackAt]] 归一化成
 *    「空槽位返回 `null`」，以保留原代码里 `Option(getStackInSlot(...))` 的语义；
 *  - `Constants.ItemName` / `Constants.BlockName` 常量名不变。
 */
abstract class Template {
  protected val suggestedComponents = Array(
    "BIOS" -> hasComponent(Constants.ItemName.EEPROM) _,
    "Screen" -> hasComponent(Constants.BlockName.ScreenTier1) _,
    "Keyboard" -> hasComponent(Constants.BlockName.Keyboard) _,
    "GraphicsCard" -> ((inventory: IItemHandler) => Array(
      Constants.ItemName.APUCreative,
      Constants.ItemName.APUTier1,
      Constants.ItemName.APUTier2,
      Constants.ItemName.GraphicsCardTier1,
      Constants.ItemName.GraphicsCardTier2,
      Constants.ItemName.GraphicsCardTier3).
      exists(name => hasComponent(name)(inventory))),
    "Inventory" -> hasInventory _,
    "OS" -> hasFileSystem _)

  protected def hostClass: Class[_ <: api.network.EnvironmentHost]

  /** 校验结果：`Array(valid: Boolean, progress: Component, warnings: Array[Component])`。 */
  protected def validateComputer(inventory: IItemHandler): Array[AnyRef] = {
    val hasCase = caseTier(inventory) != Tier.None
    val hasCPU = this.hasCPU(inventory)
    val hasRAM = this.hasRAM(inventory)
    val requiresRAM = this.requiresRAM(inventory)
    val complexity = this.complexity(inventory)
    val maxComplexity = this.maxComplexity(inventory)

    val valid = hasCase && hasCPU && (hasRAM || !requiresRAM) && complexity <= maxComplexity

    val progress =
      if (!hasCPU) Localization.Assembler.InsertCPU
      else if (!hasRAM && requiresRAM) Localization.Assembler.InsertRAM
      else Localization.Assembler.Complexity(complexity, maxComplexity)

    val warnings = mutable.ArrayBuffer.empty[Component]
    for ((name, check) <- suggestedComponents) {
      if (!check(inventory)) {
        warnings += Localization.Assembler.Warning(name)
      }
    }
    if (warnings.nonEmpty) {
      warnings.prepend(Localization.Assembler.Warnings)
    }

    Array(valid: java.lang.Boolean, progress, warnings.toArray)
  }

  /**
   * 读取槽位内容；空槽位 / 越界返回 `null`。
   *
   * `IItemHandler` 的空槽位是 `ItemStack.EMPTY`，而模板层（以及最初由 1.7.10 抄过来的
   * 判定逻辑）到处都在用 `stack != null` 判断「有没有东西」，所以这里做一次归一化。
   */
  protected def stackAt(inventory: IItemHandler, slot: Int): ItemStack = {
    if (inventory == null || slot < 0 || slot >= inventory.getSlots) return null
    val stack = inventory.getStackInSlot(slot)
    if (stack == null || stack.isEmpty) null else stack
  }

  /** 遍历物品栏里的全部槽位（跳过空槽位）。 */
  protected def stacks(inventory: IItemHandler): Seq[ItemStack] =
    (0 until inventory.getSlots).flatMap(slot => Option(stackAt(inventory, slot)))

  protected def exists(inventory: IItemHandler, p: ItemStack => Boolean) =
    stacks(inventory).exists(p)

  protected def hasCPU(inventory: IItemHandler) = exists(inventory, api.Driver.driverFor(_, hostClass) match {
    case _: api.driver.item.Processor => true
    case _ => false
  })

  protected def hasRAM(inventory: IItemHandler) = exists(inventory, api.Driver.driverFor(_, hostClass) match {
    case _: api.driver.item.Memory => true
    case _ => false
  })

  protected def requiresRAM(inventory: IItemHandler) = !stacks(inventory).
    exists(stack => api.Driver.driverFor(stack, hostClass) match {
      case driver: api.driver.item.Processor =>
        val architecture = driver.architecture(stack)
        architecture != null && architecture.getAnnotation(classOf[api.machine.Architecture.NoMemoryRequirements]) != null
      case _ => false
    })

  protected def hasComponent(name: String)(inventory: IItemHandler) = exists(inventory, stack => Option(api.Items.get(stack)) match {
    case Some(descriptor) => descriptor.name == name
    case _ => false
  })

  protected def hasInventory(inventory: IItemHandler) = exists(inventory, api.Driver.driverFor(_, hostClass) match {
    case _: api.driver.item.Inventory => true
    case _ => false
  })

  protected def hasFileSystem(inventory: IItemHandler) = exists(inventory, stack => Option(api.Driver.driverFor(stack, hostClass)) match {
    case Some(driver) => driver.slot(stack) == Slot.Floppy || driver.slot(stack) == Slot.HDD
    case _ => false
  })

  protected def complexity(inventory: IItemHandler) = {
    var acc = 0
    for (slot <- 1 until inventory.getSlots) {
      val stack = stackAt(inventory, slot)
      acc += (Option(api.Driver.driverFor(stack, hostClass)) match {
        case Some(driver: api.driver.item.Processor) => 0 // CPUs are exempt, since they control the limit.
        case Some(driver: api.driver.item.Container) => (1 + driver.tier(stack)) * 2
        case Some(driver) if driver.slot(stack) != Slot.EEPROM => 1 + driver.tier(stack)
        case _ => 0
      })
    }
    acc
  }

  protected def maxComplexity(inventory: IItemHandler) = {
    val caseTier = this.caseTier(inventory)
    val cpuTier = (0 until inventory.getSlots).foldRight(0)((slot, acc) => {
      val stack = stackAt(inventory, slot)
      acc + (api.Driver.driverFor(stack, hostClass) match {
        case processor: api.driver.item.Processor => processor.tier(stack)
        case _ => 0
      })
    })
    if (caseTier >= Tier.One && cpuTier >= Tier.One) {
      Settings.deviceComplexityByTier(caseTier) - (math.min(2, caseTier) - cpuTier) * 6
    }
    else 0
  }

  protected def caseTier(inventory: IItemHandler): Int

  protected def toPair(t: (String, Int)): tuple.Pair[String, java.lang.Integer] =
    if (t == null) null
    else tuple.Pair.of(t._1, t._2)
}
