package li.cil.oc.common.tileentity

import li.cil.oc.Settings
import li.cil.oc.api.network.Analyzable
import li.cil.oc.api.network._
import li.cil.oc.common.tileentity.traits.RedstoneChangedEventArgs
import li.cil.oc.util.Color
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB

import scala.collection.mutable
import scala.language.postfixOps

/**
 * 屏幕方块实体（原 1.7.10 `common.tileentity.Screen`）：
 * 可多方块拼接的文本显示设备，同时支持红石信号开关、触摸/行走/箭矢命中事件。
 *
 * 纹理：正面（`SOUTH`，即 `facing`）= ScreenFront 系列，多方块时按拼接位置选用
 * 角/边/中间的贴图；背面 = ScreenBack；上下左右 = ScreenSide。
 * 具体面序沿用原 `customTextures`：下/上 = ScreenSide，北 = ScreenBack，
 * 南 = ScreenFront，其它 = ScreenSide（渲染层见
 * `client.renderer.tileentity.ScreenRenderer`）。
 *
 * ==1.21.1 迁移要点==
 *  - 构造函数只有 `(pos, state)`；等级（tier）从方块实例反查（见 [[tier]]），
 *    读档时不再改写等级（1.21.1 每个等级是独立方块）。
 *  - `updateEntity()` → [[traits.TileEntity#tick]]（覆写时先调 `super.tick()`）。
 *  - `world.getTileEntity(x, y, z)` → `world.getBlockEntity(new BlockPos(x, y, z))`；
 *    `world.blockExists(...)` → `world.isLoaded(...)`。
 *  - `ForgeDirection` → `Direction`；`side.offsetX/Y/Z` → `side.getStepX/Y/Z`。
 *  - `AABB.getBoundingBox(...)` → `new AABB(...)`。
 *  - 删除 `@SideOnly`（NeoForge 会因此抛异常）；客户端专用逻辑用注释标注。
 *
 * ==降级清单==
 *  - `common.component.TextBuffer`（真实显存缓冲）：由 [[traits.TextBuffer]] 的占位实现
 *    兜底，`buffer` 的 API 表面不变；组件层移植完成后由驱动替换真实实现。
 *  - `client.gui.Screen` / `net.minecraft.client.Minecraft`：GUI 关闭逻辑删除。
 *  - `server.PacketSender`：状态同步退化为方块更新。
 */
