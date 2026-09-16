package li.cil.oc.common.event

import com.mojang.math.Axis
import li.cil.oc.{Constants, api}
import li.cil.oc.api.event.RackMountableRenderEvent
import li.cil.oc.client.Textures
import li.cil.oc.client.renderer.RenderTypes
import li.cil.oc.client.renderer.tileentity.RenderUtil
import li.cil.oc.common.datacomponents.OCComponents
import li.cil.oc.util.ExtendedDataComponentHolder._
import li.cil.oc.util.RenderState
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemDisplayContext
import net.neoforged.bus.api.SubscribeEvent

object RackMountableRenderHandler {
  lazy val DiskDriveMountable = api.Items.get(Constants.ItemName.DiskDriveMountable)

  lazy val Servers = Array(
    api.Items.get(Constants.ItemName.ServerTier1),
    api.Items.get(Constants.ItemName.ServerTier2),
    api.Items.get(Constants.ItemName.ServerTier3),
    api.Items.get(Constants.ItemName.ServerTier4),
    api.Items.get(Constants.ItemName.ServerCreative)
  )

  lazy val TerminalServer = api.Items.get(Constants.ItemName.TerminalServer)
  lazy val RackKVM = api.Items.get(Constants.ItemName.RackKVM)
  lazy val CapacitorMountable = api.Items.get(Constants.ItemName.CapacitorMountable)

  @SubscribeEvent
  def onRackMountableRendering(e: RackMountableRenderEvent.BlockEntity): Unit = {
    if (e.data != null && DiskDriveMountable == api.Items.get(e.rack.getItem(e.mountable))) {
      // Disk drive.

      for (stack <- e.data.getComponent(OCComponents.Network.DISK_ITEM)) {
        if (!stack.isEmpty) {
          val matrix = e.stack
          matrix.pushPose()
          RenderState.mirrorScale(matrix, 1, -1, 1)
          matrix.translate(10 / 16f, -(3.5f + e.mountable * 3f) / 16f, -2 / 16f)
          matrix.mulPose(Axis.XN.rotationDegrees(90))
          matrix.scale(0.5f, 0.5f, 0.5f)

          Minecraft.getInstance.getItemRenderer.renderStatic(
            stack.mutableCopy(),
            ItemDisplayContext.FIXED,
            e.light,                            
            e.overlay,                          
            matrix,                             
            e.typeBuffer,
            null,
            0                                   
          )
          matrix.popPose()
        }
      }

      for(lastAccess <- e.data.getComponent(OCComponents.Network.LAST_ACCESS)) {
        if (System.currentTimeMillis() - lastAccess < 400 && e.rack.getEnvironmentLevel.random.nextDouble() > 0.1) {
          renderOverlayFromAtlas(e, Textures.Block.RackDiskDriveActivity)
        }
      }
    }
    else if (e.data != null && Servers.contains(api.Items.get(e.rack.getItem(e.mountable)))) {
      val isRunning = e.data.getComponent(OCComponents.IS_RUNNING) getOrElse false
      val hasErrored = e.data.has(OCComponents.IS_ERRORED)
      val lastFileSystemAccess = e.data.getComponent(OCComponents.Network.LAST_DISK_ACCESS) getOrElse 0L
      val lastNetworkAccess = e.data.getComponent(OCComponents.Network.LAST_NETWORK_ACCESS) getOrElse 0L

      // Server.
      if (isRunning) {
        renderOverlayFromAtlas(e, Textures.Block.RackServerOn)
      }
      if (hasErrored && RenderUtil.shouldShowErrorLight(e.rack.hashCode * (e.mountable + 1))) {
        renderOverlayFromAtlas(e, Textures.Block.RackServerError)
      }
      if (System.currentTimeMillis() - lastFileSystemAccess < 400 && e.rack.getEnvironmentLevel.random.nextDouble() > 0.1) {
        renderOverlayFromAtlas(e, Textures.Block.RackServerActivity)
      }
      if ((System.currentTimeMillis() - lastNetworkAccess < 300 && System.currentTimeMillis() % 200 > 100) && isRunning) {
        renderOverlayFromAtlas(e, Textures.Block.RackServerNetworkActivity)
      }
    }
    else if (e.data != null && TerminalServer == api.Items.get(e.rack.getItem(e.mountable))) {
      // Terminal server.
      renderOverlayFromAtlas(e, Textures.Block.RackTerminalServerOn)
      val countConnected = e.data.getComponent(OCComponents.KEYS).map(_.size) getOrElse 0

      if (countConnected > 0) {
        val u0 = 7 / 16f
        val u1 = u0 + (2 * countConnected - 1) / 16f
        renderOverlayFromAtlas(e, Textures.Block.RackTerminalServerPresence, u0, u1)
      }
    }
    else if (e.data != null && RackKVM == api.Items.get(e.rack.getItem(e.mountable))) {
      // Rack KVM. Light one of its three server indicators for the selected server.
      renderOverlayFromAtlas(e, Textures.Block.RackTerminalServerOn)
      val selectedSlot = e.data.getComponent(OCComponents.RACK_KVM_SELECTED_SLOT).getOrElse(-1)
      val logicalIndex = (0 until 4).filter(_ != e.mountable).indexOf(selectedSlot)
      if (logicalIndex >= 0) {
        val u0 = (7 + logicalIndex * 2) / 16f
        renderOverlayFromAtlas(e, Textures.Block.RackKVMPresence, u0, u0 + 1 / 16f)
      }
    }
    else if (e.data != null && CapacitorMountable == api.Items.get(e.rack.getItem(e.mountable))) {
      // Render overlay if active (it has power)
      if (e.data.getComponent(OCComponents.IS_POWERED) getOrElse false) {
        renderOverlayFromAtlas(e, Textures.Block.RackCapacitorOn)
      }
    }
  }

