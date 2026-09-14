package li.cil.oc.common.component

import com.google.common.base.Strings
import net.neoforged.bus.api.SubscribeEvent
import li.cil.oc.Constants
import li.cil.oc._
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network._
import li.cil.oc.api.prefab
import li.cil.oc.common._
import li.cil.oc.common.component.traits.VideoRamRasterizer
import li.cil.oc.util
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.PackedColor
import li.cil.oc.util.SideTracker
import net.minecraft.world.entity.player.Player
import net.minecraft.nbt.CompoundTag
import net.neoforged.neoforge.event.level.ChunkEvent
import net.neoforged.neoforge.event.level.LevelEvent

import scala.jdk.CollectionConverters._
import scala.collection.mutable

/**
 * 屏幕 / 文本缓冲组件（对应 1.7.10 的 `common.component.TextBuffer`）。
 *
 * 1.21.1 迁移要点：
 *  - 渲染层（[[li.cil.oc.client.renderer.*]]、`client.PacketSender`、
 *    `client.ComponentTracker`）尚未移植，相关分支降级为占位，见各处的 `TODO(client):` 注释；
 *    **数据与算法部分（缓冲读写、分辨率 / 视口、色彩深度、与 GPU 的 bitblt 协议）完整保留。**
 *  - 网络发送原本走 `server.PacketSender` / `server.ComponentTracker`（尚未移植），
 *    这里改用同包的 [[ServerPacketSender]] / [[ComponentTracker]] 占位实现，报文格式与旧版一致。
 *  - `@SideOnly(Dist.CLIENT)` 全部移除（NeoForge 的 `RuntimeDistCleaner` 会对类级 `@OnlyIn` 抛异常）。
 *  - `Level#isRemote` → `Level#isClientSide`；`getTotalWorldTime` → `getGameTime`。
 *  - `CompoundTag#getInteger` → `getInt`；`ItemStack#hasTagCompound/getTagCompound` →
 *    包对象提供的 `hasTag()/getTag()`（对应自定义数据组件）。
 */
class TextBuffer(val host: EnvironmentHost) extends prefab.ManagedEnvironment with traits.TextBufferProxy with VideoRamRasterizer with DeviceInfo {
  override val node = api.Network.newNode(this, Visibility.Network).
    withComponent("screen").
    withConnector().
    create()

  private var maxResolution: (Int, Int) = Settings.screenResolutionsByTier(Tier.One)

  private var maxDepth = Settings.screenDepthsByTier(Tier.One)

  private var aspectRatio = (1.0, 1.0)

  private var powerConsumptionPerTick = Settings.get.screenCost

  private var precisionMode = false

  // For client side only.
  private var isRendering = true

  private var isDisplaying = true

  private var hasPower = true

  private var relativeLitArea = -1.0

  private var _pendingCommands: Option[PacketBuilder] = None

  private val syncInterval = 100

  private var syncCooldown = syncInterval

  private def pendingCommands = _pendingCommands.getOrElse {
    val pb = new CompressedPacketBuilder(PacketType.TextBufferMulti)
    pb.writeUTF(node.address)
    _pendingCommands = Some(pb)
    pb
  }

  var fullyLitCost: Double = computeFullyLitCost()

  // This computes the energy cost (per tick) to keep the screen running if
  // every single "pixel" is lit. This cost increases with higher tiers as
  // their maximum resolution (pixel density) increases. For a basic screen
  // this is simply the configured cost.
  def computeFullyLitCost(): Double = {
    val (w, h) = Settings.screenResolutionsByTier(0)
    val mw = getMaximumWidth
    val mh = getMaximumHeight
    powerConsumptionPerTick * (mw * mh) / (w * h)
  }

  val proxy: TextBuffer.Proxy =
    if (SideTracker.isClient) new TextBuffer.ClientProxy(this)
    else new TextBuffer.ServerProxy(this)

  val data = new util.TextBuffer(maxResolution, PackedColor.Depth.format(maxDepth))

  var viewport: (Int, Int) = data.size

