package li.cil.oc.common.capabilities

import li.cil.oc.api.internal.Colored
import li.cil.oc.integration.Mods
import net.minecraft.core.Direction
import net.minecraft.nbt.{CompoundTag, IntTag}
import net.minecraft.world.level.block.entity.BlockEntity
import net.neoforged.neoforge.capabilities.{Capability, ICapabilityProvider, ICapabilitySerializable}
import java.util.Optional
import java.util.function.Supplier
import net.minecraft.resources.ResourceLocation

object CapabilityColored {
  final val ProviderColored = ResourceLocation.fromNamespaceAndPath(Mods.IDs.OpenComputers, "colored")

  class Provider(val tileEntity: BlockEntity with Colored) extends ICapabilitySerializable[CompoundTag] with java.util.function.Supplier[Provider] with Colored {
    private val wrapper = java.util.Optional.of(this)

    override def get = this

    def invalidate() = wrapper.invalidate()

    override def getCapability[T](capability: Capability[T], facing: Direction): java.util.Optional[T] = {
      if (capability == Capabilities.ColoredCapability) wrapper.cast[T]
      else java.util.Optional.empty[T]
    }

    override def getColor = tileEntity.getColor

    override def setColor(value: Int) = tileEntity.setColor(value)

    override def controlsConnectivity = tileEntity.controlsConnectivity

    override def serializeNBT(): CompoundTag = {
      val nbt = new CompoundTag()
      nbt.putInt("color", getColor)
      nbt
    }

    override def deserializeNBT(nbt: CompoundTag) = {
      if (nbt.contains("color")) {
        setColor(nbt.getInt("color"))
      }
    }
  }

  class DefaultImpl extends Colored {
    var color = 0

    override def getColor = color

    override def setColor(value: Int): Unit = color = value

    override def controlsConnectivity = false
  }
}
