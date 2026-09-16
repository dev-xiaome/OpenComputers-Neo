package li.cil.oc.common.capabilities

import li.cil.oc.api.internal.Colored
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.BlockEntity

// NeoForge 1.21.1: The old ICapabilityProvider/ICapabilitySerializable/LazyOptional system
// has been removed. Capabilities are now registered via RegisterCapabilitiesEvent using
// BlockCapability/ItemCapability. Providers are lambdas registered per block entity type.
// This object is kept for the DefaultImpl helper class.
object CapabilityColored {

  class DefaultImpl extends Colored {
    var color = 0

    override def getColor: Int = color

    override def setColor(value: Int): Unit = color = value

    override def controlsConnectivity: Boolean = false
  }
}
