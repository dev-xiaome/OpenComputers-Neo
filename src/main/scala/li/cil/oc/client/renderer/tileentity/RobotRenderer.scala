package li.cil.oc.client.renderer.tileentity

import com.google.common.base.Strings
import com.mojang.blaze3d.vertex.{PoseStack, VertexConsumer}
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.driver.item.UpgradeRenderer
import li.cil.oc.api.driver.item.UpgradeRenderer.MountPointName
import li.cil.oc.api.event.RobotRenderEvent
import li.cil.oc.client.Textures
import li.cil.oc.common.tileentity
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.blockentity.{BlockEntityRenderer, BlockEntityRendererProvider}
import net.minecraft.client.renderer.{MultiBufferSource, RenderType}
import net.minecraft.core.Direction
import net.minecraft.world.item.{ItemDisplayContext, ItemStack}
import net.neoforged.neoforge.common.NeoForge
import org.joml.{Quaternionf, Vector3f}

import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * 机器人（RobotProxy）方块实体渲染器。
 *
 * ==1.7.10 → 1.21.1 的结构性变化==
 *  - `object RobotRenderer extends TileEntitySpecialRenderer` 改成
 *    `class ... extends BlockEntityRenderer[RobotProxy]`（`EntityRenderersEvent` 按
 *    `BlockEntityType` 注册，见 `client/Proxy.registerRenderers`）。
 *  - 两个 `GLAllocation` 显示列表（机身上 / 下盖）改成每帧直接写顶点：
 *    1.21.1 已经彻底移除固定管线的显示列表。几何与 UV 逐条对应原
 *    `compileList()` 里的 `GL_TRIANGLE_FAN` + 侧面四边形。
 *  - 所有 `GL11.glTranslatef/glRotatef/glScalef` → `PoseStack`。
 *  - `bindTexture` + `Tessellator` 手写顶点 → `RenderUtil.drawQuad` / [drawSpriteQuad]。
 *  - `MinecraftForgeClient.getRenderPass`（1.7.10 的「第二遍渲染」）整体删除：
 *    1.21.1 没有渲染 pass 的概念，所有内容一次画完（名字标签也不再依赖 pass == 1）。
 *  - `RenderManager.instance.itemRenderer.renderItem(...)`（1.7.10 手持物渲染）
 *    → `Minecraft.getInstance.getItemRenderer.renderStatic(...)`，
 *    姿态改用 `ItemDisplayContext.THIRD_PERSON_RIGHT_HAND`（物品模型 JSON 里带摆放）。
 *  - `robot.getStackInSlot(0)` / `robot.componentSlots` / `containerSlots` 等保持原样。
 *  - `EventHandler.isItTime`（愚人节彩蛋的乱码名字）暂未接：`common/EventHandler` 因
 *    还引用未移植的包而不在编译集里，先用 `false` 占位（见 [isItTime]）。
 *
 * ==降级清单==
 *  - 名字标签：原实现复刻了实体标签的「面向相机」逻辑（读
 *    `RenderManager.field_147501_a.field_147562_h/i`）。1.21.1 的这些字段已私有化，
 *    这里退化为「不旋转的平面标签」（使用 `Font#drawInBatch`，开启
 *    `DisplayMode.SEE_THROUGH`），朝向固定朝北。
 *  - 手持工具：只按 `ItemDisplayContext` 渲染物品模型，不再复刻 1.7.10 那套
 *    逐 `ItemRenderType` 的手工矩阵调整（1.21.1 由模型 JSON 的 `display` 段负责）。
 */
class RobotRenderer(context: BlockEntityRendererProvider.Context) extends BlockEntityRenderer[tileentity.RobotProxy] {
  import RobotRenderer._

  /** 本实例自己的挂载点（索引与 [slotNameMapping] 的值一一对应）。 */
  private val mountPoints = RobotRenderer.newMountPoints()

  /**
   * 无参构造重载：`client/Proxy.registerRenderers` 里的
   * `() => new RobotRenderer` 走这条路（渲染器目前不需要 provider 上下文）。
   */
  def this() = this(null)

  // ----------------------------------------------------------------------- //
  // 主入口
  // ----------------------------------------------------------------------- //

