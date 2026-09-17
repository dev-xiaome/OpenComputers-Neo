package li.cil.oc.common.blockentity

import li.cil.oc.api.network.Node
import li.cil.oc.server.component
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState

class MotionSensor(pos: BlockPos, state: BlockState) 
  extends BlockEntity(BlockEntityTypes.MOTION_SENSOR.get(), pos, state) with traits.Environment with traits.Tickable {
  val motionSensor = new component.MotionSensor(this)

  def node: Node = motionSensor.node

  override def updateEntity(): Unit = {
    super.updateEntity()
    if (isServer) {
      motionSensor.update()
    }
  }

  override def loadForServer(nbt: CompoundTag): Unit = {
    super.loadForServer(nbt)
    motionSensor.loadData(nbt)
  }

  override def saveForServer(nbt: CompoundTag): Unit = {
    super.saveForServer(nbt)
    motionSensor.saveData(nbt)
  }
}
