package li.cil.oc.common

import li.cil.oc.common.inventory.{DatabaseInventory, DiskDriveMountableInventory, ServerInventory}
import li.cil.oc.common.item.Delegator
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/**
 * 按 [[GuiType]] id 建立容器的分发表。
 *
 * 1.21.1 迁移要点：
 *  - Forge 1.7.10 的 `IGuiHandler` 已被移除：服务端菜单由 `MenuType` +
 *    `MenuProvider` 建立（见 [[li.cil.oc.common.container.MenuOpening]]），
 *    客户端界面由 `MenuType` 的 Screen 工厂注册。因此本类不再实现 `IGuiHandler`，
 *    只保留「按 id 建立容器」这一步。
 *  - 容器构造器统一多了 `windowId`（由 `MenuProvider#createMenu` 分配），
 *    玩家物品栏由 `player.inventory` 改为 `player.getInventory`。
 *  - `world.getTileEntity(x, y, z)` → `world.getBlockEntity(new BlockPos(x, y, z))`；
 *    `world.getEntityByID(id)` → `world.getEntity(id)`。
 *  - `player.getHeldItem` → `player.getMainHandItem`；`stack.hasTagCompound` → `stack.hasTag`
 *    （由 `li.cil.oc` 包对象的隐式类补回）。
 *
 * ==打开链路==
 * 1.7.10 的 `player.openGui` 在 1.21.1 不存在，取而代之的是
 * [[openGui]] / [[openItemGui]] / [[openDroneGui]]，它们：
 *  1. 用 [[li.cil.oc.common.container.MenuOpening]] 造一个会写「宿主载荷」的 `MenuProvider`；
 *  2. 由 `MenuType` 的工厂在**客户端**用同一份载荷重建容器；
 *  3. 客户端的屏幕由 [[li.cil.oc.client.GuiHandler.registerScreens]] 配对。
 * 三者必须成对维护：改一处载荷格式就要同步 [[li.cil.oc.common.container.MenuTypes]]。
 *
 * ==降级说明==
 *  - `GuiType.TabletInner`（平板内层界面）：原实现依赖 `item.Tablet.get(stack, player)`
 *    返回的 `TabletWrapper`，而 `TabletWrapper` 已随 `common/item/Tablet.scala` 一起降级
 *    （现在只有 `Tablet.getId` / `TabletData`），因此这里返回 `null` 并留下 TODO。
 */