  def markInitialized(): Unit = {
    syncCooldown = -1 // Stop polling for init state.
    relativeLitArea = -1 // Recompute lit area, avoid screens blanking out until something changes.
  }

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Display,
    DeviceAttribute.Description -> "Text buffer",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "Text Screen V0",
    DeviceAttribute.Capacity -> (maxResolution._1 * maxResolution._2).toString,
    DeviceAttribute.Width -> Array("1", "4", "8").apply(maxDepth.ordinal())
  )

  override def getDeviceInfo: java.util.Map[String, String] = deviceInfo.asJava

  // ----------------------------------------------------------------------- //

  override val canUpdate = true

  override def update(): Unit = {
    super.update()
    if (isDisplaying && host.world.getGameTime % Settings.get.tickFrequency == 0) {
      if (relativeLitArea < 0) {
        // The relative lit area is the number of pixels that are not blank
        // versus the number of pixels in the *current* resolution. This is
        // scaled to multi-block screens, since we only compute this for the
        // origin.
        val w = getViewportWidth
        val h = getViewportHeight
        var acc = 0f
        for (y <- 0 until h) {
          val line = data.buffer(y)
          val colors = data.color(y)
          for (x <- 0 until w) {
            val char = line(x)
            val color = colors(x)
            val bg = PackedColor.unpackBackground(color, data.format)
            val fg = PackedColor.unpackForeground(color, data.format)
            acc += (if (char == ' ') if (bg == 0) 0 else 1
            else if (char == 0x2588) if (fg == 0) 0 else 1
            else if (fg == 0 && bg == 0) 0 else 1)
          }
        }
        relativeLitArea = acc / (w * h).toDouble
      }
      if (node != null) {
        val hadPower = hasPower
        val neededPower = relativeLitArea * fullyLitCost * Settings.get.tickFrequency
        hasPower = node.tryChangeBuffer(-neededPower)
        if (hasPower != hadPower) {
          ServerPacketSender.sendTextBufferPowerChange(node.address, isDisplaying && hasPower, host)
        }
      }
    }

    this.synchronized {
      _pendingCommands.foreach(_.sendToPlayersNearHost(host, Option(Settings.get.maxWirelessRange(Tier.Two) * Settings.get.maxWirelessRange(Tier.Two))))
      _pendingCommands = None
    }

    if (SideTracker.isClient && syncCooldown > 0) {
      syncCooldown -= 1
      if (syncCooldown == 0) {
        syncCooldown = syncInterval
        // TODO(client): 原实现调用 `client.PacketSender.sendTextBufferInit(proxy.nodeAddress)`
        // 向服务端轮询一次完整缓冲内容，客户端网络层尚未移植。
      }
    }
  }

  // ----------------------------------------------------------------------- //

  @Callback(direct = true, doc = """function():boolean -- Returns whether the screen is currently on.""")
  def isOn(computer: Context, args: Arguments): Array[AnyRef] = result(isDisplaying)

  @Callback(doc = """function():boolean -- Turns the screen on. Returns whether the state changed, and whether it is now on.""")
  def turnOn(computer: Context, args: Arguments): Array[AnyRef] = {
    val oldPowerState = isDisplaying
    setPowerState(value = true)
    result(isDisplaying != oldPowerState, isDisplaying)
  }

  @Callback(doc = """function():boolean -- Turns off the screen. Returns whether the state changed, and whether it is now on.""")
  def turnOff(computer: Context, args: Arguments): Array[AnyRef] = {
    val oldPowerState = isDisplaying
    setPowerState(value = false)
    result(isDisplaying != oldPowerState, isDisplaying)
  }

  @Callback(direct = true, doc = """function():number, number -- The aspect ratio of the screen. For multi-block screens this is the number of blocks, horizontal and vertical.""")
  def getAspectRatio(context: Context, args: Arguments): Array[AnyRef] = this.synchronized {
    result(aspectRatio._1, aspectRatio._2)
  }

  @Callback(doc = """function():table -- The list of keyboards attached to the screen.""")
  def getKeyboards(context: Context, args: Arguments): Array[AnyRef] = {
    context.pause(0.25)
    // TODO(common.tileentity): 旧版对多方块屏幕会遍历 `tileentity.Screen#screens` 汇总
    // 所有子屏幕邻居上的键盘。`common/tileentity` 由其它施工者负责、尚未编译进本包，
    // 因此这里先只统计本节点可达的键盘（对单方块屏幕语义完全等价）。
    Array(node.neighbors.asScala.filter(_.host.isInstanceOf[api.internal.Keyboard]).map(_.address).toArray)
  }

  @Callback(direct = true, doc = """function():boolean -- Returns whether the screen is in high precision mode (sub-pixel mouse event positions).""")
  def isPrecise(computer: Context, args: Arguments): Array[AnyRef] = result(precisionMode)

  @Callback(doc = """function(enabled:boolean):boolean -- Set whether to use high precision mode (sub-pixel mouse event positions).""")
  def setPrecise(computer: Context, args: Arguments): Array[AnyRef] = {
    // Available for T3 screens only... easiest way to check for us is to
    // base it off of the maximum color depth.
    if (maxDepth == Settings.screenDepthsByTier(Tier.Three)) {
      val oldValue = precisionMode
      precisionMode = args.checkBoolean(0)
      result(oldValue)
    }
    else result(Unit, "unsupported operation")
  }

  // ----------------------------------------------------------------------- //

  override def setEnergyCostPerTick(value: Double): Unit = {
    powerConsumptionPerTick = value
    fullyLitCost = computeFullyLitCost()
  }

  override def getEnergyCostPerTick: Double = powerConsumptionPerTick

  override def setPowerState(value: Boolean): Unit = {
    if (isDisplaying != value) {
      isDisplaying = value
      if (isDisplaying) {
        val neededPower = fullyLitCost * Settings.get.tickFrequency
        hasPower = node.changeBuffer(-neededPower) == 0
      }
      ServerPacketSender.sendTextBufferPowerChange(node.address, isDisplaying && hasPower, host)
    }
  }

  override def getPowerState: Boolean = isDisplaying

  override def setMaximumResolution(width: Int, height: Int): Unit = {
    if (width < 1) throw new IllegalArgumentException("width must be larger or equal to one")
    if (height < 1) throw new IllegalArgumentException("height must be larger or equal to one")
    maxResolution = (width, height)
    fullyLitCost = computeFullyLitCost()
    proxy.onBufferMaxResolutionChange(width, width)
  }

  override def getMaximumWidth: Int = maxResolution._1

  override def getMaximumHeight: Int = maxResolution._2

  override def setAspectRatio(width: Double, height: Double): Unit = this.synchronized {
    aspectRatio = (width, height)
  }

  override def getAspectRatio: Double = aspectRatio._1 / aspectRatio._2

  override def setResolution(w: Int, h: Int): Boolean = {
    val (mw, mh) = maxResolution
    if (w < 1 || h < 1 || w > mw || h > mw || h * w > mw * mh)
      throw new IllegalArgumentException("unsupported resolution")
    // Always send to clients, their state might be dirty.
    proxy.onBufferResolutionChange(w, h)
    // Force set viewport to new resolution. This is partially for
    // backwards compatibility, and partially to enforce a valid one.
    val sizeChanged = data.size = (w, h)
    val viewportChanged = setViewport(w, h)
    if (sizeChanged || viewportChanged) {
      if (!viewportChanged && node != null) {
        node.sendToReachable("computer.signal", "screen_resized", Int.box(w), Int.box(h))
      }
      true
    }
    else false
  }

  override def setViewport(w: Int, h: Int): Boolean = {
    val (mw, mh) = data.size
    if (w < 1 || h < 1 || w > mw || h > mh)
      throw new IllegalArgumentException("unsupported viewport resolution")
    // Always send to clients, their state might be dirty.
    proxy.onBufferViewportResolutionChange(w, h)
    val (cw, ch) = viewport
    if (w != cw || h != ch) {
      viewport = (w, h)
      if (node != null) {
        node.sendToReachable("computer.signal", "screen_resized", Int.box(w), Int.box(h))
      }
      true
    }
    else false
  }

  override def getViewportWidth: Int = viewport._1

  override def getViewportHeight: Int = viewport._2

  override def setMaximumColorDepth(depth: api.internal.TextBuffer.ColorDepth): Unit = maxDepth = depth

  override def getMaximumColorDepth: api.internal.TextBuffer.ColorDepth = maxDepth

  override def setColorDepth(depth: api.internal.TextBuffer.ColorDepth): Boolean = {
    val colorDepthChanged: Boolean = super.setColorDepth(depth)
    // Always send to clients, their state might be dirty.
    proxy.onBufferDepthChange(depth)
    colorDepthChanged
  }

  override def onBufferPaletteChange(index: Int): Unit =
    proxy.onBufferPaletteChange(index)

  override def onBufferColorChange(): Unit =
    proxy.onBufferColorChange()

  override def onBufferCopy(col: Int, row: Int, w: Int, h: Int, tx: Int, ty: Int): Unit = {
    proxy.onBufferCopy(col, row, w, h, tx, ty)
  }

  override def onBufferFill(col: Int, row: Int, w: Int, h: Int, c: Int): Unit = {
    proxy.onBufferFill(col, row, w, h, c)
  }

  override def onBufferSet(col: Int, row: Int, s: String, vertical: Boolean): Unit = {
    proxy.onBufferSet(col, row, s, vertical)
  }

  override def onBufferBitBlt(col: Int, row: Int, w: Int, h: Int, ram: component.GpuTextBuffer, fromCol: Int, fromRow: Int): Unit = {
    proxy.onBufferBitBlt(col, row, w, h, ram, fromCol, fromRow)
  }

  override def onBufferRamInit(ram: component.GpuTextBuffer): Unit = {
    proxy.onBufferRamInit(ram)
  }

  override def onBufferRamDestroy(ram: component.GpuTextBuffer): Unit = {
    proxy.onBufferRamDestroy(ram)
  }

  override def rawSetText(col: Int, row: Int, text: Array[Array[Int]]): Unit = {
    super.rawSetText(col, row, text)
    proxy.onBufferRawSetText(col, row, text)
  }

  override def rawSetBackground(col: Int, row: Int, color: Array[Array[Int]]): Unit = {
    super.rawSetBackground(col, row, color)
    // Better for bandwidth to send packed shorts here. Would need a special case for handling on client,
    // though, so let's be wasteful for once...
    proxy.onBufferRawSetBackground(col, row, color)
  }

  override def rawSetForeground(col: Int, row: Int, color: Array[Array[Int]]): Unit = {
    super.rawSetForeground(col, row, color)
    // Better for bandwidth to send packed shorts here. Would need a special case for handling on client,
    // though, so let's be wasteful for once...
    proxy.onBufferRawSetForeground(col, row, color)
  }

  override def renderText: Boolean = relativeLitArea != 0 && proxy.render()

  override def renderWidth: Int = TextBufferRenderCache.renderer.charRenderWidth * getViewportWidth

  override def renderHeight: Int = TextBufferRenderCache.renderer.charRenderHeight * getViewportHeight

  override def setRenderingEnabled(enabled: Boolean): Unit = isRendering = enabled

  override def isRenderingEnabled: Boolean = isRendering

  override def keyDown(character: Char, code: Int, player: Player): Unit =
    proxy.keyDown(character, code, player)

  override def keyUp(character: Char, code: Int, player: Player): Unit =
    proxy.keyUp(character, code, player)

  override def clipboard(value: String, player: Player): Unit =
    proxy.clipboard(value, player)

  override def mouseDown(x: Double, y: Double, button: Int, player: Player): Unit =
    proxy.mouseDown(x, y, button, player)

  override def mouseDrag(x: Double, y: Double, button: Int, player: Player): Unit =
    proxy.mouseDrag(x, y, button, player)

  override def mouseUp(x: Double, y: Double, button: Int, player: Player): Unit =
    proxy.mouseUp(x, y, button, player)

  override def mouseScroll(x: Double, y: Double, delta: Int, player: Player): Unit =
    proxy.mouseScroll(x, y, delta, player)

  def copyToAnalyzer(line: Int, player: Player): Unit = {
    proxy.copyToAnalyzer(line, player)
  }

  // ----------------------------------------------------------------------- //

  override def onConnect(node: Node): Unit = {
    super.onConnect(node)
    if (node == this.node) {
      ComponentTracker.add(host.world, node.address, this)
    }
  }

  override def onDisconnect(node: Node): Unit = {
    super.onDisconnect(node)
    if (node == this.node) {
      ComponentTracker.remove(host.world, this)
    }
  }

  // ----------------------------------------------------------------------- //

  override def load(nbt: CompoundTag): Unit = {
    super.load(nbt)
    if (SideTracker.isClient) {
      if (!Strings.isNullOrEmpty(proxy.nodeAddress)) return // Only load once.
      proxy.nodeAddress = nbt.getCompound("node").getString("address")
      TextBuffer.registerClientBuffer(this)
    }
    else {
      if (nbt.contains("buffer")) {
        data.load(nbt.getCompound("buffer"))
      }
      else if (!Strings.isNullOrEmpty(node.address)) {
        data.load(SaveHandler.loadNBT(nbt, node.address + "_buffer"))
      }
    }

    if (nbt.contains(Settings.namespace + "isOn")) {
      isDisplaying = nbt.getBoolean(Settings.namespace + "isOn")
    }
    if (nbt.contains(Settings.namespace + "hasPower")) {
      hasPower = nbt.getBoolean(Settings.namespace + "hasPower")
    }
    if (nbt.contains(Settings.namespace + "maxWidth") && nbt.contains(Settings.namespace + "maxHeight")) {
      val maxWidth = nbt.getInt(Settings.namespace + "maxWidth")
      val maxHeight = nbt.getInt(Settings.namespace + "maxHeight")
      maxResolution = (maxWidth, maxHeight)
    }
    precisionMode = nbt.getBoolean(Settings.namespace + "precise")

    if (nbt.contains(Settings.namespace + "viewportWidth")) {
      val vpw = nbt.getInt(Settings.namespace + "viewportWidth")
      val vph = nbt.getInt(Settings.namespace + "viewportHeight")
      viewport = (vpw min data.width max 1, vph min data.height max 1)
    } else {
      viewport = data.size
    }
  }

  // Null check for Waila (and other mods that may call this client side).
  override def save(nbt: CompoundTag): Unit = if (node != null) {
    super.save(nbt)
    // 线程同步 hack：GPU 允许直接调用修改屏幕以获得更灵敏的体验，这带来如下问题：
    // 保存时若先保存屏幕，执行器线程可能在服务器线程开始保存计算机之前就修改了屏幕，
    // 导致计算机误以为自己改动过屏幕、而实际保存的屏幕快照并不是。
    // 旧版在这里会遍历同网络的所有计算机节点并暂停它们当前执行。
    //
    // TODO(server.machine): 原实现依赖 `tileentity.traits.Computer`（未移植）与
    // `server.machine.Machine#pause`，等 `common/tileentity` 与 `server/machine` 就位后恢复：
    // 遍历 `node.network` 上的计算机节点，对未暂停的调用 `machine.pause(0.1)`。

    SaveHandler.scheduleSave(host, nbt, node.address + "_buffer", data.save _)
    nbt.putBoolean(Settings.namespace + "isOn", isDisplaying)
    nbt.putBoolean(Settings.namespace + "hasPower", hasPower)
    nbt.putInt(Settings.namespace + "maxWidth", maxResolution._1)
    nbt.putInt(Settings.namespace + "maxHeight", maxResolution._2)
    nbt.putBoolean(Settings.namespace + "precise", precisionMode)
    nbt.putInt(Settings.namespace + "viewportWidth", viewport._1)
    nbt.putInt(Settings.namespace + "viewportHeight", viewport._2)
  }
}

