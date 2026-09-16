package li.cil.oc.client.renderer.item

import com.mojang.blaze3d.vertex.{PoseStack, VertexConsumer}
import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.api.driver.item.UpgradeRenderer.MountPointName
import li.cil.oc.api.event.RobotRenderEvent.MountPoint
import li.cil.oc.client.Textures
import li.cil.oc.integration.opencomputers.Item
import net.minecraft.client.renderer.{MultiBufferSource, RenderType}
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.AABB

/**
 * 机器人上「升级模块」的外部模型渲染（对应 1.7.10 的 `object UpgradeRenderer`）。
 *
 * ==1.7.10 → 1.21.1 的结构性变化==
 *  - `Minecraft.getMinecraft.getTextureManager.bindTexture(...)` + `GL11.glBegin` 立即模式
 *    → `MultiBufferSource#getBuffer(RenderType)` + [[VertexConsumer]]。
 *  - `GL11.glRotatef/glTranslatef` → [[PoseStack]]（`mulPose` 需要四元数，
 *    而 `MountPoint#rotation` 在 1.21.1 正好是 `org.joml.Quaternionf`，可以直接乘上去）。
 *  - 渲染上下文（`PoseStack` / `MultiBufferSource` / 打包光照）必须由调用方传入：
 *    1.21.1 没有全局渲染状态。调用方（机器人渲染器）应当已经 `pushPose` 并把原点
 *    移到机器人中心，然后调用 [[render]]；对应的 1.7.10 调用点是
 *    `RobotRenderer` 里 `GL11.glTranslatef(0.5f, 0.5f, 0.5f)` 之后的
 *    `renderer.render(stack, mountPoint, robot, f)`。
 *  - `RenderState.checkError` 删除：1.21.1 的顶点都进缓冲，没有立即模式错误可查。
 */
object UpgradeRenderer {
  lazy val craftingUpgrade = api.Items.get(Constants.ItemName.CraftingUpgrade)
  lazy val generatorUpgrade = api.Items.get(Constants.ItemName.GeneratorUpgrade)
  lazy val inventoryUpgrade = api.Items.get(Constants.ItemName.InventoryUpgrade)

  /** 升级模块的立方体半边长（原 `bounds`）。 */
  private val bounds = new AABB(-0.1, -0.1, -0.1, 0.1, 0.1, 0.1)

  def preferredMountPoint(stack: ItemStack, availableMountPoints: java.util.Set[String]): String = {
    val descriptor = api.Items.get(stack)

    if (descriptor == craftingUpgrade || descriptor == generatorUpgrade || descriptor == inventoryUpgrade) {
      if (descriptor == generatorUpgrade && availableMountPoints.contains(MountPointName.BottomBack)) MountPointName.BottomBack
      else if (descriptor == inventoryUpgrade && availableMountPoints.contains(MountPointName.TopBack)) MountPointName.TopBack
      else MountPointName.Any
    }
    else MountPointName.None
  }

  def canRender(stack: ItemStack): Boolean = {
    val descriptor = api.Items.get(stack)

    descriptor == craftingUpgrade || descriptor == generatorUpgrade || descriptor == inventoryUpgrade
  }

  /**
   * 在机器人上画出一个升级模块。
   *
   * @param stack      升级物品堆叠（决定贴图与「是否在运行」）。
   * @param mountPoint 挂载点；`rotation` 先应用，`offset` 后应用（与原实现一致）。
   * @param poseStack  当前姿态，原点应当已经在机器人中心。
   * @param buffer     顶点缓冲来源。
   * @param packedLight 打包光照（调用方按机器人所在位置取样）。
   */
  def render(stack: ItemStack,
             mountPoint: MountPoint,
             poseStack: PoseStack,
             buffer: MultiBufferSource,
             packedLight: Int): Unit = {
    if (mountPoint == null) return

    val descriptor = api.Items.get(stack)

    if (descriptor == craftingUpgrade) {
      drawSimpleBlock(poseStack, buffer, Textures.Model.UpgradeCrafting, mountPoint, 0f, packedLight)
    }
    else if (descriptor == generatorUpgrade) {
      // 原实现：`Item.dataTag(stack).getInteger("remainingTicks") > 0` 时取贴图右半边。
      val frontOffset = if (Item.dataTag(stack).getInt("remainingTicks") > 0) 0.5f else 0f
      drawSimpleBlock(poseStack, buffer, Textures.Model.UpgradeGenerator, mountPoint, frontOffset, packedLight)
    }
    else if (descriptor == inventoryUpgrade) {
      drawSimpleBlock(poseStack, buffer, Textures.Model.UpgradeInventory, mountPoint, 0f, packedLight)
    }
    // 其它升级没有外部模型，直接跳过（原实现也是空分支）。
  }

