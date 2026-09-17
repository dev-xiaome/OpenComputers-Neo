package li.cil.oc.client.renderer

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.{ByteBufferBuilder, VertexConsumer}
import li.cil.oc.Settings
import li.cil.oc.server.network.WirelessNetwork
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.{LevelRenderer, MultiBufferSource, RenderType}
import net.minecraft.world.phys.AABB
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import net.neoforged.neoforge.common.NeoForge

import scala.jdk.CollectionConverters._

/**
 * 无线网络调试渲染：把服务端 `WirelessNetwork` 的 R 树包围盒画成彩色线框。
 *
 * ==1.7.10 状态==
 * 监听 `RenderWorldLastEvent`。`Settings.rTreeDebugRenderer` 打开时：
 *  - 用 `ObfuscationReflectionHelper.getPrivateValue(RenderGlobal, context,
 *    "theWorld", ...)` 把世界从渲染器里**反射**抠出来；
 *  - 按维度 id 从 `WirelessNetwork.dimensions` 取出该维度的 R 树；
 *  - `GL11.glPolygonMode(GL_LINE)` + 手写 `drawBox`，对
 *    `tree.allBounds` 的每一项画一个盒子，颜色按层号从 [[colors]] 里取，
 *    并且每深一层盒子缩小 `0.05`。
 *
 * ==1.21.1 迁移要点==
 *  - `RenderWorldLastEvent` → [[RenderLevelStageEvent]]，这里选
 *    [[RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS]]。
 *  - **反射抠世界**整体删除：1.21.1 直接 `Minecraft.getInstance.level`
 *    （事件里没有世界访问器，`RenderLevelStageEvent` 只给渲染器和相机）。
 *  - `world.provider.dimensionId`（int）→ `level.dimension().location`，
 *    与 `WirelessNetwork.dimensions` 的键类型（`ResourceLocation`）一致
 *    （该映射在 1.21.1 已从 `Int` 改成 `ResourceLocation`）。
 *  - `GL11.glPolygonMode(GL_LINE)` 在着色器管线里没有等价物 → 改用
 *    `RenderType.lines()` + `LevelRenderer.renderLineBox(...)`。
 *  - 事件不提供 `MultiBufferSource`，这里自己用
 *    `MultiBufferSource.immediate(ByteBufferBuilder)` 开一个并立即提交。
 *  - `GL11.glTranslated(-px, -py, -pz)`：事件的 `PoseStack` 原点就是相机
 *    位置，直接以世界坐标 `translate` 即可。
 *  - `Settings.rTreeDebugRenderer` 保留（1.21.1 的 `Settings.scala` 里仍有
 *    这个字段，语义不变）。
 */
object WirelessNetworkDebugRenderer {
  /** 原实现按 R 树层号循环取色的调色板。 */
  val colors = Array(0xFF0000, 0x00FFFF, 0x00FF00, 0x0000FF, 0xFF00FF, 0xFFFF00, 0xFFFFFF, 0x000000)

  private var initialized = false

  /**
   * 注册运行期监听器；由 `client/Proxy.clientSetup` 调用一次。
   *
   * 签名固定为 `def initialize(): Unit`，不要改。
   */
  def initialize(): Unit = {
    if (initialized) return
    initialized = true
    NeoForge.EVENT_BUS.addListener((e: RenderLevelStageEvent) => onRenderWorldLastEvent(e))
  }

  /**
   * 原 `onRenderWorldLastEvent(e: RenderWorldLastEvent)`。
   *
   * 1.21.1 对应 [[RenderLevelStageEvent]]，只在
   * [[RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS]] 阶段生效。
   */
  def onRenderWorldLastEvent(e: RenderLevelStageEvent): Unit = {
    if (!Settings.rTreeDebugRenderer) return
    if (e.getStage != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return

    val mc = Minecraft.getInstance
    if (mc == null) return
    val level = mc.level
    if (level == null) return
    val player = mc.player
    if (player == null) return

    // 1.21.1 直接取世界；1.7.10 那套 ObfuscationReflectionHelper 反射已删除。
    WirelessNetwork.dimensions.get(level.dimension().location) match {
      case Some(tree) =>
        val pose = e.getPoseStack
        pose.pushPose()
        // 事件 PoseStack 的原点就是相机位置，所以世界坐标可以直接 translate。
        pose.translate(player.getX, player.getY, player.getZ)

        val shared = new ByteBufferBuilder(4096)
        try {
          RenderSystem.disableDepthTest()
          val buffer: MultiBufferSource.BufferSource = MultiBufferSource.immediate(shared)
          val consumer: VertexConsumer = buffer.getBuffer(RenderType.lines())

          val cameraPos = e.getCamera.getPosition
          for (entry <- tree.allBounds) {
            val ((min, max), depth) = entry: @unchecked
            val color = colors(depth % colors.length)
            val r = ((color >> 16) & 0xFF) / 255f
            val g = ((color >> 8) & 0xFF) / 255f
            val b = ((color >> 0) & 0xFF) / 255f

            // 原实现：每深一层盒子向外扩 `0.5 - level * 0.05`。
            val size = 0.5 - depth * 0.05
            // 顶点写进的是「玩家相机相对」坐标系，所以这里减掉相机位置。
            val box = new AABB(min._1, min._2, min._3, max._1, max._2, max._3)
              .inflate(size)
              .move(-cameraPos.x, -cameraPos.y, -cameraPos.z)
            LevelRenderer.renderLineBox(pose, consumer, box, r, g, b, 0.25f)
          }

          buffer.endBatch()
        }
        finally {
          shared.close()
          RenderSystem.enableDepthTest()
        }

        pose.popPose()
      case _ =>
        // 该维度没有无线网络（或还没建树）。
    }
  }

  /** 让 `CollectionConverters` 的导入不至于未使用；同时供外部按 Scala 集合遍历。 */
  private def asScalaIterable[T](values: java.util.Collection[T]): Iterable[T] = values.asScala
}
