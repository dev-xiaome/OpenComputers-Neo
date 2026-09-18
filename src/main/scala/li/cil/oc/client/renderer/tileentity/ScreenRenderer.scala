package li.cil.oc.client.renderer.tileentity

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.VertexConsumer
import li.cil.oc.Settings
import li.cil.oc.client.Textures
import li.cil.oc.client.renderer.RenderTypes
import li.cil.oc.common.component.{TextBuffer => ComponentTextBuffer}
import li.cil.oc.common.blockentity.Screen
import li.cil.oc.integration.util.Wrench
import li.cil.oc.util.RenderState
import li.cil.oc.util.SableCompat
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.InteractionHand
import net.minecraft.core.Direction
import com.mojang.math.Axis
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.blockentity.{BlockEntityRenderer => TileEntityRenderer}
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.world.level.block.Block
import net.neoforged.api.distmarker.{Dist, OnlyIn}
import net.minecraft.world.phys.{AABB, Vec3}

object ScreenRenderer extends BlockEntityRendererProvider[Screen] {
  override def create(ctx: BlockEntityRendererProvider.Context): ScreenRenderer =
    new ScreenRenderer()
}

class ScreenRenderer extends TileEntityRenderer[Screen] {
  private val maxRenderDistanceSq = Settings.get.maxScreenTextRenderDistance * Settings.get.maxScreenTextRenderDistance
  private val fadeDistanceSq      = Settings.get.screenTextFadeStartDistance * Settings.get.screenTextFadeStartDistance
  private val fadeRatio           = 1.0 / (maxRenderDistanceSq - fadeDistanceSq)

  private var screen: Screen = null

  override def getRenderBoundingBox(screen: Screen): AABB = screen.getRenderBoundingBox

  override def render(
                       screen: Screen,
                       dt: Float,
                       stack: PoseStack,
                       buffer: MultiBufferSource,
                       light: Int,
                       overlay: Int
                     ): Unit = {
    RenderState.checkError(getClass.getName + ".render: entering (aka: wasn'tme)")

    this.screen = screen
    if (!screen.isOrigin) return

    val distance = playerDistanceSq() / math.min(screen.width, screen.height)
    if (distance > maxRenderDistanceSq) return

    val eye_pos   = Minecraft.getInstance.player.getEyePosition(dt)
    val screenPosition = SableCompat.physicalPosition(screen.getLevel, screen.getBlockPos)
    val screenFacing = SableCompat.physicalFacing(screen.getLevel, Vec3.atCenterOf(screen.getBlockPos), screen.facing).getOpposite
    val x = screenPosition.x - eye_pos.x
    val y = screenPosition.y - eye_pos.y
    val z = screenPosition.z - eye_pos.z
    if (screenFacing.getStepX * x + screenFacing.getStepY * y + screenFacing.getStepZ * z < 0) return

    RenderSystem.setShaderColor(1, 1, 1, 1)

    stack.pushPose()
    stack.translate(0.5, 0.5, 0.5)

    RenderState.checkError(getClass.getName + ".render: setup")

    drawOverlay(stack, buffer.getBuffer(RenderTypes.BLOCK_OVERLAY))

    RenderState.checkError(getClass.getName + ".render: overlay")

    val alpha = if (distance > fadeDistanceSq)
      math.max(0, 1 - ((distance - fadeDistanceSq) * fadeRatio).toFloat)
    else 1f

    RenderState.checkError(getClass.getName + ".render: fade")

    if (screen.buffer.isRenderingEnabled) {
      val profiler = Minecraft.getInstance.getProfiler
      profiler.push("opencomputers_neo:screen_text")
      draw(stack, alpha, buffer)
      profiler.pop()
    }

    stack.popPose()

    RenderState.checkError(getClass.getName + ".render: leaving")
  }

  private def transform(stack: PoseStack): Unit = {
    screen.yaw match {
      case Direction.WEST  => stack.mulPose(Axis.YP.rotationDegrees(-90))
      case Direction.NORTH => stack.mulPose(Axis.YP.rotationDegrees(180))
      case Direction.EAST  => stack.mulPose(Axis.YP.rotationDegrees(90))
      case _               => // No yaw.
    }
    screen.pitch match {
      case Direction.DOWN => stack.mulPose(Axis.XP.rotationDegrees(90))
      case Direction.UP   => stack.mulPose(Axis.XP.rotationDegrees(-90))
      case _              => // No pitch.
    }

    stack.translate(-0.5f, -0.5f, 0.5f)
    stack.translate(0, screen.height.toFloat, 0)
    RenderState.mirrorScale(stack, 1, -1, 1)
  }

  private def isScreen(stack: ItemStack): Boolean = Block.byItem(stack.getItem) match {
    case _: li.cil.oc.common.block.Screen => true
    case _                                => false
  }

