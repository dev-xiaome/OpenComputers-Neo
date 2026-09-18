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
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.storage.LevelResource
import net.neoforged.neoforge.server.ServerLifecycleHooks

// This is deprecated and kept for compatibility with old saves.
// As of OC 1.5.10, loot disks are generated using normal floppies, and using
// a factory system that allows third-party mods to register loot disks.
object DriverLootDisk extends Item {
  override def worksWith(stack: ItemStack) = isOneOf(stack,
    api.Items.get(Constants.ItemName.Floppy)) &&
    (stack.hasTag && stack.getTag.contains(Settings.namespace + "lootPath"))

  override def createEnvironment(stack: ItemStack, host: EnvironmentHost) =
    if (!host.getEnvironmentLevel.isClientSide && stack.hasTag && ServerLifecycleHooks.getCurrentServer != null) {
      val lootName = stack.getTag.getString(Settings.namespace + "lootPath")
      // 存档路径带 `opencomputers/` 前缀，**资源路径不能带** —— 否则会去找
      // `assets/<ns>/opencomputers/loot/<name>`，那里永远不存在，软盘的文件系统就成了空的，
      // BIOS 自然会报「找不到 /init.lua」。
      val lootPath = Settings.savePath + "loot/" + lootName
      val savePath = ServerLifecycleHooks.getCurrentServer.getWorldPath(new LevelResource(lootPath)).toFile
      val fs =
        if (savePath.exists && savePath.isDirectory) {
          api.FileSystem.fromSaveDirectory(lootPath, 0, false)
        }
        else {
          // 1.21.1 的 ResourceLocation 构造函数已私有化，改用静态工厂。
          api.FileSystem.fromResource(ResourceLocation.fromNamespaceAndPath(Settings.resourceDomain, "loot/" + lootName))
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