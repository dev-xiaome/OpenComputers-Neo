package li.cil.oc.common.item.traits

import java.util

import li.cil.oc.api
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ItemStackNBTExtensions._
import li.cil.oc.util.Rarity
import scala.jdk.CollectionConverters._

import net.minecraft.network.chat.Component

import net.minecraft.world.InteractionHand

import net.minecraft.world.InteractionResultHolder

import net.minecraft.world.entity.player.Player

import net.minecraft.world.item.{Item, ItemStack, TooltipFlag, UseAnim}

import net.minecraft.world.item.context.UseOnContext

import net.minecraft.world.level.Level

/**
 * 「Delegator 子项」的行为基类（原 1.7.10 的 `Delegate`）。
 *
 * 1.7.10 用它承载「一个物品 + damage 值」的子类型；1.21.1 改为**每个子类型一个独立物品**，
 * 因此这里不再有 `parent: Delegator` / `itemId` / `createItemStack` 的 damage 派发，
 * 只保留「子项特有行为」的默认实现，供具体物品类覆写。
 *
 * 迁移映射：
 *  - `onItemUse` → [[Item#useOn]]（`UseOnContext` 同时携带世界/位置/朝向/手）
 *  - `onItemRightClick` → [[Item#use]]
 *  - `getItemUseAction` → [[Item#getUseAnimation]]
 *  - `onPlayerStoppedUsing` → [[Item#releaseUsing]]（时长语义相反：旧版是剩余、新版是已使用）
 *  - `update` → [[Item#inventoryTick]]
 *  - `getContainerItem` / `hasContainerItem` → [[Item#getCraftingRemainingItem]] / [[Item#hasCraftingRemainingItem]]
 *  - `icon` / `registerIcons` 已删除（1.21.1 走模型 JSON + `ItemColor`）
 *  - `doesSneakBypassUse` 已删除（1.21.1 由方块/`BlockItem` 侧决定）
 *
 * 具体物品类的推荐写法：
 * {{{
 *   class Memory(val tier: Int) extends Delegate {
 *     override val unlocalizedName = "Memory" + tier
 *   }
 * }}}
 */
trait Delegate extends SimpleItem {

  /**
   * 分级物品的 unlocalized name：`类名 + tier`（与语言文件键一致，例如
   * `Memory` + 0 → `item.oc.Memory0.name`）。不分级物品返回类名本身。
   */
  override def unlocalizedName: String = SimpleItem.tieredName(getClass, tier)

  /** 原 `tooltipName`：用于 `Tooltip.get(...)` 的提示名；默认取类名，`None` 表示不显示。 */
  protected def tooltipName: Option[String] = Option(getClass.getSimpleName)

  /** 提示用的附加参数。 */
  protected def tooltipData: Seq[Any] = Seq.empty[Any]

  /** 是否出现在创造模式标签页（1.21.1 由 [[li.cil.oc.common.init.Registry]] 决定，这里仅作标记）。 */
  var showInItemList: Boolean = true

  /** 原 `maxStackSize`（1.21.1 由 `Item.Properties#stacksTo` 决定，这里仅作标记）。 */
  def maxStackSize: Int = 64

  // ----------------------------------------------------------------------- //
  // 交互钩子
  // ----------------------------------------------------------------------- //

  /** 原 `onItemRightClick`。 */
  def onItemRightClick(stack: ItemStack, world: Level, player: Player): ItemStack = stack

  /**
   * 原 1.7.10 `onItemUse(stack, player, world, x, y, z, side, hitX, hitY, hitZ)` 的等价签名。
   *
   * 1.21.1 的入口是 `Item#useOn(UseOnContext)`；为了不让每个物品类都重复拆包，
   * 这里保留旧签名作为可覆写钩子，由 [[useOn]] 调用。
   */
  def onItemUse(stack: ItemStack, player: Player, position: BlockPosition,
                side: Int, hitX: Float, hitY: Float, hitZ: Float): Boolean = false

  /** 原 `onItemUse` 的 `UseOnContext` 形式（内部转发到旧签名）。 */
  def onItemUse(stack: ItemStack, context: UseOnContext): Boolean =
    onItemUse(stack, context.getPlayer,
      BlockPosition(context.getClickedPos.getX, context.getClickedPos.getY, context.getClickedPos.getZ, context.getLevel),
      context.getClickedFace.ordinal, context.getClickLocation.x.toFloat, context.getClickLocation.y.toFloat,
      context.getClickLocation.z.toFloat)

