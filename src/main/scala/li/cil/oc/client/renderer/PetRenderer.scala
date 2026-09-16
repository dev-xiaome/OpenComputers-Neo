package li.cil.oc.client.renderer

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import li.cil.oc.client.renderer.tileentity.RobotRenderer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.entity.Entity
import net.neoforged.neoforge.client.event.{ClientTickEvent, RenderPlayerEvent}
import net.neoforged.neoforge.common.NeoForge

import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * 跟随玩家的「宠物」（联动机器人）渲染。
 *
 * ==1.7.10 状态==
 *  - `onPlayerRender(e: RenderPlayerEvent.Pre)`：玩家在 [[entitledPlayers]]
 *    白名单内且未被 [[hidden]] 屏蔽时，在玩家身上画一个机器人底盘
 *    （`RobotRenderer.renderChassis(null, offset, true)`）。位置由
 *    [[PetLocation]] 做平滑跟随（把每 tick 位移积分成一个速度量，再做低通
 *    滤波），另外叠加一个正弦上下浮动 `hover`。绘制前把该玩家的颜色写进
 *    内部字段，供 `onRobotRender` 给 `RobotRenderEvent` 的升级渲染器上色。
 *  - `onRobotRender(e: RobotRenderEvent)`：`@SubscribeEvent(priority = LOWEST)`，
 *    把 `GL11.glColor3d` 设为当前宠物颜色。
 *  - `tickStart(e: ClientTickEvent)`：清缓存 + 更新所有 [[PetLocation]]。
 *
 * ==1.21.1 迁移要点==
 *  - `RenderPlayerEvent.Pre` 在 1.21.1 仍然存在，且 `PoseStack` 的**原点
 *    就是玩家实体位置**（相机相对，见 `LivingEntityRenderer#render`），
 *    所以不再需要 1.7.10 里那套「玩家世界坐标减本地玩家坐标」的补偿。
 *  - `GL11` → `PoseStack`；`GL11.glRotatef(-yaw, 0, 1, 0)` →
 *    `PoseStack#mulPose(Axis.YP.rotationDegrees(-yaw))`。
 *  - `FMLCommonHandler.instance.bus` 上的客户端 tick 在 1.21.1 合并到
 *    `NeoForge.EVENT_BUS`，对应 [[ClientTickEvent.Post]]。
 *  - `e.entityPlayer.getEntityWorld.getTotalWorldTime` →
 *    `player.level().getGameTime`。
 *  - `e.entityPlayer.getUniqueID` → `player.getUUID`。
 *  - `Minecraft.getMinecraft.thePlayer` → `Minecraft.getInstance.player`；
 *    `mc.currentScreen` → `mc.screen`。
 *  - `Entity#lastTickPosX / posX` → `Entity#xo / getX`（1.21.1 里
 *    `lastTickPosX` 已改名 `xo`）。
 *  - 颜色：1.21.1 没有全局 `glColor`，`RobotRenderEvent` 也不再携带颜色
 *    状态，因此改为通过 [[currentColor]] 暴露给 `RobotRenderer` 读取。
 *
 * ==对外契约（不要改）==
 *  - `hidden: mutable.Set[String]`：被屏蔽的玩家 UUID 字符串集合，
 *    由 `client/PacketHandler` 写入。
 *  - `isInitialized: Boolean`：由 `client/PacketHandler` 读取。
 */
object PetRenderer {
  /** 被屏蔽的玩家 UUID 字符串；由网络层写入。 */
  val hidden = mutable.Set.empty[String]

  /** 是否已完成初始化；由网络层读取。 */
  var isInitialized = false

