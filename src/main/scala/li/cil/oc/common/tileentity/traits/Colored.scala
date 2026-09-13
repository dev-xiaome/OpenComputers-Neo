package li.cil.oc.common.tileentity.traits

import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import li.cil.oc.Settings
import li.cil.oc.api.internal
import li.cil.oc.server.PacketSender
import net.minecraft.nbt.CompoundTag

trait Colored extends BlockEntity with internal.Colored {
  private var _color = 0

  def color = _color

  def color_=(value: Int) = if (value != _color) {
    _color = value
    onColorChanged()
  }

  def consumesDye = false

  override def getColor = color

  override def setColor(value: Int) = color = value

  protected def onColorChanged(): Unit = {
    if (world != null && isServer) {
      PacketSender.sendColorChange(this)
    }
  }

  override def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)
    if (nbt.contains(Settings.namespace + "renderColor")) {
      _color = nbt.getInteger(Settings.namespace + "renderColor")
    }
  }

  override def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)
    nbt.putInt(Settings.namespace + "renderColor", _color)
  }

  @SideOnly(Dist.CLIENT)
  override def readFromNBTForClient(nbt: CompoundTag): Unit = {
    super.readFromNBTForClient(nbt)
    _color = nbt.getInteger("renderColor")
  }

  override def writeToNBTForClient(nbt: CompoundTag): Unit = {
    super.writeToNBTForClient(nbt)
    nbt.putInt("renderColor", _color)
  }
}
