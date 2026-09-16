package li.cil.oc.client

import li.cil.oc.common.container.MenuTypes
import li.cil.oc.common.{GuiType, container, tileentity}
import net.minecraft.client.gui.screens.{MenuScreens, Screen}
import net.minecraft.client.gui.screens.inventory.MenuAccess
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.{Inventory, Player}
import net.minecraft.world.inventory.{AbstractContainerMenu, MenuType}
import net.minecraft.world.level.Level
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent

/**
 * 客户端界面注册表。
 *
 * ==1.21.1 与 1.7.10 的结构性差异==
 * 1.7.10 只有一条链路：服务端 `IGuiHandler#getServerGuiElement` 建 `Container`，
 * 客户端 `IGuiHandler#getClientGuiElement` 按 `guiId` + 坐标 new 一个 `GuiContainer`。
 * 1.21.1 拆成两条：
 *  - **容器界面**：`MenuType` 在客户端由 `MenuScreens.ScreenConstructor` 重建屏幕，
 *    注册入口是 [[registerScreens]]（由 [[Proxy.initialize]] 挂在 `RegisterMenuScreensEvent` 上）。
 *    屏幕构造器固定为 `(menu, playerInventory, title)`，宿主（方块实体 / 实体 / 物品）
 *    通过容器实例本身取回（见 [[li.cil.oc.common.container.MenuTypes]] 的客户端工厂），
 *    不再由本对象 new。
 *  - **无容器界面**（手册、终端屏幕、路径点）：1.7.10 里也走 `getClientGuiElement`，
 *    但 1.21.1 必须由服务端发一个自定义包、客户端收到后 `Minecraft#setScreen`。
 *    [[getClientGuiElement]] 保留为「按 id 造屏幕」的纯函数，供将来那条包处理链路调用。
 *
 * ==降级说明==
 *  - 终端（`GuiType.Terminal`）与路径点（`GuiType.Waypoint`）依赖
 *    `renderer.gui.BufferRenderer` / `TextBufferRenderCache`（文本缓冲区渲染子系统），
 *    这部分尚未移植，因此 [[getClientGuiElement]] 里对应分支返回 `null`。
 *  - 手册（`GuiType.Manual`）是纯客户端界面，已由 [[Manual]] 直接 `setScreen` 打开，
 *    不走本对象。
 */
object GuiHandler extends container.GuiHandler {
  // ----------------------------------------------------------------------- //
  // 1.21.1 主链路：MenuType -> Screen
  // ----------------------------------------------------------------------- //

  /**
   * 由 [[Proxy.initialize]] 在 `RegisterMenuScreensEvent` 里调用一次。
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
   * 把 [[MenuTypes]] 里的 `DeferredHolder` 与一个屏幕工厂接起来。
   *
   * [[MenuTypes.MenuHolder]] 的类型参数在 `registerScreens` 的调用点上已经确定，
   * 因此这里不再需要强制转型 —— 这正是 [[MenuTypes.MenuHolder]] 保留 `T` 的原因。
   */
  private def screen[M <: container.Player, U <: Screen with MenuAccess[M]](
      event: RegisterMenuScreensEvent,
      holder: MenuTypes.MenuHolder[M])
      (factory: (M, Inventory, Component) => U): Unit = {
    event.register(holder.value(), new MenuScreens.ScreenConstructor[M, U] {
      override def create(menu: M, inventory: Inventory, title: Component): U = factory(menu, inventory, title)
    })
  }

  // ----------------------------------------------------------------------- //
  // 旧链路：按 guiId 造屏幕（无容器界面仍需它）
  // ----------------------------------------------------------------------- //

  /**
   * 按 [[GuiType]] id 造屏幕。
   *
   * 1.21.1 里**只有**「没有服务端容器」的界面才需要它（手册 / 终端屏幕 / 路径点），
   * 因为容器界面走 `MenuType` 工厂。这里的每个分支都必须自己拿到宿主对象，
   * 而 `world.getBlockEntity(pos)` 在客户端是可靠的（区块已加载），
   * 因此仍然可行。
   */
  override def getClientGuiElement(id: Int, player: Player, world: Level, x: Int, y: Int, z: Int): AnyRef = {
    GuiType.Categories.get(id) match {
      case Some(GuiType.Category.None) =>
        if (id == GuiType.Manual.id) new gui.Manual() else null
      case Some(GuiType.Category.Block) =>
        // 方块宿主：文本缓冲区类界面（屏幕 / 路径点）尚未移植，见类注释的降级说明。
        world.getBlockEntity(new BlockPos(x, GuiType.extractY(y), z)) match {
          case _: tileentity.Screen if id == GuiType.Screen.id =>
            // TODO(client.gui.Screen): 需要 `renderer.gui.BufferRenderer` +
            //   `gui.traits.InputBuffer`（文本缓冲区渲染 / 输入子系统）。
            null
          case _: tileentity.Waypoint if id == GuiType.Waypoint.id =>
            // TODO(client.gui.Waypoint): 同上，但只需要 `BufferRenderer`。
            null
          case _ => null
        }
      case _ => null
    }
  }
}
