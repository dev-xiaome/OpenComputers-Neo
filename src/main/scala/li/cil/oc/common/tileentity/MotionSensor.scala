package li.cil.oc.common.tileentity

import li.cil.oc.api.network.Node
import li.cil.oc.server.component
import net.minecraft.nbt.CompoundTag

class MotionSensor extends traits.Environment {
  val motionSensor = new component.MotionSensor(this)

  def node: Node = motionSensor.node

  override def canUpdate = isServer

  override def updateEntity(): Unit = {
    super.updateEntity()
    motionSensor.update()
  }

  override def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)
    motionSensor.load(nbt)
  }

  override def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)
    motionSensor.save(nbt)
  }
}
