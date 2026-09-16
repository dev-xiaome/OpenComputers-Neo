package li.cil.oc.client.gui

import li.cil.oc.Localization
import li.cil.oc.client.Textures
import li.cil.oc.common
import li.cil.oc.common.container.{ComponentSlot, Player}
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.Slot

import scala.jdk.CollectionConverters._

/**
 * 带「动态槽位」渲染的容器界面基类（原 1.7.10 的 `li.cil.oc.client.gui.DynamicGuiContainer`）。
 *
 * 负责 OC 界面里那套固定的槽位绘制流程：
 *  1. 界面底图（[[CustomGuiContainer.texture]]，默认 [[Textures.guiBackground]]）；
 *  2. [[drawSecondaryBackgroundLayer]]（各界面自己画背景装饰）；
 *  3. [[drawInventorySlots]]（槽位底色 / 未解锁槽位的占位图标）；
 *  4. [[drawSecondaryForegroundLayer]]（标签、tooltip 等）；
 *  5. [[drawSlotHighlight]]（拖拽物品时把可放置的目标槽位高亮）。
 *
 * ==1.21.1 迁移要点==
 *  - `renderBg` / `renderLabels` / `render` 的签名换成
 *    [[net.minecraft.client.gui.GuiGraphics]] 版本；
 *  - `drawSecondaryForegroundLayer` 增加 `GuiGraphics` 参数（mouseX / mouseY 仍然相对界面左上角）；
 *  - 槽位图标从 `IIcon` 换成 [[ResourceLocation]] + 图集精灵
 *    （见 [[Icons]] 与 `common.container.ComponentSlot#tierIcon`）；
 *  - 空槽位的等级图标**不再手动画**：1.21.1 的 `AbstractContainerScreen#renderSlot`
 *    会通过 `Slot#getNoItemIcon` 自动画，重复绘制只会多一层半透明叠色；
 *  - `zLevel` 相关的 `glTranslatef` 平移改为 `PoseStack` 平移（[[GuiGraphics#pose]]）。
 *
 * ==关于 `hoveredSlot`==
 * 1.7.10 里本类把「当前悬停的槽位」存成 `Option[Slot]`。1.21.1 的
 * `AbstractContainerScreen` **已经内建**了 `protected Slot hoveredSlot` 字段
 * （null 表示没有，且它会在 `renderLabels` 之前算好），Scala 无法再用 `Option[Slot]`
 * 覆盖这个 Java 字段，因此这里：
 *  - 继续沿用父类字段（[[render]] 里也会按 1.7.10 的语义提前算一次）；
 *  - 另外提供 [[hoveredSlotOption]] 这个 Option 视图给界面代码使用。
 */
