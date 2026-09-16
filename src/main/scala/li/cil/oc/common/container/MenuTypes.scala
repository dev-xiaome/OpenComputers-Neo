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
   * `DeferredHolder` 的第二个类型参数是不变的（invariant），而客户端重建需要
   * 只有 S 知道的 `T`，所以这里把 `T` 作为类型参数带出来，而不是擦成 `MenuType[_]`。
   */
  type MenuHolder[T <: Player] = DeferredHolder[MenuType[T], MenuType[T]]

  /**
   * 与 [[MenuHolder]] 等价的「类型已擦除」视图。
   *
   * `client.GuiHandler.registerScreens` 只关心「拿一个 `MenuType` 去登记屏幕工厂」，
   * 不需要知道具体容器类型，用这个别名可以避免在那里写一堆存在类型。
   */
  type AnyMenuHolder = DeferredHolder[MenuType[_ <: Player], _ <: MenuType[_ <: Player]]

  // ----------------------------------------------------------------------- //
  // 注册表（名称与 `common/container` 下的类名一一对应）
  // ----------------------------------------------------------------------- //

  val Adapter: MenuHolder[Adapter] = registerBlock("adapter") {
    case (ctx, t: tileentity.Adapter) => new Adapter(ctx.windowId, ctx.inventory, t)
  }
  val Assembler: MenuHolder[Assembler] = registerBlock("assembler") {
    case (ctx, t: tileentity.Assembler) => new Assembler(ctx.windowId, ctx.inventory, t)
  }
  val Case: MenuHolder[Case] = registerBlock("case") {
    case (ctx, t: tileentity.Case) => new Case(ctx.windowId, ctx.inventory, t)
  }
  val Charger: MenuHolder[Charger] = registerBlock("charger") {
    case (ctx, t: tileentity.Charger) => new Charger(ctx.windowId, ctx.inventory, t)
  }
  val Database: MenuHolder[Database] = registerItem("database") {
    case ctx if !ctx.itemOrEmpty.isEmpty => new Database(ctx.windowId, ctx.inventory, new inv.DatabaseInventory {
      override def container: ItemStack = ctx.itemOrEmpty
    })
  }
  val Disassembler: MenuHolder[Disassembler] = registerBlock("disassembler") {
    case (ctx, t: tileentity.Disassembler) => new Disassembler(ctx.windowId, ctx.inventory, t)
  }
  val DiskDrive: MenuHolder[DiskDrive] = registerBlock("diskdrive") {
    case (ctx, t: tileentity.DiskDrive) => new DiskDrive(ctx.windowId, ctx.inventory, t)
  }
  val Drone: MenuHolder[Drone] = registerEntity("drone") {
    case (ctx, d: common.entity.Drone) => new Drone(ctx.windowId, ctx.inventory, d)
  }
  val Printer: MenuHolder[Printer] = registerBlock("printer") {
    case (ctx, t: tileentity.Printer) => new Printer(ctx.windowId, ctx.inventory, t)
  }
  val Rack: MenuHolder[Rack] = registerBlock("rack") {
    case (ctx, t: tileentity.Rack) => new Rack(ctx.windowId, ctx.inventory, t)
  }
  val Raid: MenuHolder[Raid] = registerBlock("raid") {
    case (ctx, t: tileentity.Raid) => new Raid(ctx.windowId, ctx.inventory, t)
  }
  val Relay: MenuHolder[Relay] = registerBlock("relay") {
    case (ctx, t: tileentity.Relay) => new Relay(ctx.windowId, ctx.inventory, t)
  }
  val Robot: MenuHolder[Robot] = registerBlock("robot") {
    case (ctx, t: tileentity.RobotProxy) => new Robot(ctx.windowId, ctx.inventory, t.robot)
  }
  val Server: MenuHolder[Server] = registerItem("server") {
    case ctx if !ctx.itemOrEmpty.isEmpty => new Server(ctx.windowId, ctx.inventory, new inv.ServerInventory {
      override def container: ItemStack = ctx.itemOrEmpty
    })
  }
  val Switch: MenuHolder[Switch] = registerBlock("switch") {
    case (ctx, t: tileentity.Switch) => new Switch(ctx.windowId, ctx.inventory, t)
  }
  val Tablet: MenuHolder[Tablet] = registerItem("tablet") {
    // TODO(common.inventory + server.component.Tablet): 平板的容器需要「平板内部物品栏
    //   + 容器槽位类型/等级」，1.7.10 由 `item.TabletWrapper` 提供；它随
    //   `common/item/Tablet.scala` 一起降级（`TabletData` 只保留了 `items` / `tier`），
    //   而 `common/inventory/TabletCaseInventory` 尚未移植。
    //   等这两块补齐后再按 `Database` / `Server` 的同样套路在这里重建。
    case _ => null
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
   * 打开界面时必须回传的上下文。
   *
   * `windowId` 与 `inventory` 来自客户端的 `MenuType#create`，
   * 其余字段来自 [[MenuHostPayload]] 写下的载荷。
   */
  private final class OpenContext(val windowId: Int,
                                  val inventory: Inventory,
                                  val op: Int,
                                  val pos: BlockPos,
                                  val key: Int,
                                  val stack: ItemStack) {
    def level: Level = inventory.player.level()

    def blockEntity: AnyRef = if (level == null || pos == null) null else level.getBlockEntity(pos)

    /** 实体宿主：载荷里存的是实体 id。 */
    def entity: AnyRef = if (level == null) null else level.getEntity(key)

    def itemOrEmpty: ItemStack = if (stack == null) ItemStack.EMPTY else stack
  }

  /** 登记一个「方块实体宿主」的容器。 */
  private def registerBlock[T <: Player](name: String)
                                        (resolve: PartialFunction[(OpenContext, AnyRef), T]): MenuHolder[T] =
    register[T](name, ctx =>
      if (ctx.op != MenuHostPayload.Block) null
      else resolve.applyOrElse((ctx, ctx.blockEntity), (_: (OpenContext, AnyRef)) => null))

  /** 登记一个「实体宿主」的容器。 */
  private def registerEntity[T <: Player](name: String)
                                         (resolve: PartialFunction[(OpenContext, AnyRef), T]): MenuHolder[T] =
    register[T](name, ctx =>
      if (ctx.op != MenuHostPayload.Entity) null
      else resolve.applyOrElse((ctx, ctx.entity), (_: (OpenContext, AnyRef)) => null))

  /** 登记一个「主手物品宿主」的容器。 */
  private def registerItem[T <: Player](name: String)
                                       (resolve: PartialFunction[OpenContext, T]): MenuHolder[T] =
    register[T](name, ctx =>
      if (ctx.op != MenuHostPayload.ItemInHand) null
      else resolve.applyOrElse(ctx, (_: OpenContext) => null))

  /** 真正调用 [[Registry.registerMenu]] 的地方。 */
  private def register[T <: Player](name: String,
                                    resolve: OpenContext => T): MenuHolder[T] =
    Registry.registerMenu(name, new Supplier[MenuType[T]] {
      override def get(): MenuType[T] = IMenuTypeExtension.create[T](new IContainerFactory[T] {
        override def create(windowId: Int, inventory: Inventory, data: RegistryFriendlyByteBuf): T = {
          if (data == null) null
          else MenuHostPayload.readOp(data) match {
            case Some(MenuHostPayload.Block) =>
              resolve(new OpenContext(windowId, inventory, MenuHostPayload.Block,
                MenuHostPayload.readBlockPos(data), 0, ItemStack.EMPTY))
            case Some(MenuHostPayload.Entity) =>
              resolve(new OpenContext(windowId, inventory, MenuHostPayload.Entity,
                null, MenuHostPayload.readEntityId(data), ItemStack.EMPTY))
            case Some(MenuHostPayload.RackSlot) =>
              val pos = MenuHostPayload.readBlockPos(data)
              val slot = MenuHostPayload.readSlot(data)
              resolve(new OpenContext(windowId, inventory, MenuHostPayload.RackSlot, pos, slot, ItemStack.EMPTY))
            case Some(MenuHostPayload.ItemInHand) =>
              resolve(new OpenContext(windowId, inventory, MenuHostPayload.ItemInHand,
                null, 0, MenuHostPayload.readItem(data)))
            case _ => null
          }
        }
      })
    })
}
