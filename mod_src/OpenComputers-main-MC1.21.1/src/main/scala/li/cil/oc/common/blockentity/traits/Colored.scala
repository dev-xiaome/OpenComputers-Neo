package li.cil.oc.common.blockentity.traits

import li.cil.oc.Settings
import li.cil.oc.api.internal
import li.cil.oc.common.datacomponents.OCComponents
import li.cil.oc.server.PacketSender
import li.cil.oc.util.Color
import li.cil.oc.util.ExtendedDataComponentHolder._
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponentHolder
import net.minecraft.world.item.DyeColor
import net.minecraft.nbt.CompoundTag
import net.minecraft.util.ColorRGBA
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.neoforge.common.MutableDataComponentHolder

trait Colored extends BaseBlockEntity with internal.Colored {
  private var _color = 0

  def consumesDye = false

  override def getColor: Int = _color

  override def setColor(value: Int) = if (value != _color) {
    _color = value
    onColorChanged()
  }

  override def controlsConnectivity = false

  protected def onColorChanged(): Unit = {
    if (getLevel != null && isServer) {
      PacketSender.sendColorChange(this)
    }
  }

  // ----------------------------------------------------------------------- //

  override def loadComponentsCommon(holder: DataComponentHolder): Unit = {
    super.loadComponentsCommon(holder)
    for(color <- holder.getComponent(OCComponents.RENDER_COLOR))
      _color = color.rgba()
  }

  override def saveComponentsCommon(holder: MutableDataComponentHolder): Unit = {
    super.saveComponentsCommon(holder)
    holder.setComponent(OCComponents.RENDER_COLOR, new ColorRGBA(_color))
  }
}
