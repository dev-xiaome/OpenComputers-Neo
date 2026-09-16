package li.cil.oc.server.component

import li.cil.oc.api.Network
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab.AbstractManagedEnvironment
import li.cil.oc.util.ExtendedDataComponentHolder._
import li.cil.oc.common.blockentity.traits.RedstoneChangedEventArgs
import li.cil.oc.common.datacomponents.OCComponents
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponentHolder
import net.minecraft.nbt.CompoundTag
import net.neoforged.neoforge.common.MutableDataComponentHolder

import scala.collection.mutable.ArrayBuffer

trait RedstoneSignaller extends AbstractManagedEnvironment {
  override val node = Network.newNode(this, Visibility.Network).
    withComponent("redstone", Visibility.Neighbors).
    create()

  var wakeThreshold = 0

  var wakeNeighborsOnly = true

  // ----------------------------------------------------------------------- //

  @Callback(direct = true, doc = """function():number -- Get the current wake-up threshold.""")
  def getWakeThreshold(context: Context, args: Arguments): Array[AnyRef] = result(wakeThreshold)

  @Callback(doc = """function(threshold:number):number -- Set the wake-up threshold.""")
  def setWakeThreshold(context: Context, args: Arguments): Array[AnyRef] = {
    val oldThreshold = wakeThreshold
    wakeThreshold = args.checkInteger(0)
    result(oldThreshold)
  }

  // ----------------------------------------------------------------------- //

  def onRedstoneChanged(args: RedstoneChangedEventArgs): Unit = {
    val side: AnyRef = if (args.side == null) "wireless" else Int.box(args.side.ordinal)
    val flatArgs = ArrayBuffer[Object]("redstone_changed", side, Int.box(args.oldValue), Int.box(args.newValue))
    if (args.color >= 0)
      flatArgs += Int.box(args.color)
    node.sendToReachable("computer.signal", flatArgs.toArray: _*)
    if (args.oldValue < wakeThreshold && args.newValue >= wakeThreshold) {
      if (wakeNeighborsOnly)
        node.sendToNeighbors("computer.start")
      else
        node.sendToReachable("computer.start")
    }
  }

  // ----------------------------------------------------------------------- //

  private final val WakeThresholdNbt = "wakeThreshold"

  override def loadData(holder: DataComponentHolder): Unit = {
    super.loadData(holder)
    wakeThreshold = holder.getComponent(OCComponents.WAKE_THRESHOLD) getOrElse 0
  }

  override def saveData(holder: MutableDataComponentHolder): Unit = {
    super.saveData(holder)
    holder.setComponent(OCComponents.WAKE_THRESHOLD, Option.when(wakeThreshold > 0) { wakeThreshold })
  }
}
