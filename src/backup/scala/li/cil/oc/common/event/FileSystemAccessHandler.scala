package li.cil.oc.common.event

import li.cil.oc.Settings
import li.cil.oc.api.event.FileSystemAccessEvent
import li.cil.oc.api.internal.Rack
import li.cil.oc.common.tileentity.Case
import li.cil.oc.common.tileentity.DiskDrive
import li.cil.oc.common.tileentity.Raid
import li.cil.oc.server.component.DiskDriveMountable
import li.cil.oc.server.component.Server
import net.minecraft.sounds.SoundSource
import net.neoforged.neoforge.common.NeoForge

/**
 * 文件系统访问：在服务端刷新「正在访问」的时间戳（用于机架服务器 / 磁盘驱动器的指示灯），
 * 在客户端播放访问音效。
 *
 * 1.21.1 迁移要点：
 *  - `@SubscribeEvent` → 显式 `addListener`（见 [[initialize]]）。
 *  - `Rack#getSizeInventory` → `IItemHandler#getSlots`。
 *  - 音效：1.7.10 的 `World#playSound(x, y, z, name, volume, pitch, delay)` 接受字符串名，
 *    1.21.1 需要注册过的 `SoundEvent`；这里用 [[li.cil.oc.common.SoundEvents.byName]]
 *    把事件里的名字映射为事件对象，并在客户端用 `playLocalSound` 播放。
 */
object FileSystemAccessHandler {
  /** 注册监听器；由主类（或 [[EventHandlers]]）调用一次。 */
  def initialize(): Unit = {
    NeoForge.EVENT_BUS.addListener((e: FileSystemAccessEvent.Server) => onFileSystemAccess(e))
    NeoForge.EVENT_BUS.addListener((e: FileSystemAccessEvent.Client) => onFileSystemAccess(e))
  }

  def onFileSystemAccess(e: FileSystemAccessEvent.Server): Unit = {
    e.getTileEntity match {
      case t: Rack =>
        for (slot <- 0 until t.getSlots) {
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

  def onFileSystemAccess(e: FileSystemAccessEvent.Client): Unit = {
    val volume = Settings.get.soundVolume
    val level = e.getWorld
    val sound = li.cil.oc.common.SoundEvents.byName(e.getSound)
    if (level != null && sound != null) {
      level.playLocalSound(e.getX, e.getY, e.getZ, sound, SoundSource.BLOCKS, volume, 1f, false)
    }
    e.getTileEntity match {
      case t: DiskDrive => t.lastAccess = System.currentTimeMillis()
      case t: Case => t.lastFileSystemAccess = System.currentTimeMillis()
      case t: Raid => t.lastAccess = System.currentTimeMillis()
      case _ =>
    }
  }
}
