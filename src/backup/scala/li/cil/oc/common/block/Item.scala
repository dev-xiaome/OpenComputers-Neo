package li.cil.oc.common.block

import java.util

import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.common.item.data.PrintData
import li.cil.oc.common.tileentity
import li.cil.oc.util.{Color, ItemColorizer, ItemCosts}
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.item.{BlockItem, ItemStack, TooltipFlag}
import net.minecraft.world.level.block.Block
import net.minecraft.world.item.{Item => MCItem}

import scala.jdk.CollectionConverters._

/**
 * OC 方块物品（原 1.7.10 的 `ItemBlock` 子类 `Item`）。
 *
 * 1.21.1 迁移要点：
 *  - 构造为 `class Item(block: Block, properties: Item.Properties) extends BlockItem(block, properties)`
 *    （`ItemBlock` 已由 `BlockItem` 取代，且 1.21.1 的物品属性必须在构造时给出）。
 *    注意本类自己就叫 `Item`，因此原版物品类型统一用别名 [[MCItem]]。
 *  - `addInformation` → `Item#appendHoverText`：方块的提示文本转发给
 *    [[SimpleBlockHooks.addInformation]]（也就是原 `SimpleBlock#addInformation`）。
 *  - `getRarity(stack)` 在 1.21.1 **不存在**（品质改成 `ItemStack` 的 `rarity` 数据组件），
 *    因此改为普通方法 [[rarity]]，由注册层在需要时把结果传给 `Item.Properties#rarity`。
 *    （`SimpleBlockHooks.rarity` 本身保留，见 [[SimpleBlockHooks]]。）
 *  - `getMetadata` / `setHasSubtypes` / `getUnlocalizedName`：1.21.1 没有 metadata，
 *    显示名走 `BlockItem#getDescriptionId`（即方块的 `getDescriptionId`），全部删除。
 *  - `getItemStackDisplayName` → `Item#getName(stack)`（3D 打印件用 `PrintData.label` 作为名字）。
 *  - `getColorFromItemStack` → 1.21.1 需在客户端通过 `RegisterColorHandlersEvent.Item` 注册
 *    `ItemColor`；[[getColorFromItemStack]] 作为普通方法保留，等客户端层接入。
 *  - `isBookEnchantable` 在 1.21.1 已移除，等价语义（OC 方块物品不可附魔）用
 *    `Item#isEnchantable` 表达。
 *  - `placeBlockAt` → `BlockItem#place`：放置成功后让可旋转的方块朝向玩家。
 *    （机器人在创造模式下复制物品栈的逻辑移到了 `block.RobotProxy#onBlockPlacedBy`。）
 *
 * 注意：注册层（`common.init.Registry`）目前给方块注册的是普通 `BlockItem`，
 * 若要让上述行为生效，需要改用本类（不属于本次施工范围，见汇报）。
 * 另外 [[li.cil.oc.common.block.BlockTooltipHandler]] 已经通过 `ItemTooltipEvent` 给
 * **所有** `BlockItem` 补上了方块提示，因此把注册层切到本类时要二选一
 * （要么去掉那个全局监听器，要么让 [[appendHoverText]] 不转发 `addInformation`），
 * 否则提示会重复一遍。
 */
class Item(block: Block, properties: MCItem.Properties) extends BlockItem(block, properties) {

  // ----------------------------------------------------------------------- //
  // 提示
  // ----------------------------------------------------------------------- //

