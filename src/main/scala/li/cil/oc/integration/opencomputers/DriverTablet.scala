package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.network.Component
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Visibility
import li.cil.oc.common.Slot
import li.cil.oc.common.item.Tablet
import li.cil.oc.common.item.data.TabletData
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraftforge.common.util.Constants.NBT

object DriverTablet extends Item {
  override def worksWith(stack: ItemStack) = isOneOf(stack,
    api.Items.get(Constants.ItemName.Tablet))

  override def createEnvironment(stack: ItemStack, host: EnvironmentHost) =
    if (host.world != null && host.world.isRemote) null
    else {
      Tablet.Server.cache.invalidate(Tablet.getId(stack))
      val data = new TabletData(stack)
      data.items.collect {
        case Some(fs) if DriverFileSystem.worksWith(fs) => fs
      }.headOption.map(DriverFileSystem.createEnvironment(_, host)) match {
        case Some(environment) => environment.node match {
          case component: Component =>
            component.setVisibility(Visibility.Network)
            environment.save(dataTag(stack))
            environment
          case _ => null
        }
        case _ => null
      }
    }

  override def slot(stack: ItemStack) = Slot.Tablet

  override def dataTag(stack: ItemStack) = {
    val data = new TabletData(stack)
    val index = data.items.indexWhere {
      case Some(fs) => DriverFileSystem.worksWith(fs)
      case _ => false
    }
    if (index >= 0 && stack.hasTagCompound && stack.getTagCompound.contains(Settings.namespace + "items")) {
      val baseTag = stack.getTagCompound.getList(Settings.namespace + "items", NBT.TAG_COMPOUND).getCompound(index)
      if (!baseTag.contains("item")) {
        baseTag.put("item", new CompoundTag())
      }
      val itemTag = baseTag.getCompound("item")
      if (!itemTag.contains("tag")) {
        itemTag.put("tag", new CompoundTag())
      }
      val stackTag = itemTag.getCompound("tag")
      if (!stackTag.contains(Settings.namespace + "data")) {
        stackTag.put(Settings.namespace + "data", new CompoundTag())
      }
      stackTag.getCompound(Settings.namespace + "data")
    }
    else new CompoundTag()
  }
}