  /**
   * 画一个 0.2 边长的立方体。
   *
   * UV 布局沿用 1.7.10：贴图左上角 0.5x0.5 是正面（`frontOffset` 用来在
   * 「关闭 / 开启」两张正面图之间切换），右上角 0.5x0.5 是顶面，
   * 右下角 0.5x0.5 是底面，左下角 0.5x0.5 是左右两侧。
   */
  private def drawSimpleBlock(poseStack: PoseStack,
                              buffer: MultiBufferSource,
                              texture: ResourceLocation,
                              mountPoint: MountPoint,
                              frontOffset: Float,
                              packedLight: Int): Unit = {
    poseStack.pushPose()

    // 原实现先 `glRotatef(...)` 再 `glTranslatef(...)`。
    poseStack.mulPose(mountPoint.rotation)
    poseStack.translate(mountPoint.offset.x.toDouble, mountPoint.offset.y.toDouble, mountPoint.offset.z.toDouble)

    val vc = buffer.getBuffer(RenderType.entityCutoutNoCull(texture))
    val f = frontOffset

    // 正面（+Z）。
    quad(vc, poseStack,
      bounds.minX, bounds.minY, bounds.maxZ, f, 0.5f,
      bounds.maxX, bounds.minY, bounds.maxZ, f + 0.5f, 0.5f,
      bounds.maxX, bounds.maxY, bounds.maxZ, f + 0.5f, 0f,
      bounds.minX, bounds.maxY, bounds.maxZ, f, 0f,
      0f, 0f, 1f, packedLight)

    // 顶面（+Y）。
    quad(vc, poseStack,
      bounds.maxX, bounds.maxY, bounds.maxZ, 1f, 0.5f,
      bounds.maxX, bounds.maxY, bounds.minZ, 1f, 1f,
      bounds.minX, bounds.maxY, bounds.minZ, 0.5f, 1f,
      bounds.minX, bounds.maxY, bounds.maxZ, 0.5f, 0.5f,
      0f, 1f, 0f, packedLight)

    // 底面（-Y）。
    quad(vc, poseStack,
      bounds.minX, bounds.minY, bounds.maxZ, 0.5f, 0.5f,
      bounds.minX, bounds.minY, bounds.minZ, 0.5f, 1f,
      bounds.maxX, bounds.minY, bounds.minZ, 1f, 1f,
      bounds.maxX, bounds.minY, bounds.maxZ, 1f, 0.5f,
      0f, -1f, 0f, packedLight)

    // 左侧（+X）。
    quad(vc, poseStack,
      bounds.maxX, bounds.maxY, bounds.maxZ, 0f, 0.5f,
      bounds.maxX, bounds.minY, bounds.maxZ, 0f, 1f,
      bounds.maxX, bounds.minY, bounds.minZ, 0.5f, 1f,
      bounds.maxX, bounds.maxY, bounds.minZ, 0.5f, 0.5f,
      1f, 0f, 0f, packedLight)

    // 右侧（-X）。
    quad(vc, poseStack,
      bounds.minX, bounds.minY, bounds.maxZ, 0f, 1f,
      bounds.minX, bounds.maxY, bounds.maxZ, 0f, 0.5f,
      bounds.minX, bounds.maxY, bounds.minZ, 0.5f, 0.5f,
      bounds.minX, bounds.minY, bounds.minZ, 0.5f, 1f,
      -1f, 0f, 0f, packedLight)

    poseStack.popPose()
  }

  /** 写一个四边形（四个角顺时针给出，UV 与之一一对应）。 */
  private def quad(vc: VertexConsumer, poseStack: PoseStack,
                   x0: Double, y0: Double, z0: Double, u0: Float, v0: Float,
                   x1: Double, y1: Double, z1: Double, u1: Float, v1: Float,
                   x2: Double, y2: Double, z2: Double, u2: Float, v2: Float,
                   x3: Double, y3: Double, z3: Double, u3: Float, v3: Float,
                   nx: Float, ny: Float, nz: Float, packedLight: Int): Unit = {
    val entry = poseStack.last()
    vertex(vc, entry, x0, y0, z0, u0, v0, nx, ny, nz, packedLight)
    vertex(vc, entry, x1, y1, z1, u1, v1, nx, ny, nz, packedLight)
    vertex(vc, entry, x2, y2, z2, u2, v2, nx, ny, nz, packedLight)
    vertex(vc, entry, x3, y3, z3, u3, v3, nx, ny, nz, packedLight)
  }

  private def vertex(vc: VertexConsumer, entry: PoseStack.Pose,
                     x: Double, y: Double, z: Double,
                     u: Float, v: Float,
                     nx: Float, ny: Float, nz: Float,
                     packedLight: Int): Unit = {
    vc.addVertex(entry, x.toFloat, y.toFloat, z.toFloat)
      .setColor(255, 255, 255, 255)
      .setUv(u, v)
      .setOverlay(OverlayTexture.NO_OVERLAY)
      .setLight(packedLight)
      .setNormal(nx, ny, nz)
  }
}