  /** 原 `onItemUseFirst` 的等价签名（`player.isShiftKeyDown` 即旧版的 sneak 语义）。 */
  def onItemUseFirst(stack: ItemStack, player: Player, position: BlockPosition,
                     side: Int, hitX: Float, hitY: Float, hitZ: Float): Boolean = false

  /**
   * 原 `onItemUseFirst` 的 `UseOnContext` 形式。
   *
   * 注意：NeoForge 的 `IItemExtension` 已有同名同参方法（返回 `InteractionResult`），
   * 因此这里改名加 `Hook` 后缀避免与接口方法冲突，由 [[useOn]] 负责分派。
   */
  def onItemUseFirstHook(stack: ItemStack, context: UseOnContext): Boolean =
    onItemUseFirst(stack, context.getPlayer,
      BlockPosition(context.getClickedPos.getX, context.getClickedPos.getY, context.getClickedPos.getZ, context.getLevel),
      context.getClickedFace.ordinal, context.getClickLocation.x.toFloat, context.getClickLocation.y.toFloat,
      context.getClickLocation.z.toFloat)

  /**
   * 原 1.7.10 `onEaten` 的 1.21.1 入口。
   *
   * 1.7.10 在 `onEaten` 里自行减少堆叠数量；1.21.1 由 `Item#finishUsingItem`
   * 负责「消耗一个」的语义，这里只做转接，避免每个可食用物品重复实现。
   */
  override def finishUsingItem(stack: ItemStack, world: Level,
                               entity: net.minecraft.world.entity.LivingEntity): ItemStack = {
    entity match {
      case player: Player => onEaten(stack, world, player)
      case _ => stack
    }
  }

  /** 原 `getItemUseAction`。 */
  def getItemUseAction(stack: ItemStack): UseAnim = UseAnim.NONE

  /** 原 `getMaxItemUseDuration`。 */
  def getMaxItemUseDuration(stack: ItemStack): Int = 0

  /** 原 `onEaten`（1.21.1 的进食走 `Item#finishUsingItem`，这里保留 OC 自有钩子）。 */
  def onEaten(stack: ItemStack, world: Level, player: Player): ItemStack = stack

  /** 原 `onPlayerStoppedUsing`。 */
  def onPlayerStoppedUsing(stack: ItemStack, player: Player, duration: Int): Unit = ()

  /** 原 `update`。 */
  def update(stack: ItemStack, world: Level, player: net.minecraft.world.entity.Entity,
             slot: Int, selected: Boolean): Unit = ()

  // ----------------------------------------------------------------------- //
  // Item 覆写（把 1.21.1 的入口转接到上面的钩子）
  // ----------------------------------------------------------------------- //

  override def use(world: Level, player: Player, hand: InteractionHand): InteractionResultHolder[ItemStack] = {
    val stack = player.getItemInHand(hand)
    val result = onItemRightClick(stack, world, player)
    InteractionResultHolder.sidedSuccess(if (result == null) stack else result, world.isClientSide)
  }

  override def useOn(context: UseOnContext): net.minecraft.world.InteractionResult = {
    val stack = context.getItemInHand
    if (onItemUseFirstHook(stack, context)) net.minecraft.world.InteractionResult.SUCCESS
    else if (onItemUse(stack, context)) net.minecraft.world.InteractionResult.SUCCESS
    else net.minecraft.world.InteractionResult.PASS
  }

  override def getUseAnimation(stack: ItemStack): UseAnim = getItemUseAction(stack)

  override def getUseDuration(stack: ItemStack, entity: net.minecraft.world.entity.LivingEntity): Int =
    getMaxItemUseDuration(stack)

  override def releaseUsing(stack: ItemStack, world: Level, entity: net.minecraft.world.entity.LivingEntity,
                            timeLeft: Int): Unit = {
    entity match {
      case player: Player => onPlayerStoppedUsing(stack, player, getUseDuration(stack, entity) - timeLeft)
      case _ =>
    }
  }

  override def inventoryTick(stack: ItemStack, world: Level, entity: net.minecraft.world.entity.Entity,
                             slot: Int, selected: Boolean): Unit =
    update(stack, world, entity, slot, selected)

