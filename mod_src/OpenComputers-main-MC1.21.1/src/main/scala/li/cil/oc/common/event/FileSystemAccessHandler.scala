package li.cil.oc.common.event

import li.cil.oc.Settings
import li.cil.oc.api.event.FileSystemAccessEvent
import li.cil.oc.api.internal.Rack
import li.cil.oc.common.blockentity.Case
import li.cil.oc.common.blockentity.DiskDrive
import li.cil.oc.common.blockentity.Raid
import li.cil.oc.server.component.DiskDriveMountable
import li.cil.oc.server.component.Server
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.SubscribeEvent
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource

object FileSystemAccessHandler {
  @SubscribeEvent
  def onFileSystemAccess(e: FileSystemAccessEvent.Server): Unit = {
    e.getBlockEntity match {
      case t: Rack =>
        for (slot <- 0 until t.getContainerSize) {
          t.getMountable(slot) match {
            case server: Server =>
              val containsNode = server.componentSlot(e.getNode.address) >= 0
              if (containsNode) {
                server.lastFileSystemAccess = System.currentTimeMillis()
                t.markChanged(slot)
              }
            case diskDrive: DiskDriveMountable =>
              val containsNode = diskDrive.filesystemNode.contains(e.getNode)
              if (containsNode) {
                diskDrive.lastAccess = System.currentTimeMillis()
                t.markChanged(slot)
              }
            case _ =>
          }
        }
      case _ =>
    }
  }

  @SubscribeEvent
  def onFileSystemAccess(e: FileSystemAccessEvent.Client): Unit = {
    val volume = Settings.get.soundVolume
    val soundName = e.getSound
    if (soundName != null && soundName.nonEmpty) {
      val sound = SoundEvent.createVariableRangeEvent(ResourceLocation.tryParse(soundName))
      e.getWorld.playLocalSound(e.getX, e.getY, e.getZ, sound, SoundSource.BLOCKS, volume, 1, false)
    }
    e.getBlockEntity match {
      case t: DiskDrive => t.lastAccess = System.currentTimeMillis()
      case t: Case => t.lastFileSystemAccess = System.currentTimeMillis()
      case t: Raid => t.lastAccess = System.currentTimeMillis()
      case _ =>
    }
  }
}
