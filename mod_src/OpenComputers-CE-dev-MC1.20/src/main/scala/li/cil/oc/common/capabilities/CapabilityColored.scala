package li.cil.oc.common.capabilities

import li.cil.oc.api.internal.Colored
import li.cil.oc.integration.Mods
import net.minecraft.core.Direction
import net.minecraft.nbt.{CompoundTag, IntTag}
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraftforge.common.capabilities.{Capability, ICapabilityProvider, ICapabilitySerializable}
import net.minecraftforge.common.util.{LazyOptional, NonNullSupplier}
import net.minecraft.resources.ResourceLocation

object CapabilityColored {
  final val ProviderColored = ResourceLocation.fromNamespaceAndPath(Mods.IDs.OpenComputers, "colored")

  class Provider(val tileEntity: BlockEntity with Colored) extends ICapabilitySerializable[CompoundTag] with NonNullSupplier[Provider] with Colored {
    private val wrapper = LazyOptional.of(this)

    override def get = this

    def invalidate() = wrapper.invalidate()

    override def getCapability[T](capability: Capability[T], facing: Direction): LazyOptional[T] = {
      if (capability == Capabilities.ColoredCapability) wrapper.cast[T]
      else LazyOptional.empty[T]
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
