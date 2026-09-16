package li.cil.oc.common.item.data

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.Persistable
import li.cil.oc.common.datacomponents.ScalaCodec
import li.cil.oc.server.component.DebugCard.AccessContext
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponentHolder
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.neoforged.neoforge.common.MutableDataComponentHolder

case class DebugCardData(var access: Option[AccessContext] = None) extends ItemData(Constants.ItemName.DebugCard) {
  def this(stack: DataComponentHolder) = {
    this()
    loadData(stack)
  }

  override def loadData(holder: DataComponentHolder): Unit = {
    access = AccessContext.loadData(holder)
  }

  override def saveData(holder: MutableDataComponentHolder): Unit = {
    AccessContext.remove(holder)
    access.foreach(_.saveData(holder))
  }
}

object DebugCardData {
  val CODEC = RecordCodecBuilder.create[DebugCardData](inst => inst.group(
    ScalaCodec.optionFieldOf("access", AccessContext.CODEC).forGetter(_.access)
  ).apply(inst, DebugCardData.apply _))
}