object TextBuffer {
  var clientBuffers = mutable.ListBuffer.empty[TextBuffer]

  /**
   * 区块卸载时丢弃该区块内的客户端缓冲。
   *
   * 1.21.1 迁移要点：`ChunkEvent.Unload#getChunk` 返回 `ChunkAccess`（可能没有坐标），
   * 因此改从 `getChunk#getPos` 取区块坐标；旧版的 `Chunk#isAtLocation(x, z)` 已移除。
   */
  @SubscribeEvent
  def onChunkUnload(e: ChunkEvent.Unload): Unit = {
    val level = e.getLevel
    val chunk = e.getChunk
    val chunkX = chunk.getPos.x
    val chunkZ = chunk.getPos.z
    clientBuffers = clientBuffers.filter(t => {
      val blockPos = BlockPosition(t.host)
      val keep = t.host.world != level || chunkX != (blockPos.x >> 4) || chunkZ != (blockPos.z >> 4)
      if (!keep) {
        ComponentTracker.remove(t.host.world, t)
      }
      keep
    })
  }

  @SubscribeEvent
  def onWorldUnload(e: LevelEvent.Unload): Unit = {
    val level = e.getLevel
    clientBuffers = clientBuffers.filter(t => {
      val keep = t.host.world != level
      if (!keep) {
        ComponentTracker.remove(t.host.world, t)
      }
      keep
    })
    ComponentTracker.clear(level)
  }

