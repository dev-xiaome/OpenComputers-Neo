package li.cil.oc.integration.computercraft

import dan200.computercraft.api.filesystem.{Mount, WritableMount}
import dan200.computercraft.api.media.IMedia
import li.cil.oc
import li.cil.oc.Settings
import li.cil.oc.api.fs.{FileSystem, Label}
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.Slot
import li.cil.oc.common.datacomponents.OCComponents
import li.cil.oc.integration.opencomputers.Item
import li.cil.oc.util.ExtendedDataComponentHolder._
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponentHolder
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.common.MutableDataComponentHolder

import java.util.UUID

object DriverComputerCraftMedia extends Item {
  override def worksWith(stack: ItemStack) = stack.getItem.isInstanceOf[IMedia]

  override def createEnvironment(stack: ItemStack, host: EnvironmentHost) = if (!host.getEnvironmentLevel.isClientSide) {
    val address = getOrCreateAddress(stack)
    val mount = fromComputerCraft(stack.getItem.asInstanceOf[IMedia].createDataMount(stack, host.getEnvironmentLevel.asInstanceOf[ServerLevel]))
    Option(oc.api.FileSystem.asManagedEnvironment(mount, new ComputerCraftLabel(stack), host, Settings.resourceDomain + ":floppy_access")) match {
      case Some(environment) =>
        environment.node.asInstanceOf[oc.server.network.Node].address = address
        environment
      case _ => null
    }
  } else null

  def fromComputerCraft(mount: AnyRef): FileSystem = DriverComputerCraftMedia.createFileSystem(mount).orNull

  override def slot(stack: ItemStack) = Slot.Floppy

  def createFileSystem(mount: AnyRef) = Option(mount) collect {
    case rw: WritableMount => new ComputerCraftWritableFileSystem(rw)
    case ro: Mount => new ComputerCraftFileSystem(ro)
  }

  private def getOrCreateAddress(stack: ItemStack): String = {
    stack.getComponent(OCComponents.ADDRESS) match {
      case Some(value) => value
      case None => UUID.randomUUID().toString
    }
  }

  class ComputerCraftLabel(val stack: ItemStack) extends Label {
    val media = stack.getItem.asInstanceOf[IMedia]

    override def getLabel(provider: HolderLookup.Provider): String = media.getLabel(provider, stack)

    override def setLabel(value: String): Unit = {
      media.setLabel(stack, value)
    }

    override def loadData(holder: DataComponentHolder): Unit = {}

    override def saveData(holder: MutableDataComponentHolder): Unit = {}
  }
}
