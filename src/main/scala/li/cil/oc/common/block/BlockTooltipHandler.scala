package li.cil.oc.common.block

import li.cil.oc.util.Tooltip
import net.minecraft.network.chat.Component
import net.minecraft.world.item.BlockItem
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent

import scala.jdk.CollectionConverters._

/**
 * 方块物品的物品提示（tooltip）。
 *
 * 1.7.10 里 `SimpleBlock#addInformation` 由 OC 自己的 `ItemBlock` 调用；
 * 1.21.1 的物品提示挂在 `Item#appendHoverText` 上，而本项目的方块物品由注册层
 * （[[li.cil.oc.common.init.Registry#registerBlockItem]]）统一创建成普通 `BlockItem`，
 * 所以这里用 NeoForge 的 [[ItemTooltipEvent]] 把方块自己的提示补回去。
 *
 * 注册时机：`Registry.Blocks.initBlocks()` 里调用一次 [[register]]。
 */
object BlockTooltipHandler {

  private var registered = false

  def register(): Unit = {
    if (registered) return
    registered = true
    NeoForge.EVENT_BUS.addListener(classOf[ItemTooltipEvent], new java.util.function.Consumer[ItemTooltipEvent] {
      override def accept(event: ItemTooltipEvent): Unit = onItemTooltip(event)
    })
  }

  private def onItemTooltip(event: ItemTooltipEvent): Unit = {
    val stack = event.getItemStack
    if (stack == null || stack.isEmpty) return
    stack.getItem match {
      case item: BlockItem => item.getBlock match {
        case block: SimpleBlockHooks =>
          val lines = new java.util.ArrayList[String]()
          try {
            block.addInformation(stack, event.getEntity, lines, event.getFlags.isAdvanced)
          }
          catch {
            case t: Throwable =>
              // 提示构建不应影响游戏（例如本地化缺失）。
              li.cil.oc.OpenComputersNeo.log.debug(s"Failed to build the tooltip of ${block}.", t)
          }
          for (line <- lines.asScala if line != null && line.nonEmpty) {
            event.getToolTip.add(Component.literal(line))
          }
        case _ => // 不是 OC 方块。
      }
      case _ => // 不是方块物品。
    }
  }

  /** 供测试/调试：直接取某个方块的提示文本行。 */
  def linesOf(block: SimpleBlockHooks, stack: net.minecraft.world.item.ItemStack): java.util.List[String] = {
    val lines = new java.util.ArrayList[String]()
    block.addInformation(stack, null, lines, advanced = false)
    lines
  }

  /** 与 [[Tooltip]] 保持一致：把提示行转成 `Component`。 */
  def toComponents(lines: java.util.List[String]): java.util.List[Component] =
    lines.asScala.map(line => Component.literal(line): Component).asJava
}
