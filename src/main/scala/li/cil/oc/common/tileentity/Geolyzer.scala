package li.cil.oc.common.tileentity

import li.cil.oc.server.component
import net.minecraft.nbt.CompoundTag

class Geolyzer extends traits.Environment {
  val geolyzer = new component.Geolyzer(this)

  def node = geolyzer.node

  override def canUpdate = false

  override def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)
    geolyzer.load(nbt)
  }

  override def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)
    geolyzer.save(nbt)
  }
}