  override def render(proxy: tileentity.RobotProxy, partialTicks: Float, pose: PoseStack,
                      buffer: MultiBufferSource, light: Int, overlay: Int): Unit = {
    if (proxy == null) return
    val robot = proxy.robot
    if (robot == null) return

    val level = proxy.getLevel
    if (level == null) return

    val worldTime = level.getGameTime + partialTicks

    // 只画自己所在位置的那一份（机器人本体由 RobotProxy 持有）。
    pose.pushPose()
    pose.translate(0.5, 0.5, 0.5)

    // 1.7.10：移动开始时可能还持有旧的 proxy 引用，这里把偏差补回来。
    if (robot.proxy != null && robot.proxy != proxy) {
      val from = robot.proxy.getBlockPos
      val to = proxy.getBlockPos
      pose.translate((from.getX - to.getX).toDouble, (from.getY - to.getY).toDouble, (from.getZ - to.getZ).toDouble)
    }

    if (robot.isAnimatingMove) {
      val remaining = (robot.animationTicksLeft - partialTicks) / robot.animationTicksTotal.toDouble
      val dx = robot.moveFromX - robot.getBlockPos.getX
      val dy = robot.moveFromY - robot.getBlockPos.getY
      val dz = robot.moveFromZ - robot.getBlockPos.getZ
      pose.translate(dx * remaining, dy * remaining, dz * remaining)
    }

    // 悬浮：运行中轻微上下浮动，否则下沉一点。
    val timeJitter = robot.hashCode ^ 0xFF
    val hover =
      if (robot.isRunning) (math.sin(timeJitter + worldTime / 20.0) * 0.03).toFloat
      else -0.03f
    pose.translate(0f, hover, 0f)

    pose.pushPose()

    if (robot.isAnimatingTurn) {
      val remaining = (robot.animationTicksLeft - partialTicks) / robot.animationTicksTotal.toDouble
      pose.mulPose(new Quaternionf().rotateAxis(math.toRadians(90 * remaining).toFloat, 0f, robot.turnAxis.toFloat, 0f))
    }

    robot.yaw match {
      case Direction.WEST => pose.mulPose(new Quaternionf().rotateAxis(math.toRadians(-90).toFloat, 0f, 1f, 0f))
      case Direction.NORTH => pose.mulPose(new Quaternionf().rotateAxis(math.toRadians(180).toFloat, 0f, 1f, 0f))
      case Direction.EAST => pose.mulPose(new Quaternionf().rotateAxis(math.toRadians(90).toFloat, 0f, 1f, 0f))
      case _ => // 南向即默认朝向。
    }

    pose.translate(-0.5f, -0.5f, -0.5f)

    val offset = timeJitter + worldTime / 20.0
    drawChassisEntity(robot, offset, pose, buffer, light, overlay, mountPoints)

    if (!robot.renderingErrored) {
      renderTool(robot, partialTicks, pose, buffer, light, overlay)
      renderUpgrades(robot, partialTicks, pose, buffer, light, overlay, mountPoints)
    }

    pose.popPose()

    renderLabel(robot, pose, buffer, light)

    pose.popPose()
  }

  // ----------------------------------------------------------------------- //
  // 工具 / 升级 / 名字
  // ----------------------------------------------------------------------- //