  def registerClientBuffer(t: TextBuffer): Unit = {
    // TODO(client): 原实现会向服务端请求一次完整初始化
    // （`client.PacketSender.sendTextBufferInit(t.proxy.nodeAddress)`，尚未移植），
    // 并把缓冲登记到 `client.ComponentTracker`。这里先只做本地登记。
    ComponentTracker.add(t.host.world, t.proxy.nodeAddress, t)
    clientBuffers += t
  }

  abstract class Proxy {
    def owner: TextBuffer

    var dirty = false

    var nodeAddress = ""

    def markDirty(): Unit = {
      dirty = true
    }

    def render() = false

    def onBufferColorChange(): Unit

    def onBufferCopy(col: Int, row: Int, w: Int, h: Int, tx: Int, ty: Int): Unit = {
      owner.relativeLitArea = -1
    }

    def onBufferDepthChange(depth: api.internal.TextBuffer.ColorDepth): Unit

    def onBufferFill(col: Int, row: Int, w: Int, h: Int, c: Int): Unit = {
      owner.relativeLitArea = -1
    }

    def onBufferPaletteChange(index: Int): Unit

    def onBufferResolutionChange(w: Int, h: Int): Unit = {
      owner.relativeLitArea = -1
    }

    def onBufferViewportResolutionChange(w: Int, h: Int): Unit = {
      owner.relativeLitArea = -1
    }