  private def renderOverlayFromAtlas(e: RackMountableRenderEvent.BlockEntity, texture: ResourceLocation, u0: Float = 0, u1: Float = 1): Unit = {
    val matrix = e.stack.last.pose
    val r = e.typeBuffer.getBuffer(RenderTypes.BLOCK_OVERLAY)
    val icon = Textures.getSprite(texture)
    r.addVertex(matrix, u0, e.v1, 0).setUv(icon.getU(u0), icon.getV(e.v1))
    r.addVertex(matrix, u1, e.v1, 0).setUv(icon.getU(u1), icon.getV(e.v1))
    r.addVertex(matrix, u1, e.v0, 0).setUv(icon.getU(u1), icon.getV(e.v0))
    r.addVertex(matrix, u0, e.v0, 0).setUv(icon.getU(u0), icon.getV(e.v0))
  }

  @SubscribeEvent
  def onRackMountableRendering(e: RackMountableRenderEvent.Block): Unit = {
    if (DiskDriveMountable == api.Items.get(e.rack.getItem(e.mountable))) {
      // Disk drive.
      e.setFrontTextureOverride(Textures.getSprite(Textures.Block.RackDiskDrive))
    } else if (Servers.contains(api.Items.get(e.rack.getItem(e.mountable)))) {
      // Server.
      e.setFrontTextureOverride(Textures.getSprite(Textures.Block.RackServer))
    } else if (TerminalServer == api.Items.get(e.rack.getItem(e.mountable))) {
      // Terminal server.
      e.setFrontTextureOverride(Textures.getSprite(Textures.Block.RackTerminalServer))
    } else if (RackKVM == api.Items.get(e.rack.getItem(e.mountable))) {
      // Rack KVM.
      e.setFrontTextureOverride(Textures.getSprite(Textures.Block.RackKVM))
    } else if (CapacitorMountable == api.Items.get(e.rack.getItem(e.mountable))) {
      e.setFrontTextureOverride(Textures.getSprite(Textures.Block.RackCapacitor))
    }
  }
}
