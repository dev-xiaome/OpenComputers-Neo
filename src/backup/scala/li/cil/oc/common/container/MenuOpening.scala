package li.cil.oc.common.container

import java.util.function.Consumer

import li.cil.oc.common.GuiType
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.{Inventory, Player => MCPlayer}
import net.minecraft.world.inventory.{AbstractContainerMenu, MenuConstructor}
import net.minecraft.world.item.ItemStack
import net.minecraft.world.{MenuProvider => MCMenuProvider}

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
 * ==关键：客户端要靠载荷重建宿主==
 * 1.21.1 的 `SimpleMenuProvider` 是 `final` 的，而且它**不会**写任何自定义数据，
 * 客户端拿到窗口 id 后根本不知道「这个容器对应哪个方块 / 实体 / 物品」。
 * 因此这里自己实现 [[MCMenuProvider]]，在 `writeClientSideData` 里用
 * [[MenuHostPayload]] 把宿主信息写下去 —— 客户端的 [[MenuTypes]] 工厂再用同一套格式读出来。
 * 写与读严格配对，改动其一必须同步另一处。
 *
 * 用法（示例）：
 * {{{
 *   MenuOpening.openBlock(player, pos)((windowId, inventory) => new Adapter(windowId, inventory, adapter))
 * }}}
 */
object MenuOpening {
  // ----------------------------------------------------------------------- //
  // 统一入口
  // ----------------------------------------------------------------------- //

  /**
   * 打开一个「方块实体宿主」的容器。
   *
   * @param pos 宿主方块坐标；客户端 `level.getBlockEntity(pos)` 取回宿主
   */
  def openBlock(player: MCPlayer, pos: BlockPos, title: Component)
               (factory: (Int, Inventory) => AbstractContainerMenu): Unit =
    open(player, title, buf => MenuHostPayload.writeBlock(buf, pos)) { factory }

  /** 打开一个「实体宿主」的容器（无人机）。 */
  def openEntity(player: MCPlayer, entityId: Int, title: Component)
                (factory: (Int, Inventory) => AbstractContainerMenu): Unit =
    open(player, title, buf => MenuHostPayload.writeEntity(buf, entityId)) { factory }

  /** 打开一个「机架插槽里的可插拔组件」的容器。 */
  def openRackSlot(player: MCPlayer, pos: BlockPos, slot: Int, title: Component)
                  (factory: (Int, Inventory) => AbstractContainerMenu): Unit =
    open(player, title, buf => MenuHostPayload.writeRackSlot(buf, pos, slot)) { factory }

  /**
   * 打开一个「主手物品宿主」的容器（数据库升级 / 服务器 / 软盘驱动器 / 平板）。
   *
   * 载荷里写的是**打开界面那一刻的**[[net.minecraft.world.item.ItemStack]] 快照，
   * 客户端据此重建幽灵物品栏；物品本身仍然只存在于玩家背包里。
   */
  def openItemInHand(player: MCPlayer, stack: ItemStack, title: Component)
                    (factory: (Int, Inventory) => AbstractContainerMenu): Unit =
    open(player, title, buf => MenuHostPayload.writeItemInHand(buf, if (stack == null) ItemStack.EMPTY else stack)) { factory }

  /** 打开界面，但不附带任何宿主信息（工厂侧会收到 [[MenuHostPayload.Invalid]]）。 */
  def openInvalid(player: MCPlayer, title: Component)
                 (factory: (Int, Inventory) => AbstractContainerMenu): Unit =
    open(player, title, buf => MenuHostPayload.writeInvalid(buf)) { factory }

  // ----------------------------------------------------------------------- //
  // 实现
  // ----------------------------------------------------------------------- //

  /**
   * 在服务端给玩家打开一个容器。
   *
   * @param player  目标玩家（必须是 [[net.minecraft.server.level.ServerPlayer]] 才会真正发包含载荷的包）
   * @param title   界面标题
   * @param write   往载荷里写宿主信息；**必须**与 `MenuTypes` 里对应工厂的读法配对
   * @param factory 拿 `windowId` 造容器；`windowId` 必须原样传给容器构造器
   */
  def open(player: MCPlayer,
           title: Component,
           write: RegistryFriendlyByteBuf => Unit)
          (factory: (Int, Inventory) => AbstractContainerMenu): Unit = {
    if (player == null) return
    player.openMenu(new HostMenuProvider(
      if (title == null) Component.empty() else title,
      new MenuConstructor {
        override def createMenu(windowId: Int, inventory: Inventory, ignored: MCPlayer): AbstractContainerMenu =
          factory(windowId, inventory)
      },
      write))
  }

  /**
   * 写「宿主信息」的自定义 `MenuProvider`。
   *
   * `SimpleMenuProvider` 是 `final` 的且不写自定义数据，所以只能自己实现；
   * `writeClientSideData` 是 NeoForge 的 `IMenuProviderExtension` 提供的默认方法，
   * 通过 override 接上即可。
   */
  private final class HostMenuProvider(title: Component,
                                       constructor: MenuConstructor,
                                       write: RegistryFriendlyByteBuf => Unit)
    extends MCMenuProvider {

    override def getDisplayName: Component = title

    override def createMenu(windowId: Int, inventory: Inventory, player: MCPlayer): AbstractContainerMenu =
      constructor.createMenu(windowId, inventory, player)

    override def writeClientSideData(menu: AbstractContainerMenu, buffer: RegistryFriendlyByteBuf): Unit =
      write(buffer)
  }

  /**
   * 1.7.10 风格的重载：按 [[GuiType]] 的 id 生成一个 `MenuConstructor`（不含载荷）。
   *
   * 保留它只是为了兼容旧调用点；**新代码请用带 `pos` / `stack` 的重载**，
   * 否则客户端无法重建宿主（[[MenuTypes]] 的工厂会返回 `null`）。
   */
  def simple(title: Component)(factory: (Int, Inventory) => AbstractContainerMenu): MCMenuProvider =
    new HostMenuProvider(
      if (title == null) Component.empty() else title,
      new MenuConstructor {
        override def createMenu(windowId: Int, inventory: Inventory, ignored: MCPlayer): AbstractContainerMenu =
          factory(windowId, inventory)
      },
      buf => MenuHostPayload.writeInvalid(buf))

  /** 供 Java / 非 Scala 调用点使用的适配器。 */
  private type WriteConsumer = Consumer[RegistryFriendlyByteBuf]
}
