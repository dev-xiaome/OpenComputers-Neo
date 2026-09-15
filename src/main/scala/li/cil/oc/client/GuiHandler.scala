package li.cil.oc.client

import li.cil.oc.Localization
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.common.container.MenuTypes
import li.cil.oc.common.inventory.{DatabaseInventory, DiskDriveMountableInventory, ServerInventory}
import li.cil.oc.common.item.Delegator
import li.cil.oc.common.{GuiType, component, entity, item, tileentity, GuiHandler => CommonGuiHandler}
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.MenuScreens
import net.minecraft.client.gui.screens.inventory.MenuAccess
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.{Inventory, Player}
import net.minecraft.world.inventory.{AbstractContainerMenu, MenuType}
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent

/**
 * 客户端界面工厂 / 注册表。
 *
 * ==1.21.1 与 1.7.10 的结构性差异==
 * 1.7.10 只有一条链路：服务端 `IGuiHandler#getServerGuiElement` 建 `Container`，
 * 客户端 `IGuiHandler#getClientGuiElement`（就是本对象）按 `guiId` + 坐标 new 一个 `GuiContainer`。
 * 1.21.1 拆成两条：
 *  - **容器界面**：`MenuType` 在客户端由 `MenuScreens.ScreenConstructor` 重建屏幕，
 *    注册入口是 [[registerScreens]]（由 `client.Proxy` 挂在 `RegisterMenuScreensEvent` 上）。
 *    屏幕构造器固定为 `(menu, playerInventory, title)`，宿主（方块实体 / 实体 / 物品）
 *    通过 `menu.otherInventory` 取回，不再由本对象 new。
 *  - **无容器界面**（手册、终端屏幕、软盘驱动器、路径点）：1.7.10 里也走 `getClientGuiElement`，
 *    但 1.21.1 必须由服务端发一个自定义包、客户端收到后 `Minecraft#setScreen`。
 *    [[getClientGuiElement]] 保留为「按 id 造屏幕」的纯函数，供将来那条包处理链路调用。
 *
 * ==降级说明==
 *  - 平板内层（`GuiType.TabletInner`）依赖已随 `common/item/Tablet.scala` 一起降级的
 *    `TabletWrapper`（见 `common/GuiHandler` 的 TODO），这里返回 `null`。
 *  - 终端（`GuiType.Terminal`）依赖 `component.TerminalServer.loaded`，1.21.1 里
 *    `api.internal.Rack` 仍是 1.7.10 风格（`rack.world` / `xPosition`），因此逻辑可以保留，
 *    但 `Rack` 的类型匹配改成 `BlockEntity`，且 `isInvalid` → `isRemoved`。
 */
object GuiHandler extends CommonGuiHandler {
  // ----------------------------------------------------------------------- //
  // 1.21.1 主链路：MenuType -> Screen
  // ----------------------------------------------------------------------- //

  /**
   * 由 `client.Proxy` 在 `RegisterMenuScreensEvent` 里调用一次。
   *
   * 16 个 `MenuType` 与 `common/container` 下的容器一一对应（见
   * [[li.cil.oc.common.container.MenuTypes]]），这里给每个容器配上同名屏幕。
   */
  def registerScreens(event: RegisterMenuScreensEvent): Unit = {
    screen(event, MenuTypes.Adapter)(new gui.Adapter(_, _, _))
    screen(event, MenuTypes.Assembler)(new gui.Assembler(_, _, _))
    screen(event, MenuTypes.Case)(new gui.Case(_, _, _))
    screen(event, MenuTypes.Charger)(new gui.Charger(_, _, _))
    screen(event, MenuTypes.Database)(new gui.Database(_, _, _))
    screen(event, MenuTypes.Disassembler)(new gui.Disassembler(_, _, _))
    screen(event, MenuTypes.DiskDrive)(new gui.DiskDrive(_, _, _))
    screen(event, MenuTypes.Drone)(new gui.Drone(_, _, _))
    screen(event, MenuTypes.Printer)(new gui.Printer(_, _, _))
    screen(event, MenuTypes.Rack)(new gui.Rack(_, _, _))
    screen(event, MenuTypes.Raid)(new gui.Raid(_, _, _))
    screen(event, MenuTypes.Relay)(new gui.Relay(_, _, _))
    screen(event, MenuTypes.Robot)(new gui.Robot(_, _, _))
    screen(event, MenuTypes.Server)(new gui.Server(_, _, _))
    screen(event, MenuTypes.Switch)(new gui.Switch(_, _, _))
    screen(event, MenuTypes.Tablet)(new gui.Tablet(_, _, _))
  }

  /**
   * 把 `MenuTypes` 里的 `DeferredHolder` 与一个屏幕工厂接起来。
   *
   * `MenuTypes.MenuHolder` 的第二个类型参数被擦成了 `MenuType[_]`，所以这里必须做一次
   * 强制转型；转型是安全的，因为 `DeferredHolder` 的泛型参数在 [[li.cil.oc.common.container.MenuTypes]] 里
   * 与容器类一一对应。
   */
  private def screen[M <: AbstractContainerMenu, U <: Screen with MenuAccess[M]](
      event: RegisterMenuScreensEvent,
      holder: MenuTypes.MenuHolder)
      (factory: (M, Inventory, Component) => U): Unit = {
    event.register(holder.get().asInstanceOf[MenuType[M]], new MenuScreens.ScreenConstructor[M, U] {
      override def create(menu: M, inventory: Inventory, title: Component): U = factory(menu, inventory, title)
    })
  }

  // ----------------------------------------------------------------------- //
  // 旧链路：按 guiId 造屏幕（无容器界面仍需它）
  // ----------------------------------------------------------------------- //

