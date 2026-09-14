package li.cil.oc.common.init

import java.util.concurrent.Callable
import java.util.function.Supplier

import li.cil.oc.OpenComputersNeo
import li.cil.oc.Settings
import li.cil.oc.api.detail.ItemAPI
import li.cil.oc.api.detail.ItemInfo
import li.cil.oc.api.fs.FileSystem
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.crafting.{Recipe, RecipeSerializer, RecipeType}
import net.minecraft.world.item.{BlockItem, CreativeModeTab, Item, ItemStack}
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.{BlockEntity, BlockEntityType}
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.bus.api.{EventPriority, IEventBus}
import net.neoforged.neoforge.capabilities.{Capabilities, ICapabilityProvider, RegisterCapabilitiesEvent}
import net.neoforged.neoforge.event.{BlockEntityTypeAddBlocksEvent, BuildCreativeModeTabContentsEvent}
import net.neoforged.neoforge.fluids.capability.IFluidHandler
import net.neoforged.neoforge.items.IItemHandler
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

  /** 配方类型（`common/recipe` 里的自定义配方用；见 [[registerRecipeType]]）。 */
  final val recipeTypes: DeferredRegister[RecipeType[_]] =
    DeferredRegister.create(Registries.RECIPE_TYPE, OpenComputersNeo.MODID)

  /** 配方序列化器：1.21.1 的配方 JSON 里 `type` 字段指向的就是它（见 [[registerRecipeSerializer]]）。 */
  final val recipeSerializers: DeferredRegister[RecipeSerializer[_]] =
    DeferredRegister.create(Registries.RECIPE_SERIALIZER, OpenComputersNeo.MODID)

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

  /** mod 事件总线引用（[[init]] 里保存），方块能力注册需要用它挂 [[RegisterCapabilitiesEvent]]。 */
  private var modBusRef: IEventBus = null

  // ----------------------------------------------------------------------- //
  // 初始化
  // ----------------------------------------------------------------------- //

  /** 在 mod 构造期调用：把各 `DeferredRegister` 挂到 mod 事件总线，并接线创造模式标签页。 */
  def init(modBus: IEventBus): Unit = {
    if (initialized) return
    initialized = true
    modBusRef = modBus

    items.register(modBus)
    blocks.register(modBus)
    blockEntities.register(modBus)
    menus.register(modBus)
    recipeTypes.register(modBus)
    recipeSerializers.register(modBus)

    // 菜单必须在 mod 构造期登记（注册表事件之前），不能等第一次打开 GUI 时才懒加载。
    initMenus()

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
  // 注册名规范化
  // ----------------------------------------------------------------------- //

  /**
   * 把 [[li.cil.oc.Constants]] 里的常量名规范成合法的 1.21.1 注册名。
   *
   * 1.21.1 的注册名只允许 `[a-z0-9/._-]`：`DeferredRegister#register` 内部会调用
   * `ResourceLocation.fromNamespaceAndPath(namespace, name)`，名字里出现大写字母会直接抛
   * `ResourceLocationException`，而这一步发生在 **mod 构造期** → 启动即崩。
   *
   * 但 `Constants.ItemName` / `BlockName` 里有大量含大写的常量
   * （`dataCard1`、`graphicsCard1`、`redstoneCard1`、`wirelessNetworkCard1`、
   * `microcontrollerCase1`、`droneCase1`、`tabletCase1`、`cardContainer1`、
   * `chipDiamond`、`nuggetIron` …），而且这些常量名**一个都不能改**
   * （Lua 侧、配方、语言文件、`api.Items.get(name)` 都依赖它们）。
   *
   * 因此统一在这里做一次小写化：**对外注册用规范名**，
   * 而 `descriptors` / `itemHolders` / `blockHolders` / `blockItemHolders` /
   * `blockEntityHolders` / `creativeOrder` / `hiddenInCreativeTab` 等**查询用键仍保留常量名**，
   * 于是 `api.Items.get(Constants.ItemName.DataCardTier1)` 等调用完全不受影响。
   */
  private def registryName(name: String): String =
    if (name == null) null else name.toLowerCase(java.util.Locale.ROOT)

  // ----------------------------------------------------------------------- //
  // 注册：物品
  // ----------------------------------------------------------------------- //

  /**
   * 注册一个独立物品。
   *
   * @param name     必须取 [[li.cil.oc.Constants.ItemName]] 中的常量（不改名），
   *                 同时用作 [[ItemInfo#name]]；实际注册名会经 [[registryName]] 小写化。
   * @param supplier 物品工厂，**注册表事件之后**才会被调用，因此可以在里面 new 物品。
   */
  def registerItem[T <: Item](name: String, supplier: Supplier[T]): DeferredItem[T] = {
    val holder = items.register(registryName(name), supplier)
    registerItemInfo(name, holder)
    holder
  }

  /** 注册一个已有实例的物品（延迟到注册表事件后再返回该实例）。 */
  def registerItem[T <: Item](name: String, instance: T): DeferredItem[T] = {
    val holder = items.register(registryName(name), new Supplier[T] {
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
    val holder = blocks.register(registryName(name), supplier)
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
    val holder = items.register(registryName(name), new Supplier[BlockItem] {
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
        val created = blocks.register(registryName(name), new Supplier[T] {
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
    val holder = blockEntities.register(registryName(name), new Supplier[BlockEntityType[_]] {
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

  /**
   * 注册菜单类型。
   *
   * 1.7.10 没有 `MenuType`：容器由 `IGuiHandler` 直接 new，客户端靠 `guiId` 反射还原。
   * 1.21.1 的客户端必须拿到一个已注册的 `MenuType` 才能重建容器，因此这里统一登记
   * （`common/container/MenuTypes.scala` 里按容器类逐个调用本方法）。
   */
  def registerMenu[T <: MenuType[_]](name: String, supplier: Supplier[T])
    : DeferredHolder[MenuType[_], MenuType[_]] = {
    val holder = menus.register(registryName(name), new Supplier[MenuType[_]] {
      override def get(): MenuType[_] = supplier.get()
    })
    holder
  }

  /**
   * 登记 `common/container` 下全部 16 个容器的 `MenuType`。
   *
   * 委托给 [[li.cil.oc.common.container.MenuTypes]]（真正定义在 `common/container` 里，
   * 因为 `MenuType` 的工厂必须能 new 出那些容器类）。本方法由 [[init]] 在 mod 构造期调用。
   */
  def initMenus(): Unit = li.cil.oc.common.container.MenuTypes.register()

  // ----------------------------------------------------------------------- //
  // 注册：配方（RecipeType / RecipeSerializer）
  // ----------------------------------------------------------------------- //

  /**
   * 注册一个配方类型。
   *
   * 注意（1.21.1 的关键约束）：**合成台只按 `RecipeType.CRAFTING` 查配方**
   * （`CraftingMenu` 里写死了 `RecipeManager#getRecipeFor(RecipeType.CRAFTING, ...)`），
   * 所以 `common/recipe` 下的自定义配方都实现 `CraftingRecipe`，
   * `getType()` 返回 `RecipeType.CRAFTING`；这里登记的类型只是「配方族」的标识，
   * 供 `data/opencomputers_neo` 下 `recipe` 目录里 json 的编写者与后续工具使用。
   *
   * @param name 配方族名（会小写化），例如 `colorizer`
   */
  def registerRecipeType[T <: Recipe[_]](name: String)
    : DeferredHolder[RecipeType[_], RecipeType[_]] = {
    val holder = recipeTypes.register(registryName(name), new Supplier[RecipeType[_]] {
      override def get(): RecipeType[_] =
        RecipeType.simple(ResourceLocation.fromNamespaceAndPath(OpenComputersNeo.MODID, registryName(name)))
    })
    holder
  }

  /**
   * 注册配方序列化器。
   *
   * 1.21.1 的配方 JSON 用 `"type": "<namespace>:<path>"` 选出反序列化器，
   * 因此**自定义配方必须在这里登记**（配方类型仍可以是 `RecipeType.CRAFTING`）。
   *
   * @param name     注册名（会小写化），例如 `colorizer`
   * @param supplier 序列化器工厂（注册表事件之后才会被调用）
   */
  def registerRecipeSerializer[T <: Recipe[_]](name: String, supplier: Supplier[RecipeSerializer[T]])
    : DeferredHolder[RecipeSerializer[_], RecipeSerializer[_]] = {
    val holder = recipeSerializers.register(registryName(name), new Supplier[RecipeSerializer[_]] {
      override def get(): RecipeSerializer[_] = supplier.get()
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

  /**
   * 名称 → 方块实体类型；不是方块实体时返回 `null`。
   *
   * 查找时会额外尝试 [[registryName]] 规范化（全小写）后的名字：方块实体类型按
   * 「类型名 = 方块注册路径（小写）」登记，而调用方往往拿着 `Constants.BlockName` 的常量
   * （可能含大写，例如 `diskDrive`）来查，两者要能对上。
   */
  def getBlockEntityType(name: String): BlockEntityType[_] =
    if (name == null) null
    else blockEntityHolders.get(name).
      orElse(blockEntityHolders.get(registryName(name))).
      map(_.value()).orNull

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

  /** 注册入口：物品部分。 */
  object Items {
    /** 由 [[li.cil.oc.OpenComputers]] 在初始化阶段调用，注册全部独立物品。 */
    def init(): Unit = initItems()

    /**
     * 注册全部独立物品。
     *
     * 1.7.10 的 `common/init/Items.scala` 用一个 `Delegator` + damage 值注册 100 多个子类型；
     * 1.21.1 改为**逐个** `Constants.ItemName.*` 注册独立 `Item`，注册名即常量值
     * （名字一个都不能改：Lua 侧与语言文件依赖它们）。
     *
     * 语言文件键仍然是 `item.oc.<类名><tier>.name`，由
     * [[li.cil.oc.common.item.traits.SimpleItem#getDescriptionId]] 生成。
     */
    def initItems(): Unit = {
      import li.cil.oc.Constants.ItemName
      import li.cil.oc.common.item
      import li.cil.oc.common.Tier
      import li.cil.oc.util.Rarity

      /** 基础属性：单堆叠。 */
      def single(): Item.Properties = new Item.Properties().stacksTo(1)

      /** 按等级给品质（与 1.7.10 的 `Rarity.byTier` 一致）。 */
      def rarityFor(tier: Int): Item.Properties = new Item.Properties().rarity(Rarity.byTier(tier))

      /** 注册物品；物品用 [[li.cil.oc.common.item.traits.Delegate#showInItemList]] 控制是否进标签页。 */
      def reg[T <: Item](name: String, supplier: () => T): DeferredItem[T] = {
        val holder = registerItem(name, new Supplier[T] {
          override def get(): T = supplier()
        })
        holder.value() match {
          case delegate: item.traits.Delegate if !delegate.showInItemList =>
            hideBlockItemInCreativeTab(name)
          case _ =>
        }
        holder
      }

      def regInstance[T <: Item](name: String, instance: T): DeferredItem[T] = {
        val holder = registerItem(name, instance)
        instance match {
          case delegate: item.traits.Delegate if !delegate.showInItemList =>
            hideBlockItemInCreativeTab(name)
          case _ =>
        }
        holder
      }

      // ------------------------------------------------------------------ //
      // 材料 / 简单物品
      // ------------------------------------------------------------------ //

      reg(ItemName.IronNugget, () => new item.IronNugget(new Item.Properties()))
      reg(ItemName.CuttingWire, () => new item.CuttingWire(new Item.Properties()))
      reg(ItemName.Acid, () => new item.Acid(single()))
      reg(ItemName.Disk, () => new item.Disk(new Item.Properties()))
      reg(ItemName.ButtonGroup, () => new item.ButtonGroup(new Item.Properties()))
      reg(ItemName.ArrowKeys, () => new item.ArrowKeys(new Item.Properties()))
      reg(ItemName.NumPad, () => new item.NumPad(new Item.Properties()))
      reg(ItemName.Transistor, () => new item.Transistor(new Item.Properties()))
      reg(ItemName.Alu, () => new item.ALU(new Item.Properties()))
      reg(ItemName.ControlUnit, () => new item.ControlUnit(new Item.Properties()))
      reg(ItemName.RawCircuitBoard, () => new item.RawCircuitBoard(new Item.Properties()))
      reg(ItemName.CircuitBoard, () => new item.CircuitBoard(new Item.Properties()))
      reg(ItemName.PrintedCircuitBoard, () => new item.PrintedCircuitBoard(new Item.Properties()))
      reg(ItemName.Card, () => new item.CardBase(new Item.Properties()))
      reg(ItemName.Interweb, () => new item.Interweb(new Item.Properties()))
      reg(ItemName.DiamondChip, () => new item.DiamondChip(new Item.Properties()))
      reg(ItemName.Chamelium, () => new item.Chamelium(single()))
      reg(ItemName.InkCartridgeEmpty, () => new item.InkCartridgeEmpty(single()))
      reg(ItemName.InkCartridge, () => new item.InkCartridge(single()))
      reg(ItemName.TexturePicker, () => new item.TexturePicker(single()))
      reg(ItemName.Manual, () => new item.Manual(single()))
      reg(ItemName.Wrench, () => new item.Wrench(single()))
      reg(ItemName.Present, () => new item.Present(single()))
      reg(ItemName.EEPROM, () => new item.EEPROM(new Item.Properties()))
      reg(ItemName.Floppy, () => new item.FloppyDisk(single()))
      reg(ItemName.LootDisk, () => new item.FloppyDisk(single()))
      // TODO(战利品磁盘): `LootDisk` 的匿名子类在原版里 `showInItemList = false`，
      // 这里用同名类但不上标签页（见上面的 showInItemList 判定）。

      // ------------------------------------------------------------------ //
      // 分级组件
      // ------------------------------------------------------------------ //

      reg(ItemName.RAMTier1, () => item.Memory.tier(Tier.One))
      reg(ItemName.RAMTier2, () => item.Memory.tier(Tier.Two))
      reg(ItemName.RAMTier3, () => item.Memory.tier(Tier.Three))
      reg(ItemName.RAMTier4, () => item.Memory.tier(Tier.Four))
      reg(ItemName.RAMTier5, () => item.Memory.tier(Tier.Five))
      reg(ItemName.RAMTier6, () => item.Memory.tier(Tier.Six))

      reg(ItemName.CPUTier1, () => item.CPU.tier(Tier.One))
      reg(ItemName.CPUTier2, () => item.CPU.tier(Tier.Two))
      reg(ItemName.CPUTier3, () => item.CPU.tier(Tier.Three))

      reg(ItemName.APUTier1, () => item.APU.tier(Tier.One))
      reg(ItemName.APUTier2, () => item.APU.tier(Tier.Two))
      reg(ItemName.APUCreative, () => item.APU.tier(Tier.Four))

      reg(ItemName.ChipTier1, () => item.Microchip.tier(Tier.One))
      reg(ItemName.ChipTier2, () => item.Microchip.tier(Tier.Two))
      reg(ItemName.ChipTier3, () => item.Microchip.tier(Tier.Three))

      reg(ItemName.GraphicsCardTier1, () => item.GraphicsCard.tier(Tier.One))
      reg(ItemName.GraphicsCardTier2, () => item.GraphicsCard.tier(Tier.Two))
      reg(ItemName.GraphicsCardTier3, () => item.GraphicsCard.tier(Tier.Three))

      reg(ItemName.ComponentBusTier1, () => item.ComponentBus.tier(Tier.One))
      reg(ItemName.ComponentBusTier2, () => item.ComponentBus.tier(Tier.Two))
      reg(ItemName.ComponentBusTier3, () => item.ComponentBus.tier(Tier.Three))
      reg(ItemName.ComponentBusCreative, () => item.ComponentBus.tier(Tier.Four))

      reg(ItemName.DataCardTier1, () => new item.DataCard(single(), Tier.One))
      reg(ItemName.DataCardTier2, () => new item.DataCard(single(), Tier.Two))
      reg(ItemName.DataCardTier3, () => new item.DataCard(single(), Tier.Three))

      // ------------------------------------------------------------------ //
      // 存储（软盘 / 硬盘）
      // ------------------------------------------------------------------ //

      reg(ItemName.HDDTier1, () => new item.HardDiskDrive(single(), Tier.One))
      reg(ItemName.HDDTier2, () => new item.HardDiskDrive(single(), Tier.Two))
      reg(ItemName.HDDTier3, () => new item.HardDiskDrive(single(), Tier.Three))

      // ------------------------------------------------------------------ //
      // 卡
      // ------------------------------------------------------------------ //

      reg(ItemName.NetworkCard, () => new item.NetworkCard(single()))
      reg(ItemName.WirelessNetworkCardTier1, () => new item.WirelessNetworkCard(single(), Tier.One))
      reg(ItemName.WirelessNetworkCardTier2, () => new item.WirelessNetworkCard(single(), Tier.Two))
      reg(ItemName.RedstoneCardTier1, () => new item.RedstoneCard(single(), Tier.One))
      reg(ItemName.RedstoneCardTier2, () => new item.RedstoneCard(single(), Tier.Two))
      reg(ItemName.InternetCard, () => new item.InternetCard(single()))
      reg(ItemName.LinkedCard, () => new item.LinkedCard(single()))
      reg(ItemName.AbstractBusCard, () => new item.AbstractBusCard(single()))
      reg(ItemName.WorldSensorCard, () => new item.WorldSensorCard(single()))
      reg(ItemName.DebugCard, () => new item.DebugCard(single()))
      reg(ItemName.Debugger, () => new item.Debugger(single()))

      // ------------------------------------------------------------------ //
      // 外壳
      // ------------------------------------------------------------------ //

      reg(ItemName.MicrocontrollerCaseTier1, () => new item.MicrocontrollerCase(single(), Tier.One))
      reg(ItemName.MicrocontrollerCaseTier2, () => new item.MicrocontrollerCase(single(), Tier.Two))
      reg(ItemName.MicrocontrollerCaseCreative, () => new item.MicrocontrollerCase(single(), Tier.Four))
      reg(ItemName.DroneCaseTier1, () => new item.DroneCase(single(), Tier.One))
      reg(ItemName.DroneCaseTier2, () => new item.DroneCase(single(), Tier.Two))
      reg(ItemName.DroneCaseCreative, () => new item.DroneCase(single(), Tier.Four))
      reg(ItemName.TabletCaseTier1, () => new item.TabletCase(single(), Tier.One))
      reg(ItemName.TabletCaseTier2, () => new item.TabletCase(single(), Tier.Two))
      reg(ItemName.TabletCaseCreative, () => new item.TabletCase(single(), Tier.Four))

      // ------------------------------------------------------------------ //
      // 服务器 / 终端 / 平板 / 无人机
      // ------------------------------------------------------------------ //

      reg(ItemName.ServerTier1, () => item.Server.tier(Tier.One))
      reg(ItemName.ServerTier2, () => item.Server.tier(Tier.Two))
      reg(ItemName.ServerTier3, () => item.Server.tier(Tier.Three))
      reg(ItemName.ServerCreative, () => item.Server.tier(Tier.Four))
      reg(ItemName.Terminal, () => new item.Terminal(single()))
      reg(ItemName.TerminalServer, () => new item.TerminalServer(single()))
      reg(ItemName.DiskDriveMountable, () => new item.DiskDriveMountable(single()))
      reg(ItemName.Tablet, () => new item.Tablet(single()))
      reg(ItemName.Drone, () => new item.Drone(single()))

      // ------------------------------------------------------------------ //
      // 升级
      // ------------------------------------------------------------------ //

      reg(ItemName.SolarGeneratorUpgrade, () => new item.UpgradeSolarGenerator(single()))
      reg(ItemName.GeneratorUpgrade, () => new item.UpgradeGenerator(single()))
      reg(ItemName.SignUpgrade, () => new item.UpgradeSign(single()))
      reg(ItemName.NavigationUpgrade, () => new item.UpgradeNavigation(single()))
      reg(ItemName.PistonUpgrade, () => new item.UpgradePiston(single()))
      reg(ItemName.CraftingUpgrade, () => new item.UpgradeCrafting(single()))
      reg(ItemName.AngelUpgrade, () => new item.UpgradeAngel(single()))
      reg(ItemName.ExperienceUpgrade, () => new item.UpgradeExperience(single()))
      reg(ItemName.InventoryUpgrade, () => new item.UpgradeInventory(single()))
      reg(ItemName.InventoryControllerUpgrade, () => new item.UpgradeInventoryController(single()))
      reg(ItemName.ChunkloaderUpgrade, () => new item.UpgradeChunkloader(single()))
      reg(ItemName.TractorBeamUpgrade, () => new item.UpgradeTractorBeam(single()))
      reg(ItemName.LeashUpgrade, () => new item.UpgradeLeash(single()))
      reg(ItemName.TankUpgrade, () => new item.UpgradeTank(single()))
      reg(ItemName.TankControllerUpgrade, () => new item.UpgradeTankController(single()))
      reg(ItemName.TradingUpgrade, () => new item.UpgradeTrading(single()))
      reg(ItemName.MFU, () => new item.UpgradeMF(single()))

      reg(ItemName.BatteryUpgradeTier1, () => new item.UpgradeBattery(rarityFor(Tier.One), Tier.One))
      reg(ItemName.BatteryUpgradeTier2, () => new item.UpgradeBattery(rarityFor(Tier.Two), Tier.Two))
      reg(ItemName.BatteryUpgradeTier3, () => new item.UpgradeBattery(rarityFor(Tier.Three), Tier.Three))

      reg(ItemName.HoverUpgradeTier1, () => new item.UpgradeHover(single(), Tier.One))
      reg(ItemName.HoverUpgradeTier2, () => new item.UpgradeHover(single(), Tier.Two))

      reg(ItemName.DatabaseUpgradeTier1, () => item.UpgradeDatabase.tier(Tier.One))
      reg(ItemName.DatabaseUpgradeTier2, () => item.UpgradeDatabase.tier(Tier.Two))
      reg(ItemName.DatabaseUpgradeTier3, () => item.UpgradeDatabase.tier(Tier.Three))

      reg(ItemName.CardContainerTier1, () => new item.UpgradeContainerCard(single(), Tier.One))
      reg(ItemName.CardContainerTier2, () => new item.UpgradeContainerCard(single(), Tier.Two))
      reg(ItemName.CardContainerTier3, () => new item.UpgradeContainerCard(single(), Tier.Three))
      reg(ItemName.UpgradeContainerTier1, () => new item.UpgradeContainerUpgrade(single(), Tier.One))
      reg(ItemName.UpgradeContainerTier2, () => new item.UpgradeContainerUpgrade(single(), Tier.Two))
      reg(ItemName.UpgradeContainerTier3, () => new item.UpgradeContainerUpgrade(single(), Tier.Three))

      reg(ItemName.Nanomachines, () => new item.Nanomachines(
        single().rarity(net.minecraft.world.item.Rarity.UNCOMMON)))
      regInstance(ItemName.HoverBoots, new item.HoverBoots(item.HoverBoots.defaultProps()
        .rarity(net.minecraft.world.item.Rarity.UNCOMMON)))

      // ------------------------------------------------------------------ //
      // 由堆叠描述的伪物品（原 `Items.registerStack`）
      // ------------------------------------------------------------------ //

      registerStackItem(ItemName.LuaBios, size => {
        val code = new Array[Byte](4 * 1024)
        val stream = getClass.getResourceAsStream(Settings.scriptPath + "bios.lua")
        val count = if (stream == null) 0 else try stream.read(code) finally stream.close()
        val stack = registerEEPROM("EEPROM (Lua BIOS)", code.take(count), null, readonly = false)
        if (stack != null) stack.setCount(size)
        stack
      })

      registerStackItem(ItemName.OpenOS, size => createItemStack(ItemName.Floppy, size))

      // ------------------------------------------------------------------ //
      // 创造模式标签页里的预配置堆叠（原 `Items.init` 的 `additionalItems`）
      // ------------------------------------------------------------------ //

      // 这些条目没有独立 `Item`，因此不注册描述符，只作为额外堆叠追加到标签页。
      def addCreativeStack(factory: => ItemStack): Unit = {
        val stack = factory
        if (stack != null && !stack.isEmpty) registeredItems += stack
      }

      addCreativeStack(item.Drone.createConfiguredDrone())
      addCreativeStack(item.Microcontroller.createConfiguredMicrocontroller())
      // TODO(创造模式): 原版还有一个预配置机器人（`Items.createConfiguredRobot`），
      // 但机器人是方块（`BlockName.Robot` + `RobotData`），等 `common/block` 移植后补上。
      addCreativeStack(item.Tablet.createConfiguredTablet())
      addCreativeStack(item.HoverBoots.createChargedHoverBoots())

      registerBuiltinAliases()
    }

    /**
     * 注册一个「由固定堆叠描述」的条目（等价于原 `Items.registerStack`）。
     *
     * 用于 `openos` / `luaBios` 这类没有独立 `Item` 类的条目，以及创造模式标签页里的
     * 预配置堆叠（原版 `additionalItems`）：描述符返回预先构造好的堆叠的副本
     * （原版 `createItemStack` 也是返回不可变模板的 `copy`）。
     *
     * @param factory 惰性工厂：**注册表事件之后**（即 `createItemStack` 被调用时）
     *                才会求值，因此可以在里面访问 [[createItemStack]]。
     */
    private def registerStackItem(name: String, factory: Int => ItemStack): Unit = {
      val info = new BaseItemInfo(name) {
        override def item(): Item = {
          val template = factory(1)
          if (template == null || template.isEmpty) null else template.getItem
        }

        override def createItemStack(size: Int): ItemStack = factory(size)
      }
      descriptors += name -> info
      creativeOrder += name
    }

    /**
     * 登记一个「由固定堆叠描述」的条目（公开版，等价于原 `Items.registerStack(stack, name)`）。
     *
     * 战利品软盘（[[li.cil.oc.common.Loot.createLootDisk]]）用它把 `loot.properties` 里
     * 声明的每个磁盘登记成描述符，并追加到创造模式标签页。
     *
     * `name` 可能与 `initItems()` 里已登记的伪物品重名（例如 `openos` 既有普通软盘条目、
     * 又有战利品磁盘条目）：这里先摘掉旧的出现位置，再登记新条目，
     * 避免创造模式标签页里出现两份同名条目。
     *
     * @param name  描述符名（`api.Items.get(name)` / `createItemStack(name)` 用它查询）
     * @param stack 模板堆叠；描述符每次返回它的副本
     */
    def registerStack(name: String, stack: ItemStack): Unit = {
      if (stack == null || stack.isEmpty) return
      creativeOrder -= name
      registerStackItem(name, _ => stack.copy())
    }
  }

  /** 注册入口：方块部分。 */
  object Blocks {
    /** 由 [[li.cil.oc.OpenComputers]] 在初始化阶段调用，注册全部方块与方块实体类型。 */
    def init(): Unit = initBlocks()

    /** 小写化注册名：1.21.1 的 `ResourceLocation` 只允许 `[a-z0-9/._-]`。 */
    private def key(name: String): String = name.toLowerCase(java.util.Locale.ROOT)

    /**
     * 注册全部方块与方块实体类型。
     *
     * 与 1.7.10 的 `common/init/Blocks.scala` 一一对应：
     *
     *  - 1.7.10 的 `GameRegistry.registerTileEntity(classOf[T], name)` → [[registerBlockEntity]] +
     *    [[bindBlockEntityBlock]]；这里按「**一个方块一个方块实体类型**」登记，类型名 = 方块注册路径
     *    （小写），于是 `BlockEntityBase.typeOf(state.getBlock)` 能直接反查到类型；
     *  - 1.7.10 的 `Items.registerBlock(...)` / `Recipes.addBlock(...)`（注册 + 加配方）在 1.21.1 拆成
     *    [[registerBlock]]（含同名 `BlockItem`）与数据包配方；配方层尚未移植，
     *    TODO(common.recipe): 等 `common/recipe` 完成后补 `data/opencomputers_neo/recipe/` 下的 json 配方；
     *  - 等级方块（`new Case(Tier.One)` 等）在 1.7.10 是同一个方块的 metadata 子类型，
     *    1.21.1 改为**每个等级一个独立方块**，名字沿用 `Constants.BlockName.*`
     *    （`Tier.One = 0`、`Two = 1`、`Three = 2`、`Four = 3`，与原 metadata 一致）；
     *  - 原 `Items.registerBlock` 注册的「技术方块」只有 `robotAfterimage` 需要从创造模式标签页隐藏。
     */
    def initBlocks(): Unit = {
      // 方块物品的 tooltip：1.21.1 挂在 Item 上，这里用 ItemTooltipEvent 转发到
      // `SimpleBlockHooks#addInformation`（原 `ItemBlock#addInformation`）。
      li.cil.oc.common.block.BlockTooltipHandler.register()

      import li.cil.oc.Constants.BlockName
      import li.cil.oc.common.{Tier, block, tileentity}

      /** 有方块实体的方块名（用于末尾统一注册 NeoForge 能力）。 */
      val entities = mutable.ArrayBuffer.empty[String]

      /** 注册方块 + 同名的方块实体类型 + 双向绑定。 */
      def blockWithEntity[T <: Block](name: String)(blockSupplier: => T)
                                     (factory: (BlockPos, BlockState) => BlockEntity): Unit = {
        registerBlock(name, new Supplier[T] {
          override def get(): T = blockSupplier
        })
        val entityName = key(name)
        registerBlockEntity(entityName, new BlockEntityType.BlockEntitySupplier[BlockEntity] {
          override def create(pos: BlockPos, state: BlockState): BlockEntity = factory(pos, state)
        })
        bindBlockEntityBlock(name, entityName)
        entities += entityName
      }

      /** 注册没有方块实体的方块。 */
      def blockOnly[T <: Block](name: String)(supplier: => T): Unit =
        registerBlock(name, new Supplier[T] {
          override def get(): T = supplier
        })

      // ------------------------------------------------------------------ //
      // 方块（顺序与 1.7.10 的 `common/init/Blocks.scala` 保持一致）
      // ------------------------------------------------------------------ //

      blockWithEntity(BlockName.AccessPoint)(new block.AccessPoint())((pos, state) => new tileentity.AccessPoint(pos, state))
      blockWithEntity(BlockName.Adapter)(new block.Adapter())((pos, state) => new tileentity.Adapter(pos, state))
      blockWithEntity(BlockName.Assembler)(new block.Assembler())((pos, state) => new tileentity.Assembler(pos, state))
      blockWithEntity(BlockName.Cable)(new block.Cable())((pos, state) => new tileentity.Cable(pos, state))
      blockWithEntity(BlockName.Capacitor)(new block.Capacitor())((pos, state) => new tileentity.Capacitor(pos, state))
      blockWithEntity(BlockName.CarpetedCapacitor)(new block.CarpetedCapacitor())((pos, state) => new tileentity.CarpetedCapacitor(pos, state))
      blockWithEntity(BlockName.CaseTier1)(new block.Case(Tier.One))((pos, state) => new tileentity.Case(pos, state))
      blockWithEntity(BlockName.CaseTier2)(new block.Case(Tier.Two))((pos, state) => new tileentity.Case(pos, state))
      blockWithEntity(BlockName.CaseTier3)(new block.Case(Tier.Three))((pos, state) => new tileentity.Case(pos, state))
      // 创造模式机箱（原 `Case(Tier.Four)`，仅创造模式标签页可见）。
      blockWithEntity(BlockName.CaseCreative)(new block.Case(Tier.Four))((pos, state) => new tileentity.Case(pos, state))
      blockWithEntity(BlockName.Charger)(new block.Charger())((pos, state) => new tileentity.Charger(pos, state))
      blockWithEntity(BlockName.Disassembler)(new block.Disassembler())((pos, state) => new tileentity.Disassembler(pos, state))
      blockWithEntity(BlockName.DiskDrive)(new block.DiskDrive())((pos, state) => new tileentity.DiskDrive(pos, state))
      blockWithEntity(BlockName.Geolyzer)(new block.Geolyzer())((pos, state) => new tileentity.Geolyzer(pos, state))
      blockWithEntity(BlockName.HologramTier1)(new block.Hologram(Tier.One))((pos, state) => new tileentity.Hologram(pos, state))
      blockWithEntity(BlockName.HologramTier2)(new block.Hologram(Tier.Two))((pos, state) => new tileentity.Hologram(pos, state))
      blockWithEntity(BlockName.Keyboard)(new block.Keyboard())((pos, state) => new tileentity.Keyboard(pos, state))
      blockWithEntity(BlockName.Microcontroller)(new block.Microcontroller())((pos, state) => new tileentity.Microcontroller(pos, state))
      blockWithEntity(BlockName.MotionSensor)(new block.MotionSensor())((pos, state) => new tileentity.MotionSensor(pos, state))
      blockWithEntity(BlockName.NetSplitter)(new block.NetSplitter())((pos, state) => new tileentity.NetSplitter(pos, state))
      blockWithEntity(BlockName.PowerConverter)(new block.PowerConverter())((pos, state) => new tileentity.PowerConverter(pos, state))
      blockWithEntity(BlockName.PowerDistributor)(new block.PowerDistributor())((pos, state) => new tileentity.PowerDistributor(pos, state))
      blockWithEntity(BlockName.Raid)(new block.Raid())((pos, state) => new tileentity.Raid(pos, state))
      blockWithEntity(BlockName.Redstone)(new block.Redstone())((pos, state) => new tileentity.Redstone(pos, state))
      blockWithEntity(BlockName.Relay)(new block.Relay())((pos, state) => new tileentity.Relay(pos, state))
      blockWithEntity(BlockName.ScreenTier1)(new block.Screen(Tier.One))((pos, state) => new tileentity.Screen(pos, state))
      blockWithEntity(BlockName.ScreenTier2)(new block.Screen(Tier.Two))((pos, state) => new tileentity.Screen(pos, state))
      blockWithEntity(BlockName.ScreenTier3)(new block.Screen(Tier.Three))((pos, state) => new tileentity.Screen(pos, state))
      blockWithEntity(BlockName.Rack)(new block.Rack())((pos, state) => new tileentity.Rack(pos, state))
      blockWithEntity(BlockName.Switch)(new block.Switch())((pos, state) => new tileentity.Switch(pos, state))
      blockWithEntity(BlockName.Print)(new block.Print())((pos, state) => new tileentity.Print(pos, state))
      blockWithEntity(BlockName.Printer)(new block.Printer())((pos, state) => new tileentity.Printer(pos, state))
      blockWithEntity(BlockName.Waypoint)(new block.Waypoint())((pos, state) => new tileentity.Waypoint(pos, state))
      blockWithEntity(BlockName.Transposer)(new block.Transposer())((pos, state) => new tileentity.Transposer(pos, state))

      // 机器人：1.7.10 的 `robot` 方块（metadata 区分 Robot / RobotProxy / RobotAfterimage），
      // 1.21.1 拆成两个独立方块；机器人本体由 `RobotProxy` 方块实体承载。
      blockWithEntity(BlockName.Robot)(new block.RobotProxy())((pos, state) => new tileentity.RobotProxy(pos, state))

      // 没有方块实体的方块。
      blockOnly(BlockName.RobotAfterimage)(new block.RobotAfterimage())
      hideBlockItemInCreativeTab(BlockName.RobotAfterimage)
      blockOnly(BlockName.ChameliumBlock)(new block.ChameliumBlock())
      blockOnly(BlockName.Endstone)(new block.FakeEndstone())

      // ------------------------------------------------------------------ //
      // 能力注册
      // ------------------------------------------------------------------ //

      registerCapabilities(entities.toSeq)
    }

    /**
     * 把 `IItemHandler` / `IFluidHandler` 能力挂到方块实体类型上。
     *
     * 1.21.1 的能力查询是
     * {{{
     *   Capabilities.ItemHandler.BLOCK.getCapability(level, pos, state, blockEntity, side)
     * }}}
     * 而注册必须在 mod 事件总线的 [[net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent]]
     * 里完成（构造函数之后、注册表冻结之后）。
     *
     * 本项目约定：
     *  - 有物品栏的方块实体混入 [[li.cil.oc.common.tileentity.ItemHandlerProvider]]
     *    （其实现类本身就是 `IItemHandler`，即 `traits.Inventory` / `traits.ComponentInventory`）；
     *  - 有储罐的（机器人 / 机架等）混入 [[li.cil.oc.common.tileentity.FluidHandlerProvider]]；
     *  - 没有混入的方块实体，能力查询返回 `null`（等价于“不支持该能力”），不会崩。
     */
    private def registerCapabilities(names: Seq[String]): Unit = {
      if (modBusRef == null || names.isEmpty) return
      modBusRef.addListener(EventPriority.NORMAL, false, classOf[RegisterCapabilitiesEvent],
        new java.util.function.Consumer[RegisterCapabilitiesEvent] {
          override def accept(event: RegisterCapabilitiesEvent): Unit = {
            for (name <- names) {
              val beType = getBlockEntityType(name)
              if (beType != null) {
                val typed = beType.asInstanceOf[BlockEntityType[BlockEntity]]
                event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, typed,
                  new ICapabilityProvider[BlockEntity, Direction, IItemHandler] {
                    override def getCapability(blockEntity: BlockEntity, side: Direction): IItemHandler =
                      blockEntity match {
                        case provider: li.cil.oc.common.tileentity.ItemHandlerProvider => provider.itemHandler(side)
                        case _ => null
                      }
                  })
                event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, typed,
                  new ICapabilityProvider[BlockEntity, Direction, IFluidHandler] {
                    override def getCapability(blockEntity: BlockEntity, side: Direction): IFluidHandler =
                      blockEntity match {
                        case provider: li.cil.oc.common.tileentity.FluidHandlerProvider => provider.fluidHandler(side)
                        case _ => null
                      }
                  })
              }
            }
          }
        })
    }
  }
}
