package li.cil.oc.util

import li.cil.oc.common.datacomponents.Migrator
import li.cil.oc.common.item.data.ItemData
import net.minecraft.core.component.{DataComponentHolder, DataComponentType, DataComponents}
import net.minecraft.world.item.component.CustomData
import net.neoforged.neoforge.common.MutableDataComponentHolder

import java.util.function.Supplier

object ExtendedDataComponentHolder {
  implicit def convert(value: DataComponentHolder): ExtendedDataComponentHolder = new ExtendedDataComponentHolder(value)
  implicit def convert(value: MutableDataComponentHolder): ExtendedMutableDataComponentHolder = new ExtendedMutableDataComponentHolder(value)
}

sealed class ExtendedDataComponentHolder(val holder: DataComponentHolder) {
  protected def tryFallback[T](dataComponent: DataComponentType[T]): Option[T] = None
  
  def getComponent[T](dataComponent: DataComponentType[T]): Option[T] = {
    holder.get(dataComponent) match {
      case null => tryFallback(dataComponent)
      case realValue => Some(realValue)
    }
  }

  def getComponent[T](sup: Supplier[DataComponentType[T]]): Option[T] = getComponent(sup.get())
}

class ExtendedMutableDataComponentHolder(holder: MutableDataComponentHolder) extends ExtendedDataComponentHolder(holder) {
  override protected def tryFallback[T](dataComponent: DataComponentType[T]): Option[T] = if(holder.has(DataComponents.CUSTOM_DATA)) {
    var result: Option[T] = None

    holder.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, (data: CustomData) => {
      data.update(tag => {
        result = Migrator.perform(dataComponent, tag, ItemData.defaultProvider).map { value =>
          holder.set(dataComponent, value)
          value
        }
      })
      
      data
    })

    result
  } else None

  def setComponent(dataComponent: DataComponentType[Unit], value: Boolean): Unit = {
    if(value) {
      holder.set(() => dataComponent, ())
    } else {
      holder.remove(() => dataComponent)
    }
  }

  def setComponent(dataComponent: Supplier[DataComponentType[Unit]], value: Boolean): Unit = {
    if(value) {
      holder.set(dataComponent, ())
    } else {
      holder.remove(dataComponent)
    }
  }
  
  def setComponent[T](dataComponent: DataComponentType[T], value: T): T = {
    holder.set(dataComponent, value)
  }

  def setComponent[T](dataComponent: DataComponentType[T], value: Option[T]): T = {
    value match {
      case Some(value) => holder.set(dataComponent, value)
      case None => holder.remove(dataComponent)
    }
  }
  
  def setComponent[T](dataComponent: Supplier[DataComponentType[T]], value: T): T = {
    holder.set(dataComponent, value)
  }

  def setComponent[T](dataComponent: Supplier[DataComponentType[T]], value: Option[T]): T = {
    value match {
      case Some(value) => holder.set(dataComponent, value)
      case None => holder.remove(dataComponent)
    }
  }

  def removeComponent[T](dataComponent: DataComponentType[T]): T = {
    holder.remove(dataComponent)
  }

  def updateComponent[T, U](dataComponent: DataComponentType[T], defaultValue: T, update: T => U): T = {
    holder.update(dataComponent, defaultValue, (v: T) => {
      update(v)
      v
    })
  }

  def updateComponent[T, U](dataComponent: Supplier[DataComponentType[T]], defaultValue: T, update: T => U): T = {
    holder.update(dataComponent, defaultValue, (v: T) => {
      update(v)
      v
    })
  }
}
