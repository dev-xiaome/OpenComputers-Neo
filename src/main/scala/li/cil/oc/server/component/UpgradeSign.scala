package li.cil.oc.server.component

import java.util

import net.neoforged.bus.api.Event
import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.event.SignChangeEvent
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.internal
import li.cil.oc.api.network.Message
import li.cil.oc.api.prefab
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedWorld._
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.tileentity.TileEntitySign
import net.minecraft.server.level.ServerLevel
import net.neoforged.neoforge.common.NeoForge
import net.minecraftforge.common.util.FakePlayerFactory
import net.minecraft.core.Direction
import net.neoforged.neoforge.event.level.BlockEvent

import scala.jdk.CollectionConverters._

abstract class UpgradeSign extends prefab.ManagedEnvironment with DeviceInfo {
  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Generic,
    DeviceAttribute.Description -> "Sign upgrade",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "Labelizer Deluxe"
  )

  override def getDeviceInfo: util.Map[String, String] = deviceInfo

  def host: EnvironmentHost

  protected def getValue(tileEntity: Option[TileEntitySign]): Array[AnyRef] = {
    tileEntity match {
      case Some(sign) => result(sign.signText.mkString("\n"))
      case _ => result(Unit, "no sign")
    }
  }

  protected def setValue(tileEntity: Option[TileEntitySign], text: String): Array[AnyRef] = {
    tileEntity match {
      case Some(sign) =>
        val player = host match {
          case robot: internal.Robot => robot.player
          case _ => FakePlayerFactory.get(host.world.asInstanceOf[ServerLevel], Settings.get.fakePlayerProfile)
        }

        val lines = text.lines.padTo(4, "").map(line => if (line.length > 15) line.substring(0, 15) else line).toArray

        if (!canChangeSign(player, sign, lines)) {
          return result(Unit, "not allowed")
        }

        lines.copyToArray(sign.signText)
        host.world.markBlockForUpdate(sign.xCoord, sign.yCoord, sign.zCoord)

        MinecraftForge.EVENT_BUS.post(new SignChangeEvent.Post(sign, lines))

        result(sign.signText.mkString("\n"))
      case _ => result(Unit, "no sign")
    }
  }

  protected def findSign(side: Direction) = {
    val hostPos = BlockPosition(host)
    host.world.getTileEntity(hostPos) match {
      case sign: TileEntitySign => Option(sign)
      case _ => host.world.getTileEntity(hostPos.offset(side)) match {
        case sign: TileEntitySign => Option(sign)
        case _ => None
      }
    }
  }

  private def canChangeSign(player: Player, tileEntity: TileEntitySign, lines: Array[String]): Boolean = {
    if (!host.world.canMineBlock(player, tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord)) {
      return false
    }

    val event = new BlockEvent.BreakEvent(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord, host.world, tileEntity.getBlockType, tileEntity.getBlockMetadata, player)
    MinecraftForge.EVENT_BUS.post(event)
    if (event.isCanceled || event.getResult == Event.Result.DENY) {
      return false
    }

    val signEvent = new SignChangeEvent.Pre(tileEntity, lines)
    MinecraftForge.EVENT_BUS.post(signEvent)
    !(signEvent.isCanceled || signEvent.getResult == Event.Result.DENY)
  }

  override def onMessage(message: Message): Unit = {
    super.onMessage(message)
    if (message.name == "tablet.use") message.source.host match {
      case machine: api.machine.Machine => (machine.host, message.data) match {
        case (tablet: internal.Tablet, Array(nbt: CompoundTag, stack: ItemStack, player: Player, blockPos: BlockPosition, side: Direction, hitX: java.lang.Float, hitY: java.lang.Float, hitZ: java.lang.Float)) =>
          host.world.getTileEntity(blockPos) match {
            case sign: TileEntitySign =>
              nbt.putString("signText", sign.signText.mkString("\n"))
            case _ =>
          }
        case _ => // Ignore.
      }
      case _ => // Ignore.
    }
  }
}
