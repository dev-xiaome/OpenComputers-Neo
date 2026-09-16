package li.cil.oc.server.component

import java.util

import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.Settings
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.internal
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedArguments._
import li.cil.oc.util.ExtendedWorld._
import net.minecraft.core.Direction
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.level.block.piston.PistonBaseBlock

import scala.jdk.CollectionConverters._

abstract class UpgradePiston(val host: EnvironmentHost) extends prefab.ManagedEnvironment with DeviceInfo {
  override val node = Network.newNode(this, Visibility.Network).
    withComponent("piston").
    withConnector().
    create()

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Generic,
    DeviceAttribute.Description -> "Piston upgrade",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "Displacer II+"
  )

  // 1.21.1：Scala `Map` → `java.util.Map` 需要显式 `asJava`。
  override def getDeviceInfo: util.Map[String, String] = deviceInfo.asJava

  def pushDirection(args: Arguments, index: Int): Direction

  def pushOrigin(side: Direction) = BlockPosition(host)

  @Callback(doc = """function([side:number]):boolean -- Tries to push the block on the specified side of the container of the upgrade. Defaults to front.""")
  def push(context: Context, args: Arguments): Array[AnyRef] = {
    val side = pushDirection(args, 0)
    val hostPos = pushOrigin(side)
    val blockPos = hostPos.offset(side)
    // 1.21.1：`Block#tryExtend`（1.7.10）已被移除，活塞的「能否推动」判定改为静态的
    // `PistonBaseBlock.isPushable(state, level, pos, pushDirection, allowOverpowered, pistonFacing)`。
    // 这里保留原语义：只判断目标方块能否被推动，推动动作本身由下方直接清除方块完成。
    val pushable = PistonBaseBlock.isPushable(
      host.world.getBlockState(blockPos.toChunkCoordinates),
      host.world, blockPos.toChunkCoordinates, side, false, side)
    if (!host.world.isAirBlock(blockPos) && node.tryChangeBuffer(-Settings.get.pistonCost) && pushable) {
      host.world.setBlockToAir(blockPos)
      // 1.21.1：`World#playSoundEffect(x, y, z, name, volume, pitch)` →
      // `Level#playSound(player, x, y, z, SoundEvent, SoundSource, volume, pitch)`；
      // 原字符串音效名 `"tile.piston.out"` 对应 `SoundEvents.PISTON_EXTEND`。
      host.world.playSound(null: net.minecraft.world.entity.player.Player,
        BlockPosition(host).x + 0.5, BlockPosition(host).y + 0.5, BlockPosition(host).z + 0.5,
        SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS,
        0.5f, host.world.random.nextFloat() * 0.25f + 0.6f)
      context.pause(0.5)
      result(true)
    }
    else result(false)
  }
}

object UpgradePiston {

  class Drone(drone: internal.Drone) extends UpgradePiston(drone) {
    override def pushDirection(args: Arguments, index: Int) = args.optSideAny(index, Direction.SOUTH)
  }

  class Tablet(tablet: internal.Tablet) extends Rotatable(tablet) {
    // 1.21.1：`Entity#getEyeHeight` 变成方法 `getEyeHeight()`。
    override def pushOrigin(side: Direction) =
      if (side == Direction.DOWN && tablet.player.getEyeHeight() > 1) super.pushOrigin(side).offset(Direction.DOWN)
      else super.pushOrigin(side)
  }

  class Rotatable(val rotatable: internal.Rotatable with EnvironmentHost) extends UpgradePiston(rotatable) {
    override def pushDirection(args: Arguments, index: Int) = rotatable.toGlobal(args.optSideForAction(index, Direction.SOUTH))
  }

}