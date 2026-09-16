package li.cil.oc.common.event

import java.io.FileInputStream
import java.io.FileOutputStream
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.nanomachines.Controller
import li.cil.oc.client.Textures
import li.cil.oc.client.renderer.RenderTypes
import li.cil.oc.common.EventHandler
import li.cil.oc.common.nanomachines.ControllerImpl
import net.minecraft.client.Minecraft
import net.neoforged.neoforge.event.entity.living.LivingEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent
import net.minecraft.client.renderer.MultiBufferSource
import com.mojang.blaze3d.vertex.{ByteBufferBuilder, DefaultVertexFormat, PoseStack, Tesselator, VertexConsumer}
import net.minecraft.world.entity.player.Player
import net.minecraft.nbt.{CompoundTag, NbtAccounter, NbtIo}
import net.neoforged.api.distmarker.{Dist, OnlyIn}
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent
import net.neoforged.neoforge.client.gui.VanillaGuiLayers
import net.neoforged.neoforge.event.tick.EntityTickEvent

object NanomachinesHandler {
  @OnlyIn(Dist.CLIENT)
  object Client {
    val TexNanomachines = RenderTypes.createTexturedQuad("nanomachines", Textures.GUI.Nanomachines, DefaultVertexFormat.POSITION_TEX, false)
    val TexNanomachinesBar = RenderTypes.createTexturedQuad("nanomachines_bar", Textures.GUI.NanomachinesBar, DefaultVertexFormat.POSITION_TEX, false)

    @SubscribeEvent
    def onRenderGameOverlay(e: RenderGuiLayerEvent.Post): Unit = {
      if (e.getName == VanillaGuiLayers.DEBUG_OVERLAY) {
        val mc = Minecraft.getInstance
        api.Nanomachines.getController(mc.player) match {
          case controller: Controller =>
            val graphics = e.getGuiGraphics
            val window = mc.getWindow
            val sizeX = 8
            val sizeY = 12
            val width = window.getGuiScaledWidth
            val height = window.getGuiScaledHeight
            val (x, y) = Settings.get.nanomachineHudPos
            val left: Int =
              math.min(width - sizeX,
                if (x < 0) width / 2 - 91 - 12
                else if (x < 1) (width * x).toInt
                else x.toInt)
            val top: Int =
              math.min(height - sizeY,
                if (y < 0) height - 39
                else if (y < 1) (y * height).toInt
                else y.toInt)
            val fill = controller.getLocalBuffer / controller.getLocalBufferSize
            val byteBuffer = new ByteBufferBuilder(786432)
            val buffer = MultiBufferSource.immediate(byteBuffer)
            drawRect(graphics.pose, buffer.getBuffer(TexNanomachines), left, top, sizeX, sizeY, sizeX, sizeY)
            drawRect(graphics.pose, buffer.getBuffer(TexNanomachinesBar), left, top, sizeX, sizeY, sizeX, sizeY, fill.toFloat)
            buffer.endBatch()
            byteBuffer.close()
          case _ => // Nothing to show.
        }
      }
    }

    private def drawRect(stack: PoseStack, r: VertexConsumer, x: Int, y: Int, w: Int, h: Int, tw: Int, th: Int, fill: Float = 1): Unit = {
      val sx = 1f / tw
      val sy = 1f / th
      r.addVertex(stack.last.pose, x.toFloat, (y + h).toFloat, 0f).setUv(0f, h * sy)
      r.addVertex(stack.last.pose, (x + w).toFloat, (y + h).toFloat, 0f).setUv(w * sx, h * sy)
      r.addVertex(stack.last.pose, (x + w).toFloat, y.toFloat + h * (1 - fill), 0f).setUv(w * sx, 1 - fill)
      r.addVertex(stack.last.pose, x.toFloat, y.toFloat + h * (1 - fill), 0f).setUv(0f, 1 - fill)
    }
  }

  object Common {
    @SubscribeEvent
    def onPlayerRespawn(e: PlayerRespawnEvent): Unit = {
      api.Nanomachines.getController(e.getEntity) match {
        case controller: Controller => controller.changeBuffer(-controller.getLocalBuffer)
        case _ => // Not a player with nanomachines.
      }
    }

    @SubscribeEvent
    def onLivingUpdate(e: EntityTickEvent.Post): Unit = {
      e.getEntity match {
        case player: Player => api.Nanomachines.getController(player) match {
          case controller: ControllerImpl =>
            if (controller.player eq player) {
              controller.update()
            }
            else {
              // Player entity instance changed (e.g. respawn), recreate the controller.
              val nbt = new CompoundTag()
              controller.saveData(nbt)
              api.Nanomachines.uninstallController(controller.player)
              api.Nanomachines.installController(player) match {
                case newController: ControllerImpl =>
                  newController.loadData(nbt)
                  newController.reset()
                case _ => // Eh?
              }
            }
          case _ => // Not a player with nanomachines.
        }
        case _ => // Not a player.
      }
    }

    @SubscribeEvent
    def onPlayerSave(e: PlayerEvent.SaveToFile): Unit = {
      val file = e.getPlayerFile("ocnm")
      api.Nanomachines.getController(e.getEntity) match {
        case controller: ControllerImpl =>
          try {
            val nbt = new CompoundTag()
            controller.saveData(nbt)
            val fos = new FileOutputStream(file)
            try NbtIo.writeCompressed(nbt, fos) catch {
              case t: Throwable =>
                OpenComputers.log.warn("Error saving nanomachine state.", t)
            }
            fos.close()
          }
          catch {
            case t: Throwable =>
              OpenComputers.log.warn("Error saving nanomachine state.", t)
          }
        case _ => // Not a player with nanomachines.
      }
    }

    @SubscribeEvent
    def onPlayerLoad(e: PlayerEvent.LoadFromFile): Unit = {
      val file = e.getPlayerFile("ocnm")
      if (file.exists()) {
        api.Nanomachines.getController(e.getEntity) match {
          case controller: ControllerImpl =>
            try {
              val fis = new FileInputStream(file)
              try controller.loadData(NbtIo.readCompressed(fis, NbtAccounter.unlimitedHeap())) catch {
                case t: Throwable =>
                  OpenComputers.log.warn("Error loading nanomachine state.", t)
              }
              fis.close()
            }
            catch {
              case t: Throwable =>
                OpenComputers.log.warn("Error loading nanomachine state.", t)
            }
          case _ => // Not a player with nanomachines.
        }
      }
    }

    @SubscribeEvent
    def onPlayerDisconnect(e: PlayerLoggedOutEvent): Unit = {
      api.Nanomachines.getController(e.getEntity) match {
        case controller: ControllerImpl =>
          // Wait a tick because saving is done after this event.
          EventHandler.scheduleServer(() => api.Nanomachines.uninstallController(e.getEntity))
        case _ => // Not a player with nanomachines.
      }
    }
  }

}
