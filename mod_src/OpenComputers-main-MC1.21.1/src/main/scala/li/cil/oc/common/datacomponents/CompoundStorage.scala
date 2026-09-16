package li.cil.oc.common.datacomponents

import com.mojang.serialization.Codec
import io.netty.buffer.ByteBuf
import net.minecraft.core.component.{DataComponentMap, DataComponentPatch, DataComponentType, DataComponents, PatchedDataComponentMap}
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.component.CustomData
import net.neoforged.neoforge.common.MutableDataComponentHolder

class CompoundStorage(orig: DataComponentMap = DataComponentMap.EMPTY) extends MutableDataComponentHolder {
  private val components: PatchedDataComponentMap = new PatchedDataComponentMap(orig)

  def isEmpty: Boolean = components.isEmpty

  def this(nbt: CompoundTag) = this(DataComponentMap.builder()
    .set(DataComponents.CUSTOM_DATA, CustomData.of(nbt))
    .build())

  override def set[T](componentType: DataComponentType[_ >: T], value: T): T =
    components.set(componentType, value)

  override def remove[T](componentType: DataComponentType[_ <: T]): T =
    components.remove(componentType)

  override def applyComponents(patch: DataComponentPatch): Unit =
    components.applyPatch(patch)

  override def applyComponents(components: DataComponentMap): Unit =
    this.components.setAll(components)

  def andApply(patch: DataComponentPatch): CompoundStorage = {
    applyComponents(patch)
    this
  }

  def toPatch: DataComponentPatch = {
    val builder = DataComponentPatch.builder()
    components.forEach(component => builder.set(component))
    builder.build()
  }

  override def getComponents: DataComponentMap = components
}

object CompoundStorage {
  val EMPTY = new CompoundStorage()

  val CODEC: Codec[CompoundStorage] = DataComponentMap.CODEC.xmap(m => new CompoundStorage(m), c => c.getComponents)
  val OPTION_CODEC: Codec[Option[CompoundStorage]] = CODEC.xmap(m => if(m.isEmpty) None else Some(m), m => m match {
    case Some(value) => value
    case None => CompoundStorage.EMPTY
  })
  val STREAM_CODEC: StreamCodec[RegistryFriendlyByteBuf, CompoundStorage] = DataComponentPatch.STREAM_CODEC
    .map[CompoundStorage](patch => new CompoundStorage().andApply(patch), _.toPatch)
  val OPTION_STREAM_CODEC: StreamCodec[RegistryFriendlyByteBuf, Option[CompoundStorage]] =
    STREAM_CODEC.map(i => Option.when(!i.isEmpty) { i }, _ getOrElse CompoundStorage.EMPTY)
}
