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

  /**
   * 可染色的「存储类」物品（软盘 / 硬盘）的注册名。
   *
   * 1.7.10 给软盘准备了 **16 张按染料命名的贴图**（`FloppyDisk#icon` 读 `oc:color`
   * 去选 `icons(0..15)`）；1.21.1 只有一张灰阶贴图，因此改为按 `oc:color` 染色。
   * 不注册这一段的话，**OpenOS 软盘和空白软盘看起来完全一样（都是灰的）**，
   * 玩家没法分辨哪张是可启动的系统盘。
   */
  private val dyeableStorageNames: Seq[String] = Seq(
    Constants.ItemName.Floppy,
    Constants.ItemName.HDDTier1,
    Constants.ItemName.HDDTier2,
    Constants.ItemName.HDDTier3)

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
   */
  private def registerItemColors(event: RegisterColorHandlersEvent.Item): Unit = {
    val blockItems = coloredBlockNames
      .map(name => Registry.getItem(name))
      .filter(_ != null)

    // 可染色的「存储类」物品（软盘 / 硬盘），见 [[dyeableStorageNames]] 的说明。
    val dyeableItems = dyeableStorageNames.map(Registry.getItem).filter(_ != null)

    val items = (blockItems ++ dyeableItems).distinct.toArray

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

    // 软盘 / 硬盘：颜色存在 `oc:color`（染料索引 0-15，与原版 `FloppyDisk#icon` 读的是同一个键），
    // 未写着色时用 `dyes(8)`（浅灰），与原版 `icons(8)` 的默认值一致。
    if (dyeableStorageNames.map(Registry.getItem).exists(i => i != null && (i eq stack.getItem))) {
      val tag = li.cil.oc.util.ItemNBT.get(stack)
      val key = li.cil.oc.Settings.namespace + "color"
      val index = if (tag != null && tag.contains(key)) (tag.getInt(key) max 0 min 15) else 8
      return Color.byOreName(Color.dyes(index))
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
