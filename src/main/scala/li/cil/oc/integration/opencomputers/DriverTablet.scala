package li.cil.oc.integration.opencomputers
import li.cil.oc.util.ItemStackNBTExtensions._

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
import net.minecraft.nbt.{CompoundTag, Tag}

object DriverTablet extends Item {
  override def worksWith(stack: ItemStack) = isOneOf(stack,
    api.Items.get(Constants.ItemName.Tablet))

  override def createEnvironment(stack: ItemStack, host: EnvironmentHost) =
    if (host.world != null && host.world.isClientSide) null
    else {
      // TODO(port): 1.7.10 在这里调用 `Tablet.Server.cache.invalidate(Tablet.getId(stack))`
      // 清掉旧的服务端组件缓存。本移植版 `common/item/Tablet.scala` 已降级
      // （只保留 `Tablet.getId` / `TabletData`，没有 `Server` 缓存），因此这一步暂时省略；
      // 等 `Tablet.Server`（`server/component/Tablet.scala` 的缓存层）接回来后在这里补上。
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
    if (index >= 0 && stack.hasTag() && stack.getTag().contains(Settings.namespace + "items")) {
      val baseTag = stack.getTag().getList(Settings.namespace + "items", Tag.TAG_COMPOUND).getCompound(index)
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
