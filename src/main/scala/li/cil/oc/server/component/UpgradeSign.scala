package li.cil.oc.server.component

import java.util
import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.event.SignChangeEvent
import li.cil.oc.api.internal
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Message
import li.cil.oc.api.prefab
import li.cil.oc.api.prefab.AbstractManagedEnvironment
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedLevel._
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.core.Direction
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.common.util.FakePlayerFactory

import scala.collection.convert.ImplicitConversionsToJava._
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.SignBlockEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.event.level.BlockEvent

abstract class UpgradeSign extends AbstractManagedEnvironment with DeviceInfo {
  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Generic,
    DeviceAttribute.Description -> "Sign upgrade",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "Labelizer Deluxe"
  )

  override def getDeviceInfo: util.Map[String, String] = deviceInfo

  def host: EnvironmentHost

  private def getAllMessages(sign: SignBlockEntity): Seq[Component] = {
    (0 until 4).map(i => sign.getFrontText.getMessage(i, false))
  }

  protected def getValue(tileEntity: Option[SignBlockEntity]): Array[AnyRef] = {
    tileEntity match {
      case Some(sign) => result(getAllMessages(sign).map(_.getString).mkString("\n"))
      case _ => result((), "no sign")
    }
  }

  protected def setValue(tileEntity: Option[SignBlockEntity], text: String): Array[AnyRef] = {
    tileEntity match {
      case Some(sign) =>
        val player = host match {
          case robot: internal.Robot => robot.player
          case _ => FakePlayerFactory.get(host.getEnvironmentLevel.asInstanceOf[ServerLevel], Settings.get.fakePlayerProfile)
        }

        val lines = text.linesIterator.padTo(4, "").map(line => if (line.length > 15) line.substring(0, 15) else line).toArray

        if (!canChangeSign(player, sign, lines)) {
          return result((), "not allowed")
        }

        lines.map(line => Component.literal(line)).copyToArray(getAllMessages(sign).toArray)
        host.getEnvironmentLevel.notifyBlockUpdate(sign.getBlockPos)

        NeoForge.EVENT_BUS.post(new SignChangeEvent.Post(sign, lines))

        result(getAllMessages(sign).mkString("\n"))
      case _ => result((), "no sign")
    }
  }

  protected def findSign(side: Direction) = {
    val hostPos = BlockPosition(host)
    host.getEnvironmentLevel.getBlockEntity(hostPos) match {
      case sign: SignBlockEntity => Option(sign)
      case _ => host.getEnvironmentLevel.getBlockEntity(hostPos.offset(side)) match {
        case sign: SignBlockEntity => Option(sign)
        case _ => None
      }
    }
  }

  private def canChangeSign(player: Player, tileEntity: SignBlockEntity, lines: Array[String]): Boolean = {
    if (!host.getEnvironmentLevel.mayInteract(player, tileEntity.getBlockPos)) {
      return false
    }
    val event = new BlockEvent.BreakEvent(host.getEnvironmentLevel, tileEntity.getBlockPos, tileEntity.getLevel.getBlockState(tileEntity.getBlockPos), player)
    NeoForge.EVENT_BUS.post(event)
    if (event.isCanceled) {
      return false
    }

    val signEvent = new SignChangeEvent.Pre(tileEntity, lines)
    NeoForge.EVENT_BUS.post(signEvent)
    !signEvent.isCanceled
  }

  override def onMessage(message: Message): Unit = {
    super.onMessage(message)
    if (message.name == "tablet.use") message.source.host match {
      case machine: api.machine.Machine => (machine.host, message.data) match {
        case (tablet: internal.Tablet, Array(nbt: CompoundTag, stack: ItemStack, player: Player, blockPos: BlockPosition, side: Direction, hitX: java.lang.Float, hitY: java.lang.Float, hitZ: java.lang.Float)) =>
          host.getEnvironmentLevel.getBlockEntity(blockPos) match {
            case sign: SignBlockEntity =>
              nbt.putString("signText", getAllMessages(sign).map(_.getString).mkString("\n"))
            case _ =>
          }
        case _ => // Ignore.
      }
      case _ => // Ignore.
    }
  }
}
