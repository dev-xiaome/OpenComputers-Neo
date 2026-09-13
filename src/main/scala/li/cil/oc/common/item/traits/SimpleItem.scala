package li.cil.oc.common.item.traits

import java.util

import li.cil.oc.util.ItemStackNBTExtensions._
import li.cil.oc.util.{ItemCosts, Tooltip}
import scala.jdk.CollectionConverters._

import net.minecraft.network.chat.Component

import net.minecraft.world.item.{Item, ItemStack, TooltipFlag}

/**
 * 「简单物品」基类（原 1.7.10 的 `SimpleItem`）。
 *
 * 1.21.1 迁移要点：
 *  - 1.7.10 用 `setUnlocalizedName("oc." + id)` 改显示名，1.21.1 没有该 API：
 *    改为覆写 [[Item#getDescriptionId]]，返回 `"oc." + unlocalizedName`。
 *    语言文件里对应的键是 `item.oc.<unlocalizedName>.name`，与已迁移的
 *    `assets/opencomputers_neo/lang` 下的 json 保持一致。
 *  - `setTextureName` / `getChestGenBase` / `doesSneakBypassUse` 在 1.21.1 已不存在
 *    （贴图走模型 JSON，战利品表走数据包，潜行旁路改由方块自身处理），全部移除。
 *  - `addInformation` → [[Item#appendHoverText]]，提示内容从 `String` 改为 [[Component]]。
 *
 * 用法：
 * {{{
 *   // 每个 Constants.ItemName.* 对应一个独立物品，直接继承本 trait。
 *   class Wrench extends SimpleItem
 *   class Memory(tier: Int) extends SimpleItem { override val unlocalizedName = "Memory" + tier }
 * }}}
 */
trait SimpleItem extends Item {

  /** 物品等级；普通物品不参与名字后缀，保持 `None` 即可。 */
  val tier: Int = li.cil.oc.common.Tier.None

  /** 对应 1.7.10 的 unlocalized name。默认取类名（PascalCase，与语言文件一致）。 */
  val unlocalizedName: String = getClass.getSimpleName

  /** 翻译键（不含 `.name` 后缀）。等价于原 `setUnlocalizedName("oc." + id)`。 */
  override def getDescriptionId: String = "oc." + unlocalizedName

  /** 等价于原 1.7.10 的 `new ItemStack(this, amount)`。 */
  def createItemStack(amount: Int = 1): ItemStack = new ItemStack(this, amount)

  /**
   * 1.21.1 不再有 `isBookEnchantable`；附魔台是否接受由 `isEnchantable` /
   * `getEnchantmentValue` 决定。OC 的物品默认不可附魔。
   */
  override def isEnchantable(stack: ItemStack): Boolean = false

  override def appendHoverText(stack: ItemStack, context: Item.TooltipContext,
                               tooltip: util.List[Component], flag: TooltipFlag): Unit = {
    super.appendHoverText(stack, context, tooltip, flag)
    appendSimpleTooltip(stack, tooltip)
  }

  /**
   * 原 `SimpleItem.addInformation` 的主体，抽成单独方法方便 [[Delegate]] 复用。
   *
   * `ItemCosts` / `Tooltip` 是 util 层已移植的代码，接口仍是
   * `java.util.List[String]`，这里做一次 `Component` ↔ `String` 的桥接。
   */
  protected def appendSimpleTooltip(stack: ItemStack, tooltip: util.List[Component]): Unit = {
    val lines = new util.ArrayList[String]()
    lines.addAll(Tooltip.get(getClass.getSimpleName))
    appendCostsTooltip(stack, lines)
    appendAddressTooltip(stack, lines)
    lines.asScala.foreach(line => tooltip.add(Component.literal(line)))
  }

  /** 材料成本提示（原 `ItemCosts.hasCosts` / `addTooltip` 分支）。 */
  protected def appendCostsTooltip(stack: ItemStack, lines: util.List[String]): Unit = {
    if (ItemCosts.hasCosts(stack)) {
      // TODO(客户端): 原版会判断 `KeyBindings.showMaterialCosts`（客户端包尚未移植），
      // 这里退化为始终显示成本，等 `li.cil.oc.client.KeyBindings` 移植后恢复。
      ItemCosts.addTooltip(stack, lines)
    }
  }

  /** 已有节点地址的组件物品在提示里显示地址前缀。 */
  protected def appendAddressTooltip(stack: ItemStack, lines: util.List[String]): Unit = {
    if (stack.hasTag()) {
      val tag = stack.getTag()
      if (tag.contains(li.cil.oc.Settings.namespace + "data")) {
        val data = tag.getCompound(li.cil.oc.Settings.namespace + "data")
        if (data.contains("node") && data.getCompound("node").contains("address")) {
          lines.add("§8" + data.getCompound("node").getString("address").substring(0, 13) + "...§7")
        }
      }
    }
  }
}
