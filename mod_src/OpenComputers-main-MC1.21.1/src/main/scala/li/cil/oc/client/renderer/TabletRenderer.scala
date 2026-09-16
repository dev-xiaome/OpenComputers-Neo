package li.cil.oc.client.renderer

import com.mojang.blaze3d.vertex.{PoseStack, VertexConsumer}
import com.mojang.math.Axis
import li.cil.oc.{Constants, api}
import li.cil.oc.common.component.{TextBuffer => ComponentTextBuffer}
import li.cil.oc.common.item.Tablet
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.util.Mth
import net.minecraft.client.renderer.entity.player.PlayerRenderer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.HumanoidArm
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.RenderHandEvent

/** Renders a running tablet like a map, with its live screen visible in-hand. */
object TabletRenderer {
  private val FrameBorder = 12
  private val BottomBorder = 20
  private val OffhandFaceSize = 1.26f
  private val CenterFaceSize = 1f

  @SubscribeEvent
  def onRenderHand(event: RenderHandEvent): Unit = {
    if (api.Items.get(event.getItemStack) != api.Items.get(Constants.ItemName.Tablet)) return

    val player = Minecraft.getInstance.player
    val wrapper = Tablet.Client.get(event.getItemStack).orElse {
      Option.when(player != null && Tablet.getId(event.getItemStack).nonEmpty) {
        Tablet.Client.get(event.getItemStack, player)
      }
    }
    val buffer = wrapper.flatMap(_.componentSlots.collectFirst {
      case Some(textBuffer: api.internal.TextBuffer) => textBuffer
    })
    val powered = wrapper.exists(_.data.isRunning)

    // Never expose the baked icon's fake screen in-hand. Until the client
    // receives the authoritative buffer snapshot, render an empty tablet face;
    // the same instance will begin drawing live text as soon as it is synced.
    renderFirstPerson(
      event.getPoseStack,
      event.getMultiBufferSource,
      event.getPackedLight,
      event.getHand,
      event.getInterpolatedPitch,
      event.getEquipProgress,
      event.getSwingProgress,
      buffer,
      powered)
    event.setCanceled(true)
  }

  private def renderFirstPerson(stack: PoseStack,
                                renderBuffer: MultiBufferSource,
                                packedLight: Int,
                                hand: InteractionHand,
                                pitch: Float,
                                equipProgress: Float,
                                swingProgress: Float,
                                textBuffer: Option[api.internal.TextBuffer],
                                powered: Boolean): Unit = {
    val player = Minecraft.getInstance.player
    if (player == null) return

    stack.pushPose()
    if (hand == InteractionHand.MAIN_HAND && player.getOffhandItem.isEmpty) {
      renderCentered(stack, renderBuffer, packedLight, pitch, equipProgress, swingProgress, textBuffer, powered)
    }
    else {
      val arm = if (hand == InteractionHand.MAIN_HAND) player.getMainArm else player.getMainArm.getOpposite
      renderAtSide(stack, renderBuffer, packedLight, arm, equipProgress, swingProgress, textBuffer, powered, OffhandFaceSize)
    }
    stack.popPose()
  }

  private def renderAtSide(stack: PoseStack,
                           renderBuffer: MultiBufferSource,
                           packedLight: Int,
                           arm: HumanoidArm,
                           equipProgress: Float,
                           swingProgress: Float,
                           textBuffer: Option[api.internal.TextBuffer],
                           powered: Boolean,
                           faceSize: Float): Unit = {
    val minecraft = Minecraft.getInstance
    val side = if (arm == HumanoidArm.RIGHT) 1f else -1f

    stack.translate(side * 0.125f, -0.125f, 0f)
    if (!minecraft.player.isInvisible) {
      stack.pushPose()
      stack.mulPose(Axis.ZP.rotationDegrees(side * 10f))
      renderPlayerArm(stack, renderBuffer, packedLight, equipProgress, swingProgress, arm)
      stack.popPose()
    }

    stack.pushPose()
    stack.translate(side * 0.51f, -0.08f + equipProgress * -1.2f, -0.75f)
    val swingRoot = Mth.sqrt(swingProgress)
    val swing = Mth.sin(swingRoot * Math.PI.toFloat)
    stack.translate(
      side * (-0.5f * swing),
      0.4f * Mth.sin(swingRoot * Math.PI.toFloat * 2f) - 0.3f * swing,
      -0.3f * Mth.sin(swingProgress * Math.PI.toFloat))
    stack.mulPose(Axis.XP.rotationDegrees(swing * -45f))
    stack.mulPose(Axis.YP.rotationDegrees(side * swing * -30f))
    renderTablet(stack, renderBuffer, textBuffer, powered, faceSize)
    stack.popPose()
  }