class Screen(pos: BlockPos, state: BlockState)
  extends BlockEntityBase(BlockEntityBase.typeOf(state.getBlock), pos, state)
    with traits.TextBuffer
    with SidedEnvironment
    with traits.Rotatable
    with traits.RedstoneAware
    with traits.Colored
    with Analyzable
    with Ordered[Screen] {

  /** 原 1.7.10 由 metadata 决定；1.21.1 从方块实例读取（0 = 创造模式等级）。 */
  val tier: Int = state.getBlock match {
    case b: li.cil.oc.common.block.Screen => b.tier
    case _ => 0
  }

  // Enable redstone functionality.
  _isOutputEnabled = true

  override def validFacings: Array[Direction] = Direction.values()

  // ----------------------------------------------------------------------- //

  /**
   * Check for multi-block screen option in next update. We do this in the
   * update to avoid unnecessary checks on chunk unload.
   */
  var shouldCheckForMultiBlock = true

  /**
   * On the client we delay connecting screens a little, to avoid glitches
   * when not all tile entity data for a chunk has been received within a
   * single tick (meaning some screens are still "missing").
   */
  var delayUntilCheckForMultiBlock = 40

  var width, height = 1

  var origin: Screen = this

  val screens = mutable.Set(this)

  var hadRedstoneInput = false

  /**
   * 多方块屏幕的整体包围盒缓存（供渲染层使用）。
   *
   * TODO(client.renderer): 1.7.10 里它是 `getRenderBoundingBox` 的缓存，并配合
   * `world.markBlockRangeForRenderUpdate` 让渲染失效。1.21.1 的渲染包围盒由
   * `BlockEntityRenderer#getRenderBoundingBox` 提供，本字段仅保留为数据缓存。
   */
  var cachedBounds: Option[AABB] = None

  var invertTouchMode = false

  private val arrows = mutable.Set.empty[Entity]

  color = Color.byTier(tier)

  // 仅客户端调用；1.7.10 的 `@SideOnly(Side.CLIENT)` 已删除。
  override def canConnect(side: Direction): Boolean = toLocal(side) != Direction.SOUTH

  // Allow connections from front for keyboards, and keyboards only...
  override def sidedNode(side: Direction): Node = {
    if (toLocal(side) != Direction.SOUTH) node
    else {
      val neighborPos = blockPos.relative(side)
      if (world != null && world.isLoaded(neighborPos) && world.getBlockEntity(neighborPos).isInstanceOf[Keyboard]) node
      else null
    }
  }

  // ----------------------------------------------------------------------- //

  def isOrigin: Boolean = origin == this

  def localPosition: (Int, Int) = {
    val (lx, ly, _) = project(this)
    val (ox, oy, _) = project(origin)
    (lx - ox, ly - oy)
  }

  def hasKeyboard: Boolean = screens.exists(screen =>
    Direction.values().map(side => (side, {
      val neighborPos = screen.blockPos.relative(side)
      if (screen.world != null && screen.world.isLoaded(neighborPos)) screen.world.getBlockEntity(neighborPos)
      else null
    })).exists {
      case (side, keyboard: Keyboard) => keyboard.hasNodeOnSide(side.getOpposite)
      case _ => false
    })

  def checkMultiBlock(): Unit = {
    shouldCheckForMultiBlock = true
    width = 1
    height = 1
    origin = this
    screens.clear()
    screens += this
    cachedBounds = None
    invertTouchMode = false
  }

  def toScreenCoordinates(hitX: Double, hitY: Double, hitZ: Double): (Boolean, Option[(Double, Double)]) = {
    // Compute absolute position of the click on the face, measured in blocks.
    def dot(f: Direction) = f.getStepX * hitX + f.getStepY * hitY + f.getStepZ * hitZ
    val (hx, hy) = (dot(toGlobal(Direction.EAST)), dot(toGlobal(Direction.UP)))
    val tx = if (hx < 0) 1 + hx else hx
    val ty = 1 - (if (hy < 0) 1 + hy else hy)
    val (lx, ly) = localPosition
    val (ax, ay) = (lx + tx, height - 1 - ly + ty)

    // Get the relative position in the *display area* of the face.
    val border = 2.25 / 16.0
    if (ax <= border || ay <= border || ax >= width - border || ay >= height - border) {
      return (false, None)
    }
    if (!isClient) return (true, None)

    val (iw, ih) = (width - border * 2, height - border * 2)
    val (rx, ry) = ((ax - border) / iw, (ay - border) / ih)

    // Make it a relative position in the displayed buffer.
    val bw = origin.buffer.getViewportWidth
    val bh = origin.buffer.getViewportHeight
    val (bpw, bph) = (origin.buffer.renderWidth / iw.toDouble, origin.buffer.renderHeight / ih.toDouble)
    val (brx, bry) = if (bpw > bph) {
      val rh = bph.toDouble / bpw.toDouble
      val bry = (ry - (1 - rh) * 0.5) / rh
      (rx, bry)
    }
    else if (bph > bpw) {
      val rw = bpw.toDouble / bph.toDouble
      val brx = (rx - (1 - rw) * 0.5) / rw
      (brx, ry)
    }
    else {
      (rx, ry)
    }

    val inBounds = bry >= 0 && bry <= 1 && brx >= 0 || brx <= 1
    (inBounds, Some((brx * bw, bry * bh)))
  }

  /**
   * 把屏幕内容复制到分析器（原实现在此调用 `common.component.TextBuffer#copyToAnalyzer`）。
   *
   * TODO(common.component): 公开 API 接口 `api.internal.TextBuffer` 没有
   * `copyToAnalyzer`，该方法是 `common.component.TextBuffer` 的自有 API；
   * 该组件包未纳入本次编译范围，这里退化为只返回「点击是否落在显示区内」。
   */
  def copyToAnalyzer(hitX: Double, hitY: Double, hitZ: Double): Boolean = {
    val (inBounds, coordinates) = toScreenCoordinates(hitX, hitY, hitZ)
    coordinates match {
      case Some(_) => false // TODO(common.component): 恢复 buffer.copyToAnalyzer(y.toInt, player)
      case _ => inBounds
    }
  }

  def click(hitX: Double, hitY: Double, hitZ: Double): Boolean = {
    val (inBounds, coordinates) = toScreenCoordinates(hitX, hitY, hitZ)
    coordinates match {
      case Some((x, y)) =>
        // Send the packet to the server (manually, for accuracy).
        origin.buffer.mouseDown(x, y, 0, null)
        true
      case _ => inBounds
    }
  }

  def walk(entity: Entity): Unit = {
    val (x, y) = localPosition
    entity match {
      case player: Player if Settings.get.inputUsername =>
        origin.node.sendToReachable("computer.signal", "walk", Int.box(x + 1), Int.box(height - y), player.getGameProfile.getName)
      case _ =>
        origin.node.sendToReachable("computer.signal", "walk", Int.box(x + 1), Int.box(height - y))
    }
  }

  /**
   * 记录命中本屏幕的箭矢。
   *
   * 1.7.10 里参数类型是 `EntityArrow`；1.21.1 的箭矢是 `AbstractArrow`（`Arrow` /
   * `SpectralArrow` 的父类），这里放宽为 `Entity` 以保持调用点不受影响。
   */
  def shot(arrow: Entity): Unit = {
    arrows.add(arrow)
  }

  // ----------------------------------------------------------------------- //

  override def canUpdate: Boolean = true

  override def tick(): Unit = {
    super.tick()
    if (shouldCheckForMultiBlock && ((isClient && isClientReadyForMultiBlockCheck) || (isServer && isConnected))) {
      // Make sure we merge in a deterministic order, to avoid getting
      // different results on server and client due to the update order
      // differing between the two. This also saves us from having to save
      // any multi-block specific state information.
      val pending = mutable.SortedSet(this)
      val queue = mutable.Queue(this)
      while (queue.nonEmpty) {
        val current = queue.dequeue()
        val (lx, ly, lz) = project(current)
        def tryQueue(dx: Int, dy: Int): Unit = {
          val (nx, ny, nz) = unproject(lx + dx, ly + dy, lz)
          val neighborPos = new BlockPos(nx, ny, nz)
          if (world != null && world.isLoaded(neighborPos)) world.getBlockEntity(neighborPos) match {
            case s: Screen if s.pitch == pitch && s.yaw == yaw && pending.add(s) => queue += s
            case _ => // Ignore.
          }
        }
        tryQueue(-1, 0)
        tryQueue(1, 0)
        tryQueue(0, -1)
        tryQueue(0, 1)
      }
      // Perform actual merges.
      while (pending.nonEmpty) {
        val current = pending.firstKey
        while (current.tryMerge()) {}
        current.screens.foreach {
          screen =>
            screen.shouldCheckForMultiBlock = false
            pending.remove(screen)
            queue += screen
        }
        if (isClient) {
          // TODO(client.renderer): 原为 world.markBlockRangeForRenderUpdate(bounds)，
          // 1.21.1 的渲染失效由客户端渲染层自行处理。
        }
      }
      // Update visibility after everything is done, to avoid noise.
      queue.foreach(screen => {
        val buffer = screen.buffer
        if (screen.isOrigin) {
          if (isServer) {
            buffer.node.asInstanceOf[Component].setVisibility(Visibility.Network)
            buffer.setEnergyCostPerTick(Settings.get.screenCost * screen.width * screen.height)
            buffer.setAspectRatio(screen.width, screen.height)
          }
        }
        else {
          if (isServer) {
            buffer.node.asInstanceOf[Component].setVisibility(Visibility.None)
            buffer.setEnergyCostPerTick(Settings.get.screenCost)
          }
          buffer.setAspectRatio(1, 1)
          val w = buffer.getWidth
          val h = buffer.getHeight
          buffer.setForegroundColor(0xFFFFFF, false)
          buffer.setBackgroundColor(0x000000, false)
          buffer.fill(0, 0, w, h, 0x20)
        }
      })
    }
    if (arrows.nonEmpty) {
      for (arrow <- arrows) {
        val hitX = arrow.getX - x
        val hitY = arrow.getY - y
        val hitZ = arrow.getZ - z
        // TODO(client.Minecraft): 原实现只处理「由本地玩家射出」的箭矢
        // （比较 `Minecraft.getMinecraft.thePlayer`），1.21.1 该 API 已移至
        // 客户端渲染层，这里退化为对所有命中的箭矢都触发点击。
        click(hitX, hitY, hitZ)
      }
      arrows.clear()
    }
  }

  private def isClientReadyForMultiBlockCheck = if (delayUntilCheckForMultiBlock > 0) {
    delayUntilCheckForMultiBlock -= 1
    false
  } else true

  override def dispose(): Unit = {
    super.dispose()
    screens.clone().foreach(_.checkMultiBlock())
    // TODO(client.gui): 原实现在客户端打开着本屏幕 GUI 时关闭它
    // （`Minecraft.getMinecraft.currentScreen` 匹配 `client.gui.Screen`）。
    // `li.cil.oc.client.gui` 未纳入本次编译范围，GUI 关闭交由其自身会话失效处理。
  }

  override protected def onColorChanged(): Unit = {
    super.onColorChanged()
    screens.clone().foreach(_.checkMultiBlock())
  }

  // ----------------------------------------------------------------------- //

  override protected def readFromNBTForServer(nbt: CompoundTag): Unit = {
    // 1.7.10 在这里从 NBT 恢复 tier；1.21.1 的等级由方块决定，不能（也不需要）改写。
    color = Color.byTier(tier)
    super.readFromNBTForServer(nbt)
    hadRedstoneInput = nbt.getBoolean(Settings.namespace + "hadRedstoneInput")
    invertTouchMode = nbt.getBoolean(Settings.namespace + "invertTouchMode")
  }

  override protected def writeToNBTForServer(nbt: CompoundTag): Unit = {
    nbt.putByte(Settings.namespace + "tier", tier.toByte)
    super.writeToNBTForServer(nbt)
    nbt.putBoolean(Settings.namespace + "hadRedstoneInput", hadRedstoneInput)
    nbt.putBoolean(Settings.namespace + "invertTouchMode", invertTouchMode)
  }

  /** 仅客户端使用（原 `@SideOnly(Side.CLIENT)`，1.21.1 已删除该注解）。 */
  override protected def readFromNBTForClient(nbt: CompoundTag): Unit = {
    super.readFromNBTForClient(nbt)
    invertTouchMode = nbt.getBoolean("invertTouchMode")
  }

  override protected def writeToNBTForClient(nbt: CompoundTag): Unit = {
    super.writeToNBTForClient(nbt)
    nbt.putBoolean("invertTouchMode", invertTouchMode)
  }

  // ----------------------------------------------------------------------- //

  override def onAnalyze(player: Player, side: Int, hitX: Float, hitY: Float, hitZ: Float): Array[Node] = Array(origin.node)

  override protected def onRedstoneInputChanged(args: RedstoneChangedEventArgs): Unit = {
    super.onRedstoneInputChanged(args)
    val hasRedstoneInput = screens.map(_.maxInput).max > 0
    if (hasRedstoneInput != hadRedstoneInput) {
      hadRedstoneInput = hasRedstoneInput
      if (hasRedstoneInput) {
        origin.buffer.setPowerState(!origin.buffer.getPowerState)
      }
    }
  }

  override protected def onRotationChanged(): Unit = {
    super.onRotationChanged()
    screens.clone().foreach(_.checkMultiBlock())
  }

  // ----------------------------------------------------------------------- //

  override def compare(that: Screen): Int =
    if (x != that.x) x - that.x
    else if (y != that.y) y - that.y
    else z - that.z

  // ----------------------------------------------------------------------- //

  private def tryMerge(): Boolean = {
    val (ox, oy, oz) = project(origin)
    def tryMergeTowards(dx: Int, dy: Int): Boolean = {
      val (nx, ny, nz) = unproject(ox + dx, oy + dy, oz)
      val neighborPos = new BlockPos(nx, ny, nz)
      world != null && world.isLoaded(neighborPos) && (world.getBlockEntity(neighborPos) match {
        case s: Screen if s.tier == tier && s.pitch == pitch && s.color == color && s.yaw == yaw && !screens.contains(s) =>
          val (sx, sy, _) = project(s.origin)
          val canMergeAlongX = sy == oy && s.height == height && s.width + width <= Settings.get.maxScreenWidth
          val canMergeAlongY = sx == ox && s.width == width && s.height + height <= Settings.get.maxScreenHeight
          if (canMergeAlongX || canMergeAlongY) {
            val newOrigin =
              if (canMergeAlongX) {
                if (sx < ox) s.origin else origin
              }
              else {
                if (sy < oy) s.origin else origin
              }
            val (newWidth, newHeight) =
              if (canMergeAlongX) (width + s.width, height)
              else (width, height + s.height)
            val newScreens = screens ++ s.screens
            for (screen <- newScreens) {
              screen.width = newWidth
              screen.height = newHeight
              screen.origin = newOrigin
              screen.screens ++= newScreens // It's a set, so there won't be duplicates.
              screen.cachedBounds = None
            }
            true
          }
          else false // Cannot merge.
        case _ => false
      })
    }
    tryMergeTowards(0, height) || tryMergeTowards(0, -1) || tryMergeTowards(width, 0) || tryMergeTowards(-1, 0)
  }

  private def project(t: Screen): (Int, Int, Int) = {
    def dot(f: Direction, s: Screen) = f.getStepX * s.x + f.getStepY * s.y + f.getStepZ * s.z
    (dot(toGlobal(Direction.EAST), t), dot(toGlobal(Direction.UP), t), dot(toGlobal(Direction.SOUTH), t))
  }

  private def unproject(x: Int, y: Int, z: Int): (Int, Int, Int) = {
    def dot(f: Direction) = f.getStepX * x + f.getStepY * y + f.getStepZ * z
    (dot(toLocal(Direction.EAST)), dot(toLocal(Direction.UP)), dot(toLocal(Direction.SOUTH)))
  }
}
