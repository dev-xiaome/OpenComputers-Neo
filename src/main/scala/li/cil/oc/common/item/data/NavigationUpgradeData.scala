package li.cil.oc.common.item.data

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.util.ItemStackNBTExtensions._
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.{ItemStack, MapItem}
import net.minecraft.world.level.Level

/**
 * 导航升级数据（原 1.7.10 的 `NavigationUpgradeData`）。
 *
 * 1.21.1 迁移要点：
 *  - `net.minecraft.init.Items.filled_map` → `net.minecraft.world.item.Items.FILLED_MAP`
 *  - `map.getItem.asInstanceOf[ItemMap].getMapData(map, world)` →
 *    `MapItem.getSavedData(map, world)`（静态方法，可能返回 `null`）
 *  - `ItemStack.loadItemStackFromNBT` / `writeToNBT` → [[StackSerializer]]
 *  - `nbt.setNewCompoundTag(name, writeToNBT)` 的旧签名要求 `CompoundTag => Unit`，
 *    这里直接用 `CompoundTag#put(name, tag)`（`CompoundTag` 是 `Tag` 的子类）
 */
class NavigationUpgradeData extends ItemData(Constants.ItemName.NavigationUpgrade) {
  def this(stack: ItemStack) = {
    this()
    load(stack)
  }

  var map: ItemStack = new ItemStack(net.minecraft.world.item.Items.FILLED_MAP)

  def mapData(world: Level): net.minecraft.world.level.saveddata.maps.MapItemSavedData = try {
    MapItem.getSavedData(map, world)
  }
  catch {
    case _: Throwable => throw new Exception("invalid map")
  }

  def getSize(world: Level): Int = {
    val info = mapData(world)
    if (info == null) throw new Exception("invalid map")
    128 * (1 << info.scale)
  }

  override def load(stack: ItemStack): Unit = {
    if (stack != null && stack.hasTag()) {
      val root = stack.getTag()
      val data = if (root.contains(Settings.namespace + "data")) root.getCompound(Settings.namespace + "data") else root
      load(data)
    }
  }

  override def save(stack: ItemStack): Unit = {
    if (!stack.hasTag()) {
      stack.setTag(new CompoundTag())
    }
    save(stack.getTag())
  }

  override def load(nbt: CompoundTag): Unit = {
    if (nbt.contains(Settings.namespace + "map")) {
      map = StackSerializer.loadItemStack(nbt.getCompound(Settings.namespace + "map"))
    }
  }

  override def save(nbt: CompoundTag): Unit = {
    if (map != null && !map.isEmpty) {
      nbt.put(Settings.namespace + "map", StackSerializer.toTag(map))
    }
  }
}