  private def renderCentered(stack: PoseStack,
                             renderBuffer: MultiBufferSource,
                             packedLight: Int,
                             pitch: Float,
                             equipProgress: Float,
                             swingProgress: Float,
                             textBuffer: Option[api.internal.TextBuffer],
                             powered: Boolean): Unit = {
    val minecraft = Minecraft.getInstance
    val swingRoot = Mth.sqrt(swingProgress)

    stack.translate(
      0f,
      -(-0.2f * Mth.sin(swingProgress * Math.PI.toFloat)) / 2f,
      -0.4f * Mth.sin(swingRoot * Math.PI.toFloat))
    val tilt = calculateMapTilt(pitch)
    stack.translate(0f, 0.04f + equipProgress * -1.2f + tilt * -0.5f, -0.72f)
    stack.mulPose(Axis.XP.rotationDegrees(tilt * -85f))

    if (!minecraft.player.isInvisible) {
      stack.pushPose()
      stack.mulPose(Axis.YP.rotationDegrees(90f))
      renderMapHand(stack, renderBuffer, packedLight, HumanoidArm.RIGHT)
      renderMapHand(stack, renderBuffer, packedLight, HumanoidArm.LEFT)
      stack.popPose()
    }

    stack.mulPose(Axis.XP.rotationDegrees(Mth.sin(swingRoot * Math.PI.toFloat) * 20f))
    stack.scale(2f, 2f, 2f)
    renderTablet(stack, renderBuffer, textBuffer, powered, CenterFaceSize)
  }

  private def calculateMapTilt(pitch: Float): Float = {
    val tilt = Mth.clamp(1f - pitch / 45f + 0.1f, 0f, 1f)
    -Mth.cos(tilt * Math.PI.toFloat) * 0.5f + 0.5f
  }

  private def renderMapHand(stack: PoseStack,
                            renderBuffer: MultiBufferSource,
                            packedLight: Int,
                            arm: HumanoidArm): Unit = {
    val minecraft = Minecraft.getInstance
    val playerRenderer = minecraft.getEntityRenderDispatcher.getRenderer(minecraft.player).asInstanceOf[PlayerRenderer]
    val side = if (arm == HumanoidArm.RIGHT) 1f else -1f

    stack.pushPose()
    stack.mulPose(Axis.YP.rotationDegrees(92f))
    stack.mulPose(Axis.XP.rotationDegrees(45f))
    stack.mulPose(Axis.ZP.rotationDegrees(side * -41f))
    stack.translate(side * 0.3f, -1.1f, 0.45f)
    if (arm == HumanoidArm.RIGHT) playerRenderer.renderRightHand(stack, renderBuffer, packedLight, minecraft.player)
    else playerRenderer.renderLeftHand(stack, renderBuffer, packedLight, minecraft.player)
    stack.popPose()
  }

  private def renderPlayerArm(stack: PoseStack,
                              renderBuffer: MultiBufferSource,
                              packedLight: Int,
                              equipProgress: Float,
                              swingProgress: Float,
                              arm: HumanoidArm): Unit = {
    val minecraft = Minecraft.getInstance
    val right = arm != HumanoidArm.LEFT
    val side = if (right) 1f else -1f
    val swingRoot = Mth.sqrt(swingProgress)
    val swingX = -0.3f * Mth.sin(swingRoot * Math.PI.toFloat)
    val swingY = 0.4f * Mth.sin(swingRoot * Math.PI.toFloat * 2f)
    val swingZ = -0.4f * Mth.sin(swingProgress * Math.PI.toFloat)

    stack.translate(side * (swingX + 0.64000005f), swingY - 0.6f + equipProgress * -0.6f, swingZ - 0.71999997f)
    stack.mulPose(Axis.YP.rotationDegrees(side * 45f))
    stack.mulPose(Axis.YP.rotationDegrees(side * Mth.sin(swingRoot * Math.PI.toFloat) * 70f))
    stack.mulPose(Axis.ZP.rotationDegrees(side * Mth.sin(swingProgress * swingProgress * Math.PI.toFloat) * -20f))
    stack.translate(side * -1f, 3.6f, 3.5f)
    stack.mulPose(Axis.ZP.rotationDegrees(side * 120f))
    stack.mulPose(Axis.XP.rotationDegrees(200f))
    stack.mulPose(Axis.YP.rotationDegrees(side * -135f))
    stack.translate(side * 5.6f, 0f, 0f)

    val playerRenderer = minecraft.getEntityRenderDispatcher.getRenderer(minecraft.player).asInstanceOf[PlayerRenderer]
    if (right) playerRenderer.renderRightHand(stack, renderBuffer, packedLight, minecraft.player)
    else playerRenderer.renderLeftHand(stack, renderBuffer, packedLight, minecraft.player)
  }

