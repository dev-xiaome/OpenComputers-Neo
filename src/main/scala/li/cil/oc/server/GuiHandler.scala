package li.cil.oc.server

import li.cil.oc.common.{GuiHandler => CommonGuiHandler}
import li.cil.oc.common.{container, tileentity}
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.level.Level

/**
 * 服务端 GUI 处理器（对应 1.7.10 的 `li.cil.oc.server.GuiHandler`）。
 *
 * ==1.7.10 → 1.21.1==
 * 1.7.10 里本对象由 `NetworkRegistry.INSTANCE.registerGuiHandler(OpenComputers, GuiHandler)`
 * 注册，`player.openGui(...)` 会被 FML 分发到它，由 `getServerGuiElement` 建 `Container`。
 * 1.21.1 没有 `IGuiHandler`：打开界面统一走
 * [[li.cil.oc.common.GuiHandler.openGui]] → [[li.cil.oc.common.container.MenuOpening]]
 * （内部就是 `Player#openMenu(MenuProvider)`），本对象只负责补上「服务端才拿得到」的那部分。
 *
 * 因此这里只需要覆写 [[CommonGuiHandler.rackServerMenu]]：
 * `common` 层不能引用 `li.cil.oc.server.component`，只能把它退化成
 * 「任何 `ServerInventory` 都当作未运行」（`isRunningProvider = () => false`）。
 * 在服务端包里则可以拿到真正的 [[li.cil.oc.server.component.Server]]，于是：
 *  - 把组件对象本身传给 `container.Server`（`stillValid` 因此会走方块实体距离判定）；
 *  - `isRunningProvider` 接上 `machine.isRunning`，界面上的电源按钮状态才是对的。
 *
 * 注意：`GuiType.ServerInRack` 目前主要由
 * [[li.cil.oc.server.component.Server]] / [[li.cil.oc.server.component.DiskDriveMountable]]
 * 用 [[li.cil.oc.common.container.MenuOpening.openRackSlot]] 直接打开（它们手上有组件对象），
 * 本覆写是给「按 guiId 打开」这条兼容路径兜底的。
 */
object GuiHandler extends CommonGuiHandler {

  override def getClientGuiElement(id: Int, player: Player, world: Level, x: Int, y: Int, z: Int): AnyRef = null

  /**
   * 机架插槽里的服务器界面：用真正的 [[li.cil.oc.server.component.Server]] 建容器。
   *
   * 对应 1.7.10 `common.GuiHandler` 里的
   * `case server: Server => new container.Server(player.inventory, server, Some(server), () => server.machine != null && server.machine.isRunning)`。
   */
  override protected def rackServerMenu(windowId: Int, player: Player, rack: tileentity.Rack, slot: Int): AbstractContainerMenu =
    rack.getMountable(slot) match {
      case server: component.Server =>
        new container.Server(windowId, player.getInventory, server, Option(server),
          () => server.machine != null && server.machine.isRunning)
      case _ => null
    }
}
