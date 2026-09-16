package li.cil.oc.client

import li.cil.oc.Settings
import li.cil.oc.common.CompressedPacketBuilder
import li.cil.oc.common.PacketType
import li.cil.oc.common.SimplePacketBuilder
import li.cil.oc.common.entity.Drone
import li.cil.oc.common.tileentity._
import li.cil.oc.common.tileentity.traits.Computer
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.level.block.entity.BlockEntity

/**
 * 客户端 → 服务端的包发送器（对应 1.7.10 的 `li.cil.oc.client.PacketSender`）。
 *
 * ==1.21.1 迁移要点==
 *  - 包体格式完全沿用 [[li.cil.oc.common.PacketBuilder]]（首字节包类型 + `DataOutputStream`
 *    负载，`CompressedPacketBuilder` 额外套 Deflater 流），因此写包代码一行都不用改，
 *    变化的只有 `Minecraft` 侧 API 与「维度不再有数字 id」。
 *  - `Minecraft.getMinecraft` → `Minecraft.getInstance()`；`thePlayer` → `player`。
 *  - 提示音：1.7.10 的 `getSoundHandler.playSound(new PositionedSoundRecord(...))` →
 *    `getSoundManager.play(SimpleSoundInstance.forUI(...))`（UI 音效不带位置）。
 *  - `sendRobotStateRequest` 的维度参数由 `Int` 改为 `ResourceLocation`：服务端
 *    `onRobotStateRequest` 现在按 `readTileEntity[RobotProxy]()` 解析，也就是
 *    「维度字符串 + 坐标」，与原数字维度 id 无法互通。
 */
object PacketSender {
  // Timestamp after which the next clipboard message may be sent. Used to
  // avoid spamming large packets on key repeat.
  protected var clipboardCooldown = 0L

  def sendComputerPower(t: Computer, power: Boolean): Unit = {
    // `traits.Computer` 只通过自类型（self-type）声明宿主必须是 `BlockEntity`，
    // 而 Scala 的自类型不产生子类型关系，因此这里显式取一次宿主方块实体。
    // `Computer` 的实现者一定混入了 `BlockEntityBase`，所以该分支必然命中。
    t match {
      case host: BlockEntity =>
        val pb = new SimplePacketBuilder(PacketType.ComputerPower)

        pb.writeTileEntity(host)
        pb.writeBoolean(power)

        pb.sendToServer()
      case _ =>
        // TODO(client): 不可达分支（`Computer` 的自类型保证宿主是 `BlockEntity`），
        // 这里选择丢包而不是抛异常。
    }
  }

  def sendDriveMode(unmanaged: Boolean): Unit = {
    val pb = new SimplePacketBuilder(PacketType.DriveMode)

    pb.writeBoolean(unmanaged)

    pb.sendToServer()
  }

  def sendDriveLock(): Unit = {
    val pb = new SimplePacketBuilder(PacketType.DriveLock)

    pb.sendToServer()
  }

  def sendDronePower(e: Drone, power: Boolean): Unit = {
    val pb = new SimplePacketBuilder(PacketType.DronePower)

    pb.writeEntity(e)
    pb.writeBoolean(power)

    pb.sendToServer()
  }

  def sendKeyDown(address: String, char: Char, code: Int): Unit = {
    val pb = new SimplePacketBuilder(PacketType.KeyDown)

    pb.writeUTF(address)
    pb.writeChar(char)
    pb.writeInt(code)

    pb.sendToServer()
  }

  def sendKeyUp(address: String, char: Char, code: Int): Unit = {
    val pb = new SimplePacketBuilder(PacketType.KeyUp)

    pb.writeUTF(address)
    pb.writeChar(char)
    pb.writeInt(code)

    pb.sendToServer()
  }

