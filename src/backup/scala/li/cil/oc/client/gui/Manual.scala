package li.cil.oc.client.gui

import com.mojang.blaze3d.platform.InputConstants
import li.cil.oc.client.Textures
import li.cil.oc.client.renderer.markdown.Document
import li.cil.oc.client.renderer.markdown.segment.InteractiveSegment
import li.cil.oc.client.renderer.markdown.segment.Segment
import li.cil.oc.client.{Manual => ManualAPI}
import li.cil.oc.{Localization, api}
import net.minecraft.client.KeyMapping
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

import scala.jdk.CollectionConverters._

/**
 * 游戏内手册的屏幕（原 1.7.10 的 `li.cil.oc.client.gui.Manual`，OCCE 1.20 版本为基准）。
 *
 * 版面：256x192 的窗口底图，正文区域左上角在窗口内的 (8, 8)，尺寸 230x176，
 * 右侧一条 6x180 的滚动条，左侧一列最多 7 个页签按钮。
 *
 * ==1.7.10 到 1.21.1 的映射要点==
 *  - `GuiScreen` → [[net.minecraft.client.gui.screens.Screen]]，
 *    贴图/坐标混入沿用本仓库的 [[traits.Window]]（它已经提供了 `guiLeft` / `guiTop`、
 *    `init()` 里的居中计算，以及先画底图再转交父类的 `render`）；
 *  - `drawScreen(mouseX, mouseY, dt)` → `render(graphics, mouseX, mouseY, dt)`，
 *    立即模式的 `GL11` / `bindTexture` 全部改由 [[GuiGraphics]] 承担；
 *  - `GuiButton` → 本仓库的 [[ImageButton]]，注册方式由「塞进 `buttonList`」改为
 *    `addRenderableWidget`，点击回调由覆写 `actionPerformed` 改为给
 *    [[ImageButton.actionPerformed]] 赋一个函数（见该类文档）；
 *  - [[api.manual.TabIconRenderer]] 的签名在 1.21.1 变成了
 *    `render(graphics, x, y)`，**必须把图标左上角坐标显式传进去**，
 *    不能再像 1.7.10 那样先 `glTranslate` 再调无参的 `render()`；
 *  - `drawHoveringText(list, x, y, font)` → `GuiGraphics#renderComponentTooltip`
 *    （这里传的坐标是屏幕绝对坐标，`Screen#render` 不会预先平移坐标原点）；
 *  - `handleMouseInput`（LWJGL2 的 `Mouse`）→ [[mouseScrolled]]；
 *    `mouseClicked` / `mouseMovedOrUp` / `mouseClickMove` 依次对应
 *    [[mouseClicked]] / [[mouseReleased]] / [[mouseDragged]]，坐标类型都变成了 `Double`；
 *  - `keyTyped(char, code)` 按物理键码判断 → `keyPressed` + `InputConstants`
 *    （1.21.1 里按键绑定已经不再存「旧的 LWJGL2 键码」，必须做 `isActiveAndMatches` 比较）；
 *  - `mc.thePlayer.closeScreen()` → [[onClose]]。
 *
 * 手册内容与历史记录仍然由 [[li.cil.oc.client.Manual]]（`api.Manual` 的客户端实现）
 * 提供，本类只负责显示与交互。
 */
class Manual extends screens.Screen(Component.empty()) with traits.Window {
  /** 正文区域宽度（窗口内相对坐标）。 */
  final val documentMaxWidth = 230
  /** 正文区域高度（窗口内相对坐标）。 */
  final val documentMaxHeight = 176
  /** 滚动条位置与尺寸（窗口内相对坐标）。 */
  final val scrollPosX = 244
  final val scrollPosY = 6
  final val scrollWidth = 6
  final val scrollHeight = 180
  /** 页签按钮的位置与尺寸（窗口内相对坐标，X 为负表示在窗口左外侧）。 */
  final val tabPosX = -23
  final val tabPosY = 7
  final val tabWidth = 23
  final val tabHeight = 26
  /** 最多显示多少个页签（与 [[li.cil.oc.client.Manual.addTab]] 的告警阈值一致）。 */
  final val maxTabsPerSide = 7

  override val windowWidth = 256
  override val windowHeight = 192

  override def backgroundImage = Textures.guiManual

  /** 是否正在拖拽滚动条；拖拽期间要按「悬停」画滚动按钮，并屏蔽页签/链接的 tooltip。 */
  var isScrolling = false
  /** 当前页面的解析结果（片段链表的头），由 [[refreshPage]] 填。 */
  var document: Segment = null
  /** 当前页面的总高度（像素），用于滚动范围与滚动条比例。 */
  var documentHeight = 0
  /** 本帧鼠标悬停到的可交互片段（链接等），点击时作用在它上面。 */
  var currentSegment = None: Option[InteractiveSegment]
  protected var scrollButton: ImageButton = _

  private def canScroll = maxOffset > 0

