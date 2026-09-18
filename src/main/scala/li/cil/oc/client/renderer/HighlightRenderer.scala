package li.cil.oc.client.renderer

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import li.cil.oc.client.Textures
import li.cil.oc.util.ExtendedLevel._
import li.cil.oc.util.BlockPosition
import li.cil.oc.{Constants, Settings, api}
import net.minecraft.client.Minecraft
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.shapes.CollisionContext
import net.neoforged.neoforge.client.event.RenderHighlightEvent
import net.neoforged.bus.api.SubscribeEvent

import scala.util.Random

object HighlightRenderer {
  private val random = new Random()

  lazy val tablet = api.Items.get(Constants.ItemName.Tablet)

  val TexHologram = RenderTypes.createTexturedQuad("hologram_effect", Textures.Model.HologramEffect, DefaultVertexFormat.POSITION_TEX_COLOR, true)

  @SubscribeEvent
  def onDrawBlockHighlight(e: RenderHighlightEvent.Block): Unit = if (e.getTarget != null && e.getTarget.getBlockPos != null) {
    val hitInfo = e.getTarget
    val world = Minecraft.getInstance.level
    val blockPos = BlockPosition(hitInfo.getBlockPos, world)
    val stack = e.getPoseStack
    if (api.Items.get(Minecraft.getInstance.player.getItemInHand(InteractionHand.MAIN_HAND)) == tablet) {
      val isAir = world.isAirBlock(blockPos)
      if (!isAir) {
        val shape = world.getBlockState(hitInfo.getBlockPos).getShape(world, hitInfo.getBlockPos, CollisionContext.of(e.getCamera.getEntity))
        val (minX, minY, minZ) = (shape.min(Direction.Axis.X).toFloat, shape.min(Direction.Axis.Y).toFloat, shape.min(Direction.Axis.Z).toFloat)
        val (maxX, maxY, maxZ) = (shape.max(Direction.Axis.X).toFloat, shape.max(Direction.Axis.Y).toFloat, shape.max(Direction.Axis.Z).toFloat)
        val sideHit = hitInfo.getDirection
        val view = e.getCamera.getPosition

        stack.pushPose()

        stack.translate(blockPos.x - view.x, blockPos.y - view.y, blockPos.z - view.z)
        stack.scale(1.002f, 1.002f, 1.002f)

        if (Settings.get.hologramFlickerFrequency > 0 && random.nextDouble() < Settings.get.hologramFlickerFrequency) {
          val (sx, sy, sz) = (1 - math.abs(sideHit.getStepX), 1 - math.abs(sideHit.getStepY), 1 - math.abs(sideHit.getStepZ))
          stack.scale(Math.max(1f + (random.nextGaussian() * 0.01).toFloat, 0.001f),
            Math.max(1f + (random.nextGaussian() * 0.001).toFloat, 0.001f),
            Math.max(1f + (random.nextGaussian() * 0.01).toFloat, 0.001f))
          stack.translate((random.nextGaussian() * 0.01 * sx).toFloat, (random.nextGaussian() * 0.01 * sy).toFloat, (random.nextGaussian() * 0.01 * sz).toFloat)
        }

        val r = e.getMultiBufferSource.getBuffer(TexHologram)
        sideHit match {
          case Direction.UP =>
            r.addVertex(stack.last.pose, maxX, maxY + 0.002f, maxZ).setUv(maxZ * 16, maxX * 16).setColor(0.0F, 1.0F, 0.0F, 0.4F)
            r.addVertex(stack.last.pose, maxX, maxY + 0.002f, minZ).setUv(minZ * 16, maxX * 16).setColor(0.0F, 1.0F, 0.0F, 0.4F)
            r.addVertex(stack.last.pose, minX, maxY + 0.002f, minZ).setUv(minZ * 16, minX * 16).setColor(0.0F, 1.0F, 0.0F, 0.4F)
            r.addVertex(stack.last.pose, minX, maxY + 0.002f, maxZ).setUv(maxZ * 16, minX * 16).setColor(0.0F, 1.0F, 0.0F, 0.4F)
          case Direction.DOWN =>
            r.addVertex(stack.last.pose, maxX, minY - 0.002f, minZ).setUv(minZ * 16, maxX * 16).setColor(0.0F, 1.0F, 0.0F, 0.4F)
            r.addVertex(stack.last.pose, maxX, minY - 0.002f, maxZ).setUv(maxZ * 16, maxX * 16).setColor(0.0F, 1.0F, 0.0F, 0.4F)
            r.addVertex(stack.last.pose, minX, minY - 0.002f, maxZ).setUv(maxZ * 16, minX * 16).setColor(0.0F, 1.0F, 0.0F, 0.4F)
            r.addVertex(stack.last.pose, minX, minY - 0.002f, minZ).setUv(minZ * 16, minX * 16).setColor(0.0F, 1.0F, 0.0F, 0.4F)
          case Direction.EAST =>
            r.addVertex(stack.last.pose, maxX + 0.002f, maxY, minZ).setUv(minZ * 16, maxY * 16).setColor(0.0F, 1.0F, 0.0F, 0.4F)
            r.addVertex(stack.last.pose, maxX + 0.002f, maxY, maxZ).setUv(maxZ * 16, maxY * 16).setColor(0.0F, 1.0F, 0.0F, 0.4F)
            r.addVertex(stack.last.pose, maxX + 0.002f, minY, maxZ).setUv(maxZ * 16, minY * 16).setColor(0.0F, 1.0F, 0.0F, 0.4F)
            r.addVertex(stack.last.pose, maxX + 0.002f, minY, minZ).setUv(minZ * 16, minY * 16).setColor(0.0F, 1.0F, 0.0F, 0.4F)
          case Direction.WEST =>
            r.addVertex(stack.last.pose, minX - 0.002f, maxY, maxZ).setUv(maxZ * 16, maxY * 16).setColor(0.0F, 1.0F, 0.0F, 0.4F)
            r.addVertex(stack.last.pose, minX - 0.002f, maxY, minZ).setUv(minZ * 16, maxY * 16).setColor(0.0F, 1.0F, 0.0F, 0.4F)
            r.addVertex(stack.last.pose, minX - 0.002f, minY, minZ).setUv(minZ * 16, minY * 16).setColor(0.0F, 1.0F, 0.0F, 0.4F)
            r.addVertex(stack.last.pose, minX - 0.002f, minY, maxZ).setUv(maxZ * 16, minY * 16).setColor(0.0F, 1.0F, 0.0F, 0.4F)
          case Direction.SOUTH =>
            r.addVertex(stack.last.pose, maxX, maxY, maxZ + 0.002f).setUv(maxX * 16, maxY * 16).setColor(0.0F, 1.0F, 0.0F, 0.4F)
            r.addVertex(stack.last.pose, minX, maxY, maxZ + 0.002f).setUv(minX * 16, maxY * 16).setColor(0.0F, 1.0F, 0.0F, 0.4F)
            r.addVertex(stack.last.pose, minX, minY, maxZ + 0.002f).setUv(minX * 16, minY * 16).setColor(0.0F, 1.0F, 0.0F, 0.4F)
            r.addVertex(stack.last.pose, maxX, minY, maxZ + 0.002f).setUv(maxX * 16, minY * 16).setColor(0.0F, 1.0F, 0.0F, 0.4F)
          case _ =>
            r.addVertex(stack.last.pose, minX, maxY, minZ - 0.002f).setUv(minX * 16, maxY * 16).setColor(0.0F, 1.0F, 0.0F, 0.4F)
            r.addVertex(stack.last.pose, maxX, maxY, minZ - 0.002f).setUv(maxX * 16, maxY * 16).setColor(0.0F, 1.0F, 0.0F, 0.4F)
            r.addVertex(stack.last.pose, maxX, minY, minZ - 0.002f).setUv(maxX * 16, minY * 16).setColor(0.0F, 1.0F, 0.0F, 0.4F)
            r.addVertex(stack.last.pose, minX, minY, minZ - 0.002f).setUv(minX * 16, minY * 16).setColor(0.0F, 1.0F, 0.0F, 0.4F)
        }

        stack.popPose()
      }
    }
  }
}
