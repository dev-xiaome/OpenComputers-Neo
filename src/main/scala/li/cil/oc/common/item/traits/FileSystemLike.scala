package li.cil.oc.common.item.traits

import java.util

import li.cil.oc.Localization
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.common.GuiType
import li.cil.oc.common.item.data.DriveData
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

trait FileSystemLike extends Delegate {
  override protected def tooltipName = None

  def kiloBytes: Int

  override def tooltipLines(stack: ItemStack, player: Player, tooltip: util.List[String], advanced: Boolean) = {
    if (stack.hasTagCompound) {
      val nbt = stack.getTagCompound
      if (nbt.contains(Settings.namespace + "data")) {
        val data = nbt.getCompound(Settings.namespace + "data")
        if (data.contains(Settings.namespace + "fs.label")) {
          tooltip.add(data.getString(Settings.namespace + "fs.label"))
        }
        if (advanced && data.contains("fs")) {
          val fsNbt = data.getCompound("fs")
          if (fsNbt.contains("capacity.used")) {
            val used = fsNbt.getLong("capacity.used")
            tooltip.add(Localization.Tooltip.DiskUsage(used, kiloBytes * 1024))
          }
        }
      }
      val data = new DriveData(stack)
      tooltip.add(Localization.Tooltip.DiskMode(data.isUnmanaged))
      tooltip.add(Localization.Tooltip.DiskLock(data.lockInfo))
    }
    super.tooltipLines(stack, player, tooltip, advanced)
  }

  override def onItemRightClick(stack: ItemStack, world: Level, player: Player): ItemStack = {
    if (!player.isSneaking && (!stack.hasTagCompound || !stack.getTagCompound.contains(Settings.namespace + "lootFactory"))) {
      player.openGui(OpenComputers, GuiType.Drive.id, world, 0, 0, 0)
      player.swingItem()
    }
    stack
  }
}
