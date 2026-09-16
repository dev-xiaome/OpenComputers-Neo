package li.cil.oc.client

import li.cil.oc.Constants
import li.cil.oc.api.internal
import li.cil.oc.common.block
import li.cil.oc.common.init.Registry
import li.cil.oc.util.{Color, ItemColorizer}
import net.minecraft.client.color.block.BlockColor
import net.minecraft.client.color.item.ItemColor
import net.minecraft.core.BlockPos
import net.minecraft.world.item.{BlockItem, ItemStack}
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.bus.api.{EventPriority, IEventBus}
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent

/**
 * 方块与物品的**染色处理器**（原 1.7.10 的 `colorMultiplier` / `getRenderColor` /
 * `ItemBlock#getColorFromItemStack`）。
 *
 * ==为什么需要它==
 * OC 的机箱 / 屏幕 / 线缆 / 变色石贴图是**灰阶**的，原版 1.7.10 靠 `Block#colorMultiplier`
 * 与 `Item#getColorFromItemStack` 上色（见 `common.block.SimpleBlock` 的说明）。
 * 1.21.1 这两个回调都不存在了，必须由客户端在
 * `RegisterColorHandlersEvent.Block` / `RegisterColorHandlersEvent.Item` 里注册
 * [[net.minecraft.client.color.block.BlockColor]] / [[net.minecraft.client.color.item.ItemColor]]。
 *
 * 颜色本身仍由方块层的钩子决定，这里只负责接线与「方块实体优先」的优先级：
 * {{{
 *   1.7.10                                                1.21.1
 *   SimpleBlock#colorMultiplier(world, x, y, z)           blockColor.getColor(state, level, pos, tintIndex)
 *    ├ traits.Colored（机箱 / 屏幕 / 线缆方块实体）→ 方块实体颜色   ├ internal.Colored（同左，优先）
 *    └ SimpleBlock#getRenderColor(meta)                    └ SimpleBlockHooks#tintColor(state, level, pos, tintIndex)
 *   ItemBlock#getColorFromItemStack(stack, pass)          itemColor.getColor(stack, tintIndex)
 * }}}
 *
 * ==必须有 `tintindex`==
 * 只有在模型面里写了 `"tintindex": 0` 的面才会被染色，因此
 * `case*` / `screen*` / `cable` / `chameliumblock` 的模型都继承
 * `opencomputers_neo:block/tinted_cube`（六面 `tintindex: 0`）。
 *
 * ==接线（重要）==
 * 本对象**不会自己注册**，必须由客户端初始化时显式调用一次：
 * {{{
 *   li.cil.oc.client.ColorHandlers.initialize(modBus)
 * }}}
 * 建议的落点：`li.cil.oc.client.Proxy.initialize(modBus)` 里
 * `RegisterMenuScreensEvent` / `EntityRenderersEvent.RegisterRenderers` 那一组监听器旁边。
 * 注册是幂等的，重复调用无副作用。
 */
object ColorHandlers {

  private var initialized = false

  /**
   * 客户端事件接线；`modBus` 是 mod 事件总线（`@Mod` 构造器的第一个事件总线参数）。
   *
   * `RegisterColorHandlersEvent` 是 **mod 总线**事件（`IModBusEvent`），且只在客户端触发。
   */
  def initialize(modBus: IEventBus): Unit = {
    if (initialized) return
    initialized = true

    modBus.addListener(EventPriority.NORMAL, false, classOf[RegisterColorHandlersEvent.Block],
      new java.util.function.Consumer[RegisterColorHandlersEvent.Block] {
        override def accept(event: RegisterColorHandlersEvent.Block): Unit = registerBlockColors(event)
      })

    modBus.addListener(EventPriority.NORMAL, false, classOf[RegisterColorHandlersEvent.Item],
      new java.util.function.Consumer[RegisterColorHandlersEvent.Item] {
        override def accept(event: RegisterColorHandlersEvent.Item): Unit = registerItemColors(event)
      })
  }

  // ----------------------------------------------------------------------- //
  // 方块
  // ----------------------------------------------------------------------- //

  /**
   * 需要染色的方块（按注册名，即 [[li.cil.oc.Constants.BlockName]] 的常量）。
   *
   * 与原版对应关系：
   *  - `case1/2/3/caseCreative` —— `Case#getRenderColor` → `Color.byTier(tier)`；
   *  - `screen1/2/3` —— `Screen#getRenderColor` → `Color.byTier(tier)`；
   *  - `cable` —— 颜色在方块实体里（[[li.cil.oc.common.tileentity.traits.Colored]]），
   *    未染色时为 [[Color.LightGray]]；
   *  - `chameliumblock` —— `ChameliumBlock#getRenderColor(meta)` → 按 `color` 属性取染料色。
   */
  private lazy val coloredBlockNames: Seq[String] = Seq(
    Constants.BlockName.CaseTier1,
    Constants.BlockName.CaseTier2,
    Constants.BlockName.CaseTier3,
    Constants.BlockName.CaseCreative,
    Constants.BlockName.ScreenTier1,
    Constants.BlockName.ScreenTier2,
    Constants.BlockName.ScreenTier3,
    Constants.BlockName.Cable,
    Constants.BlockName.ChameliumBlock)

