package li.cil.oc.common.container

import java.util.function.Supplier

import li.cil.oc.common.init.Registry
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.MenuType
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
 *  - **服务端**通过 [[MenuOpening.open]]（内部是 `player.openMenu(MenuProvider)`）创建容器并分配 `windowId`；
 *  - **客户端**收到 `ClientboundOpenScreenPacket` 后调用
 *    `MenuType#create(windowId, inventory, payload)` 重建容器 —— 也就是下面这些工厂。
 *
 * 注册时机：[[Registry.init]] 会调用 [[register]]，必须发生在 mod 构造期
 * （`menus.register(modBus)` 与注册表事件之间），因此**不能**依赖懒加载。
 *
 * ==降级说明==
 * 客户端重建容器需要的「额外数据」（方块坐标 / 实体 id / 平板堆栈等）由
 * `MenuProvider#writeClientSideData` 决定，而打开 GUI 的链路
 * （`common/GuiHandler`、`client/gui` 包、`client/GuiHandler`）整体尚未移植。
 * 在这些内容移植之前，下面每个工厂都会抛出带说明的
 * `UnsupportedOperationException` —— 它只在「客户端真的收到打开界面的包」时才会执行，
 * 而当前工程里没有任何地方会发出这种包，所以不影响启动与运行。
 *
 * TODO(client.gui): 移植 `client` 包时，把每个工厂改成：
 * {{{
 *   IMenuTypeExtension.create((windowId, inventory, data) => {
 *     val pos = data.readBlockPos()
 *     inventory.player.level().getBlockEntity(pos) match {
 *       case host: tileentity.Xxx => new Xxx(windowId, inventory, host)
 *       case _ => null
 *     }
 *   })
 * }}}
 * 并让 [[MenuOpening]] 换成会写 `writeClientSideData` 的自定义 `MenuProvider`
 * （物品 / 实体宿主 —— `Database`、`Drone`、`Server`、`Tablet` —— 需要各自的载荷格式）。
 */
object MenuTypes {
  /** 统一的持有类型：`DeferredHolder` 的第二个类型参数不变，无法还原具体类型。 */
  type MenuHolder = DeferredHolder[MenuType[_], MenuType[_]]

  // ----------------------------------------------------------------------- //
  // 注册表（名称与 `common/container` 下的类名一一对应）
  // ----------------------------------------------------------------------- //

  val Adapter: MenuHolder = register[Adapter]("adapter")
  val Assembler: MenuHolder = register[Assembler]("assembler")
  val Case: MenuHolder = register[Case]("case")
  val Charger: MenuHolder = register[Charger]("charger")
  val Database: MenuHolder = register[Database]("database")
  val Disassembler: MenuHolder = register[Disassembler]("disassembler")
  val DiskDrive: MenuHolder = register[DiskDrive]("diskdrive")
  val Drone: MenuHolder = register[Drone]("drone")
  val Printer: MenuHolder = register[Printer]("printer")
  val Rack: MenuHolder = register[Rack]("rack")
  val Raid: MenuHolder = register[Raid]("raid")
  val Relay: MenuHolder = register[Relay]("relay")
  val Robot: MenuHolder = register[Robot]("robot")
  val Server: MenuHolder = register[Server]("server")
  val Switch: MenuHolder = register[Switch]("switch")
  val Tablet: MenuHolder = register[Tablet]("tablet")

  // ----------------------------------------------------------------------- //
  // 注册
  // ----------------------------------------------------------------------- //

  /** 由 [[Registry.init]] 在 mod 构造期调用一次；重复调用是安全的（`DeferredRegister` 自身去重）。 */
  def register(): Unit = {
    // 访问一下各 `val` 只是为了确保本对象被初始化 —— `val` 的初始化过程本身就是注册过程。
    val _ = Seq(Adapter, Assembler, Case, Charger, Database, Disassembler, DiskDrive, Drone,
      Printer, Rack, Raid, Relay, Robot, Server, Switch, Tablet)
  }

  private def register[T <: Player](name: String): MenuHolder =
    Registry.registerMenu(name, new Supplier[MenuType[_]] {
      override def get(): MenuType[_] = IMenuTypeExtension.create[T](new IContainerFactory[T] {
        override def create(windowId: Int, inventory: Inventory, data: RegistryFriendlyByteBuf): T =
          throw new UnsupportedOperationException(
            "OpenComputers Neo: 菜单 '" + name + "' 的客户端重建工厂尚未实现。" +
              "TODO(client.gui): 请把 MenuProvider#writeClientSideData 写入的载荷（方块坐标 / 实体 id）" +
              "在这里读出来，再用 inventory.player.level() 重建宿主对象。详见 MenuTypes 的文档注释。")
      })
    })
}
