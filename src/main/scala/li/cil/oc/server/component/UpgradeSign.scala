package li.cil.oc.server.component

import java.util

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
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.SignBlockEntity
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.event.level.BlockEvent

import scala.jdk.CollectionConverters._

/**
 * 告示牌升级（读写告示牌文本）。
 *
 * ==1.21.1 迁移要点==
 *  - `TileEntitySign` → [[net.minecraft.world.level.block.entity.SignBlockEntity]]；
 *    `signText: Array[String]` 变为 `SignText` + `Component[]`（`getFrontText` / `setText`）。
 *  - `World#canMineBlock` 已移除，用 `Level#mayInteract`（含出生点保护判定）替代。
 *  - `BlockEvent.BreakEvent` 构造器变为 `(Level, BlockPos, BlockState, Player)`，metadata 参数消失。
 *  - NeoForge 事件没有 `Event.Result`，只通过 `ICancellableEvent#isCanceled` 判断。
 *  - `FakePlayerFactory` 迁到 `net.neoforged.neoforge.common.util`。
 *  - Scala 2.13 中 `String#lines` 解析为 Java 的 `Stream`，改用 `linesIterator`。
 */
abstract class UpgradeSign extends prefab.ManagedEnvironment with DeviceInfo {
  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Generic,
    DeviceAttribute.Description -> "Sign upgrade",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "Labelizer Deluxe"
  )

  override def getDeviceInfo: util.Map[String, String] = deviceInfo.asJava

  def host: EnvironmentHost

  /** 告示牌正面四行文本拼成的多行字符串（旧 `signText.mkString("\n")`）。 */
  private def signTextOf(sign: SignBlockEntity): String =
    sign.getFrontText.getMessages(false).map(_.getString).mkString("\n")

  protected def getValue(tileEntity: Option[SignBlockEntity]): Array[AnyRef] = {
    tileEntity match {
      case Some(sign) => result(signTextOf(sign))
      case _ => result((), "no sign")
    }
  }

  protected def setValue(tileEntity: Option[SignBlockEntity], text: String): Array[AnyRef] = {
    tileEntity match {
      case Some(sign) =>
        val player = host match {
          case robot: internal.Robot => robot.player
          case _ => FakePlayerFactory.get(host.world.asInstanceOf[ServerLevel], Settings.get.fakePlayerProfile)
        }

        val lines = text.linesIterator.padTo(4, "").map(line => if (line.length > 15) line.substring(0, 15) else line).toArray
        // 显式标注为 `Array[Component]`：`Component.literal` 返回的是 `MutableComponent`，
        // 而数组在 Scala 中是不变的，`SignChangeEvent` 需要 `Component[]`。
        val components: Array[Component] = lines.map(line => Component.literal(line))

        if (!canChangeSign(player, sign, components)) {
          return result((), "not allowed")
        }

        // `SignText` 不可变，每次 `setMessage` 都返回新实例。
        var signText = sign.getFrontText
        for (i <- components.indices) {
          signText = signText.setMessage(i, components(i))
        }
        sign.setText(signText, true)
        val pos = sign.getBlockPos
        host.world.markBlockForUpdate(BlockPosition(pos.getX, pos.getY, pos.getZ, host.world))

        NeoForge.EVENT_BUS.post(new SignChangeEvent.Post(sign, components))

        result(signTextOf(sign))
      case _ => result((), "no sign")
    }
  }

  protected def findSign(side: Direction) = {
    val hostPos = BlockPosition(host)
    host.world.getTileEntity(hostPos) match {
      case sign: SignBlockEntity => Option(sign)
      case _ => host.world.getTileEntity(hostPos.offset(side)) match {
        case sign: SignBlockEntity => Option(sign)
        case _ => None
      }
    }
  }

  private def canChangeSign(player: Player, tileEntity: SignBlockEntity, lines: Array[Component]): Boolean = {
    val pos = tileEntity.getBlockPos
    // 1.7.10 的 `World#canMineBlock` 在 1.21.1 的等价物：`Level#mayInteract`。
    if (!host.world.mayInteract(player, pos)) {
      return false
    }

    val event = new BlockEvent.BreakEvent(host.world, pos, host.world.getBlockState(pos), player)
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
          host.world.getTileEntity(blockPos) match {
            case sign: SignBlockEntity =>
              nbt.putString("signText", signTextOf(sign))
            case _ =>
          }
        case _ => // Ignore.
      }
      case _ => // Ignore.
    }
  }
}