  private def renderTablet(stack: PoseStack,
                          renderBuffer: MultiBufferSource,
                          textBuffer: Option[api.internal.TextBuffer],
                          powered: Boolean,
                          faceSize: Float): Unit = {
    val width = textBuffer.fold(160)(_.renderWidth)
    val height = textBuffer.fold(90)(_.renderHeight)
    if (width <= 0 || height <= 0) return

    stack.pushPose()
    stack.mulPose(Axis.YP.rotationDegrees(180f))
    stack.mulPose(Axis.ZP.rotationDegrees(180f))
    stack.scale(0.5f, 0.5f, 0.5f)

    val scale = faceSize / Math.max(width + FrameBorder * 2, height + FrameBorder + BottomBorder)
    stack.scale(scale, scale, -1f)
    stack.translate(-width * 0.5, -height * 0.5, 0)

    renderFrame(stack, renderBuffer, width, height, powered)

    for (buffer <- textBuffer if buffer.isRenderingEnabled) {
      stack.translate(0, 0, 0.002f)
      buffer match {
        case component: ComponentTextBuffer => component.renderText(stack, renderBuffer)
        case _ => buffer.renderText(stack)
      }
    }

    stack.popPose()
  }

  private def renderFrame(stack: PoseStack, renderBuffer: MultiBufferSource, width: Int, height: Int, powered: Boolean): Unit = {
    val builder = renderBuffer.getBuffer(RenderTypes.FONT_QUAD)
    val matrix = stack.last.pose()

    val frameWidth = width + FrameBorder * 2
    val frameHeight = height + FrameBorder + BottomBorder
    quad(builder, matrix, -FrameBorder, -FrameBorder, frameWidth, frameHeight, 0f, 0x25262D)
    quad(builder, matrix, -FrameBorder + 2, -FrameBorder + 2, frameWidth - 4, 2, 0.0005f, 0x4A4C57)
    quad(builder, matrix, -FrameBorder + 2, -FrameBorder + 4, 2, frameHeight - 8, 0.0005f, 0x3A3C46)
    quad(builder, matrix, -FrameBorder + 2, height + BottomBorder - 4, frameWidth - 4, 2, 0.0005f, 0x17181D)
    quad(builder, matrix, width + FrameBorder - 4, -FrameBorder + 4, 2, frameHeight - 8, 0.0005f, 0x17181D)
    quad(builder, matrix, 0, 0, width, height, 0.001f, 0x000000)
    quad(builder, matrix, width - 8, height + 5, 5, 5, 0.001f, if (powered) 0x66DD55 else 0x333333)
  }

  private def quad(builder: VertexConsumer,
                   matrix: org.joml.Matrix4f,
                   x: Int,
                   y: Int,
                   width: Int,
                   height: Int,
                   z: Float,
                   color: Int): Unit = {
    val r = (color >> 16) & 0xFF
    val g = (color >> 8) & 0xFF
    val b = color & 0xFF
    builder.addVertex(matrix, x.toFloat, (y + height).toFloat, z).setUv(0f, 1f).setColor(r, g, b, 255)
    builder.addVertex(matrix, (x + width).toFloat, (y + height).toFloat, z).setUv(1f, 1f).setColor(r, g, b, 255)
    builder.addVertex(matrix, (x + width).toFloat, y.toFloat, z).setUv(1f, 0f).setColor(r, g, b, 255)
    builder.addVertex(matrix, x.toFloat, y.toFloat, z).setUv(0f, 0f).setColor(r, g, b, 255)
  }
}