  /** 当前页面的滚动偏移；偏移存在历史记录上，因此前进/后退能各自保留位置。 */
  def offset = ManualAPI.history.top.offset

  /** 允许的最大滚动偏移（负数说明内容比可视区域矮，此时不可滚动）。 */
  def maxOffset = documentHeight - documentMaxHeight

  /** 把文档里的相对链接解析成绝对路径（原 1.7.10 实现里没被用到的工具方法，保留）。 */
  def resolveLink(path: String, current: String): String =
    if (path.startsWith("/")) path
    else {
      val splitAt = current.lastIndexOf('/')
      if (splitAt >= 0) current.splitAt(splitAt)._1 + "/" + path
      else path
    }

  /** 重新读取并解析当前页面，然后按已有偏移重新定位（页面高度可能变了）。 */
  def refreshPage(): Unit = {
    val content = Option(api.Manual.contentFor(ManualAPI.history.top.path)).
      getOrElse(Iterable("Document not found: " + ManualAPI.history.top.path).asJava)
    document = Document.parse(content.asScala)
    documentHeight = Document.height(document, documentMaxWidth, font)
    scrollTo(offset)
  }

  /** 打开新页面（压栈）；路径与当前页面相同时什么都不做。 */
  def pushPage(path: String): Unit = {
    if (path != ManualAPI.history.top.path) {
      ManualAPI.history.push(new ManualAPI.History(path))
      refreshPage()
    }
  }

  /** 返回上一页（出栈）；已经在第一页时关闭界面。 */
  def popPage(): Unit = {
    if (ManualAPI.history.size > 1) {
      ManualAPI.history.pop()
      refreshPage()
    }
    else {
      onClose()
    }
  }

  override protected def init(): Unit = {
    super.init()
    // 与 1.7.10 一致：手册里要能用鼠标点链接，因此进入界面时释放鼠标锁并清掉按住的按键。
    minecraft.mouseHandler.releaseMouse()
    KeyMapping.releaseAll()

    // 注意：页签按钮必须最先注册，下面的 render 会按「索引 == 页签序号」从
    // renderables 里取回它们（基类在每次 init 时会清空该列表，所以索引总是稳定的）。
    for ((tab, i) <- ManualAPI.tabs.zipWithIndex if i < maxTabsPerSide) {
      val x = guiLeft + tabPosX
      val y = guiTop + tabPosY + i * (tabHeight - 1)
      val button = new ImageButton(i, x, y, tabWidth, tabHeight, Textures.guiManualTab)
      button.actionPerformed = _ => api.Manual.navigate(tab.path)
      addRenderableWidget(button)
    }

    scrollButton = new ImageButton(-1, guiLeft + scrollPosX, guiTop + scrollPosY, 6, 13, Textures.guiButtonScroll)
    scrollButton.actionPerformed = _ => ()
    addRenderableWidget(scrollButton)

    refreshPage()
  }

