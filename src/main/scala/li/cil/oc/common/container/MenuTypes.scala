package li.cil.oc.common.container

import java.util.function.Supplier

import li.cil.oc.common
import li.cil.oc.common.{inventory => inv, item, tileentity}
import li.cil.oc.common.init.Registry
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.{AbstractContainerMenu, MenuType}
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension
import net.neoforged.neoforge.network.IContainerFactory
import net.neoforged.neoforge.registries.DeferredHolder

/**
 * `common/container` 里 16 个容器的 `MenuType` 注册表。
 *
 * 1.7.10 里没有 `MenuType` 这个概念：容器由 `IGuiHandler#getServerGuiElement` 直接 new，
 * 客户端靠「`guiId` + 方块坐标」反射 new 出对应的 `GuiContainer`。
 * 1.21.1 改成：
 *  - 每个容器登记一个 [[net.minecraft.world.inventory.MenuType]]；
 *  - **服务端**通过 [[MenuOpening.openBlock]] 等（内部是 `player.openMenu(MenuProvider)`）
 *    创建容器并分配 `windowId`，同时把宿主信息写进载荷；
 *  - **客户端**收到 `ClientboundOpenScreenPacket` 后调用
 *    `MenuType#create(windowId, inventory, payload)` 重建容器 —— 也就是下面这些工厂。
 *
 * 注册时机：[[Registry.init]] 会调用 [[register]]，必须发生在 mod 构造期
 * （`menus.register(modBus)` 与注册表事件之间），因此**不能**依赖懒加载。
 *
 * ==客户端重建（本文件的核心）==
 * 载荷格式由 [[MenuHostPayload]] 定义，操作码决定宿主是方块 / 实体 / 机架插槽 / 主手物品：
 *  - 方块宿主：`inventory.player.level().getBlockEntity(pos)` 取回方块实体，按它的实际类型
 *    与容器一一对应（同一个 `MenuType` 可能对应多种宿主，例如 RAID 与磁盘驱动器）；
 *  - 机架插槽：取回机架后 `getMountable(slot)`，再按可插拔组件的类型分派；
 *  - 主手物品：把载荷里的 [[net.minecraft.world.item.ItemStack]] 快照包进
 *    `DatabaseInventory` / `ServerInventory` / `DiskDriveMountableInventory` 幽灵物品栏。
 *
 * ==注意：同一个 `MenuType` 会被多种宿主共用==
 * 这不是设计失误，而是 1.7.10 的本来语义：`GuiType.Raid` 只有一种容器
 * （[[Raid]]），但磁盘驱动器、机架里的服务器 / 磁盘驱动器各自对应不同的容器类。
 * 因此工厂按**宿主的实际类型**分派，而不是按 `MenuType` 分派。
 */
object MenuTypes {
  /**
   * 统一的持有类型。
   *
   * `DeferredHolder` 的第二个类型参数（也就是「值类型」）可以精确保留成
   * `MenuType[T]` —— 这正是客户端重建工厂需要的类型，`registerScreens` 因此
   * 不需要在调用点写强制转型。（第一个参数「注册表类型」无法还原，擦成
   * `MenuType[_]` 即可，它不参与任何一方的方法签名。）
   */
  type MenuHolder[T <: Player] = DeferredHolder[MenuType[_], MenuType[T]]

  /**
   * 与 [[MenuHolder]] 等价的「类型已擦除」视图。
   *
   * `client.GuiHandler.registerScreens` 只关心「拿一个 `MenuType` 去登记屏幕工厂」，
   * 不需要知道具体容器类型，用这个别名可以避免在那里写一堆存在类型。
   */
  type AnyMenuHolder = DeferredHolder[MenuType[_], MenuType[_ <: Player]]

  // ----------------------------------------------------------------------- //
  // 注册表（名称与 `common/container` 下的类名一一对应）
  // ----------------------------------------------------------------------- //

