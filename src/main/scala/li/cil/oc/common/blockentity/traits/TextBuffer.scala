package li.cil.oc.common.blockentity.traits

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.internal
import li.cil.oc.api.network.Node
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.minecraft.nbt.CompoundTag

trait TextBuffer extends Environment with Tickable {
  lazy val buffer: internal.TextBuffer = {
    val screenItem = api.Items.get(Constants.BlockName.ScreenTier1).createItemStack(1)
    val driver = Option(api.Driver.driverFor(screenItem, getClass))
      .orElse(Option(api.Driver.driverFor(screenItem)))
      .getOrElse {
        li.cil.oc.OpenComputers.log.error(
          s"[OC-DIAG] 屏幕 driver 查找失败：stack=$screenItem itemInfo=${api.Items.get(screenItem)} " +
            s"host=${getClass.getName} 已注册 item driver 数=${api.Driver.itemDrivers.size}")
        throw new IllegalStateException("No item driver found for the screen item.")
      }
    val buffer = driver.createEnvironment(screenItem, this).asInstanceOf[api.internal.TextBuffer]
    val (maxWidth, maxHeight) = Settings.screenResolutionsByTier(tier)
    buffer.setMaximumResolution(maxWidth, maxHeight)
    buffer.setMaximumColorDepth(Settings.screenDepthsByTier(tier))
    buffer
  }

  override def node: Node = buffer.node

  def tier: Int

  override def updateEntity(): Unit = {
    super.updateEntity()
    if (isClient || isConnected) {
      buffer.update()
    }
  }

  // ----------------------------------------------------------------------- //

  private def reapplyTierToBuffer(): Unit = {
    // Re-apply tier-based limits before loading data. This guards against the
    // case where the `buffer` lazy val was forced before `load(nbt)` ran
    // (e.g. during network join scheduled by initialize()), which would leave
    // it initialised with the default tier-0 (OneBit) depth even for a
    // higher-tier screen.  setMaximumColorDepth only updates the `maxDepth`
    // field; it does NOT touch the already-constructed data buffer, so calling
    // it again here is safe and idempotent.
    val (maxWidth, maxHeight) = Settings.screenResolutionsByTier(tier)
    buffer.setMaximumResolution(maxWidth, maxHeight)
    buffer.setMaximumColorDepth(Settings.screenDepthsByTier(tier))
  }

  override def loadForServer(nbt: CompoundTag): Unit = {
    super.loadForServer(nbt)
    reapplyTierToBuffer()
    buffer.loadData(nbt)
  }

  override def saveForServer(nbt: CompoundTag): Unit = {
    super.saveForServer(nbt)
    buffer.saveData(nbt)
  }

  override def loadForClient(nbt: CompoundTag): Unit = {
    super.loadForClient(nbt)
    reapplyTierToBuffer()
    buffer.loadData(nbt)
  }

  override def saveForClient(nbt: CompoundTag): Unit = {
    super.saveForClient(nbt)
    buffer.saveData(nbt)
  }
}