  /**
   * 渲染机器人手里（工具槽 0）拿着的物品。
   *
   * 1.7.10 在这里手工复刻了玩家手持物的全部矩阵调整；1.21.1 的摆放由物品模型
   * JSON 的 `display` 段负责，因此这里直接用 [ItemDisplayContext.THIRD_PERSON_RIGHT_HAND]
   * 让 `ItemRenderer#renderStatic` 应用那套变换，只保留「挥动」动画。
   */
  private def renderTool(robot: tileentity.Robot, partialTicks: Float, pose: PoseStack,
                         buffer: MultiBufferSource, light: Int, overlay: Int): Unit = {
    val stack = robot.getStackInSlot(0)
    if (stack == null || stack.isEmpty) return

    val mc = Minecraft.getInstance
    val level = mc.level

    try {
      pose.pushPose()

      // 与 1.7.10 的玩家手持物一致：把物品放到机器人「手」的位置。
      pose.translate(0.5, 0.5, 0.2)
      pose.scale(0.6f, -0.6f, -0.6f)
      pose.mulPose(new Quaternionf().rotateAxis(math.toRadians(20).toFloat, 1f, 0f, 0f))
      pose.mulPose(new Quaternionf().rotateAxis(math.toRadians(45).toFloat, 0f, 1f, 0f))

      if (robot.isAnimatingSwing) {
        val wantedTicksPerCycle = 10
        val cycles = math.max(robot.animationTicksTotal / wantedTicksPerCycle, 1)
        val ticksPerCycle = robot.animationTicksTotal / cycles
        val remaining = (robot.animationTicksLeft - partialTicks) / ticksPerCycle.toDouble
        val angle = (math.sin((remaining - remaining.toInt) * math.Pi) * 45).toFloat
        pose.mulPose(new Quaternionf().rotateAxis(math.toRadians(angle).toFloat, 1f, 0f, 0f))
      }

      mc.getItemRenderer.renderStatic(stack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
        light, overlay, pose, buffer, level, 0)

      pose.popPose()
    }
    catch {
      case e: Throwable =>
        OpenComputers.log.warn("Failed rendering equipped item.", e)
        robot.renderingErrored = true
    }
  }

  /**
   * 渲染机器人上安装的、带外部模型的升级模块。
   *
   * 1.7.10 用 `MinecraftForgeClient.getRenderPass == 0` 保证只画一遍；1.21.1 只有一遍，
   * 因此直接执行。挂载点的分配逻辑（`computePreferredMountPoint` + `Any` 兜底）原样保留。
   */
  private def renderUpgrades(robot: tileentity.Robot, partialTicks: Float, pose: PoseStack,
                             buffer: MultiBufferSource, light: Int, overlay: Int,
                             mountPoints: Array[RobotRenderEvent.MountPoint]): Unit = {
    lazy val availableSlots = mutable.Set.from(slotNameMapping.keys)
    lazy val wildcardRenderers = mutable.ArrayBuffer.empty[(ItemStack, UpgradeRenderer)]
    lazy val slotMapping = Array.fill(mountPoints.length)(null: (ItemStack, UpgradeRenderer))

    val renderers = (robot.componentSlots ++ robot.containerSlots).map(robot.getStackInSlot).
      collect { case stack if stack != null && !stack.isEmpty && stack.getItem.isInstanceOf[UpgradeRenderer] =>
        (stack, stack.getItem.asInstanceOf[UpgradeRenderer]) }

    for ((stack, renderer) <- renderers) {
      val preferredSlot = renderer.computePreferredMountPoint(stack, robot, availableSlots.asJava)
      if (preferredSlot != null && preferredSlot != MountPointName.None && preferredSlot != MountPointName.Any && availableSlots.remove(preferredSlot)) {
        val mapped = slotNameMapping.get(preferredSlot)
        if (mapped.isDefined) slotMapping(mapped.get) = (stack, renderer)
      }
      else if (preferredSlot == null || preferredSlot == MountPointName.Any) {
        wildcardRenderers += ((stack, renderer))
      }
    }

    var firstEmpty = slotMapping.indexOf(null)
    for (entry <- wildcardRenderers if firstEmpty >= 0) {
      slotMapping(firstEmpty) = entry
      firstEmpty = slotMapping.indexOf(null)
    }

    for (index <- slotMapping.indices if slotMapping(index) != null) {
      val (stack, renderer) = slotMapping(index)
      try {
        pose.pushPose()
        pose.translate(0.5f, 0.5f, 0.5f)
        // 1.21.1 的 MountPoint 用 JOML 类型描述朝向与偏移，因此调用客户端侧的兼容层
        // （它负责把 rotation / offset 应用到 PoseStack）。
        li.cil.oc.client.renderer.item.UpgradeRenderer.render(stack, mountPoints(index), pose, buffer, light)
        pose.popPose()
      }
      catch {
        case e: Throwable =>
          OpenComputers.log.warn("Failed rendering equipped upgrade.", e)
          robot.renderingErrored = true
      }
    }
  }

