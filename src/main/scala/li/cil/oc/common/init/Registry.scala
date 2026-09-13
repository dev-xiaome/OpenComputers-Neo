package li.cil.oc.common.init

import java.util.concurrent.Callable
import java.util.function.Supplier

import li.cil.oc.OpenComputersNeo
import li.cil.oc.Settings
import li.cil.oc.api.detail.ItemAPI
import li.cil.oc.api.detail.ItemInfo
import li.cil.oc.api.fs.FileSystem
import li.cil.oc.common.Tier
import net.minecraft.core.Registry
import net.minecraft.core.registries.{Registries, RegistrySynchronization}
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.{BlockItem, Item, ItemStack}
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.{BlockEntity, BlockEntityType}
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.{DeferredBlock, DeferredHolder, DeferredItem, DeferredRegister}

import scala.collection.mutable

/**
 * 1.21.1 风格的注册层，替代原版的 `Blocks.scala` / `Items.scala`。
 *
 * 设计要点（与 1.7.10 的差异）：
 *  - 1.7.10 用「一个物品 + damage 值」表示多个子类型（`Delegator`/`Delegate` 机制）。
 *    1.21.1 改为**每个子类型注册一个独立物品**，因此这里按名字逐个登记，
 *    `Constants.ItemName.*` / `Constants.BlockName.*` 的常量名保持不变（Lua 侧与文档依赖）。
 *  - 名称 → [[ItemInfo]] 的映射由本对象维护，等价于原 `Items.get(name)` /
 *    `api.Items.get(name)`，因此后续代码可以继续沿用这两个查询入口。
 *  - 注册（`DeferredRegister`）本身必须在 mod 构造期完成，因此
 *    [[li.cil.oc.OpenComputersNeo]] 构造时会调用 [[init]]。
 *
 * 注意：本对象实现了 [[li.cil.oc.api.detail.ItemAPI]]，需要在骨架的自举阶段
 * 被赋值给 `li.cil.oc.api.API.items` 才能让 `api.Items.get(...)` 生效。
 * 具体接线由 `li.cil.oc.OpenComputers`（骨架，尚未移植）负责；
 * 在它完成之前，请直接使用 [[get]] / [[getItem]] 等方法。
 *
 * TODO(注册·物品)：所有独立物品的注册入口 [[Items.init]] 需要等物品类移植完成后补全。
 * TODO(注册·方块)：所有方块的注册入口 [[Blocks.init]] 需要等方块类移植完成后补全。
 */
object Registry extends ItemAPI {

  // ----------------------------------------------------------------------- //
  // 注册器
  // ----------------------------------------------------------------------- //

  final val items: DeferredRegister.Items = DeferredRegister.createItems(OpenComputersNeo.MODID)

  final val blocks: DeferredRegister.Blocks = DeferredRegister.createBlocks(OpenComputersNeo.MODID)

