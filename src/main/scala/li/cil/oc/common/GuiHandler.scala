package li.cil.oc.common

import li.cil.oc.common.inventory.{DatabaseInventory, DiskDriveMountableInventory, ServerInventory}
import li.cil.oc.common.item.Delegator
import li.cil.oc.server.component.{DiskDriveMountable, Server}
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
 * ==降级说明==
 *  - `GuiType.TabletInner`（平板内层界面）：原实现依赖 `item.Tablet.get(stack, player)`
 *    返回的 `TabletWrapper`，而 `TabletWrapper` 已随 `common/item/Tablet.scala` 一起降级
 *    （现在只有 `Tablet.getId` / `TabletData`），因此这里返回 `null` 并留下 TODO。
 *  - `GuiType.Drive`/`Terminal`/`Screen`/`Waypoint`/`Manual` 等只在客户端有界面的 id
 *    原本就不在本类里，客户端重建走 `MenuType` 的工厂。
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
        world.getBlockEntity(new BlockPos(x, GuiType.extractY(y), z)) match {
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
            val slot = GuiType.extractSlot(y)
            t.getMountable(slot) match {
              // `server.component.Server` 本身就是 `ServerInventory`，
              // 并且实现了 `api.machine.MachineHost`，可直接作为宿主物品栏传进去。
              case server: Server =>
                new container.Server(windowId, player.getInventory, server, Option(server),
                  () => server.machine != null && server.machine.isRunning)
              case _ => null
            }
          case t: tileentity.Rack if id == GuiType.DiskDriveMountableInRack.id =>
            val slot = GuiType.extractSlot(y)
            t.getMountable(slot) match {
              case drive: DiskDriveMountable =>
                new container.DiskDrive(windowId, player.getInventory, drive)
              case _ => null
            }
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
            // TODO(common.item): 原实现是 `new container.Tablet(player.inventory, item.Tablet.get(stack, player))`，
            // 依赖已删除的 `TabletWrapper`（提供内部物品栏 + 槽位类型/等级）。
            // 等 `common/item/Tablet.scala` 给出等价数据（`TabletData` 里有 items/tier）后再补上。
            null
          case _ => null
        }
      case _ => null
    }
  }

  /**
   * 客户端界面工厂。
   *
   * 1.21.1 里界面由 `MenuType` 的 Screen 工厂创建（`RegisterMenuScreensEvent`），
   * 这里只为兼容旧调用链保留入口，由 [[li.cil.oc.client.GuiHandler]] 覆写。
   */
  def getClientGuiElement(id: Int, player: Player, world: Level, x: Int, y: Int, z: Int): AnyRef = null

  /**
   * 在服务端按 [[GuiType]] id 打开界面（取代 1.7.10 的 `player.openGui`）。
   *
   * 注意：客户端重建容器需要 `MenuType` 的工厂参数（方块坐标 / 实体 id / 物品堆叠），
   * 目前 [[li.cil.oc.common.container.MenuTypes]] 里的工厂还是占位实现，
   * 详见该文件的说明。
   */
  def openGui(id: Int, player: Player, world: Level, x: Int, y: Int, z: Int): Unit = {
    container.MenuOpening.open(player, Component.empty())((windowId, _) =>
      getServerMenu(id, windowId, player, world, x, y, z))
  }
}
