package li.cil.oc.client.gui

import java.util

import li.cil.oc.client.gui.traits.SlotLocking
import li.cil.oc.client.gui.widget.WidgetContainer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.{Font, GuiGraphics}
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.{AbstractContainerMenu, ClickType, Slot}

import scala.jdk.CollectionConverters._

/**
 * 所有 OC 容器界面的基类（原 1.7.10 的 `li.cil.oc.client.gui.CustomGuiContainer`）。
 *
 * 1.7.10 里它存在的理由是在 `GuiContainer` 与「其它会做基类变换的模组」之间插一层
 * （注释里吐槽的 TMI / EnderIO 那段）；1.21.1 里这个理由已经不存在，
 * 但这一层仍然是 OC 全部界面共用的父类，负责：
 *
 *  - 提供 1.7.10 命名风格的兼容访问器（[[mc]] / [[fontRendererObj]] / [[inventorySlots]] /
 *    [[container]] / [[zLevel]]），让界面代码的改动量最小；
 *  - 把 [[net.minecraft.client.gui.screens.inventory.AbstractContainerScreen]] 的
 *    `renderBg` / `renderLabels` 拆成更细的扩展点（见 [[DynamicGuiContainer]]）；
 *  - 提供关卡工具：[[drawTexturedRect]] / [[drawHoveringText]]；
 *  - 混入 [[WidgetContainer]]（自绘小组件）与 [[SlotLocking]]（槽位锁定扩展点）。
 *
 * ==与 1.7.10 的差异==
 *  - 构造器必须带上 `playerInventory` 与 `title`（1.21.1 的 `AbstractContainerScreen` 要求），
 *    参数名刻意用 `container0` 而不是 `container`：`net.minecraft.world.inventory.Slot`
 *    已经有 `container` 字段，同名容易混。
 *  - `doesGuiPauseGame` → [[isPauseScreen]]。
 */
abstract class CustomGuiContainer[C <: AbstractContainerMenu](
    val container0: C,
    playerInventory: Inventory,
    title: Component)
  extends AbstractContainerScreen[C](container0, playerInventory, title)
    with WidgetContainer with SlotLocking {

  // ----------------------------------------------------------------------- //
  // 1.7.10 兼容访问器
  //
  // 注意：这组访问器只为少改界面代码，语义必须与 1.21.1 保持一致。
  // ----------------------------------------------------------------------- //

  /** 原 `mc`（1.7.10 `Minecraft.getMinecraft`）。 */
  protected def mc: Minecraft = minecraft

  /** 原 `fontRendererObj`（1.21.1 是 `net.minecraft.client.gui.Font`）。 */
  protected def fontRendererObj: Font = font

  /** 原 `inventorySlots`（1.21.1 是 `menu.slots` 的 Scala 视图）。 */
  protected def inventorySlots: IndexedSeq[Slot] = menu.slots.asScala.toIndexedSeq

  /**
   * 原 `zLevel`。
   *
   * 1.21.1 的 z 序由 [[GuiGraphics]] 的 blitOffset 与绘制顺序决定，
   * 已经没有「设置一次就影响后续绘制」的全局 z 了，因此这里只是一个普通数值成员，
   * 供仍然按 1.7.10 思路写 z 偏移的界面读取。
   */
  protected var zLevel: Float = 0f

  /** 原 `container`（1.21.1 的容器实例放在 `AbstractContainerScreen#menu` 里）。 */
  protected def container: C = menu

  // ----------------------------------------------------------------------- //
  // WidgetContainer
  // ----------------------------------------------------------------------- //

  override def windowX: Int = leftPos

  override def windowY: Int = topPos

  override def windowZ: Float = zLevel

  // ----------------------------------------------------------------------- //
  // 子类扩展点
  // ----------------------------------------------------------------------- //

  /** 背景层（在界面底图之后、槽位之前绘制）。 */
  protected def drawSecondaryBackgroundLayer(guiGraphics: GuiGraphics): Unit

  /** 前景层（在槽位高亮之前绘制，坐标是相对界面左上角的）。 */
  protected def drawSecondaryForegroundLayer(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int): Unit

  // ----------------------------------------------------------------------- //
  // 绘制工具
  // ----------------------------------------------------------------------- //

  /**
   * 画一块贴图区域，等价 1.7.10 的 `drawTexturedModalRect(x, y, u, v, w, h)`。
   *
   * `x` / `y` 是相对界面左上角的坐标（也就是 1.7.10 的 GUI 内坐标），
   * 因为 `renderBg` / `renderLabels` 的 pose 已经被父类平移到界面左上角。
   * `texWidth` / `texHeight` 必须传贴图的真实像素尺寸（1.7.10 固定按 256x256 处理）。
   */
  protected def drawTexturedRect(guiGraphics: GuiGraphics,
                                 texture: ResourceLocation,
                                 x: Int, y: Int, u: Int, v: Int, w: Int, h: Int,
                                 texWidth: Int, texHeight: Int): Unit = {
    guiGraphics.blit(texture, x, y, u.toFloat, v.toFloat, w, h, texWidth, texHeight)
  }

  /**
   * 在屏幕绝对坐标 `(x, y)` 画 tooltip，等价 1.7.10 的 `drawHoveringText`。
   *
   * 1.21.1 的 tooltip 边框 / 背景 / 换行由原版统一处理，因此这里只需要把行交给
   * [[GuiGraphics#renderComponentTooltip]]。
   */
  protected def drawHoveringText(guiGraphics: GuiGraphics, lines: util.List[Component], x: Int, y: Int): Unit = {
    if (lines != null && !lines.isEmpty) {
      guiGraphics.renderComponentTooltip(font, lines, x, y)
    }
  }

  /** 1.7.10 的 `add(list, value)` 小工具（原实现里到处在用）。 */
  protected def add[T](list: util.List[T], value: Any): Boolean = list.add(value.asInstanceOf[T])

  // ----------------------------------------------------------------------- //
  // 槽位锁定扩展点（原 1.7.10 的 traits.LockedHotbar 直接覆写 handleMouseClick）
  // ----------------------------------------------------------------------- //

  override protected def isSlotLocked(slot: Slot): Boolean = false

  override protected def isHotbarKeyLocked: Boolean = false

  override protected def slotClicked(slot: Slot, slotId: Int, mouseButton: Int, clickType: ClickType): Unit = {
    if (!isSlotLocked(slot)) {
      super.slotClicked(slot, slotId, mouseButton, clickType)
    }
  }

  override protected def checkHotbarKeyPressed(keyCode: Int, scanCode: Int): Boolean = {
    if (isHotbarKeyLocked) false
    else super.checkHotbarKeyPressed(keyCode, scanCode)
  }

  // ----------------------------------------------------------------------- //
  // 渲染
  // ----------------------------------------------------------------------- //

  /**
   * 渲染入口。
   *
   * 与 1.7.10 的 `drawScreen` 相比，这里**额外**负责把自绘小组件画出来
   * （1.7.10 是界面自己在背景层里调用 `drawWidgets()`）。
   * 子类（[[DynamicGuiContainer]]）覆写时应当调用 `super.render` 以免漏画小组件。
   */
  override def render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float): Unit = {
    super.render(guiGraphics, mouseX, mouseY, partialTick)
    drawWidgets(guiGraphics)
  }
}
