package li.cil.oc.common.item.data

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import li.cil.oc.Settings
import li.cil.oc.common.datacomponents.{OCComponents, ScalaCodec}
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import li.cil.oc.server.fs
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponentHolder
import net.minecraft.world.entity.player.Player
import li.cil.oc.util.ExtendedDataComponentHolder._
import net.neoforged.neoforge.common.MutableDataComponentHolder

case class DriveData(var isUnmanaged: Boolean = false, var lockInfo: String = "") extends ItemData(null) {
  def this(stack: DataComponentHolder) = {
    this()
    loadData(stack)
  }

  def isLocked: Boolean = {
    lockInfo != null && !lockInfo.isEmpty
  }

  override def loadData(holder: DataComponentHolder): Unit = {
    isUnmanaged = holder.getComponent(OCComponents.UNMANAGED) getOrElse false
    lockInfo = holder.getComponent(OCComponents.LOCK) getOrElse ""
  }

  override def saveData(holder: MutableDataComponentHolder): Unit = {
    holder.setComponent(OCComponents.UNMANAGED, isUnmanaged)
    holder.setComponent(OCComponents.LOCK, lockInfo)
  }
}

object DriveData {
  val CODEC = RecordCodecBuilder.create[DriveData](inst => inst.group(
    ScalaCodec.BOOL.fieldOf("unmanaged").forGetter(_.isUnmanaged),
    Codec.STRING.fieldOf("lock").forGetter(_.lockInfo)
  ).apply(inst, DriveData.apply _))

  def lock(stack: ItemStack, player: Player): Unit = {
    val key = player.getName.getString
    val data = new DriveData(stack)
    if (!data.isLocked) {
      data.lockInfo = key match {
        case name: String if name != null && name.nonEmpty => name
        case _ => "notch" // meaning: "unknown"
      }
      data.saveData(stack)
    }
  }

  def setUnmanaged(stack: ItemStack, unmanaged: Boolean): Unit = {
    val data = new DriveData(stack)
    if (data.isUnmanaged != unmanaged) {
      fs.FileSystem.removeAddress(stack)
      data.lockInfo = ""
    }
    data.isUnmanaged = unmanaged
    data.saveData(stack)
  }
}