  override def appendHoverText(stack: ItemStack, context: MCItem.TooltipContext, tooltip: util.List[Component], flag: TooltipFlag): Unit = {
    super.appendHoverText(stack, context, tooltip, flag)
    getBlock match {
      case hooks: SimpleBlockHooks =>
        val lines = new util.ArrayList[String]()
        // 1.21.1 的 `appendHoverText` 拿不到玩家；原实现里玩家只用于（未来的）按键提示，
        // 因此这里传 `null`（与 `BlockTooltipHandler` 在无玩家时传 `null` 一致）。
        hooks.addInformation(stack, null, lines, flag.isAdvanced)
        // 原：`if (KeyBindings.showMaterialCosts) ItemCosts.addTooltip(...) else <按住某键的提示>`
        // TODO(客户端): `li.cil.oc.client.KeyBindings` 未移植，这里退化为始终显示材料成本
        // （与 `common.item.traits.SimpleItem` 的处理一致）。
        if (ItemCosts.hasCosts(stack)) {
          ItemCosts.addTooltip(stack, lines)
        }
        for (line <- lines.asScala if line != null && line.nonEmpty) {
          tooltip.add(Component.literal(line))
        }
      case _ => // 不是 OC 方块，交给原版。
    }
  }

  /**
   * 原 `getRarity(stack)`。
   *
   * 1.21.1 的物品品质是 `ItemStack` 的数据组件（`DataComponents.RARITY`），
   * `Item` 上已没有可覆写的 `getRarity`，因此保留为普通方法。
   */
  def rarity(stack: ItemStack): net.minecraft.world.item.Rarity = getBlock match {
    case hooks: SimpleBlockHooks => hooks.rarity(stack)
    case _ => net.minecraft.world.item.Rarity.COMMON
  }

  /** 原 `getItemStackDisplayName`：3D 打印件用保存的标签作为显示名。 */
  override def getName(stack: ItemStack): Component = {
    val printInfo = api.Items.get(Constants.BlockName.Print)
    if (printInfo != null && api.Items.get(stack) == printInfo) {
      val data = new PrintData(stack)
      data.label.map(name => Component.literal(name): Component).getOrElse(super.getName(stack))
    }
    else super.getName(stack)
  }

  /**
   * 原 `getColorFromItemStack(stack, tintIndex)`（线缆按 `ItemColorizer` 的染色显示）。
   *
   * TODO(客户端): 1.21.1 的物品染色要在客户端注册
   * `RegisterColorHandlersEvent.Item` 的 `ItemColor`，`li.cil.oc.client` 尚未移植，
   * 因此这里只保留计算方法。
   */
  def getColorFromItemStack(stack: ItemStack, tintIndex: Int): Int = {
    val cableInfo = api.Items.get(Constants.BlockName.Cable)
    if (cableInfo != null && api.Items.get(stack) == cableInfo) {
      if (ItemColorizer.hasColor(stack)) {
        ItemColorizer.getColor(stack)
      }
      else Color.LightGray
    }
    else 0xFFFFFF
  }

  /** 原 `isBookEnchantable(a, b) = false`；1.21.1 用 `isEnchantable` 表达「不可附魔」。 */
  override def isEnchantable(stack: ItemStack): Boolean = false

  // ----------------------------------------------------------------------- //
  // 放置
  // ----------------------------------------------------------------------- //

  override def place(context: BlockPlaceContext): InteractionResult = {
    val result = super.place(context)
    if (result.consumesAction) {
      // If it's a rotatable block try to make it face the player.
      val level = context.getLevel
      val pos = context.getClickedPos
      val player = context.getPlayer
      if (player != null) {
        level.getBlockEntity(pos) match {
          case keyboard: tileentity.Keyboard =>
            keyboard.setFromEntityPitchAndYaw(player)
            keyboard.setFromFacing(context.getClickedFace)
          case rotatable: tileentity.traits.Rotatable =>
            rotatable.setFromEntityPitchAndYaw(player)
            if (!rotatable.validFacings.contains(rotatable.pitch)) {
              rotatable.pitch = rotatable.validFacings.headOption.getOrElse(Direction.NORTH)
            }
            if (!rotatable.isInstanceOf[tileentity.RobotProxy]) {
              rotatable.invertRotation()
            }
          case _ => // Ignore.
        }
      }
    }
    result
  }
}
