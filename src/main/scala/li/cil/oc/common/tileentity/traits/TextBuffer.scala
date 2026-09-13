package li.cil.oc.common.tileentity.traits

import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.network.Node
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player

/**
 * 屏幕 / 机器人屏幕使用的文本缓冲区宿主
 * （对应 1.7.10 的 `common.tileentity.traits.TextBuffer`）。
 *
 * 1.21.1 迁移要点：
 *  - `updateEntity()` → [[TileEntity.tick]]（覆写时先调 `super.tick()`）。
 *  - `@SideOnly(Side.CLIENT)` 删除。
 *  - 真实缓冲区原本由屏幕物品的驱动创建：
 *    `api.Driver.driverFor(screenItem, getClass).createEnvironment(screenItem, this)`，
 *    但组件层 `li.cil.oc.common.component.TextBuffer` 与 `li.cil.oc.client.gui` 尚未移植，
 *    因此这里先用 [[TextBuffer.Placeholder]] 占位，**API 表面保持不变**
 *    （`buffer` / `tier` / `hasScreen` / `isActive` / `setBuffer` / `maxResolution`）。
 *
 * TODO(common.component): 待 server/client 组件层移植后接回真实缓冲区：
 * {{{
 *   val screenItem = api.Items.get(Constants.BlockName.ScreenTier1).createItemStack(1)
 *   api.Driver.driverFor(screenItem, getClass).createEnvironment(screenItem, this)
 *     .asInstanceOf[api.internal.TextBuffer]
 * }}}
 */
trait TextBuffer extends Environment {
  // 注意：Scala 的自类型不会被继承，每个子 trait 都必须重新声明。
  self: net.minecraft.world.level.block.entity.BlockEntity =>

  /** 屏幕等级（0 起），由具体方块实体提供。 */
  def tier: Int

  /** 缓冲区的最大分辨率（列, 行），取自配置的等级表。 */
  def maxResolution: (Int, Int) = {
    val index = tier max 0 min (Settings.screenResolutionsByTier.length - 1)
    Settings.screenResolutionsByTier(index)
  }

  /** 缓冲区的最大颜色深度，取自配置的等级表。 */
  def maxColorDepth: api.internal.TextBuffer.ColorDepth = {
    val index = tier max 0 min (Settings.screenDepthsByTier.length - 1)
    Settings.screenDepthsByTier(index)
  }

  private var bufferOverride: Option[api.internal.TextBuffer] = None

  private lazy val placeholderBuffer: api.internal.TextBuffer = {
    val instance = new TextBuffer.Placeholder()
    val (width, height) = maxResolution
    instance.setMaximumResolution(width, height)
    instance.setMaximumColorDepth(maxColorDepth)
    instance
  }

  /** 文本缓冲区；默认是占位实现，可由 [[setBuffer]] 换成真实实现。 */
  def buffer: api.internal.TextBuffer = bufferOverride.getOrElse(placeholderBuffer)

  /** 替换缓冲区实现（组件层 / 同步包拿到真实缓冲区时调用）。 */
  def setBuffer(value: api.internal.TextBuffer): Unit = bufferOverride = Option(value)

  override def node: Node = buffer.node

  /** 该方块实体是否带真实屏幕（1.7.10 由 Screen 组件层判断）。 */
  def hasScreen: Boolean = true

  /** 缓冲区是否已通电（原 `buffer.getPowerState`）。 */
  def isActive: Boolean = buffer.getPowerState

  override def tick(): Unit = {
    super.tick()
    if (isClient || isConnected) {
      buffer.update()
    }
  }

  // ----------------------------------------------------------------------- //

  override protected def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)
    buffer.load(nbt)
  }

  override protected def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)
    buffer.save(nbt)
  }

  override protected def readFromNBTForClient(nbt: CompoundTag): Unit = {
    super.readFromNBTForClient(nbt)
    buffer.load(nbt)
  }

  override protected def writeToNBTForClient(nbt: CompoundTag): Unit = {
    super.writeToNBTForClient(nbt)
    buffer.save(nbt)
  }
}