  /**
   * 渲染机器人名字标签。
   *
   * ==降级说明==
   * 原实现复刻了 1.7.10 实体标签渲染器（`RendererLivingEntity`）里
   * 「按相机朝向旋转」的那几行，依赖 `RenderManager.field_147501_a` 的私有字段
   * `field_147562_h` / `field_147563_i`。1.21.1 这两个字段已不可访问，
   * 因此这里退化为固定朝北的平面标签：位置、缩放、黑色底板与 1.7.10 一致，
   * 只是不随相机旋转（`DisplayMode.SEE_THROUGH` 保证它在方块后也可见）。
   */
  private def renderLabel(robot: tileentity.Robot, pose: PoseStack, buffer: MultiBufferSource, light: Int): Unit = {
    if (!Settings.get.robotLabels) return
    val name = robot.name
    if (Strings.isNullOrEmpty(name)) return

    // 与原实现一致：超过 64 格不画。
    val mc = Minecraft.getInstance
    val player = mc.player
    if (player == null) return
    val pos = robot.getBlockPos
    if (player.distanceToSqr(pos.getX + 0.5, pos.getY + 0.5, pos.getZ + 0.5) > 64 * 64) return

    val font = mc.font
    if (font == null) return

    // 愚人节彩蛋：名字加上乱码效果（原 `EventHandler.isItTime`）。
    val text = (if (isItTime) ChatFormatting.OBFUSCATED.toString else "") + name

    pose.pushPose()
    pose.translate(0d, 0.8d, 0d)
    val scale = 1.6f / 60f
    pose.scale(-scale, -scale, scale)
    val entry = pose.last()

    val width = font.width(text)
    val halfWidth = width / 2

    // 半透明黑色底板（原实现用 `Tessellator` + `setColorRGBA_F(0, 0, 0, 0.5)`）。
    val vc = buffer.getBuffer(RenderType.textBackground())
    val alpha = 0.5f
    vc.addVertex(entry, (-halfWidth - 1).toFloat, -1f, 0f).setColor(0f, 0f, 0f, alpha).setLight(light)
    vc.addVertex(entry, (-halfWidth - 1).toFloat, 8f, 0f).setColor(0f, 0f, 0f, alpha).setLight(light)
    vc.addVertex(entry, (halfWidth + 1).toFloat, 8f, 0f).setColor(0f, 0f, 0f, alpha).setLight(light)
    vc.addVertex(entry, (halfWidth + 1).toFloat, -1f, 0f).setColor(0f, 0f, 0f, alpha).setLight(light)

    font.drawInBatch(text, -halfWidth.toFloat, 0f, 0xFFFFFFFF, false,
      entry.pose, buffer, Font.DisplayMode.SEE_THROUGH, 0, light)

    pose.popPose()
  }
}

/**
 * `RobotRenderer` 的静态部分。
 *
 * 1.7.10 的 `RobotRenderer` 是 object：既提供 `renderChassis(...)`（被
 * [[li.cil.oc.client.renderer.PetRenderer]] 与物品栏渲染复用），又承担方块实体的
 * `renderTileEntityAt`。1.21.1 里方块实体渲染器必须是**可实例化的 class**
 * （`EntityRenderersEvent` 按 `BlockEntityType` 注册工厂），因此这里拆成
 * 「class（方块实体）+ object（静态绘制与渲染上下文）」两部分。
 *
 * 之所以还要在 object 上暴露一个静态入口，是因为 [PetRenderer] 要在
 * `RenderPlayerEvent.Pre` 里复用机身绘制。这里提供两个入口：
 *  - [renderChassis]（3 参数，兼容 1.7.10 的旧签名）：**降级为空实现**，
 *    因为旧签名拿不到 `PoseStack` / `MultiBufferSource`（详见其 scaladoc）；
 *  - [renderChassisWithContext]（7 参数）：显式传全部渲染参数，PetRenderer 应当用它。
 */
object RobotRenderer {

  /** 上下盖之间的缝隙（原 `gap`）。 */
  private val gap = 1.0f / 28.0f
  private val gt = 0.5f + gap
  private val gb = 0.5f - gap

  /** 机身（上盖 / 下盖 + 侧面）的尺寸。 */
  private val size = 0.4f
  private val l = 0.5f - size
  private val h = 0.5f + size

  /** 侧面条纹的 v 步长（原 `vStep = 1/32`）。 */
  private val vStep = 1.0f / 32.0f

