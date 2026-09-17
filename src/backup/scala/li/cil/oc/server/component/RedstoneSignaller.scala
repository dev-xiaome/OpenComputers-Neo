package li.cil.oc.server.component

import li.cil.oc.api.Network
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab
import li.cil.oc.common.tileentity.traits.RedstoneChangedEventArgs
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag

import scala.collection.mutable.ArrayBuffer

trait RedstoneSignaller extends prefab.ManagedEnvironment {
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
    // 1.21.1：`Direction` 没有 `UNKNOWN`（不存在「无线来源」这个取值）。
    // 原实现在「无线红石」时把 side 报成字符串 "wireless"，这里约定 `side == null`
    // 表示同一种情况（`RedstoneWireless` 即用 `null` 构造事件），以保持 Lua 侧
    // `redstone_changed` 信号的第 2 个参数语义不变。
    val side: AnyRef = args.side match {
      case null => "wireless"
      case value => Int.box(value.ordinal)
    }
    val flatArgs = ArrayBuffer[Object]("redstone_changed", side, Int.box(args.oldValue), Int.box(args.newValue))
    if (args.color >= 0)
      flatArgs += Int.box(args.color)
    node.sendToReachable("computer.signal", flatArgs.toSeq: _*)
    if (args.oldValue < wakeThreshold && args.newValue >= wakeThreshold) {
      if (wakeNeighborsOnly)
        node.sendToNeighbors("computer.start")
      else
        node.sendToReachable("computer.start")
    }
  }

  // ----------------------------------------------------------------------- //

  override def load(nbt: CompoundTag): Unit = {
    super.load(nbt)
    // 1.21.1：`CompoundTag#getInteger` → `getInt`。
    wakeThreshold = nbt.getInt("wakeThreshold")
  }

  override def save(nbt: CompoundTag): Unit = {
    super.save(nbt)
    nbt.putInt("wakeThreshold", wakeThreshold)
  }
}
