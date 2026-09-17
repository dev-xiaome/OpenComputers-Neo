package li.cil.oc.common.item.data

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.common.Tier
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.nbt.{CompoundTag, Tag}
import net.minecraft.world.item.ItemStack

/**
 * 平板电脑数据（原 1.7.10 的 `TabletData`）。
 *
 * 1.21.1 迁移要点：
 *  - `NBT.TAG_COMPOUND` → [[Tag.TAG_COMPOUND]]
 *  - `ListTag#foreach` 由 [[li.cil.oc.util.ExtendedNBT.ExtendedListTag]] 提供
 *  - `ItemStack.loadItemStackFromNBT` → [[StackSerializer.loadItemStack]]
 *  - `nbt.setNewCompoundTag(name, stack.writeToNBT)` 的旧签名要求 `CompoundTag => Unit`，
 *    这里改用 `nbt.put(name, StackSerializer.toTag(stack))`
 */
class TabletData extends ItemData(Constants.ItemName.Tablet) {
  def this(stack: ItemStack) = {
    this()
    load(stack)
  }

  var items: Array[Option[ItemStack]] = Array.fill[Option[ItemStack]](32)(None)
  var isRunning: Boolean = false
  var energy: Double = 0.0
  var maxEnergy: Double = 0.0
  var tier: Int = Tier.One
  var container: Option[ItemStack] = None

  override def load(nbt: CompoundTag): Unit = {
    nbt.getList(Settings.namespace + "items", Tag.TAG_COMPOUND).foreach((slotNbt: CompoundTag) => {
      val slot = slotNbt.getByte("slot")
      if (slot >= 0 && slot < items.length) {
        val stack = StackSerializer.loadItemStack(slotNbt.getCompound("item"))
        items(slot) = if (stack != null && !stack.isEmpty) Option(stack) else None
      }
    })
    isRunning = nbt.getBoolean(Settings.namespace + "isRunning")
    energy = nbt.getDouble(Settings.namespace + "energy")
    maxEnergy = nbt.getDouble(Settings.namespace + "maxEnergy")
    tier = nbt.getInt(Settings.namespace + "tier")
    if (nbt.contains(Settings.namespace + "container")) {
      val stack = StackSerializer.loadItemStack(nbt.getCompound(Settings.namespace + "container"))
      container = if (stack != null && !stack.isEmpty) Option(stack) else None
    }
  }

  override def save(nbt: CompoundTag): Unit = {
    nbt.setNewTagList(Settings.namespace + "items",
      items.zipWithIndex collect {
        case (Some(stack), slot) => (stack, slot)
      } map {
        case (stack, slot) =>
          val slotNbt = new CompoundTag()
          slotNbt.putByte("slot", slot.toByte)
          slotNbt.put("item", StackSerializer.toTag(stack))
          slotNbt
      })
    nbt.putBoolean(Settings.namespace + "isRunning", isRunning)
    nbt.putDouble(Settings.namespace + "energy", energy)
    nbt.putDouble(Settings.namespace + "maxEnergy", maxEnergy)
    nbt.putInt(Settings.namespace + "tier", tier)
    container.foreach(stack => nbt.put(Settings.namespace + "container", StackSerializer.toTag(stack)))
  }
}
