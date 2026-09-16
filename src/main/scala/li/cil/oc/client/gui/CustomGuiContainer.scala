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
 *  - 提供 1.7.10 命名风格的兼容访问器（[[mc]] / [[fontRendererObj]] / [[inventoryContainer]] /
 *    [[xSize]] / [[ySize]] / [[guiLeft]] / [[guiTop]] / [[zLevel]] / [[buttonList]]），
 *    让搬运过来的界面代码改动量最小；
 *  - 把 [[net.minecraft.client.gui.screens.inventory.AbstractContainerScreen]] 的
 *    `renderBg` / `renderLabels` 拆成更细的扩展点（见 [[DynamicGuiContainer]]）；
 *  - 提供关卡工具：[[drawTexturedRect]] / [[drawHoveringText]] / [[drawStringWithShadow]]；
 *  - 混入 [[WidgetContainer]]（自绘小组件）与 [[SlotLocking]]（槽位锁定扩展点）。
 *
 * ==与 1.7.10 的差异==
 *  - 构造器必须带上 `playerInventory` 与 `title`（1.21.1 的 `AbstractContainerScreen` 要求），
 *    参数名刻意用 `container0` 而不是 `container`：`net.minecraft.world.inventory.Slot`
 *    已经有 `container` 字段，同名容易混。
 *  - `guiLeft` / `guiTop` / `xSize` / `ySize` 在 1.21.1 分别叫
 *    `leftPos` / `topPos` / `imageWidth` / `imageHeight`，语义完全一致（都是相对屏幕的
 *    左上角坐标与界面尺寸），这里补回旧名字只是为了少改界面代码。
 *  - `doesGuiPauseGame` → [[isPauseScreen]]（OC 的界面一律不暂停游戏）。
 *  - `drawScreen(mouseX, mouseY, dt)` → [[render(GuiGraphics, Int, Int, Float)]]。
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

  /** 原 `inventoryContainer`（1.7.10 里是构造器参数；1.21.1 就是 `menu`）。 */
  protected def inventoryContainer: C = menu

  /** 原 `xSize`（1.21.1 是 `imageWidth`）。 */
  protected def xSize: Int = imageWidth

  protected def xSize_=(value: Int): Unit = imageWidth = value

  /** 原 `ySize`（1.21.1 是 `imageHeight`）。 */
  protected def ySize: Int = imageHeight

  protected def ySize_=(value: Int): Unit = imageHeight = value

  /** 原 `guiLeft`（1.21.1 是 `leftPos`）。 */
  protected def guiLeft: Int = leftPos

  /** 原 `guiTop`（1.21.1 是 `topPos`）。 */
  protected def guiTop: Int = topPos

  /**
   * 原 `zLevel`。
   *
   * 1.21.1 的 z 序由 [[GuiGraphics]] 的 blitOffset 与绘制顺序决定，
   * 已经没有「设置一次就影响后续绘制」的全局 z 了，因此这里只是一个普通数值成员，
   * 供仍然按 1.7.10 思路写 z 偏移的界面读取。
   */
  protected var zLevel: Float = 0f

  /**
   * 原 `buttonList`（1.7.10 的 `List[GuiButton]`）。
   *
   * 1.21.1 的按钮统一由 `Screen#addRenderableWidget` 管理，界面**不应**再往这里塞东西；
   * 保留它只是为了让搬运期还没改完的 `add(buttonList, button)` 之类代码能编译。
   * 新代码请用 [[addRenderableWidget]]。
   */
  protected lazy val buttonList: util.List[ImageButton] = new util.ArrayList[ImageButton]()

  /** 原 `buttonList.add(...)` 的等价物：同时登记为可渲染组件。 */
  protected def addButton(button: ImageButton): ImageButton = {
    buttonList.add(button)
    addRenderableWidget(button)
  }

  override def isPauseScreen: Boolean = false

  // ----------------------------------------------------------------------- //
  // WidgetContainer
  // ----------------------------------------------------------------------- //

  override def windowX: Int = leftPos

  override def windowY: Int = topPos

  override def windowZ: Float = zLevel

  // ----------------------------------------------------------------------- //
  // 子类扩展点
  // ----------------------------------------------------------------------- //

  /** 背景层（在界面底图之后、槽位之前绘制），坐标是相对界面左上角的。 */
  protected def drawSecondaryBackgroundLayer(guiGraphics: GuiGraphics): Unit

  /** 前景层（在槽位高亮之前绘制），坐标是相对界面左上角的。 */
  protected def drawSecondaryForegroundLayer(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int): Unit

  // ----------------------------------------------------------------------- //
  // 绘制工具
  // ----------------------------------------------------------------------- //

  /**
   * 画一块贴图区域，等价 1.7.10 的 `drawTexturedModalRect(x, y, u, v, w, h)`。
   *
   * `x` / `y` 是相对界面左上角的坐标（也就是 1.7.10 的 GUI 内坐标），
   * 因为 `renderBg` / `renderLabels` 的 pose 已经被父类平移到界面左上角。
   * 1.21.1 的 GUI 贴图统一按 **256x256** 解析 UV（与 1.7.10 一致），
   * 因此这里不需要 `texWidth` / `texHeight` 参数。
   */
  protected def drawTexturedModalRect(guiGraphics: GuiGraphics, x: Int, y: Int, u: Int, v: Int, w: Int, h: Int): Unit =
    guiGraphics.blit(texture, x, y, u.toFloat, v.toFloat, w, h, 256, 256)

  /**
   * 画一块贴图区域，显式给出贴图的真实像素尺寸。
   *
   * 只在贴图不是 256x256 时才需要它；否则用上面那个重载。
   */
  protected def drawTexturedRect(guiGraphics: GuiGraphics,
                                 texture: ResourceLocation,
                                 x: Int, y: Int, u: Int, v: Int, w: Int, h: Int,
                                 texWidth: Int, texHeight: Int): Unit = {
    guiGraphics.blit(texture, x, y, u.toFloat, v.toFloat, w, h, texWidth, texHeight)
  }

  /** 原版贴图的默认位置；子类可覆写（原 1.7.10 用 `bindTexture` 临时切换）。 */
  protected def texture: ResourceLocation = li.cil.oc.client.Textures.guiBackground

  /** 在屏幕绝对坐标 `(x, y)` 画带阴影的文字，等价 1.7.10 的 `fontRendererObj.drawStringWithShadow`。 */
  protected def drawStringWithShadow(guiGraphics: GuiGraphics, text: String, x: Int, y: Int, color: Int): Unit =
    guiGraphics.drawString(font, if (text == null) "" else text, x, y, color, true)

  /**
   * 在**屏幕绝对坐标** `(x, y)` 画 tooltip，等价 1.7.10 的 `drawHoveringText`。
   *
   * 1.21.1 的 tooltip 边框 / 背景 / 换行由原版统一处理，因此这里只需要把行交给
   * [[GuiGraphics#renderComponentTooltip]]。
   */
  protected def drawHoveringText(lines: util.List[Component], x: Int, y: Int, font: Font): Unit = {
    if (lines != null && !lines.isEmpty) {
      currentGuiGraphics match {
        case Some(graphics) => graphics.renderComponentTooltip(font, lines, x, y)
        case _ =>
      }
    }
  }

  /**
   * 1.7.10 风格的 tooltip 重载：`x` / `y` 是**相对界面左上角**的坐标
   * （原实现的调用点写的都是 `mouseX - guiLeft, mouseY - guiTop`）。
   *
   * 这里统一转成屏幕绝对坐标再交给原版，避免每个界面各自记得加上 `leftPos`。
   * 行内的 String 会被转成 [[Component#literal]]。
   *
   * **注意**：tooltip 传进来的是「界面内坐标」，这里统一加上 `leftPos` / `topPos`
   * 转成屏幕绝对坐标；调用点不要自己再加一遍（否则 tooltip 会出现两倍偏移）。
   */
  protected def copiedDrawHoveringText(lines: util.List[String], x: Int, y: Int, font: Font): Unit = {
    if (lines != null && !lines.isEmpty) {
      val components = new util.ArrayList[Component](lines.size())
      lines.forEach(line => components.add(Component.literal(if (line == null) "" else line)))
      drawHoveringText(components, x + leftPos, y + topPos, font)
    }
  }

  /**
   * 当前这一帧的 [[GuiGraphics]]。
   *
   * 1.7.10 的 `GuiContainer` 把 `GuiGraphics` 的对应物（`Tessellator` / `GL11` 状态）
   * 放在实例字段上，界面代码因此可以在任意方法里绘制。1.21.1 把它改成
   * **逐次传入**的参数，为了少改搬运过来的代码，[[render]] 会把它记在这里，
   * 供 [[drawHoveringText]] / [[drawStringWithShadow]] 这类「旧签名」工具使用。
   */
  protected var currentGuiGraphics: Option[GuiGraphics] = None

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
   * 与 1.7.10 的 `drawScreen` 相比，这里**额外**负责：
   *  1. 把 [[GuiGraphics]] 记到 [[currentGuiGraphics]]；
   *  2. 把自绘小组件画出来（1.7.10 是界面自己在背景层里调用 `drawWidgets()`）。
   * 子类（[[DynamicGuiContainer]]）覆写时应当调用 `super.render` 以免漏画小组件。
   */
  override def render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float): Unit = {
    currentGuiGraphics = Some(guiGraphics)
    super.render(guiGraphics, mouseX, mouseY, partialTick)
    drawWidgets(guiGraphics)
    currentGuiGraphics = None
  }
}
