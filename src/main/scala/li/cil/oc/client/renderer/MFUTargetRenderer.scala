package li.cil.oc.client.renderer

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.{ByteBufferBuilder, VertexConsumer}
import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.util.ItemNBT
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.{LevelRenderer, MultiBufferSource, RenderType}
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.AABB
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import net.neoforged.neoforge.common.NeoForge

/**
 * MFU（多方块升级）目标指示器。
 *
 * ==1.7.10 状态==
 * 监听 `RenderWorldLastEvent`。当玩家主手拿着 MFU、且 MFU 的 NBT 里存了
 * 目标坐标时：
 *  - 读 `Settings.namespace + "coord"`（int 数组：x, y, z, side）与维度，
 *    维度不符或距离超过 64 就跳过；
 *  - 用 `GL11.glPolygonMode(GL_LINE)` 画一个比方块略大（expand 0.1）的
 *    绿色线框盒子（`drawBox`），再高亮 MFU 记录的那个面（`drawFace`，
 *    alpha 0.25）；两者都先关掉深度测试与背面剔除。
 *
 * ==1.21.1 迁移要点==
 *  - `RenderWorldLastEvent` 被 `RenderLevelStageEvent` 取代（按 `Stage` 分派）。
 *    这里用 [[RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS]]：它在
 *    所有半透明方块之后、粒子之前，适合画这种世界空间半透明覆盖层。
 *  - `GL11.glPolygonMode(GL_LINE)` 在 1.21.1 的着色器管线里**没有等价物**，
 *    改用 `RenderType.lines()` + `LevelRenderer.renderLineBox(...)`：线框盒子
 *    直接用整盒描边，高亮的那个面用一个被压扁到零厚度的包围盒描边。
 *  - `GL11.glTranslated(-px, -py, -pz)`：事件的 `PoseStack` 原点已经是
 *    相机位置，因此只 translate 目标方块坐标即可。
 *  - `RenderLevelStageEvent` **不提供** `MultiBufferSource`，所以这里自己
 *    用 `MultiBufferSource.immediate(ByteBufferBuilder)` 开一个，画完
 *    `endBatch()` 立刻提交。
 *  - `stack.hasTagCompound` / `getTagCompound` → `hasTag` / `getTag`
 *    （`li.cil.oc` 包对象的 `ItemStackNBTExtensions` 隐式类）。
 *  - `data.contains(key, NBT.TAG_INT_ARRAY)` →
 *    `data.contains(key)` + `getIntArray(key).length` 检查（1.21.1 的
 *    `CompoundTag#contains(String, int)` 已被移除）。
 *  - **维度判定**：`common.item.UpgradeMF` 在 1.21.1 里不再把维度 id 放进
 *    `coord` 数组（数组只有 4 项：x, y, z, side），而是写进独立的字符串键
 *    `Settings.namespace + "dimension"`（值是
 *    `level.dimension().location().toString`）。这里按新格式读取。
 */
object MFUTargetRenderer {
  private val color = 0x00FF00

  /** 原 `lazy val mfu = api.Items.get(Constants.ItemName.MFU)`。 */
  private lazy val mfu = api.Items.get(Constants.ItemName.MFU)

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
    if (e.getStage != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return

    val mc = Minecraft.getInstance
    if (mc == null) return
    val player = mc.player
    if (player == null) return
    val level = player.level()
    if (level == null) return

    val stack: ItemStack = player.getMainHandItem
    if (stack == null || stack.isEmpty) return
    if (mfu == null || api.Items.get(stack) != mfu) return

    // 1.21.1：`ItemStack` 不再直接持有 `CompoundTag`，统一走 `ItemNBT`（数据组件）。
    val data = ItemNBT.get(stack)
    if (data == null) return

    val coordKey = Settings.namespace + "coord"
    if (!data.contains(coordKey)) return
    val coord = data.getIntArray(coordKey)
    if (coord.length < 4) return
    val x = coord(0)
    val y = coord(1)
    val z = coord(2)
    val side = coord(3)

    // 维度判定：1.21.1 的 MFU 把维度写进独立字符串键。
    val dimensionKey = Settings.namespace + "dimension"
    if (data.contains(dimensionKey)) {
      if (data.getString(dimensionKey) != level.dimension().location().toString) return
    }

    if (player.distanceToSqr(x + 0.5, y + 0.5, z + 0.5) > 64 * 64) return

    // 原 `BlockPosition(x, y, z).bounds.expand(0.1, 0.1, 0.1)`。
    val bounds: AABB = new AABB(x, y, z, x + 1, y + 1, z + 1).inflate(0.1, 0.1, 0.1)

    val r = ((color >> 16) & 0xFF) / 255f
    val g = ((color >> 8) & 0xFF) / 255f
    val b = ((color >> 0) & 0xFF) / 255f
    val alpha = 0.25f

    val pose = e.getPoseStack
    pose.pushPose()
    // 事件 PoseStack 的原点就是相机位置，所以这里只移目标方块坐标。
    pose.translate(x.toDouble, y.toDouble, z.toDouble)

    val shared = new ByteBufferBuilder(1536)
    try {
      RenderSystem.disableDepthTest()
      val buffer: MultiBufferSource.BufferSource = MultiBufferSource.immediate(shared)
      val consumer: VertexConsumer = buffer.getBuffer(RenderType.lines())

      // 1) 线框盒子（在方块局部坐标里就是 0..1 的盒子）。
      LevelRenderer.renderLineBox(pose, consumer,
        new AABB(0, 0, 0, 1, 1, 1).inflate(0.1, 0.1, 0.1), r, g, b, alpha)

      // 2) 高亮 MFU 记录的那个面（原 `drawFace`）。
      faceBounds(side) match {
        case Some(face) =>
          LevelRenderer.renderLineBox(pose, consumer, face.inflate(0.1, 0.1, 0.1), r, g, b, 1f)
        case None =>
      }

      buffer.endBatch()
    }
    finally {
      shared.close()
      RenderSystem.enableDepthTest()
    }

    pose.popPose()
  }

  /**
   * 原 `drawFace` 的六个 `side match` 分支。
   *
   * `side` 是 `Direction#ordinal`（0=DOWN, 1=UP, 2=NORTH, 3=SOUTH, 4=WEST,
   * 5=EAST，与 1.21.1 的 `Direction` 顺序一致）。返回的包围盒在该轴上
   * 厚度为 0，正好只用描出这个面的四条边。
   */
  private def faceBounds(side: Int): Option[AABB] = side match {
    case 0 => Some(new AABB(0, 0, 0, 1, 0, 1)) // DOWN
    case 1 => Some(new AABB(0, 1, 0, 1, 1, 1)) // UP
    case 2 => Some(new AABB(0, 0, 0, 1, 1, 0)) // NORTH
    case 3 => Some(new AABB(0, 0, 1, 1, 1, 1)) // SOUTH
    case 4 => Some(new AABB(0, 0, 0, 0, 1, 1)) // WEST
    case 5 => Some(new AABB(1, 0, 0, 1, 1, 1)) // EAST
    case _ => None
  }
}