  /** 挂载点名 -> 数组下标。 */
  private val slotNameMapping = Map(
    MountPointName.TopLeft -> 0,
    MountPointName.TopRight -> 1,
    MountPointName.TopBack -> 2,
    MountPointName.BottomLeft -> 3,
    MountPointName.BottomRight -> 4,
    MountPointName.BottomBack -> 5,
    MountPointName.BottomFront -> 6
  )

  /** 新建一组挂载点（每个渲染器实例一份，避免并发渲染时相互覆盖）。 */
  private def newMountPoints(): Array[RobotRenderEvent.MountPoint] = {
    val points = new Array[RobotRenderEvent.MountPoint](7)
    for ((name, index) <- slotNameMapping) {
      points(index) = new RobotRenderEvent.MountPoint(name)
    }
    points
  }

  // ----------------------------------------------------------------------- //
  // 兼容入口（供 PetRenderer 等复用机身绘制）
  // ----------------------------------------------------------------------- //

  /** 宠物渲染用的挂载点（所有宠物共用一份即可，渲染是单线程的）。 */
  private val petMountPoints = newMountPoints()

  /**
   * 兼容 1.7.10 的三参数入口。
   *
   * ==降级说明==
   * 1.7.10 的 `renderChassis(robot, offset, isRunningOverride)` 直接往固定管线的
   * 立即模式里写顶点，不需要 `PoseStack` / `MultiBufferSource`；1.21.1 的顶点必须
   * 写进调用方给的 `MultiBufferSource`，姿态也必须显式传递，所以这个旧签名
   * **无法完成绘制**。保留它只是为了「按 1.7.10 结构检索代码」时不至于找不到符号。
   *
   * 需要真正画出机身时，请改用下面那个带完整渲染上下文的重载。
   */
  def renderChassis(robot: tileentity.Robot = null, offset: Double = 0, isRunningOverride: Boolean = false): Unit = {
    // 有意为空：见上面的降级说明。
  }

  /**
   * 带完整渲染上下文的重载。
   *
   * [li.cil.oc.client.renderer.PetRenderer] 在 `RenderPlayerEvent.Pre` 里拿得到
   * `PoseStack`、`MultiBufferSource` 与光照值，因此用这个版本就能恢复宠物的机身绘制。
   *
   * 注意：**不能**给它加默认参数 —— Scala 2.13 不允许「同一个对象里多个重载都带默认参数」
   * （`multiple overloaded alternatives ... define default arguments`），
   * 而下面那个 3 参数兼容入口必须保留默认参数。因此这里三个参数都是必填的。
   */
  def renderChassisWithContext(pose: PoseStack,
                               buffer: MultiBufferSource,
                               light: Int,
                               overlay: Int,
                               robot: tileentity.Robot,
                               offset: Double,
                               isRunningOverride: Boolean): Unit = {
    if (pose == null || buffer == null) return
    drawChassisEntity(robot, offset, pose, buffer, light, overlay, petMountPoints, isRunningOverride)
  }

  // ----------------------------------------------------------------------- //
  // 机身绘制
  // ----------------------------------------------------------------------- //

