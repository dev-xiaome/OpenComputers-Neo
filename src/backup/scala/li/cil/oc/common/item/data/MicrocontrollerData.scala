package li.cil.oc.common.item.data

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.common.Tier
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.nbt.{CompoundTag, Tag}
import net.minecraft.world.item.ItemStack

/**
 * 单片机（Microcontroller）/ 无人机（Drone）的组件数据
 * （原 1.7.10 的 `MicrocontrollerData`）。
 *
 * 1.21.1 迁移要点：
 *  - `NBT.TAG_COMPOUND` → [[Tag.TAG_COMPOUND]]
 *  - `ListTag` 的 `map` / `foreach` 由 [[li.cil.oc.util.ExtendedNBT]] 提供，
 *    但这里为了显式跳过空项，改用 `ExtendedListTag#toArray` 后自行过滤
 *  - `ItemStack.loadItemStackFromNBT` → [[StackSerializer.loadItemStack]]
 *    （1.21.1 的 `parseOptional` 在标签非法时返回空栈，需要显式过滤）
 *  - `tier.toByte` / `putByte` 语义不变
 */
class MicrocontrollerData(itemName: String = Constants.BlockName.Microcontroller) extends ItemData(itemName) {
  def this(stack: ItemStack) = {
    this()
    load(stack)
  }

  var tier: Int = Tier.One

  var components: Array[ItemStack] = Array.empty[ItemStack]

  var storedEnergy: Int = 0

  override def load(nbt: CompoundTag): Unit = {
    tier = nbt.getByte(Settings.namespace + "tier")
    components = StackSerializer.mapList(
      nbt.getList(Settings.namespace + "components", Tag.TAG_COMPOUND),
      StackSerializer.loadItemStack).
      filter(stack => stack != null && !stack.isEmpty).
      toArray
    storedEnergy = nbt.getInt(Settings.namespace + "storedEnergy")

    // 需要时给 EEPROM 预留一格，避免在单片机方块实体里调整组件数组长度。
    val eeprom = api.Items.get(Constants.ItemName.EEPROM)
    if (eeprom != null && !components.exists(stack => api.Items.get(stack) == eeprom)) {
      components :+= ItemStack.EMPTY
    }
  }

  override def save(nbt: CompoundTag): Unit = {
    nbt.putByte(Settings.namespace + "tier", tier.toByte)
    nbt.setNewTagList(Settings.namespace + "components",
      components.filter(stack => stack != null && !stack.isEmpty).map(StackSerializer.toTag).toIterable)
    nbt.putInt(Settings.namespace + "storedEnergy", storedEnergy)
  }

  def copyItemStack(): ItemStack = {
    val stack = createItemStack()
    val newInfo = new MicrocontrollerData(stack)
    newInfo.save(stack)
    stack
  }
}