  def sendClipboard(address: String, value: String): Unit = {
    if (value != null && !value.isEmpty) {
      if (value.length > Settings.get.maxClipboardTextLength || System.currentTimeMillis() < clipboardCooldown) {
        // 1.21.1：UI 音效不需要坐标，用 `SimpleSoundInstance.forUI` 播一个单音，提示玩家
        // 剪贴板内容过长或发送过于频繁。
        val sound = SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HARP.value(), 1f, 1f)
        Minecraft.getInstance().getSoundManager.play(sound)
      }
      else {
        clipboardCooldown = System.currentTimeMillis() + value.length / 10
        for (part <- value.grouped(16 * 1024)) {
          val pb = new CompressedPacketBuilder(PacketType.Clipboard)

          pb.writeUTF(address)
          pb.writeUTF(part)

          pb.sendToServer()
        }
      }
    }
  }

  def sendMouseClick(address: String, x: Double, y: Double, drag: Boolean, button: Int): Unit = {
    val pb = new SimplePacketBuilder(PacketType.MouseClickOrDrag)

    pb.writeUTF(address)
    pb.writeFloat(x.toFloat)
    pb.writeFloat(y.toFloat)
    pb.writeBoolean(drag)
    pb.writeByte(button.toByte)

    pb.sendToServer()
  }

  def sendMouseScroll(address: String, x: Double, y: Double, scroll: Int): Unit = {
    val pb = new SimplePacketBuilder(PacketType.MouseScroll)

    pb.writeUTF(address)
    pb.writeFloat(x.toFloat)
    pb.writeFloat(y.toFloat)
    pb.writeByte(scroll)

    pb.sendToServer()
  }

  def sendMouseUp(address: String, x: Double, y: Double, button: Int): Unit = {
    val pb = new SimplePacketBuilder(PacketType.MouseUp)

    pb.writeUTF(address)
    pb.writeFloat(x.toFloat)
    pb.writeFloat(y.toFloat)
    pb.writeByte(button.toByte)

    pb.sendToServer()
  }

  def sendCopyToAnalyzer(address: String, line: Int): Unit = {
    val pb = new SimplePacketBuilder(PacketType.CopyToAnalyzer)

    pb.writeUTF(address)
    pb.writeInt(line)

    pb.sendToServer()
  }

  def sendMultiPlace(): Unit = {
    val pb = new SimplePacketBuilder(PacketType.MultiPartPlace)
    pb.sendToServer()
  }

  def sendPetVisibility(): Unit = {
    val pb = new SimplePacketBuilder(PacketType.PetVisibility)

    pb.writeBoolean(!Settings.get.hideOwnPet)

    pb.sendToServer()
  }

  def sendRackMountableMapping(t: Rack, mountableIndex: Int, nodeIndex: Int, side: Option[Direction]): Unit = {
    val pb = new SimplePacketBuilder(PacketType.RackMountableMapping)

    pb.writeTileEntity(t)
    pb.writeInt(mountableIndex)
    pb.writeInt(nodeIndex)
    pb.writeDirection(side)

    pb.sendToServer()
  }

  def sendRackRelayState(t: Rack, enabled: Boolean): Unit = {
    val pb = new SimplePacketBuilder(PacketType.RackRelayState)

    pb.writeTileEntity(t)
    pb.writeBoolean(enabled)

    pb.sendToServer()
  }

  def sendRobotAssemblerStart(t: Assembler): Unit = {
    val pb = new SimplePacketBuilder(PacketType.RobotAssemblerStart)

    pb.writeTileEntity(t)

    pb.sendToServer()
  }

  /**
   * 请求服务端重发某个位置上机器人的状态。
   *
   * 1.7.10 用数字维度 id（`pb.writeInt(dimension)`）；1.21.1 的服务端
   * `onRobotStateRequest` 走 `readTileEntity[RobotProxy]()`，因此这里改成写维度
   * 的 `ResourceLocation`，布局与 `PacketBuilder.writeTileEntity` 一致。
   */
  def sendRobotStateRequest(dimension: ResourceLocation, x: Int, y: Int, z: Int): Unit = {
    val pb = new SimplePacketBuilder(PacketType.RobotStateRequest)

    pb.writeUTF(if (dimension != null) dimension.toString else "")
    pb.writeInt(x)
    pb.writeInt(y)
    pb.writeInt(z)

    pb.sendToServer()
  }

  def sendServerPower(t: Rack, mountableIndex: Int, power: Boolean): Unit = {
    val pb = new SimplePacketBuilder(PacketType.ServerPower)

    pb.writeTileEntity(t)
    pb.writeInt(mountableIndex)
    pb.writeBoolean(power)

    pb.sendToServer()
  }

  def sendTextBufferInit(address: String): Unit = {
    val pb = new SimplePacketBuilder(PacketType.TextBufferInit)

    pb.writeUTF(address)

    pb.sendToServer()
  }

  def sendWaypointLabel(t: Waypoint): Unit = {
    val pb = new SimplePacketBuilder(PacketType.WaypointLabel)

    pb.writeTileEntity(t)
    pb.writeUTF(t.label)

    pb.sendToServer()
  }
}