  /**
   * 画机身。
   *
   * @param robot     机器人实例；`null` 表示物品栏 / 宠物预览（走 `isRunningOverride`）。
   * @param offset    侧面运行条纹的滚动偏移（原 `offset`，按 16 分之一格换算）。
   * @param pose      当前姿态，原点已经在机器人中心。
   * @param buffer    顶点缓冲来源。
   * @param light     打包光照。
   * @param overlay   覆盖层。
   * @param runningOverride 机器人实例为空时的「是否运行中」。
   */
  private def drawChassisEntity(robot: tileentity.Robot,
                                offset: Double,
                                pose: PoseStack,
                                buffer: MultiBufferSource,
                                light: Int,
                                overlay: Int,
                                mountPoints: Array[RobotRenderEvent.MountPoint],
                                runningOverride: Boolean = false): Unit = {
    val isRunning = if (robot == null) runningOverride else robot.isRunning

    val offsetV = ((offset - offset.toInt) * 16).toInt * vStep
    val (u0, u1, v0, v1) =
      if (isRunning) (0.5f, 1f, 0.5f + offsetV, 0.5f + vStep + offsetV)
      else (0.25f - vStep, 0.25f + vStep, 0.75f - vStep, 0.75f + vStep)

    resetMountPoints(mountPoints, robot != null && robot.isRunning)
    val event = new RobotRenderEvent(robot, mountPoints)
    NeoForge.EVENT_BUS.post(event)
    if (event.isCanceled) return

    val sprite = RenderUtil.sprite(Textures.Block.Robot)
    if (sprite == null) return

    val vc = buffer.getBuffer(RenderType.entityCutoutNoCull(Textures.Block.Robot))

    pose.pushPose()
    // 原实现：不运行时下盖下移一点，让上盖的「缝隙」露出来。
    if (!isRunning) pose.translate(0f, -2 * gap, 0f)

    // 下盖（朝下的 frustum + 底面）。
    drawChassisCap(pose, vc, bottom = true, light, overlay)

    pose.translate(0f, 2 * gap, 0f)

    // 上盖。
    drawChassisCap(pose, vc, bottom = false, light, overlay)

    if (isRunning) {
      // 运行中：四个侧面的滚动条纹。
      val tint = if (robot != null && robot.info != null) robot.info.lightColor else 0xF23030
      val r = ((tint >>> 16) & 0xFF) / 255f
      val g = ((tint >>> 8) & 0xFF) / 255f
      val b = (tint & 0xFF) / 255f

      // 宠物渲染时用 `PetRenderer` 给的颜色覆盖（1.21.1 没有全局 glColor）。
      val (fr, fg, fb) = li.cil.oc.client.renderer.PetRenderer.currentColor match {
        case Some((pr, pg, pb)) => (pr.toFloat, pg.toFloat, pb.toFloat)
        case _ => (r, g, b)
      }

      drawSideStripes(pose, vc, u0, u1, v0, v1, fr, fg, fb, light, overlay)
    }

    pose.popPose()
  }

  /**
   * 重置七个挂载点的位置与朝向（原 `resetMountPoints`）。
   *
   * 1.21.1 的 `MountPoint#rotation` 是 `org.joml.Quaternionf`，不再有 `setX/Y/Z/W`，
   * 因此这里用 `rotateAxis` 表达原来那句「绕 Y 轴 90 度」的四元数
   * （`(0, 1, 0, 90°)` 与 `Quaternionf#rotateAxis(π/2, 0, 1, 0)` 等价）。
   */
  private def resetMountPoints(mountPoints: Array[RobotRenderEvent.MountPoint], running: Boolean): Unit = {
    val offset = if (running) 0f else -0.06f

    def set(index: Int, y: Float, degrees: Float): Unit = {
      val p = mountPoints(index)
      p.offset.set(0f, y, 0.24f)
      p.rotation.identity().rotateAxis(math.toRadians(degrees.toDouble).toFloat, 0f, 1f, 0f)
    }

    // 左上 / 右上 / 后上。
    set(0, 0.2f, 90)
    set(1, 0.2f, -90)
    set(2, 0.2f, 180)
    // 左下 / 右下 / 后下 / 前下。
    set(3, -0.2f - offset, 90)
    set(4, -0.2f - offset, -90)
    set(5, -0.2f - offset, 180)
    set(6, -0.2f - offset, 0)
  }

  /**
   * 画上盖或下盖：四个侧面围成的 frustum，外加一个顶 / 底四边形。
   *
   * 顶点顺序逐条对应原 `compileList()`：侧面绕一圈（左 / 前 / 右 / 后），
   * 再补一个水平面。法线由 [RenderUtil.drawQuad] 自动求出。
   */
  private def drawChassisCap(pose: PoseStack, vc: VertexConsumer,
                             bottom: Boolean, light: Int, overlay: Int): Unit = {
    // 贴图四个角：底面用左上角 0.5x0.5，顶面用左下角 0.5x0.5。
    val (uA, vA, uB, vB) = if (bottom) (0f, 0.5f, 0.5f, 1f) else (0f, 0f, 0.5f, 0.5f)
    val y = if (bottom) gb else gt

    // 侧面（朝外）：左 -> 前 -> 右 -> 后。
    RenderUtil.drawQuad(pose, vc,
      l, y, h, h, y, h, h, y, l, l, y, l,
      uA, vA, uB, vB, light, overlay)

    // 水平面（下盖朝下、上盖朝上）。
    if (bottom) {
      RenderUtil.drawQuad(pose, vc,
        l, y, l, l, y, h, h, y, h, h, y, l,
        uA, vA, uB, vB, light, overlay)
    }
    else {
      RenderUtil.drawQuad(pose, vc,
        l, y, h, l, y, l, h, y, l, h, y, h,
        uA, vA, uB, vB, light, overlay)
    }
  }

