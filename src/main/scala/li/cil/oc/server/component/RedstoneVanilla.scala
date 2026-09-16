package li.cil.oc.server.component

import java.util

import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.Settings
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network._
import li.cil.oc.common.tileentity.traits.{RedstoneAware, RedstoneChangedEventArgs}
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedWorld._
import net.minecraft.core.Direction

import scala.jdk.CollectionConverters._

trait RedstoneVanilla extends RedstoneSignaller with DeviceInfo {
  def redstone: EnvironmentHost with RedstoneAware

  // ----------------------------------------------------------------------- //

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Communication,
    DeviceAttribute.Description -> "Redstone controller",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "Rs100-V",
    DeviceAttribute.Capacity -> "16",
    DeviceAttribute.Width -> "1"
  )

  // 1.21.1：`DeviceInfo#getDeviceInfo` 返回 `java.util.Map`，Scala 的 `Map` 需要显式转换。
  override def getDeviceInfo: util.Map[String, String] = deviceInfo.asJava

  // 1.21.1：`ForgeDirection.VALID_DIRECTIONS` → `Direction.values()`。
  // 顺序完全一致（DOWN=0, UP=1, NORTH=2, SOUTH=3, WEST=4, EAST=5），
  // 因此下面 `valuesToMap` 里用 `ordinal` 当下标仍然正确。
  protected val SIDE_RANGE: Array[Direction] = Direction.values()

  // ----------------------------------------------------------------------- //
  @Callback(direct = true, doc = "function([side:number]):number or table -- Get the redstone input (all sides, or optionally on the specified side)")
  def getInput(context: Context, args: Arguments): Array[AnyRef] = {
    getOptionalSide(args) match {
      case Some(side: Int) => result(redstone.getInput(fromOrdinal(side)))
      case _ => result(valuesToMap(redstone.getInput).asJava)
    }
  }

  @Callback(direct = true, doc = "function([side:number]):number or table -- Get the redstone output (all sides, or optionally on the specified side)")
  def getOutput(context: Context, args: Arguments): Array[AnyRef] = {
    getOptionalSide(args) match {
      case Some(side: Int) => result(redstone.getOutput(fromOrdinal(side)))
      case _ => result(valuesToMap(redstone.getOutput).asJava)
    }
  }

  @Callback(doc = "function([side:number, ]value:number or table):number or table --  Set the redstone output (all sides, or optionally on the specified side). Returns previous values")
  def setOutput(context: Context, args: Arguments): Array[AnyRef] = {
    var ret: AnyRef = null
    if (getAssignment(args) match {
      case (side: Direction, value: Int) =>
        ret = new java.lang.Integer(redstone.getOutput(side))
        redstone.setOutput(side, value)
      case (value: util.Map[_, _], _) =>
        ret = valuesToMap(redstone.getOutput).asJava
        redstone.setOutput(value)
    }) {
      if (Settings.get.redstoneDelay > 0)
        context.pause(Settings.get.redstoneDelay)
    }
    result(ret)
  }

  @Callback(direct = true, doc = "function(side:number):number -- Get the comparator input on the specified side.")
  def getComparatorInput(context: Context, args: Arguments): Array[AnyRef] = {
    val side = checkSide(args, 0)
    val blockPos = BlockPosition(redstone).offset(side)
    if (redstone.world.blockExists(blockPos)) {
      val blockState = redstone.world.getBlockState(blockPos)
      // 1.21.1：`Block#hasComparatorInputOverride` 与
      // `Block#getComparatorInputOverride(World, x, y, z, side)` 已合并为
      // `BlockState#hasAnalogOutputSignal` / `BlockState#getAnalogOutputSignal(Level, BlockPos, Direction)`。
      if (blockState.hasAnalogOutputSignal) {
        return result(blockState.getAnalogOutputSignal(redstone.world, blockPos.toChunkCoordinates, side.getOpposite))
      }
    }
    result(0)
  }

  // ----------------------------------------------------------------------- //

  override def onMessage(message: Message): Unit = {
    super.onMessage(message)
    if (message.name == "redstone.changed") message.data match {
      case Array(args: RedstoneChangedEventArgs) =>
        onRedstoneChanged(args)
      case _ =>
    }
  }

  // ----------------------------------------------------------------------- //

  /** 把 Lua 侧的方向序号（0..5，等于 `Direction#ordinal`）还原为 `Direction`。 */
  private def fromOrdinal(ordinal: Int): Direction = Direction.values()(ordinal)

  private def getOptionalSide(args: Arguments): Option[Int] = {
    if (args.count == 1)
      Option(checkSide(args, 0).ordinal)
    else
      None
  }

  private def getAssignment(args: Arguments): (Any, Any) = {
    args.count match {
      case 2 => (checkSide(args, 0), args.checkInteger(1))
      case 1 => (args.checkTable(0), null)
      case _ => throw new Exception("invalid number of arguments, expected 1 or 2")
    }
  }

  protected def checkSide(args: Arguments, index: Int): Direction = {
    val side = args.checkInteger(index)
    if (side < 0 || side > 5)
      throw new IllegalArgumentException("invalid side")
    // 1.21.1：`ForgeDirection.getOrientation(i)` → `Direction.from3DDataValue(i)`，
    // `RedstoneAware#toGlobal` 的签名也已经是 `Direction => Direction`。
    redstone.toGlobal(Direction.from3DDataValue(side))
  }

  private def valuesToMap(ar: Array[Int]): Map[Int, Int] = SIDE_RANGE.map(_.ordinal).map{ case side if side < ar.length => side -> ar(side) }.toMap
}