abstract class GuiHandler {
  /**
   * 建立服务端容器菜单。
   *
   * @param id        [[GuiType]] 里的 id（就是原来的 `guiId`）
   * @param windowId  由 `MenuProvider#createMenu` 分配的窗口 id，必须原样传给容器构造器
   * @param x         方块坐标 x / 实体 id（见 [[GuiType.Category]]）
   * @param y         方块坐标 y，高 8 位是槽位（见 `GuiType.embedSlot`）
   * @param z         方块坐标 z
   * @return 对应的容器；没有对应容器时返回 `null`
   */
  def getServerMenu(id: Int, windowId: Int, player: Player, world: Level, x: Int, y: Int, z: Int): AbstractContainerMenu = {
    GuiType.Categories.get(id) match {
      case Some(GuiType.Category.Block) =>
        val pos = new BlockPos(x, GuiType.extractY(y), z)
        world.getBlockEntity(pos) match {
          case t: tileentity.Adapter if id == GuiType.Adapter.id =>
            new container.Adapter(windowId, player.getInventory, t)
          case t: tileentity.Assembler if id == GuiType.Assembler.id =>
            new container.Assembler(windowId, player.getInventory, t)
          case t: tileentity.Charger if id == GuiType.Charger.id =>
            new container.Charger(windowId, player.getInventory, t)
          case t: tileentity.Case if id == GuiType.Case.id =>
            new container.Case(windowId, player.getInventory, t)
          case t: tileentity.Disassembler if id == GuiType.Disassembler.id =>
            new container.Disassembler(windowId, player.getInventory, t)
          case t: tileentity.DiskDrive if id == GuiType.DiskDrive.id =>
            new container.DiskDrive(windowId, player.getInventory, t)
          case t: tileentity.Printer if id == GuiType.Printer.id =>
            new container.Printer(windowId, player.getInventory, t)
          case t: tileentity.Raid if id == GuiType.Raid.id =>
            new container.Raid(windowId, player.getInventory, t)
          case t: tileentity.Relay if id == GuiType.Relay.id =>
            new container.Relay(windowId, player.getInventory, t)
          case t: tileentity.RobotProxy if id == GuiType.Robot.id =>
            new container.Robot(windowId, player.getInventory, t.robot)
          case t: tileentity.Rack if id == GuiType.Rack.id =>
            new container.Rack(windowId, player.getInventory, t)
          case t: tileentity.Rack if id == GuiType.ServerInRack.id =>
            rackServerMenu(windowId, player, t, GuiType.extractSlot(y))
          case t: tileentity.Rack if id == GuiType.DiskDriveMountableInRack.id =>
            rackDiskDriveMenu(windowId, player, t, GuiType.extractSlot(y))
          case t: tileentity.Switch if id == GuiType.Switch.id =>
            new container.Switch(windowId, player.getInventory, t)
          case _ => null
        }
      case Some(GuiType.Category.Entity) =>
        world.getEntity(x) match {
          case drone: entity.Drone if id == GuiType.Drone.id =>
            new container.Drone(windowId, player.getInventory, drone)
          case _ => null
        }
      case Some(GuiType.Category.Item) =>
        val stack = player.getMainHandItem
        Delegator.subItem(stack) match {
          case Some(_: item.UpgradeDatabase) if id == GuiType.Database.id =>
            new container.Database(windowId, player.getInventory, new DatabaseInventory {
              override def container: ItemStack = stack
            })
          case Some(_: item.Server) if id == GuiType.Server.id =>
            new container.Server(windowId, player.getInventory, new ServerInventory {
              override def container: ItemStack = stack
            })
          case Some(_: item.DiskDriveMountable) if id == GuiType.DiskDriveMountable.id =>
            new container.DiskDrive(windowId, player.getInventory, new DiskDriveMountableInventory {
              override def container: ItemStack = stack
            })
          case Some(_: item.Tablet) if id == GuiType.TabletInner.id =>
            // TODO(common.item + common.inventory): 原实现是
            //   `new container.Tablet(player.inventory, item.Tablet.get(stack, player))`，
            //   依赖已删除的 `TabletWrapper`（提供内部物品栏 + 槽位类型/等级）。
            //   等 `TabletData` 给出等价数据、且 `common/inventory/TabletCaseInventory`
            //   移植完成后，按 `Database` / `Server` 的同样套路补上。
            null
          case _ => null
        }
      case _ => null
    }
  }

  /** 机架插槽里的服务器（原 `GuiType.ServerInRack` 分支）。 */
  protected def rackServerMenu(windowId: Int, player: Player, rack: tileentity.Rack, slot: Int): AbstractContainerMenu =
    rack.getMountable(slot) match {
      // 机架里的服务器组件本身就是 `ServerInventory`，直接作为宿主物品栏传进去。
      // TODO(server.component.Server): 该组件尚未移植，等移植完成后可把
      //   `isRunningProvider` 改成 `() => serverComponent.machine != null && serverComponent.machine.isRunning`。
      case server: ServerInventory =>
        new container.Server(windowId, player.getInventory, server, true, () => false, Some(rack), slot)
      case _ => null
    }

  /** 机架插槽里的磁盘驱动器（原 `GuiType.DiskDriveMountableInRack` 分支）。 */
  protected def rackDiskDriveMenu(windowId: Int, player: Player, rack: tileentity.Rack, slot: Int): AbstractContainerMenu =
    rack.getMountable(slot) match {
      case drive: DiskDriveMountableInventory =>
        new container.DiskDrive(windowId, player.getInventory, drive)
      case _ => null
    }

  /**
   * 客户端界面工厂。
   *
   * 1.21.1 里界面由 `MenuType` 的 Screen 工厂创建（`RegisterMenuScreensEvent`），
   * 这里只为兼容旧调用链保留入口，由 [[li.cil.oc.client.GuiHandler]] 覆写。
   */
  def getClientGuiElement(id: Int, player: Player, world: Level, x: Int, y: Int, z: Int): AnyRef = null