  override def getClientGuiElement(id: Int, player: Player, world: Level, x: Int, y: Int, z: Int): AnyRef = {
    GuiType.Categories.get(id) match {
      case Some(GuiType.Category.Block) =>
        // 1.21.1：`world.getTileEntity(x, y, z)` -> `world.getBlockEntity(new BlockPos(...))`。
        world.getBlockEntity(new BlockPos(x, GuiType.extractY(y), z)) match {
          case t: tileentity.Adapter if id == GuiType.Adapter.id =>
            new gui.Adapter(new li.cil.oc.common.container.Adapter(0, player.getInventory, t), player.getInventory, Component.empty())
          case _ => null
        }
      case Some(GuiType.Category.Entity) =>
        // 1.21.1：`world.getEntityByID(x)` -> `world.getEntity(x)`。
        world.getEntity(x) match {
          case drone: entity.Drone if id == GuiType.Drone.id =>
            new gui.Drone(new li.cil.oc.common.container.Drone(0, player.getInventory, drone), player.getInventory, Component.empty())
          case _ => null
        }
      case Some(GuiType.Category.Item) =>
        // 1.21.1：`player.getHeldItem` -> `player.getMainHandItem`。
        val stack = player.getMainHandItem
        Delegator.subItem(stack) match {
          case Some(_: item.traits.FileSystemLike) if id == GuiType.Drive.id =>
            new gui.Drive(player.getInventory, () => stack)
          case Some(_: item.UpgradeDatabase) if id == GuiType.Database.id =>
            new gui.Database(new li.cil.oc.common.container.Database(0, player.getInventory, new DatabaseInventory {
              override def container: ItemStack = stack
            }), player.getInventory, Component.empty())
          case Some(_: item.Server) if id == GuiType.Server.id =>
            new gui.Server(new li.cil.oc.common.container.Server(0, player.getInventory, new ServerInventory {
              override def container: ItemStack = stack
            }), player.getInventory, Component.empty())
          case Some(_: item.Tablet) if id == GuiType.Tablet.id =>
            // TODO(client.item.Tablet): 原实现从 `item.Tablet.get(stack, player)` 拿到平板包裹物，
            //   再取出内部的 `api.internal.TextBuffer` 开终端界面。`TabletWrapper` 已随
            //   `common/item/Tablet.scala` 降级删除，等它给出等价数据（`TabletData`）后再补。
            null
          case Some(_: item.Tablet) if id == GuiType.TabletInner.id =>
            // TODO(client.item.Tablet): 同上，缺 `TabletWrapper`。
            null
          case Some(_: item.DiskDriveMountable) if id == GuiType.DiskDriveMountable.id =>
            new gui.DiskDrive(new li.cil.oc.common.container.DiskDrive(0, player.getInventory, new DiskDriveMountableInventory {
              override def container: ItemStack = stack
            }), player.getInventory, Component.empty())
          case Some(_: item.Terminal) if id == GuiType.Terminal.id =>
            terminalScreen(player, stack)
          case _ => null
        }
      case Some(GuiType.Category.None) =>
        if (id == GuiType.Manual.id) new gui.Manual()
        else null
      case _ => null
    }
  }

  /**
   * 无线终端（`GuiType.Terminal`）开屏逻辑。
   *
   * 与原实现的差别只在 API 名称：`stack.hasTagCompound` → `stack.hasTag`、
   * `player.isEntityAlive` → `player.isAlive`、`player.posX/Y/Z` → `getX/Y/Z`、
   * `getDistanceFrom` → `distanceToSqr`、`rack.isInvalid` → `isRemoved`、
   * `Minecraft.getMinecraft.displayGuiScreen` → `Minecraft.getInstance.setScreen`、
   * `player.addChatMessage` → `player.displayClientMessage(..., false)`。
   */
  private def terminalScreen(player: Player, stack: ItemStack): AnyRef = {
    if (!stack.hasTag) return null
    val address = stack.getTag.getString(Settings.namespace + "server")
    val key = stack.getTag.getString(Settings.namespace + "key")
    if (address == null || address.isEmpty || key == null || key.isEmpty) return null
    component.TerminalServer.loaded.find(address) match {
      case Some(term) if term.rack != null =>
        // 1.21.1 里 `api.internal.Rack` 仍暴露 `world` / `xPosition` 等（`EnvironmentHost`），
        // 但 `isInvalid` / `getDistanceFrom` 只存在于 `BlockEntity` 上，
        // 因此这里自己算距离，并把「已移除」判断挪到 `BlockEntity` 分支里。
        def inRange: Boolean = {
          if (!player.isAlive) return false
          val rack = term.rack
          if (rack.world == null) return false
          rack match {
            case be: BlockEntity if be.isRemoved => return false
            case _ =>
          }
          val dx = rack.xPosition - player.getX
          val dy = rack.yPosition - player.getY
          val dz = rack.zPosition - player.getZ
          dx * dx + dy * dy + dz * dz < term.range * term.range
        }
        if (inRange) {
          if (term.sidedKeys.contains(key)) {
            new gui.Screen(term.buffer, true, () => true, () => {
              // 别人把终端重新绑定到这台服务器时自动关屏。
              if (stack.getTag.getString(Settings.namespace + "key") != key) {
                Minecraft.getInstance().setScreen(null)
              }
              // 走出范围时自动关屏。
              if (!inRange) {
                Minecraft.getInstance().setScreen(null)
              }
              true
            })
          }
          else {
            player.displayClientMessage(Localization.Terminal.InvalidKey, false)
            null
          }
        }
        else {
          player.displayClientMessage(Localization.Terminal.OutOfRange, false)
          null
        }
      case _ =>
        player.displayClientMessage(Localization.Terminal.OutOfRange, false)
        null
    }
  }
}