    def onBufferMaxResolutionChange(w: Int, h: Int): Unit = {
    }

    def onBufferSet(col: Int, row: Int, s: String, vertical: Boolean): Unit = {
      owner.relativeLitArea = -1
    }

    def onBufferBitBlt(col: Int, row: Int, w: Int, h: Int, ram: component.GpuTextBuffer, fromCol: Int, fromRow: Int): Unit = {
      owner.relativeLitArea = -1
    }

    def onBufferRamInit(ram: component.GpuTextBuffer): Unit = {
      owner.relativeLitArea = -1
    }

    def onBufferRamDestroy(ram: component.GpuTextBuffer): Unit = {
      owner.relativeLitArea = -1
    }

    def onBufferRawSetText(col: Int, row: Int, text: Array[Array[Int]]): Unit = {
      owner.relativeLitArea = -1
    }

    def onBufferRawSetBackground(col: Int, row: Int, color: Array[Array[Int]]): Unit = {
      owner.relativeLitArea = -1
    }

    def onBufferRawSetForeground(col: Int, row: Int, color: Array[Array[Int]]): Unit = {
      owner.relativeLitArea = -1
    }

    def keyDown(character: Char, code: Int, player: Player): Unit

    def keyUp(character: Char, code: Int, player: Player): Unit

