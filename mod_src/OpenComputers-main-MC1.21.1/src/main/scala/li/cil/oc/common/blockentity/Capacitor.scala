package li.cil.oc.common.blockentity

import java.util
import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Visibility
import li.cil.oc.util.BlockPosHelper
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.core.Direction
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.common.extensions.IBlockEntityExtension

import scala.jdk.CollectionConverters._

class Capacitor(blockEntityType: BlockEntityType[_], pos: BlockPos, state: BlockState)
  extends BlockEntity(blockEntityType, pos, state) with traits.Environment with DeviceInfo with IBlockEntityExtension {
  def this(pos: BlockPos, state: BlockState) = this(BlockEntityTypes.CAPACITOR.get(), pos, state)

  // Start with maximum theoretical capacity, gets reduced after validation.
  // This is done so that we don't lose energy while loading.
  val node = api.Network.newNode(this, Visibility.Network).
    withConnector(maxCapacity).
    create()

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Power,
    DeviceAttribute.Description -> "Battery",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "CapBank3x",
    DeviceAttribute.Capacity -> maxCapacity.toString
  )

  override def getDeviceInfo: util.Map[String, String] = deviceInfo.asJava

  // ----------------------------------------------------------------------- //

  override def dispose(): Unit = {
    super.dispose()
    if (isServer) {
      indirectNeighbors.map(coordinate => {
        if (getLevel.isLoaded(coordinate)) Option(getLevel.getBlockEntity(coordinate))
        else None
      }).collect {
        case Some(capacitor: Capacitor) => capacitor.recomputeCapacity()
      }
    }
  }

  override def onConnect(node: Node): Unit = {
    super.onConnect(node)
    if (node == this.node) {
      recomputeCapacity(updateSecondGradeNeighbors = true)
    }
  }

  // ----------------------------------------------------------------------- //

  def recomputeCapacity(updateSecondGradeNeighbors: Boolean = false): Unit = {
    node.setLocalBufferSize(
      Settings.get.bufferCapacitor +
        Settings.get.bufferCapacitorAdjacencyBonus * Direction.values.count(side => {
          val blockPos = BlockPosHelper.relative(getBlockPos, side)
          getLevel.isLoaded(blockPos) && (getLevel.getBlockEntity(blockPos) match {
            case capacitor: Capacitor => true
            case _ => false
          })
        }) +
        Settings.get.bufferCapacitorAdjacencyBonus / 2 * indirectNeighbors.count(blockPos => getLevel.isLoaded(blockPos) && (getLevel.getBlockEntity(blockPos) match {
          case capacitor: Capacitor =>
            if (updateSecondGradeNeighbors) {
              capacitor.recomputeCapacity()
            }
            true
          case _ => false
        })))
  }

  private def indirectNeighbors = Direction.values.map(getBlockPos.relative(_, 2): BlockPos)

  protected def maxCapacity = Settings.get.bufferCapacitor + Settings.get.bufferCapacitorAdjacencyBonus * 9
}