  // ----------------------------------------------------------------------- //
  // 打开界面（服务端）
  // ----------------------------------------------------------------------- //

  /**
   * 在服务端按 [[GuiType]] id 打开界面（取代 1.7.10 的 `player.openGui`）。
   *
   * 会按 `id` 的类别自动选择载荷格式：
   *  - [[GuiType.Category.Block]] → 方块坐标（机架的两种内嵌界面会额外带上槽位号）；
   *  - [[GuiType.Category.Entity]] → 实体 id；
   *  - [[GuiType.Category.Item]] → 主手物品堆叠。
   */
  def openGui(id: Int, player: Player, world: Level, x: Int, y: Int, z: Int): Unit = {
    if (player == null || world == null) return
    // 纯客户端界面（[[GuiType.Screen]] / [[GuiType.Waypoint]] / [[GuiType.Manual]]）
    // 在 1.7.10 也走 `player.openGui`，然后由客户端 `IGuiHandler#getClientGuiElement`
    // 造出 `GuiScreen`。1.21.1 没有这条通道：`MenuType` 只能表达「有服务端容器」的界面。
    // 若在这里继续走 `openMenu`，工厂会返回 `null` 并让 `Player#openMenu` 在
    // `menu.containerId` 上 NPE，所以必须先拦下来。
    // TODO(client): 需要补一个「服务端 → 客户端，请开某个无容器界面」的自定义包
    //   （`PacketType` 里目前没有对应项），届时在这里发包而不 return。
    if (!hasServerMenu(id)) return
    GuiType.Categories.get(id) match {
      case Some(GuiType.Category.Block) =>
        val pos = new BlockPos(x, GuiType.extractY(y), z)
        val slot = GuiType.extractSlot(y)
        val rackSubGui = id == GuiType.ServerInRack.id || id == GuiType.DiskDriveMountableInRack.id
        if (rackSubGui) {
          container.MenuOpening.openRackSlot(player, pos, slot, Component.empty())((windowId, inventory) =>
            getServerMenu(id, windowId, player, world, x, y, z))
        }
        else {
          container.MenuOpening.openBlock(player, pos, Component.empty())((windowId, inventory) =>
            getServerMenu(id, windowId, player, world, x, y, z))
        }
      case Some(GuiType.Category.Entity) =>
        container.MenuOpening.openEntity(player, x, Component.empty())((windowId, inventory) =>
          getServerMenu(id, windowId, player, world, x, y, z))
      case Some(GuiType.Category.Item) =>
        openItemGui(id, player)
      case _ =>
        // 纯客户端界面（手册 / 终端屏幕 / 路径点）没有服务端容器，由客户端自己开屏。
    }
  }

  /**
   * 该 id 是否存在**服务端容器**。
   *
   * [[getServerMenu]] 对「纯客户端界面」返回 `null`，而 `MenuProvider` 的工厂一旦返回
   * `null` 就会让 `Player#openMenu` 崩在 `menu.containerId` 上。因此 [[openGui]] 需要
   * 先问一次。这里用黑名单而不是「`getServerMenu` 能不能返回非 null」，
   * 是因为后者会真的去 new 一个容器（会往菜单里加槽位、产生副作用）。
   */
  def hasServerMenu(id: Int): Boolean =
    id != GuiType.Screen.id && id != GuiType.Waypoint.id && id != GuiType.Manual.id

  /**
   * 打开「主手物品」宿主界面（取代 1.7.10 的
   * `player.openGui(OpenComputers, GuiType.Database.id, ...)` 等调用）。
   *
   * 物品栏内容通过载荷快照传给客户端，客户端重建的是**幽灵物品栏**：
   * 槽位只表示「这一格里能放什么」，真正的物品仍然在玩家背包里那份堆叠的 NBT 上。
   */
  def openItemGui(id: Int, player: Player): Unit = {
    if (player == null) return
    val stack = player.getMainHandItem
    container.MenuOpening.openItemInHand(player, stack, Component.empty())((windowId, inventory) =>
      getServerMenu(id, windowId, player, player.level(), 0, 0, 0))
  }
}