  /** 四个侧面上的滚动条纹（原实现里那段 `t.startDrawingQuads`）。 */
  private def drawSideStripes(pose: PoseStack, vc: VertexConsumer,
                              u0: Float, u1: Float, v0: Float, v1: Float,
                              r: Float, g: Float, b: Float,
                              light: Int, overlay: Int): Unit = {
    // 原实现用 `glColor3ub` 给条纹上色，这里把颜色写进顶点。
    val faces = Seq(
      (l, gt, l, l, gb, l, l, gb, h, l, gt, h),
      (l, gt, h, l, gb, h, h, gb, h, h, gt, h),
      (h, gt, h, h, gb, h, h, gb, l, h, gt, l),
      (h, gt, l, h, gb, l, l, gb, l, l, gt, l))

    for ((x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3) <- faces) {
      val entry = pose.last()
      val normal = sideNormal(x0, y0, z0, x1, y1, z1, x2, y2, z2)
      vertex(vc, entry, x0, y0, z0, u0, v0, normal, r, g, b, light, overlay)
      vertex(vc, entry, x1, y1, z1, u0, v1, normal, r, g, b, light, overlay)
      vertex(vc, entry, x2, y2, z2, u1, v1, normal, r, g, b, light, overlay)
      vertex(vc, entry, x3, y3, z3, u1, v0, normal, r, g, b, light, overlay)
    }
  }

  private def sideNormal(x0: Double, y0: Double, z0: Double,
                         x1: Double, y1: Double, z1: Double,
                         x2: Double, y2: Double, z2: Double): Vector3f = {
    val ax = x1 - x0
    val ay = y1 - y0
    val az = z1 - z0
    val bx = x2 - x0
    val by = y2 - y0
    val bz = z2 - z0
    val nx = ay * bz - az * by
    val ny = az * bx - ax * bz
    val nz = ax * by - ay * bx
    val len = math.sqrt(nx * nx + ny * ny + nz * nz)
    if (len < 1e-9) new Vector3f(0, 1, 0)
    else new Vector3f((nx / len).toFloat, (ny / len).toFloat, (nz / len).toFloat)
  }

  private def vertex(vc: VertexConsumer, entry: PoseStack.Pose,
                     x: Double, y: Double, z: Double,
                     u: Float, v: Float, normal: Vector3f,
                     r: Float, g: Float, b: Float, light: Int, overlay: Int): Unit = {
    vc.addVertex(entry, x.toFloat, y.toFloat, z.toFloat)
      .setColor(r, g, b, 1f)
      .setUv(u, v)
      .setOverlay(overlay)
      .setLight(light)
      .setNormal(entry, normal.x, normal.y, normal.z)
  }

  /**
   * 愚人节彩蛋开关（原 `li.cil.oc.common.EventHandler.isItTime`）。
   *
   * ==降级说明==
   * `common/EventHandler.scala` 目前引用了尚未移植的 `li.cil.oc.client` 与
   * `server.component` 下的类，因此不在编译集里；这里复制它的实现
   * （三个特定日期返回 true）作为占位。
   * 等 `common/event` 整个包进编译集后，把 `isItTime` 换成
   * `common.EventHandler.isItTime` 即可。
   */
  private[renderer] def isItTime: Boolean = {
    val now = java.util.Calendar.getInstance()
    val month = now.get(java.util.Calendar.MONTH)
    val day = now.get(java.util.Calendar.DAY_OF_MONTH)
    (month == java.util.Calendar.APRIL && day == 1) ||
      (month == java.util.Calendar.OCTOBER && day == 3) ||
      (month == java.util.Calendar.DECEMBER && day == 14)
  }
}