  // 1.18.2: IVertexBuilder → VertexConsumer
  private def drawOverlay(matrix: PoseStack, r: VertexConsumer): Unit =
    if (screen.facing == Direction.UP || screen.facing == Direction.DOWN) {
      // 1.18.2: Hand.MAIN_HAND → InteractionHand.MAIN_HAND
      val stack = Minecraft.getInstance.player.getItemInHand(InteractionHand.MAIN_HAND)
      if (!stack.isEmpty) {
        if (Wrench.holdsApplicableWrench(Minecraft.getInstance.player, screen.getBlockPos) || isScreen(stack)) {
          matrix.pushPose()
          transform(matrix)
          matrix.translate(screen.width / 2f - 0.5f, screen.height / 2f - 0.5f, 0.05f)

          val icon = Textures.getSprite(Textures.Block.ScreenUpIndicator)
          r.addVertex(matrix.last.pose, 0, 1, 0).setUv(icon.getU0, icon.getV1)
          r.addVertex(matrix.last.pose, 1, 1, 0).setUv(icon.getU1, icon.getV1)
          r.addVertex(matrix.last.pose, 1, 0, 0).setUv(icon.getU1, icon.getV0)
          r.addVertex(matrix.last.pose, 0, 0, 0).setUv(icon.getU0, icon.getV0)

          matrix.popPose()
        }
      }
    }

  private def draw(stack: PoseStack, alpha: Float, buffer: MultiBufferSource): Unit = {
    RenderState.checkError(getClass.getName + ".draw: entering (aka: wasntme)")

    val sx = screen.width
    val sy = screen.height
    val tw = sx * 16f
    val th = sy * 16f

    transform(stack)

    val border = 2.25f
    stack.translate(sx * border / tw, sy * border / th, 0)

    val isx = sx - (border / 8)
    val isy = sy - (border / 8)

    val sizeX  = screen.buffer.renderWidth
    val sizeY  = screen.buffer.renderHeight
    val scaleX = isx / sizeX
    val scaleY = isy / sizeY

    if (true) {
      if (scaleX > scaleY) {
        stack.translate(sizeX * 0.5f * (scaleX - scaleY), 0, 0)
        stack.scale(scaleY, scaleY, 1)
      } else {
        stack.translate(0, sizeY * 0.5f * (scaleY - scaleX), 0)
        stack.scale(scaleX, scaleX, 1)
      }
    } else {
      stack.scale(scaleX, scaleY, 1)
    }

    stack.translate(0, 0, 0.01f)

    RenderState.checkError(getClass.getName + ".draw: setup")

    screen.buffer match {
      case textBuffer: ComponentTextBuffer => textBuffer.renderText(stack, buffer)
      case _ => screen.buffer.renderText(stack)
    }

    RenderState.checkError(getClass.getName + ".draw: text")
  }

  @OnlyIn(Dist.CLIENT)
  override def shouldRenderOffScreen(screen: Screen): Boolean = screen.isOrigin && (screen.width > 1 || screen.height > 1)

  private def playerDistanceSq(): Double = {
    val player = Minecraft.getInstance.player
    val bounds = getRenderBoundingBox(screen)

    // Block entities inside a Sable sub-level retain their plot coordinates,
    // while the player is rendered in physical world space. Project the
    // screen bounds before applying OC's own text-distance culling.
    val corners = for {
      x <- Array(bounds.minX, bounds.maxX)
      y <- Array(bounds.minY, bounds.maxY)
      z <- Array(bounds.minZ, bounds.maxZ)
    } yield SableCompat.physicalPosition(screen.getLevel, new Vec3(x, y, z))
    val physicalBounds = (
      corners.map(_.x).min, corners.map(_.y).min, corners.map(_.z).min,
      corners.map(_.x).max, corners.map(_.y).max, corners.map(_.z).max)

    val playerPosition = SableCompat.physicalPosition(screen.getLevel, new Vec3(player.getX, player.getY, player.getZ))
    val px = playerPosition.x
    val py = playerPosition.y
    val pz = playerPosition.z

    val ex = physicalBounds._4 - physicalBounds._1
    val ey = physicalBounds._5 - physicalBounds._2
    val ez = physicalBounds._6 - physicalBounds._3
    val cx = physicalBounds._1 + ex * 0.5
    val cy = physicalBounds._2 + ey * 0.5
    val cz = physicalBounds._3 + ez * 0.5
    val dx = px - cx
    val dy = py - cy
    val dz = pz - cz

    (if (dx < -ex) { val d = dx + ex; d * d }
    else if (dx > ex) { val d = dx - ex; d * d }
    else 0.0) +
      (if (dy < -ey) { val d = dy + ey; d * d }
      else if (dy > ey) { val d = dy - ey; d * d }
      else 0.0) +
      (if (dz < -ez) { val d = dz + ez; d * d }
      else if (dz > ez) { val d = dz - ez; d * d }
      else 0.0)
  }
}