abstract class DynamicGuiContainer[C <: li.cil.oc.common.container.Player](
    menu0: C,
    playerInventory: Inventory,
    title: Component)
  extends CustomGuiContainer[C](menu0, playerInventory, title) {

  // ----------------------------------------------------------------------- //
  // 悬停槽位
  // ----------------------------------------------------------------------- //

  /** 当前悬停槽位的 Option 视图；见类注释里关于 `hoveredSlot` 的说明。 */
  protected def hoveredSlotOption: Option[Slot] = Option(hoveredSlot)

  /** 槽位是否属于玩家物品栏。 */
  protected def isInPlayerInventory(slot: Slot): Boolean =
    slot != null && (slot.container eq playerInventory)

  // ----------------------------------------------------------------------- //
  // 背景层
  // ----------------------------------------------------------------------- //

  /** 各界面自己的背景装饰；默认什么都不画（与 1.7.10 的默认实现一致）。 */
  override protected def drawSecondaryBackgroundLayer(guiGraphics: GuiGraphics): Unit = {}

  override protected def renderBg(guiGraphics: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int): Unit = {
    // 1.7.10：bindTexture + drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize)
    // 1.21.1：GUI 贴图按 256x256 解析 UV，语义完全一致。
    guiGraphics.blit(texture, leftPos, topPos, 0, 0, imageWidth, imageHeight)
    drawSecondaryBackgroundLayer(guiGraphics)
    drawInventorySlots(guiGraphics)
  }

  /**
   * 画所有槽位。
   *
   * `AbstractContainerScreen` 只在**没有**平移过的 pose 里画槽位（它自己会
   * `translate(leftPos, topPos)`），所以这里也要把 pose 平移到界面左上角，
   * 槽位坐标（[[Slot#x]] / [[Slot#y]]）才是相对界面的。
   */
  protected def drawInventorySlots(guiGraphics: GuiGraphics): Unit = {
    val pose = guiGraphics.pose()
    pose.pushPose()
    pose.translate(leftPos.toFloat, topPos.toFloat, 0f)
    for (slot <- inventorySlots) {
      drawSlotInventory(guiGraphics, slot)
    }
    pose.popPose()
  }

  private def drawSlotInventory(guiGraphics: GuiGraphics, slot: Slot): Unit = slot match {
    case component: ComponentSlot if component.slot == common.Slot.None || component.tier == common.Tier.None =>
      // 未解锁 / 无效槽位：画一个「不可用」占位图标（原 `drawDisabledSlot`）。
      if (!slot.hasItem && slot.x >= 0 && slot.y >= 0) {
        drawDisabledSlot(guiGraphics, component)
      }
    case _ =>
      if (!isInPlayerInventory(slot)) {
        drawSlotBackground(guiGraphics, slot.x - 1, slot.y - 1)
      }
      // 空槽位的等级图标由父类的 renderSlot 通过 getNoItemIcon 绘制，这里不重复画。
  }

  /** 槽位底色（原实现手写 18x18 的 UV 四边形，这里等价于把整张贴图铺满 18x18）。 */
  protected def drawSlotBackground(guiGraphics: GuiGraphics, x: Int, y: Int): Unit = {
    guiGraphics.blit(Textures.guiSlot, x, y, 0f, 0f, 18, 18, 18, 18)
  }

  /** 未解锁 / 无效槽位的占位图标（原 `IIcon`，现在是图集精灵）。 */
  protected def drawDisabledSlot(guiGraphics: GuiGraphics, slot: li.cil.oc.common.container.ComponentSlot): Unit = {
    val sprite = spriteFor(slot.tierIcon)
    if (sprite != null) {
      val pose = guiGraphics.pose()
      pose.pushPose()
      pose.translate(leftPos.toFloat, topPos.toFloat, 0f)
      guiGraphics.blit(slot.x, slot.y, 0, 16, 16, sprite)
      pose.popPose()
    }
  }

  private def spriteFor(icon: ResourceLocation): TextureAtlasSprite =
    if (icon == null) null
    else Minecraft.getInstance.getTextureAtlas(Icons.atlas).apply(icon)

  // ----------------------------------------------------------------------- //
  // 前景层
  // ----------------------------------------------------------------------- //

  /** 默认前景：玩家物品栏标签。界面可以覆写并调用 `super` 保留该标签。 */
  override protected def drawSecondaryForegroundLayer(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int): Unit = {
    guiGraphics.drawString(font,
      Localization.localizeImmediately("container.inventory"),
      8, imageHeight - 96 + 2, 0x404040, false)
  }

  override protected def renderLabels(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int): Unit = {
    // 注意：这里刻意**不**调用 super.renderLabels —— 1.7.10 的 OC 界面同样不画原版的
    // 标题 / 物品栏标签，标签由 drawSecondaryForegroundLayer 自己负责。
    drawSecondaryForegroundLayer(guiGraphics, mouseX, mouseY)

    for (slot <- inventorySlots) {
      drawSlotHighlight(guiGraphics, slot)
    }
  }

  /**
   * 槽位高亮：把「能放下鼠标拖着的那叠物品」的槽位标出来。
   *
   * 调用时机是 `renderLabels`，此时 pose 已经是界面相对坐标。
   */
  protected def drawSlotHighlight(guiGraphics: GuiGraphics, slot: Slot): Unit = {
    if (menu.getCarried.isEmpty) slot match {
      case component: ComponentSlot if component.slot == common.Slot.None || component.tier == common.Tier.None => // Ignore.
      case _ =>
        val currentIsInPlayerInventory = isInPlayerInventory(slot)
        val drawHighlight = hoveredSlotOption match {
          case Some(hovered) =>
            val hoveredIsInPlayerInventory = isInPlayerInventory(hovered)
            (currentIsInPlayerInventory != hoveredIsInPlayerInventory) &&
              ((currentIsInPlayerInventory && slot.hasItem && isSelectiveSlot(hovered) && hovered.mayPlace(slot.getItem)) ||
                (hoveredIsInPlayerInventory && hovered.hasItem && isSelectiveSlot(slot) && slot.mayPlace(hovered.getItem)))
          case _ =>
            // TODO(integration.NEI): 1.7.10 里鼠标悬停在 NEI 物品面板上时，也会按
            //   「该物品能不能放进这个槽位」来高亮；NEI 没有 1.21.1 版本，这段联动整体删除。
            false
        }
        if (drawHighlight) {
          // 原实现是 0x80FFFFFF 的渐变矩形，等价于一层半透明白。
          guiGraphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x80FFFFFF)
        }
    }
  }

  private def isSelectiveSlot(slot: Slot): Boolean = slot match {
    case component: ComponentSlot => component.slot != common.Slot.Any && component.slot != common.Slot.Tool
    case _ => false
  }

  // ----------------------------------------------------------------------- //
  // 渲染
  // ----------------------------------------------------------------------- //

  override def render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float): Unit = {
    // 按 1.7.10 的语义提前算出悬停槽位（父类 render 里还会自己再算一次，见类注释）。
    // 注意 isHovering 的参数是**屏幕绝对坐标**：它内部会自己减掉 leftPos / topPos。
    hoveredSlot = menu.slots.asScala.find(slot => isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY)).orNull

    // 父类（CustomGuiContainer）的 render 已经在渲染收尾处把自绘小组件画掉了，
    // 这里不再重复调用 drawWidgets，避免半透明组件被叠两次。
    super.render(guiGraphics, mouseX, mouseY, partialTick)

    // 槽位物品的 tooltip：1.21.1 的 render 不再自动调用它，需要界面自己收尾。
    renderTooltip(guiGraphics, mouseX, mouseY)

    // TODO(integration.NEI): 1.7.10 在这里额外跑了一遍 NEI 高亮
    //   （通过反射读 ItemPanel 的私有字段，若鼠标悬停的物品能放进当前悬停槽位就再加一层高亮）。
    //   NEI 没有 1.21.1 版本，整段联动（含 hoveredStackNEI 字段与 drawNEIHighlights）已删除。
  }
}
