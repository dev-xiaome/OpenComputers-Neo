package li.cil.oc.common.tileentity.traits

import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import net.minecraft.nbt.CompoundTag

trait TextBuffer extends Environment {
  lazy val buffer = {
    val screenItem = api.Items.get(Constants.BlockName.ScreenTier1).createItemStack(1)
    val buffer = api.Driver.driverFor(screenItem, getClass).createEnvironment(screenItem, this).asInstanceOf[api.internal.TextBuffer]
    val (maxWidth, maxHeight) = Settings.screenResolutionsByTier(tier)
    buffer.setMaximumResolution(maxWidth, maxHeight)
    buffer.setMaximumColorDepth(Settings.screenDepthsByTier(tier))
    buffer
  }

  override def node = buffer.node

  def tier: Int

  override def updateEntity(): Unit = {
    super.updateEntity()
    if (isClient || isConnected) {
      buffer.update()
    }
  }

  // ----------------------------------------------------------------------- //

  override def readFromNBTForServer(nbt: CompoundTag) = {
    super.readFromNBTForServer(nbt)
    buffer.load(nbt)
  }

  override def writeToNBTForServer(nbt: CompoundTag) = {
    super.writeToNBTForServer(nbt)
    buffer.save(nbt)
  }

  @SideOnly(Dist.CLIENT)
  override def readFromNBTForClient(nbt: CompoundTag): Unit = {
    super.readFromNBTForClient(nbt)
    buffer.load(nbt)
  }

  override def writeToNBTForClient(nbt: CompoundTag): Unit = {
    super.writeToNBTForClient(nbt)
    buffer.save(nbt)
  }
}