  val Adapter: MenuHolder[Adapter] = registerBlock[Adapter]("adapter") {
    case (ctx, t: tileentity.Adapter) => new Adapter(ctx.windowId, ctx.inventory, t)
  }
  val Assembler: MenuHolder[Assembler] = registerBlock[Assembler]("assembler") {
    case (ctx, t: tileentity.Assembler) => new Assembler(ctx.windowId, ctx.inventory, t)
  }
  val Case: MenuHolder[Case] = registerBlock[Case]("case") {
    case (ctx, t: tileentity.Case) => new Case(ctx.windowId, ctx.inventory, t)
  }
  val Charger: MenuHolder[Charger] = registerBlock[Charger]("charger") {
    case (ctx, t: tileentity.Charger) => new Charger(ctx.windowId, ctx.inventory, t)
  }
  val Database: MenuHolder[Database] = registerItem[Database]("database") { ctx =>
    if (ctx.itemOrEmpty.isEmpty) null
    else new Database(ctx.windowId, ctx.inventory, new inv.DatabaseInventory {
      override def container: ItemStack = ctx.itemOrEmpty
    })
  }
  val Disassembler: MenuHolder[Disassembler] = registerBlock[Disassembler]("disassembler") {
    case (ctx, t: tileentity.Disassembler) => new Disassembler(ctx.windowId, ctx.inventory, t)
  }
  val DiskDrive: MenuHolder[DiskDrive] = register[DiskDrive]("diskdrive", ctx => ctx.op match {
    case MenuHostPayload.Block => ctx.blockEntity match {
      case t: tileentity.DiskDrive => new DiskDrive(ctx.windowId, ctx.inventory, t)
      case _ => null
    }
    // 机架插槽里的磁盘驱动器与方块形态共用同一个 `MenuType`
    // （服务端 `GuiType.DiskDriveMountableInRack` 走的也是 `container.DiskDrive`）。
    case MenuHostPayload.RackSlot => ctx.rackMountable match {
      case drive: inv.DiskDriveMountableInventory => new DiskDrive(ctx.windowId, ctx.inventory, drive)
      case _ => null
    }
    case _ => null
  })
  val Drone: MenuHolder[Drone] = registerEntity[Drone]("drone") {
    case (ctx, d: common.entity.Drone) => new Drone(ctx.windowId, ctx.inventory, d)
  }
  val Printer: MenuHolder[Printer] = registerBlock[Printer]("printer") {
    case (ctx, t: tileentity.Printer) => new Printer(ctx.windowId, ctx.inventory, t)
  }
  val Rack: MenuHolder[Rack] = registerBlock[Rack]("rack") {
    case (ctx, t: tileentity.Rack) => new Rack(ctx.windowId, ctx.inventory, t)
  }
  val Raid: MenuHolder[Raid] = registerBlock[Raid]("raid") {
    case (ctx, t: tileentity.Raid) => new Raid(ctx.windowId, ctx.inventory, t)
  }
  val Relay: MenuHolder[Relay] = registerBlock[Relay]("relay") {
    case (ctx, t: tileentity.Relay) => new Relay(ctx.windowId, ctx.inventory, t)
  }
  val Robot: MenuHolder[Robot] = registerBlock[Robot]("robot") {
    case (ctx, t: tileentity.RobotProxy) => new Robot(ctx.windowId, ctx.inventory, t.robot)
  }
  val Server: MenuHolder[Server] = register[Server]("server", ctx => ctx.op match {
    case MenuHostPayload.ItemInHand if !ctx.itemOrEmpty.isEmpty =>
      new Server(ctx.windowId, ctx.inventory, new inv.ServerInventory {
        override def container: ItemStack = ctx.itemOrEmpty
      })
    // 机架插槽里的服务器与物品形态共用同一个 `MenuType`
    // （服务端 `GuiType.ServerInRack` 走的也是 `container.Server`）。
    case MenuHostPayload.RackSlot => ctx.rackMountable match {
      case inventory: inv.ServerInventory =>
        // 屏幕需要「哪台机架 + 第几格」来做电源键与「物品被取走就关屏」。
        new Server(ctx.windowId, ctx.inventory, inventory, true, () => false, ctx.rack, ctx.key)
      case _ => null
    }
    case _ => null
  })

  val Switch: MenuHolder[Switch] = registerBlock[Switch]("switch") {
    case (ctx, t: tileentity.Switch) => new Switch(ctx.windowId, ctx.inventory, t)
  }
  val Tablet: MenuHolder[Tablet] = registerItem[Tablet]("tablet") { ctx =>
    // TODO(common.inventory + server.component.Tablet): 平板的容器需要「平板内部物品栏
    //   + 容器槽位类型/等级」，1.7.10 由 `item.TabletWrapper` 提供；它随
    //   `common/item/Tablet.scala` 一起降级（`TabletData` 只保留了 `items` / `tier`），
    //   而 `common/inventory/TabletCaseInventory` 尚未移植。
    //   在那之前用 [[EmptyItemHandler]] 兜底：界面能开、槽位是空的，
    //   而不是让工厂返回 `null` 让原版在打开界面时崩。
    new Tablet(ctx.windowId, ctx.inventory, new EmptyItemHandler("Tablet", EmptyItemHandler.TabletSlots), common.Slot.None, common.Tier.Any)
  }

  // ----------------------------------------------------------------------- //
  // 注册
  // ----------------------------------------------------------------------- //

  /** 由 [[Registry.init]] 在 mod 构造期调用一次；重复调用是安全的（`DeferredRegister` 自身去重）。 */
  def register(): Unit = {
    // 访问一下各 `val` 只是为了确保本对象被初始化 —— `val` 的初始化过程本身就是注册过程。
    val _ = Seq(Adapter, Assembler, Case, Charger, Database, Disassembler, DiskDrive, Drone,
      Printer, Rack, Raid, Relay, Robot, Server, Switch, Tablet)
  }

  // ----------------------------------------------------------------------- //
  // 工厂
  // ----------------------------------------------------------------------- //

