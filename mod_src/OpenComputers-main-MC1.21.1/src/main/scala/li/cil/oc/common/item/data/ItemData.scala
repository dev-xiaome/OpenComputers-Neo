package li.cil.oc.common.item.data

import li.cil.oc.api
import li.cil.oc.api.Persistable
import li.cil.oc.util.ClientAccessHelper
import net.minecraft.client.Minecraft
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.{DataComponentHolder, DataComponents}
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.component.CustomData
import net.neoforged.api.distmarker.Dist
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.common.MutableDataComponentHolder
import net.neoforged.neoforge.server.ServerLifecycleHooks

abstract class ItemData(val itemName: String) extends Persistable {
  def createItemStack(provider: HolderLookup.Provider = ItemData.defaultProvider) = {
    if (itemName == null) ItemStack.EMPTY
    else {
      val stack = api.Items.get(itemName).createItemStack(1)
      saveData(stack)
      stack
    }
  }
}

object ItemData {
  def isOnRenderThread = Minecraft.getInstance().isSameThread

  def defaultProvider = if (FMLEnvironment.dist.isClient && isOnRenderThread) {
    ClientAccessHelper.getClientRegistryAccess
  } else {
    ServerLifecycleHooks.getCurrentServer.registryAccess() match {
      case null => FMLEnvironment.dist match {
        case Dist.CLIENT => ClientAccessHelper.getClientRegistryAccess
        case Dist.DEDICATED_SERVER => throw new IllegalStateException("cannot get registry provider before server is initialized!")
      }
      case notNull => notNull
    }
  }
}
