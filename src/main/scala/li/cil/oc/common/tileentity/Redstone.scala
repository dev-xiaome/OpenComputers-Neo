package li.cil.oc.common.tileentity

import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.network._
import li.cil.oc.api.prefab
import li.cil.oc.common.tileentity.traits.RedstoneChangedEventArgs
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.state.BlockState

import scala.collection.mutable

/**
 * 红石 I/O 方块（对应 1.7.10 的 `common.tileentity.Redstone`）。
 *
 * 节点结构（与 1.7.10 一致）：
 *  - `instance.node`：真正的红石组件节点，`setOutputEnabled` 打开后可见性提到
 *    `Visibility.Network`，计算机才能看到它；
 *  - `dummyNode`：一个 `Visibility.None` 的哑节点，挂在 `instance.node` 下面，
 *    只用来把 `redstone.changed` 事件广播给邻居（避免把事件也发回给红石组件自己）。
 *
 * 纹理：下/上 = RedstoneTop，其它四面 = RedstoneSide。
 *
 * ==1.21.1 迁移要点==
 *  - 构造函数改为 `(pos, state)`，方块实体类型由方块反查（见 [[BlockEntityBase.typeOf]]）。
 *  - `readFromNBTForServer` / `writeToNBTForServer` 在 1.21.1 是 `protected` 钩子。
 *  - `NBTTagCompound#getCompoundTag` → `getCompound`；`setNewCompoundTag` 由
 *    [[li.cil.oc.util.ExtendedNBT]] 提供。
 *
 * ==降级说明==
 * TODO(integration.util.BundledRedstone / server.component): 原实现按
 * `BundledRedstone.isAvailable` 在 `component.Redstone.Bundled` 与 `component.Redstone.Vanilla`
 * 之间二选一，这两个组件（以及 BundledRedstone 的提供者链）都尚未移植。
 * 这里统一退化为 [[Redstone.Placeholder]]：保留 `wakeNeighborsOnly` / `node` / `load` /
 * `save` / `onMessage` 与 `redstone.changed` → `computer.signal` 的信号格式，
 * 只是不再暴露 Lua 侧的 `getInput` / `getOutput` / `setOutput` / `getComparatorInput` 回调。
 * 捆绑红石的 vanilla 输入部分已内联在 [[traits.RedstoneAware]] 里（见其 `computeVanillaRedstoneInput`）。
 */
class Redstone(pos: BlockPos, state: BlockState)
  extends BlockEntityBase(BlockEntityBase.typeOf(state.getBlock), pos, state)
    with traits.Environment with traits.BundledRedstoneAware {

  val instance = new Redstone.Placeholder(this)
  instance.wakeNeighborsOnly = false
  val node = instance.node
  val dummyNode = if (node != null) {
    node.setVisibility(Visibility.Network)
    _isOutputEnabled = true
    api.Network.newNode(this, Visibility.None).create()
  }
  else null

  override def canUpdate: Boolean = isServer

  // ----------------------------------------------------------------------- //

  override def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)
    instance.load(nbt.getCompound(Settings.namespace + "redstone"))
  }

  override def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)
    nbt.setNewCompoundTag(Settings.namespace + "redstone", instance.save)
  }

  // ----------------------------------------------------------------------- //

  override def onRedstoneInputChanged(args: RedstoneChangedEventArgs): Unit = {
    super.onRedstoneInputChanged(args)
    if (node != null && node.network != null) {
      node.connect(dummyNode)
      dummyNode.sendToNeighbors("redstone.changed", args)
    }
  }
}

object Redstone {

  /**
   * 红石组件的**最小占位实现**
   * （原 `li.cil.oc.server.component.Redstone.Vanilla` / `.Bundled`）。
   *
   * 节点形状与 `RedstoneSignaller` 一致：组件名 `redstone`、组件可见性 `Neighbors`、
   * 网络可达性 `Network`；`redstone.changed` 消息会被翻译成 Lua 侧的
   * `computer.signal("redstone_changed", side, old, new[, color])`。
   *
   * TODO(server.component): `li.cil.oc.server.component.Redstone*` 移植完成后删除本类，
   * 并把方块实体的 `instance` 改回按 `BundledRedstone.isAvailable` 二选一。
   */
  private[tileentity] class Placeholder(val redstone: traits.Environment with traits.BundledRedstoneAware)
    extends prefab.ManagedEnvironment {

    override val node: Component = api.Network.newNode(this, Visibility.Network).
      withComponent("redstone", Visibility.Neighbors).
      create()

    var wakeThreshold = 0

    var wakeNeighborsOnly = true

    // --------------------------------------------------------------------- //

    override def onMessage(message: Message): Unit = {
      super.onMessage(message)
      if (message.name == "redstone.changed") message.data match {
        case Array(args: RedstoneChangedEventArgs) =>
          onRedstoneChanged(args)
        case _ =>
      }
    }

    /**
     * 广播红石变化（原 `RedstoneSignaller#onRedstoneChanged`）。
     *
     * 1.21.1 的 `Direction` 没有 `UNKNOWN`（也就是没有「无线来源」这个取值），
     * 因此 `side` 一律是真实方向的序数。
     */
    def onRedstoneChanged(args: RedstoneChangedEventArgs): Unit = {
      val flatArgs = mutable.ArrayBuffer[AnyRef](
        "redstone_changed",
        Int.box(args.side.ordinal),
        Int.box(args.oldValue),
        Int.box(args.newValue))
      if (args.color >= 0) {
        flatArgs += Int.box(args.color)
      }
      node.sendToReachable("computer.signal", flatArgs.toSeq: _*)
      if (args.oldValue < wakeThreshold && args.newValue >= wakeThreshold) {
        if (wakeNeighborsOnly) {
          node.sendToNeighbors("computer.start")
        }
        else {
          node.sendToReachable("computer.start")
        }
      }
    }

    // --------------------------------------------------------------------- //

    override def load(nbt: CompoundTag): Unit = {
      super.load(nbt)
      wakeThreshold = nbt.getInt("wakeThreshold")
    }

    override def save(nbt: CompoundTag): Unit = {
      super.save(nbt)
      nbt.putInt("wakeThreshold", wakeThreshold)
    }
  }
}