object TextBuffer {
  /**
   * 组件层移植前的**最小占位**文本缓冲区。
   *
   * 只保留分辨率 / 视口 / 颜色深度 / 供电状态等状态，渲染与输入回调都是空实现，
   * 节点为 `null`（与 `traits.Hub#node` 的降级方式一致）。
   *
   * TODO(common.component): `li.cil.oc.common.component.TextBuffer` 移植完成后删除本类，
   * 改由 [[TextBuffer#buffer]] 返回驱动创建的真实缓冲区。
   */
  private[tileentity] class Placeholder extends api.internal.TextBuffer {
    private var energyCostPerTick = 0.0
    private var powerState = false
    private var maximumWidth = 0
    private var maximumHeight = 0
    private var aspectRatio = 1.0
    private var width = 0
    private var height = 0
    private var viewportWidth = 0
    private var viewportHeight = 0
    private var maximumColorDepth: api.internal.TextBuffer.ColorDepth = api.internal.TextBuffer.ColorDepth.OneBit
    private var colorDepth: api.internal.TextBuffer.ColorDepth = api.internal.TextBuffer.ColorDepth.OneBit
    private val palette = Array.fill(16)(0)
    private var foregroundColor = 0
    private var backgroundColor = 0
    private var foregroundFromPalette = false
    private var backgroundFromPalette = false
    private var renderingEnabled = false

    // ManagedEnvironment / Persistable ------------------------------------- //

    override def node(): Node = null

    override def canUpdate(): Boolean = false

    override def update(): Unit = {}

    override def load(nbt: CompoundTag): Unit = {}

    override def save(nbt: CompoundTag): Unit = {}

    // 能量 / 供电 ----------------------------------------------------------- //

    override def setEnergyCostPerTick(value: Double): Unit = energyCostPerTick = value

    override def getEnergyCostPerTick(): Double = energyCostPerTick

    override def setPowerState(value: Boolean): Unit = powerState = value

    override def getPowerState(): Boolean = powerState

    // 最大分辨率 / 宽高比 ---------------------------------------------------- //

    override def setMaximumResolution(width: Int, height: Int): Unit = {
      maximumWidth = width
      maximumHeight = height
    }

    override def getMaximumWidth(): Int = maximumWidth

    override def getMaximumHeight(): Int = maximumHeight

    override def setAspectRatio(width: Double, height: Double): Unit = aspectRatio = if (height == 0) 1.0 else width / height

    override def getAspectRatio(): Double = aspectRatio

    // 当前分辨率 / 视口 ------------------------------------------------------ //

    override def setResolution(width: Int, height: Int): Boolean = {
      val clampedWidth = width max 0 min maximumWidth
      val clampedHeight = height max 0 min maximumHeight
      val changed = clampedWidth != this.width || clampedHeight != this.height
      this.width = clampedWidth
      this.height = clampedHeight
      changed
    }

    override def getWidth(): Int = width

    override def getHeight(): Int = height

    override def setViewport(width: Int, height: Int): Boolean = {
      val clampedWidth = width max 0 min this.width
      val clampedHeight = height max 0 min this.height
      val changed = clampedWidth != viewportWidth || clampedHeight != viewportHeight
      viewportWidth = clampedWidth
      viewportHeight = clampedHeight
      changed
    }

    override def getViewportWidth(): Int = viewportWidth

    override def getViewportHeight(): Int = viewportHeight

    // 颜色深度 / 调色板 ------------------------------------------------------ //

    override def setMaximumColorDepth(depth: api.internal.TextBuffer.ColorDepth): Unit = maximumColorDepth = depth

    override def getMaximumColorDepth(): api.internal.TextBuffer.ColorDepth = maximumColorDepth

    override def setColorDepth(depth: api.internal.TextBuffer.ColorDepth): Boolean = {
      val changed = depth != colorDepth && depth.ordinal() <= maximumColorDepth.ordinal()
      if (changed) colorDepth = depth
      changed
    }

    override def getColorDepth(): api.internal.TextBuffer.ColorDepth = colorDepth

    override def setPaletteColor(index: Int, color: Int): Unit =
      if (index >= 0 && index < palette.length) palette(index) = color

    override def getPaletteColor(index: Int): Int =
      if (index >= 0 && index < palette.length) palette(index) else 0

    // 当前前景 / 背景色 ------------------------------------------------------ //

    override def setForegroundColor(color: Int): Unit = setForegroundColor(color, isFromPalette = false)

    override def setForegroundColor(color: Int, isFromPalette: Boolean): Unit = {
      foregroundColor = color
      foregroundFromPalette = isFromPalette
    }

    override def getForegroundColor(): Int = foregroundColor

    override def isForegroundFromPalette(): Boolean = foregroundFromPalette

    override def setBackgroundColor(color: Int): Unit = setBackgroundColor(color, isFromPalette = false)

    override def setBackgroundColor(color: Int, isFromPalette: Boolean): Unit = {
      backgroundColor = color
      backgroundFromPalette = isFromPalette
    }

    override def getBackgroundColor(): Int = backgroundColor

    override def isBackgroundFromPalette(): Boolean = backgroundFromPalette

    // 缓冲区内容（占位实现不保存内容） --------------------------------------- //

    override def copy(column: Int, row: Int, width: Int, height: Int, horizontalTranslation: Int, verticalTranslation: Int): Unit = {}

    override def fill(column: Int, row: Int, width: Int, height: Int, value: Char): Unit = {}

    override def fill(column: Int, row: Int, width: Int, height: Int, value: Int): Unit = {}

    override def set(column: Int, row: Int, value: String, vertical: Boolean): Unit = {}

    override def get(column: Int, row: Int): Char = ' '

    override def getCodePoint(column: Int, row: Int): Int = ' '

    override def getForegroundColor(column: Int, row: Int): Int = foregroundColor

    override def isForegroundFromPalette(column: Int, row: Int): Boolean = foregroundFromPalette

    override def getBackgroundColor(column: Int, row: Int): Int = backgroundColor

    override def isBackgroundFromPalette(column: Int, row: Int): Boolean = backgroundFromPalette

    override def rawSetText(column: Int, row: Int, text: Array[Array[Char]]): Unit = {}

    override def rawSetText(column: Int, row: Int, text: Array[Array[Int]]): Unit = {}

    override def rawSetForeground(column: Int, row: Int, color: Array[Array[Int]]): Unit = {}

    override def rawSetBackground(column: Int, row: Int, color: Array[Array[Int]]): Unit = {}

    // 渲染（仅客户端） ------------------------------------------------------- //

    override def renderText(): Boolean = false

    override def renderWidth(): Int = 0

    override def renderHeight(): Int = 0

    override def setRenderingEnabled(enabled: Boolean): Unit = renderingEnabled = enabled

    override def isRenderingEnabled(): Boolean = renderingEnabled

    // 输入事件（占位实现直接丢弃） ------------------------------------------- //

    override def keyDown(character: Char, code: Int, player: Player): Unit = {}

    override def keyUp(character: Char, code: Int, player: Player): Unit = {}

    override def clipboard(value: String, player: Player): Unit = {}

    override def mouseDown(x: Double, y: Double, button: Int, player: Player): Unit = {}

    override def mouseDrag(x: Double, y: Double, button: Int, player: Player): Unit = {}

    override def mouseUp(x: Double, y: Double, button: Int, player: Player): Unit = {}

    override def mouseScroll(x: Double, y: Double, delta: Int, player: Player): Unit = {}
  }
}
