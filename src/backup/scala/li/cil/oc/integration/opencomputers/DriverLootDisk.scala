package li.cil.oc.integration.opencomputers
import li.cil.oc.util.ItemStackNBTExtensions._

import java.io

import li.cil.oc.Constants
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.storage.LevelResource
import net.neoforged.neoforge.server.ServerLifecycleHooks

// This is deprecated and kept for compatibility with old saves.
// As of OC 1.5.10, loot disks are generated using normal floppies, and using
// a factory system that allows third-party mods to register loot disks.
object DriverLootDisk extends Item {
  override def worksWith(stack: ItemStack) =
    isOneOf(stack, api.Items.get(Constants.ItemName.LootDisk))

  override def createEnvironment(stack: ItemStack, host: EnvironmentHost) =
    if (!host.world.isClientSide && stack.hasTag()) {
      val lootPath = "loot/" + stack.getTag().getString(Settings.namespace + "lootPath")
      // 1.21.1 没有 `DimensionManager.getCurrentSaveRootDirectory`，改从当前服务端取存档根目录。
      val saveRoot = Option(ServerLifecycleHooks.getCurrentServer).map(_.getWorldPath(LevelResource.ROOT).toFile).orNull
      val savePath = if (saveRoot == null) null else new io.File(saveRoot, Settings.savePath + lootPath)
      val fs =
        if (savePath != null && savePath.exists && savePath.isDirectory) {
          api.FileSystem.fromSaveDirectory(lootPath, 0, false)
        }
        else {
          api.FileSystem.fromClass(OpenComputers.getClass, Settings.resourceDomain, lootPath)
        }
      val label =
        if (dataTag(stack).contains(Settings.namespace + "fs.label")) {
          dataTag(stack).getString(Settings.namespace + "fs.label")
        }
        else null
      api.FileSystem.asManagedEnvironment(fs, label, host, Settings.resourceDomain + ":floppy_access")
    }
    else null

  override def slot(stack: ItemStack) = Slot.Floppy
}