    def clipboard(value: String, player: Player): Unit

    def mouseDown(x: Double, y: Double, button: Int, player: Player): Unit

    def mouseDrag(x: Double, y: Double, button: Int, player: Player): Unit

    def mouseUp(x: Double, y: Double, button: Int, player: Player): Unit

    def mouseScroll(x: Double, y: Double, delta: Int, player: Player): Unit

    def copyToAnalyzer(line: Int, player: Player): Unit
  }

  class ClientProxy(val owner: TextBuffer) extends Proxy {
    /**
     * TODO(client): `client.renderer.font.TextBufferRenderData`（字形渲染数据）尚未移植，
     * 这里降级为一个只保存「脏标记 + 单元数据」的占位实现；
     * `TextBufferRenderCache.render` 目前是空实现，因此 `render()` 只回报「是否有变更」。
     */
    val renderer = new TextBufferRenderData {
      override def dirty: Boolean = ClientProxy.this.dirty

      override def dirty_=(value: Boolean): Unit = ClientProxy.this.dirty = value

      override def data = owner.data

      override def viewport: (Int, Int) = owner.viewport
    }

    override def render() = {
      val wasDirty = dirty
      TextBufferRenderCache.render(renderer)
      wasDirty
    }

    override def onBufferColorChange(): Unit = {
      markDirty()
    }

    override def onBufferCopy(col: Int, row: Int, w: Int, h: Int, tx: Int, ty: Int): Unit = {
      super.onBufferCopy(col, row, w, h, tx, ty)
      markDirty()
    }

    override def onBufferDepthChange(depth: api.internal.TextBuffer.ColorDepth): Unit = {
      markDirty()
    }

    override def onBufferFill(col: Int, row: Int, w: Int, h: Int, c: Int): Unit = {
      super.onBufferFill(col, row, w, h, c)
      markDirty()
    }

    override def onBufferPaletteChange(index: Int): Unit = {
      markDirty()
    }

    override def onBufferResolutionChange(w: Int, h: Int): Unit = {
      super.onBufferResolutionChange(w, h)
      markDirty()
    }

    override def onBufferViewportResolutionChange(w: Int, h: Int): Unit = {
      super.onBufferViewportResolutionChange(w, h)
      markDirty()
    }

    override def onBufferSet(col: Int, row: Int, s: String, vertical: Boolean): Unit = {
      super.onBufferSet(col, row, s, vertical)
      markDirty()
    }

    override def onBufferBitBlt(col: Int, row: Int, w: Int, h: Int, ram: component.GpuTextBuffer, fromCol: Int, fromRow: Int): Unit = {
      super.onBufferBitBlt(col, row, w, h, ram, fromCol, fromRow)
      markDirty()
    }

    override def onBufferRamInit(ram: component.GpuTextBuffer): Unit = {
      super.onBufferRamInit(ram)
    }

    override def onBufferRamDestroy(ram: component.GpuTextBuffer): Unit = {
      super.onBufferRamDestroy(ram)
    }

    override def keyDown(character: Char, code: Int, player: Player): Unit = {
      debug(player, s"{type = keyDown, char = $character, code = $code}")
      // TODO(client): 原实现发送 `client.PacketSender.sendKeyDown(nodeAddress, character, code)`。
    }

    override def keyUp(character: Char, code: Int, player: Player): Unit = {
      debug(player, s"{type = keyUp, char = $character, code = $code}")
      // TODO(client): 原实现发送 `client.PacketSender.sendKeyUp(nodeAddress, character, code)`。
    }

    override def clipboard(value: String, player: Player): Unit = {
      debug(player, s"{type = clipboard}")
      // TODO(client): 原实现发送 `client.PacketSender.sendClipboard(nodeAddress, value)`。
    }

    override def mouseDown(x: Double, y: Double, button: Int, player: Player): Unit = {
      debug(player, s"{type = mouseDown, x = $x, y = $y, button = $button}")
      // TODO(client): 原实现发送 `client.PacketSender.sendMouseClick(nodeAddress, x, y, drag = false, button)`。
    }

