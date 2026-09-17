package li.cil.oc.common.item.data

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.common.Tier
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag

class MicrocontrollerData(itemName: String = Constants.BlockName.Microcontroller) extends ItemData(itemName) {
  def this(stack: ItemStack) = {
    this()
    loadData(stack)
  }

  var tier = Tier.One

  var components: Array[ItemStack] = Array[ItemStack](ItemStack.EMPTY)

  var storedEnergy = 0

  private final val TierTag = Settings.namespace + "tier"
  private final val ComponentsTag = Settings.namespace + "components"
  private final val StoredEnergyTag = Settings.namespace + "storedEnergy"

  override def loadData(nbt: CompoundTag): Unit = {
    tier = nbt.getByte(TierTag)
    components = nbt.getList(ComponentsTag, Tag.TAG_COMPOUND).
      toTagArray[CompoundTag].map(ItemStack.parseOptional(li.cil.oc.util.RegistryAccessHelper.getOrEmpty(), _)).filter(!_.isEmpty)
    storedEnergy = nbt.getInt(StoredEnergyTag)

    // Reserve slot for EEPROM if necessary, avoids having to resize the
    // components array in the MCU tile entity, which isn't possible currently.
    if (!components.exists(stack => api.Items.get(stack) == api.Items.get(Constants.ItemName.EEPROM))) {
      components :+= ItemStack.EMPTY
    }
  }

  override def saveData(nbt: CompoundTag): Unit = {
    nbt.putByte(TierTag, tier.toByte)
    nbt.setNewTagList(ComponentsTag, components.filter(!_.isEmpty).toIterable)
    nbt.putInt(StoredEnergyTag, storedEnergy)
  }

  // 1.21.1：`Item#getRarity(ItemStack)` 已移除，品质改为写进 `RARITY` 数据组件；
  // 本物品的品质随堆叠里的 tier 变化，因此每次写数据时一并刷新组件（见 `ItemData.applyRarityComponent`）。
  override protected def applyRarityComponent(stack: ItemStack): Unit = setRarityFromTier(stack, tier)

  def copyItemStack(): ItemStack = {
    val stack = createItemStack()
    val newInfo = new MicrocontrollerData(stack)
    newInfo.saveData(stack)
    stack
  }
}
