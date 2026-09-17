package li.cil.oc.server.machine

import li.cil.oc.api
import net.minecraft.nbt.CompoundTag

abstract class ArchitectureAPI(val machine: api.machine.Machine) {
  protected def node = machine.node

  protected def components = machine.components

  def initialize(): Unit

  def loadData(nbt: CompoundTag): Unit = {}

  def saveData(nbt: CompoundTag): Unit = {}
}