    override def mouseDrag(x: Double, y: Double, button: Int, player: Player): Unit = {
      debug(player, s"{type = mouseDrag, x = $x, y = $y, button = $button}")
      // TODO(client): 原实现发送 `client.PacketSender.sendMouseClick(nodeAddress, x, y, drag = true, button)`。
    }

    override def mouseUp(x: Double, y: Double, button: Int, player: Player): Unit = {
      debug(player, s"{type = mouseUp, x = $x, y = $y, button = $button}")
      // TODO(client): 原实现发送 `client.PacketSender.sendMouseUp(nodeAddress, x, y, button)`。
    }

    override def mouseScroll(x: Double, y: Double, delta: Int, player: Player): Unit = {
      debug(player, s"{type = mouseScroll, x = $x, y = $y, delta = $delta}")
      // TODO(client): 原实现发送 `client.PacketSender.sendMouseScroll(nodeAddress, x, y, delta)`。
    }

    override def copyToAnalyzer(line: Int, player: Player): Unit = {
      // TODO(client): 原实现发送 `client.PacketSender.sendCopyToAnalyzer(nodeAddress, line)`。
    }

    private lazy val Debugger = api.Items.get(Constants.ItemName.Debugger)

    /**
     * 网络调试器（手持 Debugger 时把发往该节点的报文打印到日志）。
     *
     * 1.21.1 迁移要点：`Minecraft.getMinecraft` / `thePlayer` / `getHeldItem`
     * 分别改为 `Minecraft.getInstance()` / `player` / `getMainHandItem`。
     */
    private def debug(player: Player, message: String): Unit = {
      val minecraft = net.minecraft.client.Minecraft.getInstance
      val localPlayer = if (minecraft != null) minecraft.player else null
      if (localPlayer != null && api.Items.get(localPlayer.getMainHandItem) == Debugger) {
        li.cil.oc.OpenComputers.log.info(s"[NETWORK DEBUGGER] Sending packet to node $nodeAddress: " + message)
      }
    }
  }

  class ServerProxy(val owner: TextBuffer) extends Proxy {
    override def onBufferColorChange(): Unit = {
      owner.host.markChanged()
      owner.synchronized(ServerPacketSender.appendTextBufferColorChange(owner.pendingCommands, owner.data.foreground, owner.data.background))
    }

    override def onBufferCopy(col: Int, row: Int, w: Int, h: Int, tx: Int, ty: Int): Unit = {
      super.onBufferCopy(col, row, w, h, tx, ty)
      owner.host.markChanged()
      owner.synchronized(ServerPacketSender.appendTextBufferCopy(owner.pendingCommands, col, row, w, h, tx, ty))
    }

    override def onBufferDepthChange(depth: api.internal.TextBuffer.ColorDepth): Unit = {
      owner.host.markChanged()
      owner.synchronized(ServerPacketSender.appendTextBufferDepthChange(owner.pendingCommands, depth))
    }

    override def onBufferFill(col: Int, row: Int, w: Int, h: Int, c: Int): Unit = {
      super.onBufferFill(col, row, w, h, c)
      owner.host.markChanged()
      owner.synchronized(ServerPacketSender.appendTextBufferFill(owner.pendingCommands, col, row, w, h, c))
    }

    override def onBufferPaletteChange(index: Int): Unit = {
      owner.host.markChanged()
      owner.synchronized(ServerPacketSender.appendTextBufferPaletteChange(owner.pendingCommands, index, owner.getPaletteColor(index)))
    }

    override def onBufferResolutionChange(w: Int, h: Int): Unit = {
      super.onBufferResolutionChange(w, h)
      owner.host.markChanged()
      owner.synchronized(ServerPacketSender.appendTextBufferResolutionChange(owner.pendingCommands, w, h))
    }

    override def onBufferViewportResolutionChange(w: Int, h: Int): Unit = {
      super.onBufferViewportResolutionChange(w, h)
      owner.host.markChanged()
      owner.synchronized(ServerPacketSender.appendTextBufferViewportResolutionChange(owner.pendingCommands, w, h))
    }

    override def onBufferMaxResolutionChange(w: Int, h: Int): Unit = {
      if (owner.node.network != null) {
        super.onBufferMaxResolutionChange(w, h)
        owner.host.markChanged()
        owner.synchronized(ServerPacketSender.appendTextBufferMaxResolutionChange(owner.pendingCommands, w, h))
      }
    }

    override def onBufferSet(col: Int, row: Int, s: String, vertical: Boolean): Unit = {
      super.onBufferSet(col, row, s, vertical)
      owner.host.markChanged()
      owner.synchronized(ServerPacketSender.appendTextBufferSet(owner.pendingCommands, col, row, s, vertical))
    }