  // ----------------------------------------------------------------------- //
  // 品质 / 颜色 / 容器
  // ----------------------------------------------------------------------- //

  /**
   * 原 `getRarity(stack)`。
   *
   * 1.21.1 的物品品质在注册时通过 `Item.Properties#rarity` 固定，`Item` 上**没有**可覆写的
   * `getRarity`，因此这里保留为普通钩子；具体物品若要按等级显示品质，
   * 应在注册时把 [[rarity]] 的结果传给 `Item.Properties#rarity`。
   */
  def rarity(stack: ItemStack): net.minecraft.world.item.Rarity = Rarity.byTier(tierFromDriver(stack))

  protected def tierFromDriver(stack: ItemStack): Int =
    api.Driver.driverFor(stack) match {
      case driver: api.driver.Item => driver.tier(stack)
      case _ => 0
    }

  /**
   * 原 `color(stack, pass)`：1.21.1 的染色改由 `RegisterColorHandlersEvent.Item` +
   * `ItemColor` 处理（客户端阶段），这里保留钩子供后续注册。
   */
  def color(stack: ItemStack, pass: Int): Int = 0xFFFFFF

  /** 原 `getContainerItem`：1.21.1 通过 `Item.Properties#craftRemainder` 声明。 */
  def getContainerItem(stack: ItemStack): ItemStack = null

  /** 原 `hasContainerItem`。 */
  def hasContainerItem(stack: ItemStack): Boolean = false

  /** 原 `displayName`：1.21.1 的名字是 [[Component]]，这里返回可选覆盖值。 */
  def displayName(stack: ItemStack): Option[Component] = None

  override def getName(stack: ItemStack): Component = displayName(stack) match {
    case Some(name) => name
    case _ => super.getName(stack)
  }

  // ----------------------------------------------------------------------- //
  // 提示
  // ----------------------------------------------------------------------- //

  override def appendHoverText(stack: ItemStack, context: Item.TooltipContext,
                               tooltip: util.List[Component], flag: TooltipFlag): Unit = {
    // 不调用 `super`（`SimpleItem` 的通用提示由 tooltipLines 统一处理）。
    tooltipLines(stack, tooltip)
  }

  /**
   * 原 `tooltipLines`（`player` / `advanced` 参数在 1.21.1 由 `TooltipFlag` 表达：
   * `flag.isAdvanced` 即旧版的 `advanced`；玩家不再可用，需要时用
   * `net.minecraft.client.Minecraft.getInstance().player`）。
   */
  def tooltipLines(stack: ItemStack, tooltip: util.List[Component]): Unit = {
    val lines = new util.ArrayList[String]()
    if (tooltipName.isDefined) {
      val name = tooltipName.get
      lines.addAll(li.cil.oc.util.Tooltip.get(name, tooltipData: _*))
      tooltipExtended(stack, lines)
    }
    tooltipCosts(stack, lines)
    lines.asScala.foreach(line => tooltip.add(Component.literal(line)))
  }

  /** 原 `tooltipLines` 的兼容重载（保留旧签名，方便逐字搬运）。 */
  def tooltipLines(stack: ItemStack, player: Player, tooltip: util.List[String], advanced: Boolean): Unit = {
    if (tooltipName.isDefined) {
      tooltip.addAll(li.cil.oc.util.Tooltip.get(tooltipName.get, tooltipData: _*))
      tooltipExtended(stack, tooltip)
    }
    tooltipCosts(stack, tooltip)
  }

  /** 除成本外的扩展提示。 */
  protected def tooltipExtended(stack: ItemStack, tooltip: util.List[String]): Unit = ()

  /** 材料成本 + 节点地址。 */
  protected def tooltipCosts(stack: ItemStack, tooltip: util.List[String]): Unit = {
    appendCostsTooltip(stack, tooltip)
    appendAddressTooltip(stack, tooltip)
  }

  // ----------------------------------------------------------------------- //
  // 其它
  // ----------------------------------------------------------------------- //

  def isDamageable: Boolean = false

  def damage(stack: ItemStack): Int = 0

  def maxDamage(stack: ItemStack): Int = 0

  /** 原 `Delegate.equals(stack)`：1.21.1 下退化为「物品类型相同」。 */
  def equals(stack: ItemStack): Boolean = stack != null && !stack.isEmpty && (stack.getItem eq this)

  override def toString: String = unlocalizedName
}