  /** 原实现的联动玩家白名单（UUID → 机器人灯光颜色 RGB）。 */
  private val entitledPlayers = Map(
    "9f1f262f-0d68-4e13-9161-9eeaf4a0a1a8" -> (0.3, 0.9, 0.6), // Sangar
    "18f8bed4-f027-44af-8947-6a3a2317645a" -> (1.0, 0.0, 0.0), // Jodarion
    "36123742-2cf6-4cfc-8b65-278581b3caeb" -> (0.5, 0.7, 1.0), // DaKaTotal
    "2c0c214b-96f4-4565-b513-de90d5fbc977" -> (1.0, 0.0, 0.0), // MichiRavencroft
    "f3ba6ec8-c280-4950-bb08-1fcb2eab3a9c" -> (0.18, 0.95, 0.922), // Vexatos
    "9d636bdd-b9f4-4b80-b9ce-586ca04bd4f3" -> (0.8, 0.77, 0.75), // StoneNomad
    "23c7ed71-fb13-4abe-abe7-f355e1de6e62" -> (0.3, 0.3, 1.0), // LizzyTheSiren
    "076541f1-f10a-46de-a127-dfab8adfbb75" -> (0.2, 1.0, 0.1), // vifino
    "e7e90198-0ccf-4662-a827-192ec8f4419d" -> (0.0, 0.2, 0.6), // Izaya
    "f514ee69-7bbb-4e46-9e94-d8176324cec2" -> (0.098, 0.471, 0.784), // Wobbo
    "f812c043-78ba-4324-82ae-e8f05c52ae6e" -> (0.1, 0.8, 0.5) // payonel
  )

  /**
   * 平滑跟随用的位置缓存。
   *
   * 1.7.10 用 Guava 的 `CacheBuilder.expireAfterAccess(5, SECONDS)`；
   * 1.21.1 继续用同一套 API，只是把泛型写法改成 Scala 2.13 的显式形式。
   */
  private val petLocations = com.google.common.cache.CacheBuilder.newBuilder()
    .expireAfterAccess(5, java.util.concurrent.TimeUnit.SECONDS)
    .asInstanceOf[com.google.common.cache.CacheBuilder[Entity, PetLocation]]
    .build[Entity, PetLocation]()

  /** 当前正在渲染的宠物颜色；`RobotRenderer` 可以通过 [[currentColor]] 读取。 */
  private var rendering: Option[(Double, Double, Double)] = None

  private var initialized = false

  /**
   * 注册运行期监听器；由 `client/Proxy.clientSetup` 调用一次。
   *
   * 签名固定为 `def initialize(): Unit`，不要改。
   */
  def initialize(): Unit = {
    if (initialized) return
    initialized = true
    NeoForge.EVENT_BUS.addListener((e: RenderPlayerEvent.Pre) => onPlayerRender(e))
    NeoForge.EVENT_BUS.addListener((e: ClientTickEvent.Post) => tickStart(e))
    isInitialized = true
  }

  /** 当前宠物颜色（供 `RobotRenderer` / 升级渲染器查询）。 */
  def currentColor: Option[(Double, Double, Double)] = rendering

  /** 原 `onPlayerRender(e: RenderPlayerEvent.Pre)`。 */
  def onPlayerRender(e: RenderPlayerEvent.Pre): Unit = {
    val player = e.getEntity
    if (player == null) return
    val uuid = player.getUUID.toString
    if (hidden.contains(uuid) || !entitledPlayers.contains(uuid)) return

    val mc = Minecraft.getInstance
    if (mc == null || mc.level == null) return

    rendering = Some(entitledPlayers(uuid))

    val worldTime = mc.level.getGameTime
    val partialTick = e.getPartialTick
    val timeJitter = player.hashCode ^ 0xFF
    val offset = timeJitter + worldTime / 20.0
    val hover = (math.sin(timeJitter + (worldTime + partialTick) / 20.0) * 0.03).toFloat

    val location = petLocations.get(player, new java.util.concurrent.Callable[PetLocation] {
      override def call(): PetLocation = new PetLocation(player)
    })

    try {
      val pose = e.getPoseStack
      if (pose == null) return

      // 1.21.1 的 `RenderPlayerEvent.Pre` 里 PoseStack 原点已经是玩家实体
      // 位置（相机相对），不再需要 1.7.10 的「世界坐标差」补偿。
      pose.pushPose()

      location.applyInterpolatedTransformations(pose, partialTick)

      pose.scale(0.3f, 0.3f, 0.3f)
      pose.translate(0f, hover, 0f)

      // 原 `RobotRenderer.renderChassis(null, offset, isRunningOverride = true)`。
      // 1.21.1 的顶点必须写进 `MultiBufferSource`，而 `RenderPlayerEvent.Pre` 正好
      // 提供 `getMultiBufferSource()` / `getPackedLight()`，因此这里改调
      // 带完整渲染上下文的重载（旧 3 参数签名已降级为空实现）。
      RobotRenderer.renderChassisWithContext(
        pose, e.getMultiBufferSource, e.getPackedLight, OverlayTexture.NO_OVERLAY,
        null, offset, true)

      pose.popPose()
    }
    finally {
      rendering = None
    }
  }

