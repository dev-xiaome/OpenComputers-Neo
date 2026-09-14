package li.cil.oc.common.container

import net.minecraft.network.chat.Component
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.player.{Inventory, Player => MCPlayer}
import net.minecraft.world.inventory.{AbstractContainerMenu, MenuConstructor}

/**
 * 服务端打开容器的辅助（取代 1.7.10 的 `player.openGui(mod, guiId, world, x, y, z)`）。
 *
 * 1.7.10 的 `IGuiHandler` 会在服务端按 `guiId` 反射 new 一个 `Container`、在客户端 new 一个 `GuiContainer`；
 * 1.21.1 改成分两步：
 *  - **服务端**：`player.openMenu(MenuProvider)`，`MenuProvider#createMenu` 拿到
 *    `windowId` 后 new 出容器（本对象负责这一步）；
 *  - **客户端**：由 `MenuType#create(windowId, inventory, payload)` 重建容器，`MenuType`
 *    登记在 [[MenuTypes]] 里。
 *
 * 用法（示例）：
 * {{{
 *   MenuOpening.open(player, title)((windowId, inventory) => new Adapter(windowId, inventory, adapter))
 * }}}
 *
 * TODO(客户端): 上面第二步在 `client` 包与 GUI 打开链路移植完成前还不可用，
 * 详见 [[MenuTypes]] 的说明。
 */
object MenuOpening {
  /** 在服务端给玩家打开一个容器；`factory` 拿到的 `windowId` 必须原样传给容器构造器。 */
  def open(player: MCPlayer, title: Component)(factory: (Int, Inventory) => AbstractContainerMenu): Unit = {
    if (player == null) return
    player.openMenu(new SimpleMenuProvider(new MenuConstructor {
      override def createMenu(windowId: Int, inventory: Inventory, ignored: MCPlayer): AbstractContainerMenu =
        factory(windowId, inventory)
    }, if (title == null) Component.empty() else title))
  }
}
