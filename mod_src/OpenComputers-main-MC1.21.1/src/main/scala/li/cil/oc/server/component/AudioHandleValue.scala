package li.cil.oc.server.component

import li.cil.oc.api.machine.Context
import li.cil.oc.api.prefab.AbstractValue
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponentHolder
import net.minecraft.nbt.CompoundTag
import net.neoforged.neoforge.common.MutableDataComponentHolder

final class AudioHandleValue extends AbstractValue {
  def this(owner: String, handle: Int) = {
    this()
    this.owner = owner
    this.handle = handle
  }

  var owner = ""
  var handle = 0

  override def dispose(context: Context): Unit = {
    super.dispose(context)
    if (context.node() != null && context.node().network() != null) {
      val node = context.node().network().node(owner)
      if (node != null) {
        node.host() match {
          case ac: AudioCard => try ac.closeHandle(owner, handle) catch {
            case _: Throwable =>
          }
        }
      }
    }
  }

  override def loadData(holder: DataComponentHolder, nbt: CompoundTag, provider: HolderLookup.Provider): Unit = {
    super.loadData(holder, nbt, provider)
    owner = nbt.getString("owner")
    handle = nbt.getInt("handle")
  }

  override def saveData(holder: MutableDataComponentHolder, nbt: CompoundTag, provider: HolderLookup.Provider): Unit = {
    super.saveData(holder, nbt, provider)
    nbt.putString("owner", owner)
    nbt.putInt("handle", handle)
  }

  override def toString: String = handle.toString
}