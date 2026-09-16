package li.cil.oc.common.datacomponents

import li.cil.oc.Settings
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponentType
import net.minecraft.nbt.CompoundTag

import java.util.function.Supplier

trait Migrator[T] {
  def fromNBT(tag: CompoundTag, provider: HolderLookup.Provider): Option[T]
}

object Migrator {
  trait DataNamespace[T] extends Migrator[T] {
    def fromData(tag: CompoundTag, provider: HolderLookup.Provider): Option[T]

    override def fromNBT(tag: CompoundTag, provider: HolderLookup.Provider): Option[T] = tag.getCompound(Settings.namespace + "data") match {
      case tag: CompoundTag => fromData(tag, provider)
      case _ => None
    }
  }

  def perform[T](ty: DataComponentType[T], nbt: CompoundTag, provider: HolderLookup.Provider): Option[T] = {
    Migrators.map.get(ty) match {
      case Some(migrator) => migrator.fromNBT(nbt, provider) map(_.asInstanceOf[T])
      case None => None
    }
  }
}
