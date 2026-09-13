package li.cil.oc.common.init

import java.util.concurrent.Callable
import java.util.function.Supplier

import li.cil.oc.OpenComputersNeo
import li.cil.oc.Settings
import li.cil.oc.api.detail.ItemAPI
import li.cil.oc.api.detail.ItemInfo
import li.cil.oc.api.fs.FileSystem
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.{BlockItem, CreativeModeTab, Item, ItemStack}
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.{BlockEntity, BlockEntityType}
import net.neoforged.bus.api.{EventPriority, IEventBus}
import net.neoforged.neoforge.event.{BlockEntityTypeAddBlocksEvent, BuildCreativeModeTabContentsEvent}
import net.neoforged.neoforge.registries.{DeferredBlock, DeferredHolder, DeferredItem, DeferredRegister}

import scala.collection.mutable

/**
 * 1.21.1 风格的注册层，替代原版的 `Blocks.scala` / `Items.scala`。
 *
 * 与 1.7.10 的核心差异：
 *  - 1.7.10 用「一个物品 + damage 值」表示多个子类型（`Delegator` / `Delegate` 机制）。
 *    1.21.1 改为**每个子类型注册一个独立物品**，因此这里按名字逐个登记；
 *    [[li.cil.oc.Constants.ItemName]] / [[li.cil.oc.Constants.BlockName]] 的常量名保持不变
 *    （Lua 侧与文档依赖这些名字）。
 *  - 名称 → [[ItemInfo]] 的映射由本对象维护，等价于原 `Items.get(name)` /
 *    `api.Items.get(name)`，后续代码可继续沿用这两个查询入口。
 *
 * 使用方式：
 * {{{
 *   // 1) 在主类构造期（mod 构造期）调用一次：
 *   Registry.init(modBus)
 *
 *   // 2) 注册物品要等注册表事件之后才能调用 holder.value()，因此只把「工厂」
 *   //    交给本层，由本层负责延迟求值。分级物品请在伴生对象里提供工厂：
 *   Registry.registerItem(Constants.ItemName.Wrench, () => new li.cil.oc.common.item.Wrench())
 *   Registry.registerItem(Constants.ItemName.CPUTier1, () => li.cil.oc.common.item.CPU.tier(0))
 *
 *   // 3) 查询：
 *   val info = Registry.get(Constants.ItemName.Wrench)     // ItemInfo
 *   val item = Registry.getItem(Constants.ItemName.Wrench) // Item
 *   val stack = Registry.createItemStack(Constants.ItemName.Wrench)
 * }}}
 *
 * 注意：本对象实现了 [[li.cil.oc.api.detail.ItemAPI]]，需要在骨架自举阶段被赋给
 * `li.cil.oc.api.API.items`，才能让 `li.cil.oc.api.Items.get(...)` 生效
 * （见 [[install]]；接线由 `li.cil.oc.OpenComputers` 负责，该类尚未移植）。
 *
 * 待办：[[Items.init]] 需要等 `common/item` 包下的全部 scala 文件 移植完成后补全。
 * 待办：[[Blocks.init]] 需要等 `common/block` 包下的全部 scala 文件 移植完成后补全。
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

  // ----------------------------------------------------------------------- //
  // 状态
  // ----------------------------------------------------------------------- //

  /** 名称 → 描述符。等价于原 `Items.descriptors`。 */
  private val descriptors = mutable.Map.empty[String, ItemInfo]

  /** 名称 → 物品延迟持有对象。 */
  private val itemHolders = mutable.Map.empty[String, DeferredHolder[Item, _ <: Item]]

  /** 名称 → 方块延迟持有对象。 */
  private val blockHolders = mutable.Map.empty[String, DeferredBlock[_ <: Block]]

  /** 名称 → 方块物品延迟持有对象。 */
  private val blockItemHolders = mutable.Map.empty[String, DeferredItem[_ <: Item]]

  /** 名称 → 方块实体类型延迟持有对象。 */
  private val blockEntityHolders = mutable.Map.empty[String, DeferredHolder[BlockEntityType[_], BlockEntityType[_]]]

  /** 方块实体名 → 需要绑定的方块名集合（用于 [[BlockEntityTypeAddBlocksEvent]]）。 */
  private val blockEntityBlocks = mutable.Map.empty[String, mutable.Set[String]]

  /** 创造模式标签页展示顺序（按注册顺序保持稳定）。 */
  private val creativeOrder = mutable.ArrayBuffer.empty[String]

  /** 方块物品不进入创造模式标签页的方块名集合。 */
  private val hiddenInCreativeTab = mutable.Set.empty[String]

  /** 名称别名，等价于原 `Items.aliases`。 */
  private val aliases = Map(
    "dataCard" -> li.cil.oc.Constants.ItemName.DataCardTier1,
    "wlanCard" -> li.cil.oc.Constants.ItemName.WirelessNetworkCardTier2)

  /** 注册过的战利品软盘 / EEPROM 堆叠，供创造模式标签页追加。 */
  private val registeredItems = mutable.ArrayBuffer.empty[ItemStack]

  private var initialized = false

  // ----------------------------------------------------------------------- //
  // 初始化
  // ----------------------------------------------------------------------- //

  /** 在 mod 构造期调用：把各 `DeferredRegister` 挂到 mod 事件总线，并接线创造模式标签页。 */
  def init(modBus: IEventBus): Unit = {
    if (initialized) return
    initialized = true

    items.register(modBus)
    blocks.register(modBus)
    blockEntities.register(modBus)
    menus.register(modBus)

    modBus.addListener(EventPriority.NORMAL, false, classOf[BuildCreativeModeTabContentsEvent],
      new java.util.function.Consumer[BuildCreativeModeTabContentsEvent] {
        override def accept(event: BuildCreativeModeTabContentsEvent): Unit = addCreativeTabEntries(event)
      })

    modBus.addListener(EventPriority.NORMAL, false, classOf[BlockEntityTypeAddBlocksEvent],
      new java.util.function.Consumer[BlockEntityTypeAddBlocksEvent] {
        override def accept(event: BlockEntityTypeAddBlocksEvent): Unit = addBlockEntityBlocks(event)
      })
  }

  /** 在 [[BlockEntityTypeAddBlocksEvent]] 中把方块补进对应的方块实体类型。 */
  private def addBlockEntityBlocks(event: BlockEntityTypeAddBlocksEvent): Unit = {
    for ((beName, blockNames) <- blockEntityBlocks) {
      val beType = getBlockEntityType(beName)
      if (beType != null) {
        val valid = blockNames.toSeq.map(getBlock).filter(_ != null)
        if (valid.nonEmpty) event.modify(beType, valid.toSeq: _*)
      }
    }
  }

  // ----------------------------------------------------------------------- //
  // 注册：物品
  // ----------------------------------------------------------------------- //

  /**
   * 注册一个独立物品。
   *
   * @param name     必须取 [[li.cil.oc.Constants.ItemName]] 中的常量（不改名），
   *                 同时用作注册名与 [[ItemInfo#name]]。
   * @param supplier 物品工厂，**注册表事件之后**才会被调用，因此可以在里面 new 物品。
   */
  def registerItem[T <: Item](name: String, supplier: Supplier[T]): DeferredItem[T] = {
    val holder = items.register(name, supplier)
    registerItemInfo(name, holder)
    holder
  }

  /** 注册一个已有实例的物品（延迟到注册表事件后再返回该实例）。 */
  def registerItem[T <: Item](name: String, instance: T): DeferredItem[T] = {
    val holder = items.register(name, new Supplier[T] {
      override def get(): T = instance
    })
    registerItemInfo(name, holder)
    holder
  }

  private def registerItemInfo[T <: Item](name: String, holder: DeferredItem[T]): Unit = {
    val info = new BaseItemInfo(name) {
      override def item(): Item = holder.value()

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
   * @param withBlockItem 是否注册同名 `BlockItem`（多方块内部方块可传 `false`）。
   */
  def registerBlock[T <: Block](name: String, supplier: Supplier[T], withBlockItem: Boolean): DeferredBlock[T] = {
    val holder = blocks.register(name, supplier)
    blockHolders += name -> holder
    descriptors += name -> new BaseItemInfo(name) {
      override def block(): Block = holder.value()

      override def item(): Item = blockItemHolders.get(name).map(_.value()).orNull

      override def createItemStack(size: Int): ItemStack = new ItemStack(holder.value(), size)
    }
    if (withBlockItem) registerBlockItem(name, holder, hidden = false)
    holder
  }

  /**
   * 为一个方块补注册 `BlockItem`。
   *
   * @param hidden 是否从创造模式标签页隐藏（例如机器人残留方块）。
   */
  def registerBlockItem[T <: Block](name: String, block: DeferredBlock[T], hidden: Boolean): DeferredItem[BlockItem] = {
    val holder = items.register(name, new Supplier[BlockItem] {
      override def get(): BlockItem = new BlockItem(block.value(), new Item.Properties())
    })
    blockItemHolders += name -> holder
    if (hidden) hiddenInCreativeTab += name else creativeOrder += name
    descriptors.get(name) match {
      case Some(base: BaseItemInfo) => base.attachBlockItem(holder)
      case _ =>
    }
    holder
  }

  /** 直接给一个已实例化的方块补注册 `BlockItem`（方块已有延迟持有对象时直接复用）。 */
  def registerBlockItem[T <: Block](name: String, block: T, hidden: Boolean): DeferredItem[BlockItem] = {
    val holder: DeferredBlock[T] = blockHolders.get(name) match {
      case Some(existing) => existing.asInstanceOf[DeferredBlock[T]]
      case _ =>
        val created = blocks.register(name, new Supplier[T] {
          override def get(): T = block
        })
        blockHolders += name -> created
        created
    }
    registerBlockItem(name, holder, hidden)
  }

  /** 把某个方块标记为「方块物品不进入创造模式标签页」。 */
  def hideBlockItemInCreativeTab(name: String): Unit = hiddenInCreativeTab += name

  // ----------------------------------------------------------------------- //
  // 注册：方块实体 / 菜单
  // ----------------------------------------------------------------------- //

  /**
   * 注册方块实体类型。
   *
   * 1.21.1 的 `BlockEntityType.Builder.of` 需要方块参数，而方块与方块实体往往互相引用，
   * 因此这里推荐「先注册类型（不传方块），再用 [[bindBlockEntityBlock]] 绑定方块」，
   * 绑定的方块会在 [[BlockEntityTypeAddBlocksEvent]] 里补进类型。
   *
   * 注意：`DeferredHolder` 的第二个类型参数是不变的，因此返回值统一用
   * `BlockEntityType[_]`，不要试图还原具体类型。
   *
   * @param name 一般沿用 [[li.cil.oc.Constants.BlockName]] 中的常量名。
   */
  def registerBlockEntity[T <: BlockEntity](name: String, supplier: Supplier[BlockEntityType[T]])
    : DeferredHolder[BlockEntityType[_], BlockEntityType[_]] = {
    val holder = blockEntities.register(name, new Supplier[BlockEntityType[_]] {
      override def get(): BlockEntityType[_] = supplier.get()
    })
    blockEntityHolders += name -> holder
    holder
  }

  /** 用 `BlockEntityType.Builder` + 方块实体构造器注册（方块集合随后用 [[bindBlockEntityBlock]] 补）。 */
  def registerBlockEntity[T <: BlockEntity](name: String, factory: BlockEntityType.BlockEntitySupplier[T])
    : DeferredHolder[BlockEntityType[_], BlockEntityType[_]] =
    registerBlockEntity(name, new Supplier[BlockEntityType[T]] {
      override def get(): BlockEntityType[T] =
        BlockEntityType.Builder.of(factory, Array.empty[Block]: _*).build(null)
    })

  /** 把方块与方块实体类型关联起来（对应原 `GameRegistry.registerTileEntity`）。 */
  def bindBlockEntityBlock(blockName: String, blockEntityName: String): Unit =
    blockEntityBlocks.getOrElseUpdate(blockEntityName, mutable.Set.empty[String]) += blockName

  /** 注册菜单类型。 */
  def registerMenu[T <: MenuType[_]](name: String, supplier: Supplier[T])
    : DeferredHolder[MenuType[_], MenuType[_]] = {
    val holder = menus.register(name, new Supplier[MenuType[_]] {
      override def get(): MenuType[_] = supplier.get()
    })
    holder
  }

  // ----------------------------------------------------------------------- //
  // 查询
  // ----------------------------------------------------------------------- //

  override def get(name: String): ItemInfo =
    if (name == null) null else descriptors.getOrElse(name, null)

  override def get(stack: ItemStack): ItemInfo = {
    if (stack == null || stack.isEmpty) return null
    stack.getItem match {
      case blockItem: BlockItem =>
        // 方块物品优先按方块反查，保证 `block()` 与 `item()` 都有值。
        findBlockName(blockItem.getBlock) match {
          case Some(name) => get(name)
          case _ => findItemName(stack.getItem) match {
            case Some(name) => get(name)
            case _ => null
          }
        }
      case item => findItemName(item) match {
        case Some(name) => get(name)
        case _ => null
      }
    }
  }

  private def findItemName(item: Item): Option[String] =
    itemHolders.collectFirst { case (name, holder) if holder.isBound && (holder.value() eq item) => name }
      .orElse(blockItemHolders.collectFirst { case (name, holder) if holder.isBound && (holder.value() eq item) => name })

  private def findBlockName(block: Block): Option[String] =
    blockHolders.collectFirst { case (name, holder) if holder.isBound && (holder.value() eq block) => name }

  /** 名称 → 物品实例；不是物品时返回 `null`。 */
  def getItem(name: String): Item =
    itemHolders.get(name).orElse(blockItemHolders.get(name)).map(_.value()).orNull

  /** 名称 → 方块实例；不是方块时返回 `null`。 */
  def getBlock(name: String): Block = blockHolders.get(name).map(_.value()).orNull

  /** 名称 → 方块实体类型；不是方块实体时返回 `null`。 */
  def getBlockEntityType(name: String): BlockEntityType[_] =
    blockEntityHolders.get(name).map(_.value()).orNull

  /** 名称 → 物品延迟持有对象；不是物品时返回 `null`。 */
  def getItemHolder(name: String): DeferredHolder[Item, _ <: Item] =
    itemHolders.get(name).orElse(blockItemHolders.get(name)).orNull

  /** 名称 → 方块延迟持有对象；不是方块时返回 `null`。 */
  def getBlockHolder(name: String): DeferredBlock[_ <: Block] = blockHolders.getOrElse(name, null)

  /** 名称 → 方块物品延迟持有对象；没有时返回 `null`。 */
  def getBlockItemHolder(name: String): DeferredItem[_ <: Item] = blockItemHolders.getOrElse(name, null)

  def isRegistered(name: String): Boolean = descriptors.contains(name) || aliases.contains(name)

  /** 全部已注册描述符（按名称排序，含别名）。 */
  def all: Seq[ItemInfo] = descriptors.keys.toSeq.sorted.map(descriptors)

  /** 创造模式标签页展示顺序。 */
  def creativeTabEntries: Seq[String] = creativeOrder.toSeq

  /** 创建 1 个该名称物品的堆叠；未注册时返回 `null`。 */
  def createItemStack(name: String): ItemStack = createItemStack(name, 1)

  /** 创建 `amount` 个该名称物品的堆叠；未注册时返回 `null`。 */
  def createItemStack(name: String, amount: Int): ItemStack = {
    val info = get(name)
    if (info == null) null else info.createItemStack(amount)
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
  def getTag(stack: ItemStack): CompoundTag = li.cil.oc.util.ItemNBT.get(stack)

  /** 读取自定义 NBT，没有时创建空 tag 并写入。 */
  def getOrCreateTag(stack: ItemStack): CompoundTag = li.cil.oc.util.ItemNBT.getOrCreate(stack)

  /** 判断是否有自定义 NBT。 */
  def hasTag(stack: ItemStack): Boolean = li.cil.oc.util.ItemNBT.has(stack)

  /** 写入自定义 NBT。 */
  def setTag(stack: ItemStack, tag: CompoundTag): Unit = li.cil.oc.util.ItemNBT.set(stack, tag)

  // ----------------------------------------------------------------------- //
  // 创造模式标签页
  // ----------------------------------------------------------------------- //

  /**
   * 在 [[BuildCreativeModeTabContentsEvent]] 中把已注册物品填入 OC 标签页。
   * 空标签页会被游戏隐藏，因此这一步是标签页可见的关键。
   */
  def addCreativeTabEntries(event: BuildCreativeModeTabContentsEvent): Unit = {
    if (event.getTab ne li.cil.oc.api.CreativeTab.instance()) return
    import net.minecraft.world.item.CreativeModeTab.TabVisibility
    for (name <- creativeOrder if !hiddenInCreativeTab.contains(name)) {
      val stack = createItemStack(name, 1)
      if (stack != null && !stack.isEmpty) event.accept(stack, TabVisibility.PARENT_AND_SEARCH_TABS)
    }
    // 战利品软盘 / EEPROM 之类的额外堆叠（原 `Items.init` 里的 `additionalItems`）。
    for (stack <- registeredItems if stack != null && !stack.isEmpty) {
      event.accept(stack, TabVisibility.PARENT_AND_SEARCH_TABS)
    }
  }

  // ----------------------------------------------------------------------- //
  // api.Items 接线
  // ----------------------------------------------------------------------- //

  /** 把本对象注册成 API 的物品查询入口（等价于原 `API.items = Items`）。 */
  def install(): Unit = li.cil.oc.api.API.items = this

  // ----------------------------------------------------------------------- //
  // 原 `Items.scala` 的其余 API 表面
  // ----------------------------------------------------------------------- //

  @Deprecated
  override def registerFloppy(name: String, color: Int, factory: Callable[FileSystem]): ItemStack =
    registerFloppy(name, color, factory, doRecipeCycling = false)

  override def registerFloppy(name: String, color: Int, factory: Callable[FileSystem], doRecipeCycling: Boolean): ItemStack = {
    // TODO(战利品磁盘): 依赖 li.cil.oc.common.Loot（尚未移植）与文件系统实现（阶段 5）。
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

  /** 等价于原 `Items.safeGetStack`。 */
  def safeGetStack(name: String): ItemStack = {
    val info = get(name)
    if (info == null) null else info.createItemStack(1)
  }

  // ----------------------------------------------------------------------- //
  // 描述符实现
  // ----------------------------------------------------------------------- //
  /**
   * [[ItemInfo]] 的通用实现。
   *
   * `name` 走**构造参数**而非抽象成员：Scala 的匿名子类会先跑父类构造器，
   * 若把 `name` 写成抽象 `def` 再在匿名类里赋值，父类侧可能读到 `null`。
   */
  private abstract class BaseItemInfo(name: String) extends ItemInfo {
    private var blockItemRef: DeferredHolder[Item, _ <: Item] = null

    private[init] def attachBlockItem(holder: DeferredHolder[Item, _ <: Item]): Unit = blockItemRef = holder

    override def name(): String = name

    override def block(): Block = null

    override def item(): Item = if (blockItemRef == null) null else blockItemRef.value()

    override def createItemStack(size: Int): ItemStack = null

    override def toString: String = s"ItemInfo($name)"
  }

  /** 注册入口：物品部分（待 `common/item` 移植完成后补全）。 */
  object Items {
    /** 由 [[li.cil.oc.OpenComputers]] 在初始化阶段调用，注册全部独立物品。 */
    def init(): Unit = initItems()

    /** 注册全部独立物品。TODO: 等 `li.cil.oc.common.item.*` 移植完成后逐个补上。 */
    def initItems(): Unit = {
      // 每个 Constants.ItemName.* 对应一个独立物品，注册形态示例：
      //
      //   registerItem(Constants.ItemName.Wrench, () => new li.cil.oc.common.item.Wrench())
      //   registerItem(Constants.ItemName.CPUTier1, () => li.cil.oc.common.item.CPU.tier(0))
      //
      // 分级物品建议在物品类的伴生对象里提供 `tier(t: Int): Item` 工厂，
      // 由 `Item.Properties` + `tier` 构造，这样 unlocalizedName 会自动带上等级后缀
      // （与语言文件键 `item.oc.<类名><tier>.name` 一致）。
      //
      // 注册完成后调用 `Registry.registerBuiltinAliases()`。
      registerBuiltinAliases()
    }
  }

  /** 注册入口：方块部分（待 `common/block` 移植完成后补全）。 */
  object Blocks {
    /** 由 [[li.cil.oc.OpenComputers]] 在初始化阶段调用，注册全部方块与方块实体类型。 */
    def init(): Unit = initBlocks()

    /** 注册全部方块与方块实体类型。TODO: 等 `li.cil.oc.common.block` 移植完成后补上。 */
    def initBlocks(): Unit = {
      // 注册形态示例：
      //
      //   registerBlock(Constants.BlockName.ScreenTier1, () => new li.cil.oc.common.block.Screen(new BlockBehaviour.Properties()))
      //   registerBlockEntity(Constants.BlockName.ScreenTier1, () => BlockEntityType.Builder
      //     .of((pos, state) => new li.cil.oc.common.tileentity.Screen(pos, state), Array.empty[Block]: _*).build(null))
      //   bindBlockEntityBlock(Constants.BlockName.ScreenTier1, Constants.BlockName.ScreenTier1)
    }
  }
}
