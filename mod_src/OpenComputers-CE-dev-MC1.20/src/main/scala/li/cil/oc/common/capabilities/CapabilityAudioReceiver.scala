package li.cil.oc.common.capabilities

import li.cil.oc.api.audio.AudioReceiver
import li.cil.oc.integration.Mods
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3
import net.minecraftforge.common.capabilities.{Capability, ICapabilitySerializable}
import net.minecraftforge.common.util.{LazyOptional, NonNullSupplier}

object CapabilityAudioReceiver {
  final val ProviderAudioReceiver = ResourceLocation.fromNamespaceAndPath(Mods.IDs.OpenComputers, "audio_receiver")

  class Provider(val tileEntity: BlockEntity with AudioReceiver) extends ICapabilitySerializable[CompoundTag] with NonNullSupplier[Provider] with AudioReceiver {
    private val wrapper = LazyOptional.of(this)

    override def get: Provider = this

    def invalidate(): Unit = wrapper.invalidate()

    override def getCapability[T](capability: Capability[T], facing: Direction): LazyOptional[T] = {
      if (capability == Capabilities.AudioReceiverCapability) wrapper.cast[T]
      else LazyOptional.empty[T]
    }

    override def serializeNBT(): CompoundTag = {
      new CompoundTag()
    }

    override def deserializeNBT(nbt: CompoundTag): Unit = {

    }

    override def level(): Level = tileEntity.level()

    override def address(): String = tileEntity.address()

    override def position(): Vec3 = tileEntity.position()

    override def setChanged(): Unit = tileEntity.setChanged()

    override def distance(): Int = tileEntity.distance()
  }
}