  /**
   * 打开 / 重建容器时必须回传的上下文。
   *
   * `windowId` 与 `inventory` 来自**客户端**的 `MenuType#create`（服务端重建时
   * 传的是 `0` 与玩家物品栏，见各容器的便捷构造器），其余字段来自
   * [[MenuHostPayload]] 写下的载荷。
   *
   * 之所以公开：它同时也是 `common/container` 下各容器的便捷构造器参数，
   * 让「服务端新建容器」与「客户端重建容器」共用同一份构造代码
   * （见 `MenuTypes.register` 的类注释）。
   */
  final class OpenContext(val windowId: Int,
                          val inventory: Inventory,
                          val op: Int,
                          val pos: BlockPos,
                          val key: Int,
                          val stack: ItemStack) {

    def level: Level = inventory.player.level()

    def blockEntity: AnyRef = if (level == null || pos == null) null else level.getBlockEntity(pos)

    /** 实体宿主：载荷里存的是实体 id。 */
    def entity: AnyRef = if (level == null) null else level.getEntity(key)

    /** 机架宿主（[[MenuHostPayload.RackSlot]] 专用）。 */
    def rack: Option[tileentity.Rack] = blockEntity match {
      case r: tileentity.Rack => Some(r)
      case _ => None
    }

    /** 机架插槽里的可插拔组件（[[MenuHostPayload.RackSlot]] 专用）。 */
    def rackMountable: AnyRef = blockEntity match {
      case r: tileentity.Rack => r.getMountable(key)
      case _ => null
    }

    def itemOrEmpty: ItemStack = if (stack == null) ItemStack.EMPTY else stack
  }

  /**
   * 登记一个「方块实体宿主」的容器。
   *
   * `resolve` 拿到上下文与宿主方块实体，返回具体容器，或用 `case _` 兜底返回 `null`
   * （宿主类型不匹配 / 方块实体已消失）。
   *
   * 之所以把上下文也交给 `resolve`：容器构造器需要 `windowId` 与玩家物品栏，
   * 它们只在 [[OpenContext]] 里。
   */
  private def registerBlock[T <: Player](name: String)
                                        (resolve: PartialFunction[(OpenContext, AnyRef), T]): MenuHolder[T] =
    register[T](name, ctx => {
      if (ctx.op != MenuHostPayload.Block) null
      else resolve.lift((ctx, ctx.blockEntity)).orNull
    })

  /** 登记一个「实体宿主」的容器。 */
  private def registerEntity[T <: Player](name: String)
                                         (resolve: PartialFunction[(OpenContext, AnyRef), T]): MenuHolder[T] =
    register[T](name, ctx => {
      if (ctx.op != MenuHostPayload.Entity) null
      else resolve.lift((ctx, ctx.entity)).orNull
    })

  /** 登记一个「主手物品宿主」的容器。 */
  private def registerItem[T <: Player](name: String)
                                       (resolve: OpenContext => T): MenuHolder[T] =
    register[T](name, ctx => if (ctx.op != MenuHostPayload.ItemInHand) null else resolve(ctx))

  /**
   * 真正调用 [[Registry.registerMenu]] 的地方。
   *
   * `resolve` 的返回类型刻意放宽成 [[Player]]（而不是 `T`）：`MenuType#create` 在
   * 「宿主不存在 / 载荷不对」时要返回 `null`，而 `null` 与具体容器类型 `T` 在
   * Scala 2 里无法统一（没有 union type）。工厂内部做一次窄化转型，
   * 转型是安全的 —— 因为 [[registerBlock]] 等辅助方法的 `T` 由调用点的
   * `MenuHolder[T]` 唯一确定。
   */
  private def register[T <: Player](name: String,
                                    resolve: OpenContext => Player): MenuHolder[T] =
    Registry.registerMenu[MenuType[T]](name, new Supplier[MenuType[T]] {
      override def get(): MenuType[T] = IMenuTypeExtension.create[T](new IContainerFactory[T] {
        override def create(windowId: Int, inventory: Inventory, data: RegistryFriendlyByteBuf): T = {
          val ctx = readContext(windowId, inventory, data)
          if (ctx == null) null.asInstanceOf[T] else resolve(ctx).asInstanceOf[T]
        }
      })
    }).asInstanceOf[MenuHolder[T]]

  /** 按载荷里的操作码把「客户端重建容器所需的上下文」解出来。 */
  private def readContext(windowId: Int, inventory: Inventory, data: RegistryFriendlyByteBuf): OpenContext =
    if (data == null) null
    else MenuHostPayload.readOp(data) match {
      case Some(MenuHostPayload.Block) =>
        new OpenContext(windowId, inventory, MenuHostPayload.Block,
          MenuHostPayload.readBlockPos(data), 0, ItemStack.EMPTY)
      case Some(MenuHostPayload.Entity) =>
        new OpenContext(windowId, inventory, MenuHostPayload.Entity,
          null, MenuHostPayload.readEntityId(data), ItemStack.EMPTY)
      case Some(MenuHostPayload.RackSlot) =>
        val pos = MenuHostPayload.readBlockPos(data)
        val slot = MenuHostPayload.readSlot(data)
        new OpenContext(windowId, inventory, MenuHostPayload.RackSlot, pos, slot, ItemStack.EMPTY)
      case Some(MenuHostPayload.ItemInHand) =>
        new OpenContext(windowId, inventory, MenuHostPayload.ItemInHand,
          null, 0, MenuHostPayload.readItem(data))
      case _ => null
    }
}
