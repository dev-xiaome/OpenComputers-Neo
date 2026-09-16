package li.cil.oc.client.gui

import java.util

import com.mojang.blaze3d.systems.RenderSystem
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

  /**
   * 把 Scala 的字符串序列转成 `java.util.List`，供 `tooltip.addAll(...)` 使用。
   *
   * 1.7.10 里这一步是 `scala.collection.convert.WrapAsJava._` 提供的隐式
   * `asJavaCollection`；Scala 2.13 把它移到了 `scala.jdk.CollectionConverters`，
   * 但**本工程用的 2.13.14 运行时里没有 `asJavaCollection` 这个名字**
   * （只有 `asJava` 这组隐式），因此这里统一走 [[scala.jdk.CollectionConverters]] 的
   * `Seq#asJava` 扩展，并保留 `asJavaCollection` 这个旧名字给搬运过来的界面代码用。
   */
  protected def toJava(lines: Iterable[String]): util.List[String] = {
    val list = new util.ArrayList[String]()
    lines.foreach(line => list.add(if (line == null) "" else line))
    list
  }

  /** 在屏幕绝对坐标 `(x, y)` 画带阴影的文字，等价 1.7.10 的 `fontRendererObj.drawStringWithShadow`。 */
  protected def drawStringWithShadow(guiGraphics: GuiGraphics, text: String, x: Int, y: Int, color: Int): Unit =
    guiGraphics.drawString(font, if (text == null) "" else text, x, y, color, true)

  /**
   * 在**屏幕绝对坐标** `(x, y)` 画 tooltip，等价 1.7.10 的 `drawHoveringText`。
   *
   * 1.21.1 的 tooltip 边框 / 背景 / 换行由原版统一处理，因此这里只需要把行交给
   * [[GuiGraphics#renderComponentTooltip]]。
   *
   * ==为什么必须显式抵消 pose 平移（机箱电源按钮 tooltip 跑到窗口右边的根因）==
   *  - 1.7.10 的 `drawHoveringText(list, x, y, font)` 是**自己手绘**背景与文字的，
   *    画在哪个位置完全由传入的 `x` / `y` 决定；
   *  - 1.21.1 的 [[GuiGraphics#renderComponentTooltip]] 画在**当前 PoseStack** 上：
   *    实际屏幕位置 = 传入坐标 + 当前 pose 的平移量
   *    （见 `GuiGraphics#renderTooltipInternal`，它只 `pushPose` 加一层 z 偏移，
   *    不会重置已有的平移）。
   *  - OC 的 tooltip 都是在 [[DynamicGuiContainer.renderLabels]] 里发起的，而
   *    `AbstractContainerScreen#render` 在调用 `renderLabels` 之前执行了
   *    `pose.translate(leftPos, topPos, 0)`、并且要到整个 `render` 收尾才 `popPose`。
   *    于是 tooltip 会**再叠加一次**界面左上角偏移，表现为整个提示框被推到窗口
   *    右下（x 超出屏幕宽时还会被原版的贴边逻辑压到最右边）。
   *
   * 这里在绘制前把这次平移抵消掉，使传入的 `x` / `y` 真正等于屏幕绝对坐标
   * —— 与 1.7.10 以及原版 tooltip 的语义一致，同时原版的贴边判断也能基于正确坐标。
   */
  protected def drawHoveringText(lines: util.List[Component], x: Int, y: Int, font: Font): Unit = {
    if (lines != null && !lines.isEmpty) {
      currentGuiGraphics match {
        case Some(graphics) =>
          val pose = graphics.pose()
          pose.pushPose()
          pose.translate(-leftPos.toFloat, -topPos.toFloat, 0f)
          graphics.renderComponentTooltip(font, lines, x, y)
          pose.popPose()
        case _ =>
      }
    }
  }

  /**
   * 1.7.10 风格的 tooltip 重载（行内是 String），坐标语义与 [[drawHoveringText]] 相同：
   * `x` / `y` 是**屏幕绝对坐标**（原版 tooltip 与 1.7.10 `drawHoveringText` 都是这个语义）。
   *
   * 行内的 String 会被转成 [[Component#literal]]。
   *
   * **注意**：调用点直接传 `mouseX` / `mouseY` 即可，不要再自己加 `leftPos` / `topPos`，
   * 也不要再自己减一次（1.7.10 的调用点写的是 `mouseX - guiLeft`，那是为了配合当时
   * `GL11.glTranslatef(guiLeft, guiTop)` 之后的绘制矩阵，1.21.1 不需要）。
   */
  protected def copiedDrawHoveringText(lines: util.List[String], x: Int, y: Int, font: Font): Unit = {
    if (lines != null && !lines.isEmpty) {
      val components = new util.ArrayList[Component](lines.size())
      lines.forEach(line => components.add(Component.literal(if (line == null) "" else line)))
      drawHoveringText(components, x, y, font)
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

    // 1.7.10 里每个 GUI 绘制点之前都会把顶点颜色重置成不透明白色 —— 例如
    // `DynamicGuiContainer.drawGuiContainerBackgroundLayer` 开头的 `GL11.glColor4f(1, 1, 1, 1)`、
    // `Case.drawSecondaryBackgroundLayer` 开头的 `GL11.glColor3f(1, 1, 1)`
    // （原注释写着 "Required under Linux."，说明这是必需的、而不是可选的）。
    //
    // 1.21.1 没有固定管线颜色，等价物是 [[GuiGraphics#setColor]]：它内部转发到
    // `RenderSystem.setShaderColor`，也就是所有 `position_tex` 类着色器的 `ColorModulator`
    // uniform —— GUI 贴图的 `blit` 正是用它调制的。
    // 如果不重置，任何把着色器颜色留在非白色状态的渲染器（方块实体渲染器 / 实体渲染器 /
    // 物品渲染扩展等，`RenderSystem.setShaderColor` 是全局状态且不会自动还原）都会把
    // GUI 贴图整体染色，表现就是「GUI 贴图没有颜色 / 灰白」。
    //
    // 这里在 GUI 渲染入口统一重置一次，覆盖 `renderBg`（底图 + 槽位）与 `renderLabels`；
    // 白色本来就是 1.21.1 GUI 渲染的默认值，因此渲染结束后不需要再恢复。
    guiGraphics.setColor(1f, 1f, 1f, 1f)

    // 混合状态同样必须由界面自己保证，理由是 1.21.1 的 `GuiGraphics#blit` 属于
    // **立即绘制**，而它的无色重载完全不碰混合状态（只有带颜色参数的那个重载才会
    // 成对 `enableBlend` / `disableBlend`）。混合函数因此会沿用上一个渲染阶段
    // （世界 / 实体 / 粒子 / 天气）留下的值：
    //  - 残留加色混合（`GL_SRC_ALPHA, GL_ONE`）会把界面越叠越白、失去原有颜色；
    //  - 残留的取反类混合（`GL_ONE_MINUS_DST_COLOR` 等，实体受伤闪白用的那种）
    //    会把贴图画成「底片」，看上去就是反的；
    //  - 混合干脆没开启时，alpha 被忽略，半透明的槽位阴影与面板边框会变成实心色块，
    //    界面的明暗关系整体翻转，也就是反馈里的「像背面、发灰」。
    //
    // 1.7.10 的对应物是 `RenderState.makeItBlend()`
    // （`glEnable(GL_BLEND)` + `glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)`
    // + `glDisable(GL_ALPHA_TEST)`），1.20 CE 与社区 1.21.1 版写成
    // `RenderSystem.enableBlend()` + `RenderSystem.defaultBlendFunc()`；
    // 原版自己的 `Screen#renderMenuBackgroundTexture` 也是「blit 前 enableBlend、
    // 画完 disableBlend」，可见管好这两个状态是绘制方的责任。
    //
    // 这里只开启、不在收尾关闭：`renderBg` / `renderLabels` / 自绘小组件都会用到
    // 半透明贴图，整段渲染都应保持默认混合；原版随后绘制的内容走 RenderType，
    // 会自行设置并恢复自己需要的状态。
    RenderSystem.enableBlend()
    RenderSystem.defaultBlendFunc()

    super.render(guiGraphics, mouseX, mouseY, partialTick)

    // 槽位绘制（[[DynamicGuiContainer.drawInventorySlots]]）按 1.20 CE 的写法在收尾处
    // `disableBlend` 并恢复深度测试，而这里紧接着要画的自绘小组件（进度条等）依旧需要
    // 「不透明白色 + 默认混合」这套状态（1.7.10 里整个 GUI 绘制期间 `RenderState.makeItBlend`
    // 都是开着的），因此再设置一次，避免小组件贴图在半透明部分画错。
    guiGraphics.setColor(1f, 1f, 1f, 1f)
    RenderSystem.enableBlend()
    RenderSystem.defaultBlendFunc()
    drawWidgets(guiGraphics)
    currentGuiGraphics = None
  }
}