  private def registerBlockColors(event: RegisterColorHandlersEvent.Block): Unit = {
    // `Registry.getBlock` 内部就是 `DeferredHolder#value`；本事件在注册表冻结之后才触发，
    // 所以这里能拿到真实方块实例。取不到的（例如常量与注册名不一致）直接跳过，
    // 不影响其它方块。
    val blocks: Array[Block] = coloredBlockNames
      .map(name => Registry.getBlock(name))
      .filter(_ != null)
      .toArray

    if (blocks.isEmpty) return

    event.register(new BlockColor {
      override def getColor(state: BlockState, level: BlockAndTintGetter, pos: BlockPos, tintIndex: Int): Int =
        blockTintColor(state, level, pos, tintIndex)
    }, blocks: _*)
  }

  /**
   * 原 `SimpleBlock#colorMultiplier`：
   * 优先取方块实体的颜色（[[internal.Colored]]），否则回落到方块自己的 [[block.SimpleBlockHooks.tintColor]]。
   *
   * `level` / `pos` 在物品形态或某些渲染路径下可能为 `null`，因此全部做了判空。
   */
  private def blockTintColor(state: BlockState, level: BlockAndTintGetter, pos: BlockPos, tintIndex: Int): Int = {
    if (state == null || state.getBlock == null) return 0xFFFFFF

    if (level != null && pos != null) {
      val blockEntity = level.getBlockEntity(pos)
      if (blockEntity != null) blockEntity match {
        case colored: internal.Colored => return colored.getColor
        case _ =>
      }
    }

    state.getBlock match {
      case hooks: block.SimpleBlockHooks => hooks.tintColor(state, level, pos, tintIndex)
      case _ => 0xFFFFFF
    }
  }

  // ----------------------------------------------------------------------- //
  // 物品
  // ----------------------------------------------------------------------- //

  /**
   * 物品形态的染色。
   *
   * 1.7.10 的 `ItemBlock#getColorFromItemStack` 会回落到方块的 `getRenderColor(metadata)`，
   * 所以机箱 / 屏幕的物品（含创造模式标签页与手持）按等级着色；`block.Item` 另外覆盖了
   * 线缆一种情况：颜色存在堆叠的 NBT 里（[[ItemColorizer]]），没写着色时是 [[Color.LightGray]]。
   *
   * ==绝对不要把软盘 / 硬盘（`floppy` / `hdd1` / `hdd2` / `hdd3`）注册进来==
   * 曾有一版把 `Constants.ItemName.Floppy` 与 `HDDTier1` 到 `HDDTier3` 也交给了
   * [[ItemColor]]，想用「按 `oc:color` 返回染料色」代替 1.7.10 的 16 张独立贴图，
   * 结果**所有软盘与硬盘的图标在实机里整体变暗到几乎看不见**（玩家报告「图标消失了」）。
   * 原因是：
   *  - `item/generated`（这几个物品模型的 parent）的 `layer0` **带 `tintindex: 0`**
   *    （原版就是靠它给刷怪蛋 / 皮革盔甲上色的），所以这里注册的返回值**确实会生效**，
   *    并不是「没有 tintindex 的无效注册」；
   *  - 而 `item/floppydisk_dye*` 与 `item/harddiskdrive*` 这几十张贴图**本身就是有颜色的**
   *    （实测平均亮度约 107 到 132）。[[ItemColor]] 只能把颜色**乘**上去：
   *    未写着色时回落到 `dyes(8)`（即 `dyeGray` = 0x666666，约 40% 亮度），
   *    相乘后图标平均亮度掉到 43 到 53，在创造模式物品栏的深色底上等于「消失」。
   *  - `openos` 这个条目在创造标签页里渲染的**就是 `floppy` 的堆叠**
   *    （见 `Registry.registerStackItem`），所以它跟着一起变暗 ——
   *    玩家看到的现象才是「**所有**软盘的图标都没了」。
   *
   * 1.21.1 的正确做法是「按 `oc:color` 换**模型**」，而不是「给同一张贴图染色」：
   * 用 `ItemProperties.register` 注册一个读 `oc:color` 的 predicate，
   * 再在 `models/item/floppy.json` 里写 `overrides` 指向 16 个分别引用
   * `floppydisk_dyeblack` 到 `floppydisk_dyewhite` 的子模型，
   * 等价于 1.7.10 的 `FloppyDisk#icons(0..15)`。这需要「注册 predicate」的代码
   * 与 16 个子模型，属于独立的一次改动。
   */
  private def registerItemColors(event: RegisterColorHandlersEvent.Item): Unit = {
    val items = coloredBlockNames
      .map(name => Registry.getItem(name))
      .filter(_ != null)
      .distinct
      .toArray

    if (items.isEmpty) return

    event.register(new ItemColor {
      override def getColor(stack: ItemStack, tintIndex: Int): Int = itemTintColor(stack, tintIndex)
    }, items: _*)
  }

  private def itemTintColor(stack: ItemStack, tintIndex: Int): Int = {
    if (stack == null || stack.isEmpty) return 0xFFFFFF

    if (isCableItem(stack)) {
      // 原 `block.Item#getColorFromItemStack`：线缆按堆叠里保存的颜色绘制，否则浅灰。
      return if (ItemColorizer.hasColor(stack)) ItemColorizer.getColor(stack) else Color.LightGray
    }

    stack.getItem match {
      case blockItem: BlockItem => blockTintColor(blockItem.getBlock.defaultBlockState(), null, null, tintIndex)
      case _ => 0xFFFFFF
    }
  }

  /** 该堆叠是不是未染色的「线缆」方块物品（`api.Items.get(stack)` 的等价判断）。 */
  private def isCableItem(stack: ItemStack): Boolean = {
    val cable = Registry.getItem(Constants.BlockName.Cable)
    cable != null && (stack.getItem eq cable)
  }
}
