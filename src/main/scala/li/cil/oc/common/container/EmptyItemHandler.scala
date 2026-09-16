package li.cil.oc.common.container

import li.cil.oc.common.inventory.Inventory
import net.minecraft.world.item.ItemStack

/**
 * 只读的空物品栏（所有槽位都是空的，且拒绝任何写入），但**带有显示名**。
 *
 * ==用途==
 * 客户端重建「宿主物品栏还在、但内容无法还原」的容器时用它兜底。
 * 目前唯一的用户是 [[MenuTypes.Tablet]]：平板电脑的内部物品栏由
 * `common/item/Tablet.scala` 的 `TabletData` 描述，而把 `TabletData` 包成
 * `IItemHandler` 的 `TabletCaseInventory` 尚未移植（见 `MenuTypes` 的 TODO）。
 *
 * 有了这个兜底，平板界面至少**能打开**（槽位是空的、`getInventoryName` 有值），
 * 而不是让整个 `MenuType` 工厂返回 `null` 让原版在打开界面时抛异常。
 * 等 `TabletCaseInventory` 移植完成后，把工厂换成真实物品栏即可。
 *
 * 选择继承 [[li.cil.oc.common.inventory.Inventory]]（而不是直接实现 `IItemHandler`）
 * 是为了同时拿到 `getInventoryName` —— OC 的界面统一用它做标题，
 * 纯 `IItemHandler` 没有名字这个概念。
 */
class EmptyItemHandler(name: String, slotCount: Int) extends Inventory {
  override def items: Array[Option[ItemStack]] = Array.fill(math.max(0, slotCount))(None)

  override protected def inventoryName: String = name

  /** 幽灵物品栏：槽位只表示「能放什么」，容量为 0。 */
  override def getSlotLimit(slot: Int): Int = 0

  override def isItemValid(slot: Int, stack: ItemStack): Boolean = false

  /** 一次性禁用全部写入，避免容器层把这层兜底写坏。 */
  override def insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack = stack

  /**
   * [[li.cil.oc.common.inventory.SimpleInventory]] 的抽象成员：本物品栏不持有任何真实数据
   * （`items` 每次调用都新建空数组），因此没有需要存盘的东西。
   */
  override def markDirty(): Unit = {}
}

object EmptyItemHandler {
  /** 平板电脑的内部物品栏槽位数（与 `TabletData.items` 的长度一致）。 */
  final val TabletSlots = 32
}
