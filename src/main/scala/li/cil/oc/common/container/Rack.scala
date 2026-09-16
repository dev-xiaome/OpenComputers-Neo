package li.cil.oc.common.container

import li.cil.oc.api.component.RackMountable
import li.cil.oc.common.Slot
import li.cil.oc.common.tileentity
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.core.Direction
import net.minecraft.nbt.{CompoundTag, IntArrayTag, Tag}
import net.minecraft.world.entity.player.Inventory

/**
 * 机架容器（原 1.7.10 `container.Rack`）。
 *
 * 除了常规槽位，机架还要把「每个插槽里的节点映射 / 节点存在性 / 中继开关」同步给客户端；
 * 1.7.10 用 `ICrafting#sendProgressBarUpdate` 塞不下这些数据，所以走自定义 NBT 增量
 * （见 [[Player.detectCustomDataChanges]] 与 [[Player.customDataSync]]）。
 *
 * 1.21.1 迁移要点：
 *  - `Container` → `AbstractContainerMenu`（构造器多 `windowId` + [[MenuTypes]] 的 `MenuType`）；
 *  - `NBTTagCompound#hasKey/getInteger` → `contains/getInt`；
 *  - `NBT.TAG_INT_ARRAY`（`net.minecraftforge.common.util.Constants`）→
 *    [[net.minecraft.nbt.Tag.TAG_INT_ARRAY]]；
 *  - `NBTTagIntArray#func_150302_c()` → `getAsIntArray`；
 *  - `ForgeDirection.getOrientation(i)` → `Direction.from3DDataValue(i)`；
 *  - `getSizeInventory` → `getSlots`；`getMountable(slot)` 的返回类型已经是 `RackMountable`。
 */
class Rack(windowId: Int, playerInventory: Inventory, val rack: tileentity.Rack)
  extends Player(windowId, MenuTypes.Rack.value(), playerInventory, rack) {
  addSlotToContainer(20, 23, Slot.RackMountable)
  addSlotToContainer(20, 43, Slot.RackMountable)
  addSlotToContainer(20, 63, Slot.RackMountable)
  addSlotToContainer(20, 83, Slot.RackMountable)
  addPlayerInventorySlots(8, 128)

  final val MaxConnections = 4
  val nodePresence = Array.fill(4)(Array.fill(4)(false))

  override def updateCustomData(nbt: CompoundTag): Unit = {
    super.updateCustomData(nbt)
    nbt.getList("nodeMapping", Tag.TAG_INT_ARRAY).map((sides: IntArrayTag) =>
      sides.getAsIntArray.map(side => if (side >= 0) Option(Direction.from3DDataValue(side)) else None)
    ).copyToArray(rack.nodeMapping)
    nbt.getBooleanArray("nodePresence").grouped(MaxConnections).copyToArray(nodePresence)
    rack.isRelayEnabled = nbt.getBoolean("isRelayEnabled")
  }

  override protected def detectCustomDataChanges(nbt: CompoundTag): Unit = {
    super.detectCustomDataChanges(nbt)
    nbt.setNewTagList("nodeMapping", rack.nodeMapping.map(sides => toNbt(sides.map {
      case Some(side) => side.ordinal()
      case _ => -1
    })).toIndexedSeq)
    nbt.setBooleanArray("nodePresence", (0 until rack.getSlots).flatMap(slot => rack.getMountable(slot) match {
      case mountable: RackMountable =>
        (Seq(true) ++ (0 until math.min(MaxConnections - 1, mountable.getConnectableCount)).
          map(index => mountable.getConnectableAt(index) != null)).padTo(MaxConnections, false)
      case _ => Seq.fill(MaxConnections)(false)
    }).toArray)
    nbt.putBoolean("isRelayEnabled", rack.isRelayEnabled)
  }
}