  override def render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, dt: Float): Unit = {
    // 父类实现是 traits.Window：先按 windowWidth x windowHeight 贴底图，再转交 Screen 绘制部件。
    super.render(graphics, mouseX, mouseY, dt)

    // 内容不足以滚动时把滚动按钮置灰；拖拽时强制按悬停外观绘制。
    scrollButton.active = canScroll
    scrollButton.hoverOverride = isScrolling

    // 页签图标画在按钮内部的 (5, 5) 处（按钮贴图本身比图标大一圈）。
    // 这里把坐标直接传给渲染器：1.21.1 的 TabIconRenderer 不再依赖外部矩阵。
    for ((tab, i) <- ManualAPI.tabs.zipWithIndex if i < maxTabsPerSide) {
      val button = renderables.get(i).asInstanceOf[ImageButton]
      tab.renderer.render(graphics, button.getX + 5, button.getY + 5)
    }

    // 正文：Document.render 内部会自行裁剪到正文区域，并返回本帧悬停到的可交互片段。
    currentSegment = Document.render(graphics, document, guiLeft + 8, guiTop + 8, documentMaxWidth, documentMaxHeight, offset, font, mouseX, mouseY)

    // 1.7.10 的 drawHoveringText 会自己按换行符切行，这里手工切好后交给原版 tooltip 绘制。
    def localizeAndWrap(text: String): java.util.List[Component] =
      Localization.localizeImmediately(text).linesIterator.map(Component.literal).toList
        .asInstanceOf[List[Component]].asJava

    // 拖拽滚动条时不显示 tooltip，免得跟着鼠标乱闪。
    if (!isScrolling) currentSegment match {
      case Some(segment) =>
        segment.tooltip match {
          case Some(text) if text.nonEmpty => graphics.renderComponentTooltip(font, localizeAndWrap(text), mouseX, mouseY)
          case _ =>
        }
      case _ =>
    }

    // 悬停在页签按钮上时显示该页签的提示（悬停判定用屏幕绝对坐标，与按钮自身的判定一致）。
    if (!isScrolling) for ((tab, i) <- ManualAPI.tabs.zipWithIndex if i < maxTabsPerSide) {
      val button = renderables.get(i).asInstanceOf[ImageButton]
      if (mouseX > button.getX && mouseX < button.getX + tabWidth && mouseY > button.getY && mouseY < button.getY + tabHeight)
        tab.tooltip.foreach(text => graphics.renderComponentTooltip(font, localizeAndWrap(text), mouseX, mouseY))
    }

    // 悬停或拖拽滚动条时，在旁边显示当前滚动百分比（1.7.10 沿用至今的行为）。
    if (canScroll && (isCoordinateOverScrollBar(mouseX - guiLeft, mouseY - guiTop) || isScrolling)) {
      val lines: java.util.List[Component] = java.util.List.of(Component.literal(s"${100 * offset / maxOffset}%"))
      graphics.renderComponentTooltip(font, lines, guiLeft + scrollPosX + scrollWidth, scrollButton.getY + scrollButton.getHeight + 1)
    }
  }

  override def keyPressed(keyCode: Int, scanCode: Int, mods: Int): Boolean = {
    val input = InputConstants.getKey(keyCode, scanCode)
    // 跳跃键 = 返回上一页（1.7.10 用的是 keyBindJump 的旧键码，这里必须比较 Key）。
    if (minecraft.options.keyJump.isActiveAndMatches(input)) {
      popPage()
      return true
    }
    // 背包键 = 关闭界面。
    if (minecraft.options.keyInventory.isActiveAndMatches(input)) {
      onClose()
      return true
    }
    super.keyPressed(keyCode, scanCode, mods)
  }

  override def mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean = {
    // 1.7.10 是 ±120 的滚轮事件，这里只关心方向。
    if (scrollY < 0) {
      scrollDown()
      true
    }
    else if (scrollY > 0) {
      scrollUp()
      true
    }
    else super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
  }

  override def mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean = {
    val mcx = mouseX.toInt - guiLeft
    val mcy = mouseY.toInt - guiTop
    // 左键按在滚动条上：开始拖拽。
    if (canScroll && button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isCoordinateOverScrollBar(mcx, mcy)) {
      isScrolling = true
      scrollMouse(mouseY)
      return true
    }
    // 左键按在正文上：交给本帧悬停到的可交互片段处理（链接 / 物品图等）。
    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isCoordinateOverContent(mcx, mcy)) {
      if (currentSegment.exists(_.onMouseClick(mouseX.toInt, mouseY.toInt))) {
        return true
      }
    }
    // 右键 = 返回上一页。
    if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
      popPage()
      return true
    }
    super.mouseClicked(mouseX, mouseY, button)
  }

  override def mouseMoved(mouseX: Double, mouseY: Double): Unit = {
    // 拖拽滚动条时即使鼠标移出滚动条也要继续跟随（原 1.7.10 的 mouseClickMove 行为）。
    if (isScrolling) scrollMouse(mouseY)
    super.mouseMoved(mouseX, mouseY)
  }

  override def mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean = {
    if (isScrolling) {
      scrollMouse(mouseY)
      return true
    }
    super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
  }

  override def mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean = {
    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isScrolling) {
      isScrolling = false
      return true
    }
    super.mouseReleased(mouseX, mouseY, button)
  }

  /** 把鼠标 Y 换算成滚动偏移：滚动按钮高 13，因此轨道有效长度为 scrollHeight - 13。 */
  private def scrollMouse(mouseY: Double): Unit =
    scrollTo(math.round((mouseY - guiTop - scrollPosY - 6.5) * maxOffset / (scrollHeight - 13.0)).toInt)

  private def scrollUp() = scrollTo(offset - Document.lineHeight(font) * 3)

  private def scrollDown() = scrollTo(offset + Document.lineHeight(font) * 3)

  /** 滚动到指定偏移，并同步移动滚动按钮的位置。 */
  private def scrollTo(row: Int): Unit = {
    ManualAPI.history.top.offset = math.max(0, math.min(maxOffset, row))
    val yMin = guiTop + scrollPosY
    if (maxOffset > 0)
      scrollButton.setY(yMin + (scrollHeight - 13) * offset / maxOffset)
    else
      scrollButton.setY(yMin)
  }

  /** 坐标是否落在正文区域内（参数为窗口内相对坐标）。 */
  private def isCoordinateOverContent(x: Int, y: Int) =
    x >= 8 && x < 8 + documentMaxWidth &&
      y >= 8 && y < 8 + documentMaxHeight

  /** 坐标是否落在滚动条轨道内（参数为窗口内相对坐标）。 */
  private def isCoordinateOverScrollBar(x: Int, y: Int) =
    x >= scrollPosX && x < scrollPosX + scrollWidth &&
      y >= scrollPosY && y < scrollPosY + scrollHeight
}
