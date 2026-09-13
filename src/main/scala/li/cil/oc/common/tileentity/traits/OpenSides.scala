package li.cil.oc.common.tileentity.traits

import cpw.mods.fml.relauncher.{Side, SideOnly}
import li.cil.oc.Settings
import net.minecraft.nbt.CompoundTag
import net.minecraft.core.Direction

/**
  * @author Vexatos
  */
trait OpenSides extends BlockEntity {
  protected def SideCount = Direction.VALID_DIRECTIONS.length

  protected def defaultState: Boolean = false

  var openSides = Array.fill(SideCount)(defaultState)

  def compressSides = (Direction.VALID_DIRECTIONS, openSides).zipped.foldLeft(0)((acc, entry) => acc | (if (entry._2) entry._1.flag else 0)).toByte

  def uncompressSides(byte: Byte) = Direction.VALID_DIRECTIONS.map(d => (d.flag & byte) != 0)

  def isSideOpen(side: Direction) = side != Direction.UNKNOWN && openSides(side.ordinal())

  def setSideOpen(side: Direction, value: Boolean): Unit = if (side != Direction.UNKNOWN && openSides(side.ordinal()) != value) {
    openSides(side.ordinal()) = value
  }

  override def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)
    if (nbt.contains(Settings.namespace + "openSides"))
      openSides = uncompressSides(nbt.getByte(Settings.namespace + "openSides"))
  }

  override def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)
    nbt.putByte(Settings.namespace + "openSides", compressSides)
  }

  @SideOnly(Dist.CLIENT)
  override def readFromNBTForClient(nbt: CompoundTag): Unit = {
    super.readFromNBTForClient(nbt)
    openSides = uncompressSides(nbt.getByte(Settings.namespace + "openSides"))
  }

  override def writeToNBTForClient(nbt: CompoundTag): Unit = {
    super.writeToNBTForClient(nbt)
    nbt.putByte(Settings.namespace + "openSides", compressSides)
  }
}