  final val blockEntities: DeferredRegister[BlockEntityType[_]] =
    DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, OpenComputersNeo.MODID)

  final val menus: DeferredRegister[MenuType[_]] =
    DeferredRegister.create(Registries.MENU, OpenComputersNeo.MODID)

  /**
   * 该方块对应的方块物品在 1.7.10 里没有（例如机器人残留、机器人代理、多方块内部方块）。
   * 1.21.1 的方块仍然需要一个 `BlockItem` 才能被玩家放置/拾取，
   * 只是不进入创造模式标签页；这里用该集合表示「注册方块物品但不展示」。
   */
  private val hiddenBlockItems = mutable.Set.empty[String]

  /** 名称 → 描述符。等价于原 `Items.descriptors`。 */
  private val descriptors = mutable.Map.empty[String, ItemInfo]

  /** 物品/方块实例 → 名称。等价于原 `Items.names`，用于 `get(stack)` 反查。 */
  private val names = mutable.Map.empty[Any, String]

  /** 名称 → 延迟持有对象（物品）。 */
  private val itemHolders = mutable.Map.empty[String, DeferredHolder[Item, _ <: Item]]

  /** 名称 → 延迟持有对象（方块）。 */
  private val blockHolders = mutable.Map.empty[String, DeferredBlock[_ <: Block]]

  /** 名称 → 延迟持有对象（方块物品）。 */
  private val blockItemHolders = mutable.Map.empty[String, DeferredItem[_ <: Item]]

  /** 名称 → 延迟持有对象（方块实体类型）。 */
  private val blockEntityHolders = mutable.Map.empty[String, DeferredHolder[BlockEntityType[_], _ <: BlockEntityType[_]]]

  /** 创造模式标签页的展示顺序，按注册顺序保持稳定。 */
  private val creativeOrder = mutable.ArrayBuffer.empty[String]

  private val aliases = Map(
    "dataCard" -> li.cil.oc.Constants.ItemName.DataCardTier1,
    "wlanCard" -> li.cil.oc.Constants.ItemName.WirelessNetworkCardTier2
  )

  private var initialized = false

  // ----------------------------------------------------------------------- //
  // 初始化
  // ----------------------------------------------------------------------- //

  /** 在 mod 构造期调用；把四个 `DeferredRegister` 挂到 mod 事件总线上。 */
  def init(modBus: IEventBus): Unit = {
    if (initialized) return
    initialized = true

    items.register(modBus)
    blocks.register(modBus)
    blockEntities.register(modBus)
    menus.register(modBus)

    // 延迟到通用初始化阶段再补全方块实体的合法方块集合（见 patchBlockEntities）。
    modBus.addListener(new java.util.function.Consumer[net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent] {
      override def accept(event: net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent): Unit =
        event.enqueueWork(new Runnable {
          override def run(): Unit = patchBlockEntities()
        })
    })
  }

  /**
   * 1.21.1 起 `BlockEntityType` 的「合法方块集合」不再能通过 `Builder` 一次写死
   * （`Builder.of` 仍接受可变参数，但方块类型注册时其方块往往还没注册完成），
   * 这里统一在 `FMLCommonSetupEvent` 里把每个类型对应的方块补进注册表。
   */
  private def patchBlockEntities(): Unit = {
    for ((name, holder) <- blockEntityHolders) {
      blockHolders.get(name) match {
        case Some(block) =>
          val valid = Registry.get(block).asInstanceOf[mutable.Set[Block]]
          valid += block.value()
        case _ =>
      }
    }
  }

  // ----------------------------------------------------------------------- //
  // 注册：物品
  // ----------------------------------------------------------------------- //

  /**
   * 注册一个独立物品。
   *
   * `name` 必须取 [[li.cil.oc.Constants.ItemName]] 中的常量（不改名），
   * 它同时用作注册名与 [[ItemInfo.name]]。
   */
  def registerItem[T <: Item](name: String, supplier: Supplier[T]): DeferredItem[T] = {
    val holder = items.register(name, supplier)
    registerItemInfo(name, holder)
    holder
  }

  /** 注册一个物品并把既有实例登记进名称表（等价于原 `Items.registerItem(instance, id)`）。 */
  def registerItem[T <: Item](name: String, instance: T): DeferredItem[T] =
    registerItem(name, new Supplier[T] {
      override def get(): T = instance
    })

  private def registerItemInfo[T <: Item](name: String, holder: DeferredHolder[Item, T]): Unit = {
    val info = new BaseItemInfo(name) {
      override def item: Item = holder.value()

      override def createItemStack(size: Int): ItemStack = new ItemStack(holder.value(), size)
    }
    descriptors += name -> info
    itemHolders += name -> holder
    creativeOrder += name
  }

  // ----------------------------------------------------------------------- //
  // 注册：方块（含方块物品）
  // ----------------------------------------------------------------------- //

  /** 注册方块，并默认注册同名 `BlockItem`。 */
  def registerBlock[T <: Block](name: String, supplier: Supplier[T]): DeferredBlock[T] =
    registerBlock(name, supplier, withBlockItem = true)

  /**
   * 注册方块。
   *
   * @param withBlockItem 是否注册同名 `BlockItem`（多方块内部方块可为 `false`）。
   */
  def registerBlock[T <: Block](name: String, supplier: Supplier[T], withBlockItem: Boolean): DeferredBlock[T] = {
    val holder = blocks.register(name, supplier)
    blockHolders += name -> holder
    descriptors += name -> new BaseItemInfo(name) {
      override def block: Block = holder.value()

      override def item: Item = blockItemHolders.get(name).map(_.value()).orNull

      override def createItemStack(size: Int): ItemStack = new ItemStack(holder.value(), size)
    }
    names += holder.value() -> name
    if (withBlockItem) {
      registerBlockItem(name, holder.value(), hidden = false)
    }
    holder
  }

  /**
   * 为一个方块补注册 `BlockItem`。
   *
   * @param hidden 是否从创造模式标签页隐藏（例如机器人残留方块）。
   */
  def registerBlockItem(name: String, block: Block, hidden: Boolean): DeferredItem[BlockItem] = {
    val holder = items.register(name, new Supplier[BlockItem] {
      override def get(): BlockItem = new BlockItem(block, new Item.Properties())
    })
    blockItemHolders += name -> holder
    names += holder.value() -> name
    if (hidden) hiddenBlockItems += name else creativeOrder += name
    val info = descriptors(name)
    info match {
      case base: BaseItemInfo => base.attachBlockItem(holder)
      case _ =>
    }
    holder
  }

  /** 把某个方块标记为「方块物品不进入创造模式标签页」。 */
  def hideBlockItemInCreativeTab(name: String): Unit = hiddenBlockItems += name

  // ----------------------------------------------------------------------- //
  // 注册：方块实体 / 菜单
  // ----------------------------------------------------------------------- //

  /** 注册方块实体类型。`name` 一般沿用 `Constants.BlockName.*`。 */
  def registerBlockEntity[T <: BlockEntity](name: String, supplier: Supplier[BlockEntityType[T]])
    : DeferredHolder[BlockEntityType[_], BlockEntityType[T]] = {
    val holder = blockEntities.register(name, new Supplier[BlockEntityType[_]] {
      override def get(): BlockEntityType[_] = supplier.get()
    })
    blockEntityHolders += name -> holder
    holder
  }

  /** 注册方块实体类型的便捷写法（方块稍后通过 [[bindBlockEntityBlock]] 绑定）。 */
  def registerBlockEntity[T <: BlockEntity](name: String,
                                            factory: BlockEntityType.BlockEntitySupplier[T]): DeferredHolder[BlockEntityType[_], BlockEntityType[T]] =
    registerBlockEntity(name, new Supplier[BlockEntityType[T]] {
      override def get(): BlockEntityType[T] = BlockEntityType.Builder.of(factory).build(null)
    })

  /** 把方块与方块实体类型关联起来（用于补 `BlockEntityType` 的合法方块集合）。 */
  def bindBlockEntityBlock(blockName: String, blockEntityName: String): Unit =
    blockEntitiesByBlock += blockEntityName -> blockName

  private val blockEntitiesByBlock = mutable.Map.empty[String, String]

  /** 注册菜单类型。 */
  def registerMenu[T <: MenuType[_]](name: String, supplier: Supplier[T]): DeferredHolder[MenuType[_], T] =
    menus.register(name, new Supplier[MenuType[_]] {
      override def get(): T = supplier.get()
    })

  // ----------------------------------------------------------------------- //
  // 查询
  // ----------------------------------------------------------------------- //

  override def get(name: String): ItemInfo =
    if (name == null) null else descriptors.getOrElse(name, null)

  override def get(stack: ItemStack): ItemInfo = {
    if (stack == null || stack.isEmpty) return null
    val key = stack.getItem
    // 方块物品优先按方块反查，保证 `block()` 与 `item()` 都有值。
    blockItemKey(stack) match {
      case Some(name) => get(name)
      case _ => names.get(key) match {
        case Some(name) => get(name)
        case _ => null
      }
    }
  }

  /** 方块物品 → 方块名称。 */
  private def blockItemKey(stack: ItemStack): Option[String] = {
    stack.getItem match {
      case blockItem: BlockItem =>
        names.get(blockItem.getBlock).orElse(blockItemHolders.collectFirst {
          case (name, holder) if holder.isBound && (holder.value() eq blockItem) => name
        })
      case _ => None
    }
  }

  /** 名称 → 物品实例；不是物品时返回 `null`。 */
  def getItem(name: String): Item =
    itemHolders.get(name).orElse(blockItemHolders.get(name)).map(_.value()).orNull

  /** 名称 → 方块实例；不是方块时返回 `null`。 */
  def getBlock(name: String): Block = blockHolders.get(name).map(_.value()).orNull

  /** 名称 → 方块实体类型；不是方块实体时返回 `null`。 */
  def getBlockEntityType(name: String): BlockEntityType[_] =
    blockEntityHolders.get(name).map(_.value()).orNull

  /** 名称 → 延迟持有对象（物品）。 */
  def getItemHolder(name: String): DeferredHolder[Item, _ <: Item] =
    itemHolders.get(name).orElse(blockItemHolders.get(name)).orNull

  /** 名称 → 延迟持有对象（方块）。 */
  def getBlockHolder(name: String): DeferredBlock[_ <: Block] = blockHolders.getOrElse(name, null)

  /** 名称 → 延迟持有对象（方块物品）。 */
  def getBlockItemHolder(name: String): DeferredItem[_ <: Item] = blockItemHolders.getOrElse(name, null)

  def isRegistered(name: String): Boolean = descriptors.contains(name) || aliases.contains(name)

  /** 所有已注册的描述符（含别名）。 */
  def all: Seq[ItemInfo] = descriptors.keys.toSeq.sorted.map(descriptors)

  /** 创造模式标签页的展示顺序。 */
  def creativeTabEntries: Seq[String] = creativeOrder.toSeq

  /** 创建 1 个该名称物品的堆叠；未注册时返回 `null`。 */
  def createItemStack(name: String): ItemStack = createItemStack(name, 1)

  /** 创建 `size` 个该名称物品的堆叠；未注册时返回 `null`。 */
  def createItemStack(name: String, size: Int): ItemStack = {
    val info = get(name)
    if (info == null) null else info.createItemStack(size)
  }

  /** 注册名字别名（等价于原 `Items.aliases`）。 */
  def registerAlias(alias: String, name: String): Unit =
    if (descriptors.contains(name)) descriptors += alias -> descriptors(name)

  /** 应用内置别名表；在全部注册完成后调用。 */
  def registerBuiltinAliases(): Unit = for ((alias, name) <- aliases) registerAlias(alias, name)

  // ----------------------------------------------------------------------- //
  // 物品 NBT 便捷方法（统一数据组件 `opencomputers_neo:nbt`）
  // ----------------------------------------------------------------------- //

  /** 读取自定义 NBT；没有时返回 `null`。 */
  def getOrCreateTag(stack: ItemStack): CompoundTag = li.cil.oc.util.ItemNBT.getOrCreate(stack)

  /** 读取自定义 NBT；没有时返回 `null`。 */
  def getTag(stack: ItemStack): CompoundTag = li.cil.oc.util.ItemNBT.get(stack)

  /** 判断是否有自定义 NBT。 */
  def hasTag(stack: ItemStack): Boolean = li.cil.oc.util.ItemNBT.has(stack)

  /** 写入自定义 NBT。 */
  def setTag(stack: ItemStack, tag: CompoundTag): Unit = li.cil.oc.util.ItemNBT.set(stack, tag)

  // ----------------------------------------------------------------------- //
  // 创造模式标签页
  // ----------------------------------------------------------------------- //

  /** 在 `BuildCreativeModeTabContentsEvent` 中把已注册物品填入 OC 标签页。 */
  def addCreativeTabEntries(event: net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent): Unit = {
    if (event.getTab ne li.cil.oc.api.CreativeTab.instance()) return
    import net.minecraft.world.item.CreativeModeTab.TabVisibility
    for (name <- creativeOrder if !hiddenBlockItems.contains(name)) {
      val stack = createItemStack(name, 1)
      if (stack != null && !stack.isEmpty) event.accept(stack, TabVisibility.PARENT_AND_SEARCH_TABS)
    }
  }

  // ----------------------------------------------------------------------- //
  // 描述符实现
  // ----------------------------------------------------------------------- //

  /**
   * `ItemInfo` 的通用实现。
   *
   * `name` 走**构造参数**而非抽象成员：Scala 的抽象 `def` 会在子类构造器
   * 中后于父类构造器初始化，匿名子类里读它可能拿到 `null`。
   */
  private abstract class BaseItemInfo(val itemName: String) extends ItemInfo {
    private var blockItemRef: DeferredHolder[Item, _ <: Item] = null

    private[init] def attachBlockItem(holder: DeferredHolder[Item, _ <: Item]): Unit = blockItemRef = holder

    override def name(): String = itemName

    override def block(): Block = null

    override def item(): Item =
      if (blockItemRef != null) blockItemRef.value() else null

    override def createItemStack(size: Int): ItemStack = null

    override def toString: String = s"ItemInfo($itemName)"
  }

  // ----------------------------------------------------------------------- //
  // api.Items 接线辅助
  // ----------------------------------------------------------------------- //

  /** 把本对象注册成 API 的物品查询入口（等价于原 `API.items = Items`）。 */
  def install(): Unit = li.cil.oc.api.API.items = this

  // ----------------------------------------------------------------------- //
  // 兼容原 Items.scala 的其余 API 表面
  // ----------------------------------------------------------------------- //

  private val registeredItems = mutable.ArrayBuffer.empty[ItemStack]

  /** 注册过的战利品软盘 / EEPROM 堆叠，供创造模式标签页追加。 */
  def extraCreativeTabItems: Seq[ItemStack] = registeredItems.toSeq

  @Deprecated
  override def registerFloppy(name: String, color: Int, factory: Callable[FileSystem]): ItemStack =
    registerFloppy(name, color, factory, doRecipeCycling = false)

  override def registerFloppy(name: String, color: Int, factory: Callable[FileSystem], doRecipeCycling: Boolean): ItemStack = {
    // TODO(战利品磁盘): 依赖 li.cil.oc.common.Loot（尚未移植）与开放计算机文件系统（阶段 5）。
    val stack = createItemStack(li.cil.oc.Constants.ItemName.LootDisk, 1)
    if (stack != null) registeredItems += stack
    stack
  }

  override def registerEEPROM(name: String, code: Array[Byte], data: Array[Byte], readonly: Boolean): ItemStack = {
    val nbt = new CompoundTag()
    if (name != null) {
      nbt.putString(Settings.namespace + "label", name.trim.take(24))
    }
    if (code != null) {
      nbt.putByteArray(Settings.namespace + "eeprom", code.take(Settings.get.eepromSize))
    }
    if (data != null) {
      nbt.putByteArray(Settings.namespace + "userdata", data.take(Settings.get.eepromDataSize))
    }
    nbt.putBoolean(Settings.namespace + "readonly", readonly)

    val stackNbt = new CompoundTag()
    stackNbt.put(Settings.namespace + "data", nbt)

    val stack = createItemStack(li.cil.oc.Constants.ItemName.EEPROM, 1)
    if (stack != null) {
      setTag(stack, stackNbt)
      registeredItems += stack
    }
    stack
  }

  /** 与原 `Items.safeGetStack` 等价。 */
  def safeGetStack(name: String): ItemStack = {
    val info = get(name)
    if (info == null) null else info.createItemStack(1)
  }

  // ----------------------------------------------------------------------- //
  // 内部小工具
  // ----------------------------------------------------------------------- //

  private def rl(path: String): ResourceLocation = new ResourceLocation(OpenComputersNeo.MODID, path)

  private def tiers = Tier
}