  /**
   * 原 `onRobotRender(e: RobotRenderEvent)`（`priority = LOWEST`）。
   *
   * 1.21.1 里 `RobotRenderEvent` 不再携带 `GL11` 颜色状态，颜色改由
   * [[currentColor]] 暴露；这里保留一个可显式调用的取色钩子。
   */
  def onRobotRender(): Option[(Double, Double, Double)] = rendering

  /**
   * 平滑跟随位置。
   *
   * 原实现把玩家的**每 tick 位移**积分进 `x/y/z`（当作惯性速度），
   * 再做 `*= 0.05` 的低通滤波；`yaw` 用 `*= 0.2` 的插值。
   * 1.21.1 里 `posX/posY/posZ` 改成 `getX()/getY()/getZ()`，
   * `lastTickPosX` 改成 `xo`。
   */
  private class PetLocation(val owner: Entity) {
    var x = 0.0
    var y = 0.0
    var z = 0.0
    var yaw = owner.getYRot

    var lastX = x
    var lastY = y
    var lastZ = z
    var lastYaw = yaw

    def update(): Unit = {
      val dx = owner.xo - owner.getX
      val dy = owner.yo - owner.getY
      val dz = owner.zo - owner.getZ
      val dYaw = owner.getYRot - yaw
      lastX = x
      lastY = y
      lastZ = z
      lastYaw = yaw
      x += dx
      y += dy
      z += dz
      x *= 0.05
      y *= 0.05
      z *= 0.05
      yaw += dYaw * 0.2f
    }

    /** 原 `applyInterpolatedTransformations(dt)`；1.21.1 改成写进 `PoseStack`。 */
    def applyInterpolatedTransformations(pose: PoseStack, dt: Float): Unit = {
      val ix = lastX + (x - lastX) * dt
      val iy = lastY + (y - lastY) * dt
      val iz = lastZ + (z - lastZ) * dt
      val iYaw = lastYaw + (yaw - lastYaw) * dt

      pose.translate(ix, iy, iz)
      if (!isForInventory) {
        pose.mulPose(Axis.YP.rotationDegrees(-iYaw))
      }
      else {
        pose.mulPose(Axis.YP.rotationDegrees(-owner.getYRot))
      }
      pose.translate(0.3, -0.1, -0.2)
    }

    /** 原 `isForInventory`：本地玩家且打开了界面（此时玩家实体本身不渲染）。 */
    private def isForInventory: Boolean = {
      val mc = Minecraft.getInstance
      mc != null && mc.screen != null && (owner eq mc.player)
    }
  }

  /** 原 `tickStart(e: ClientTickEvent)`，1.21.1 对应 `ClientTickEvent.Post`。 */
  def tickStart(e: ClientTickEvent.Post): Unit = {
    petLocations.cleanUp()
    for (pet <- petLocations.asMap.values.asScala) {
      pet.update()
    }
  }
}