    override def onBufferBitBlt(col: Int, row: Int, w: Int, h: Int, ram: component.GpuTextBuffer, fromCol: Int, fromRow: Int): Unit = {
      super.onBufferBitBlt(col, row, w, h, ram, fromCol, fromRow)
      owner.host.markChanged()
      owner.synchronized(ServerPacketSender.appendTextBufferBitBlt(owner.pendingCommands, col, row, w, h, ram.owner, ram.id, fromCol, fromRow))
    }

    override def onBufferRamInit(ram: component.GpuTextBuffer): Unit = {
      super.onBufferRamInit(ram)
      owner.host.markChanged()
      val nbt = new CompoundTag()
      ram.save(nbt)
      owner.synchronized(ServerPacketSender.appendTextBufferRamInit(owner.pendingCommands, ram.owner, ram.id, nbt))
    }

    override def onBufferRamDestroy(ram: component.GpuTextBuffer): Unit = {
      super.onBufferRamDestroy(ram)
      owner.host.markChanged()
      owner.synchronized(ServerPacketSender.appendTextBufferRamDestroy(owner.pendingCommands, ram.owner, ram.id))
    }

    override def onBufferRawSetText(col: Int, row: Int, text: Array[Array[Int]]): Unit = {
      super.onBufferRawSetText(col, row, text)
      owner.host.markChanged()
      owner.synchronized(ServerPacketSender.appendTextBufferRawSetText(owner.pendingCommands, col, row, text))
    }

    override def onBufferRawSetBackground(col: Int, row: Int, color: Array[Array[Int]]): Unit = {
      super.onBufferRawSetBackground(col, row, color)
      owner.host.markChanged()
      owner.synchronized(ServerPacketSender.appendTextBufferRawSetBackground(owner.pendingCommands, col, row, color))
    }

    override def onBufferRawSetForeground(col: Int, row: Int, color: Array[Array[Int]]): Unit = {
      super.onBufferRawSetForeground(col, row, color)
      owner.host.markChanged()
      owner.synchronized(ServerPacketSender.appendTextBufferRawSetForeground(owner.pendingCommands, col, row, color))
    }

    override def keyDown(character: Char, code: Int, player: Player): Unit = {
      sendToKeyboards("keyboard.keyDown", player, Char.box(character), Int.box(code))
    }

    override def keyUp(character: Char, code: Int, player: Player): Unit = {
      sendToKeyboards("keyboard.keyUp", player, Char.box(character), Int.box(code))
    }

    override def clipboard(value: String, player: Player): Unit = {
      sendToKeyboards("keyboard.clipboard", player, value)
    }

    override def mouseDown(x: Double, y: Double, button: Int, player: Player): Unit = {
      sendMouseEvent(player, "touch", x, y, button)
    }

    override def mouseDrag(x: Double, y: Double, button: Int, player: Player): Unit = {
      sendMouseEvent(player, "drag", x, y, button)
    }

    override def mouseUp(x: Double, y: Double, button: Int, player: Player): Unit = {
      sendMouseEvent(player, "drop", x, y, button)
    }

    override def mouseScroll(x: Double, y: Double, delta: Int, player: Player): Unit = {
      sendMouseEvent(player, "scroll", x, y, delta)
    }

    override def copyToAnalyzer(line: Int, player: Player): Unit = {
      val stack = player.getMainHandItem
      if (stack != null && !stack.isEmpty) {
        if (!stack.hasTag()) {
          stack.setTag(new CompoundTag())
        }
        stack.getTag().remove(Settings.namespace + "clipboard")

        if (line >= 0 && line < owner.getViewportHeight) {
          val text = owner.data.lineToString(line)
          if (!Strings.isNullOrEmpty(text)) {
            stack.getTag().putString(Settings.namespace + "clipboard", text)
          }
        }

        if (stack.getTag().isEmpty) {
          stack.setTag(null)
        }
      }
    }

    private def sendMouseEvent(player: Player, name: String, x: Double, y: Double, data: Int) = {
      val args = mutable.ArrayBuffer.empty[AnyRef]

      args += player
      args += name
      if (owner.precisionMode) {
        args += Double.box(x)
        args += Double.box(y)
      }
      else {
        args += Int.box(x.toInt + 1)
        args += Int.box(y.toInt + 1)
      }
      args += Int.box(data)
      if (Settings.get.inputUsername) {
        args += player.getName.getString
      }

      owner.node.sendToReachable("computer.checked_signal", args.toSeq: _*)
    }

    private def sendToKeyboards(name: String, values: AnyRef*): Unit = {
      // TODO(common.tileentity): 旧版对多方块屏幕会把输入转发给所有子屏幕的键盘
      // （`tileentity.Screen#screens`），该包尚未编译进本包，先按单方块屏幕处理。
      owner.node.sendToNeighbors(name, values.toSeq: _*)
    }
  }

}
