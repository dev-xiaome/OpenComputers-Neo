package li.cil.oc.server.component

import java.util
import java.util.UUID
import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.OpenComputers
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.internal
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab
import li.cil.oc.api.prefab.AbstractManagedEnvironment
import li.cil.oc.common.EventHandler
import li.cil.oc.common.datacomponents.OCComponents
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedArguments._
import li.cil.oc.util.ExtendedNBT._
import li.cil.oc.util.ExtendedDataComponentHolder._
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponentHolder
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Mob
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.StringTag

import scala.collection.convert.ImplicitConversionsToJava._
import scala.collection.convert.ImplicitConversionsToScala._
import scala.collection.mutable
import net.minecraft.nbt.Tag
import net.neoforged.neoforge.common.MutableDataComponentHolder

class UpgradeLeash(val host: Entity with internal.Drone) extends AbstractManagedEnvironment with traits.LevelAware with DeviceInfo {
  override val node = Network.newNode(this, Visibility.Network).
    withComponent("leash").
    create()

  final val MaxLeashedEntities = 8

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Generic,
    DeviceAttribute.Description -> "Leash",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "FlockControl (FC-3LS)",
    DeviceAttribute.Capacity -> MaxLeashedEntities.toString
  )

  override def getDeviceInfo: util.Map[String, String] = deviceInfo

  val leashedEntities = mutable.Set.empty[UUID]

  override def position = BlockPosition(host.asInstanceOf[Entity])

  @Callback(doc = """function(side:number):boolean -- Tries to put an entity on the specified side of the device onto a leash.""")
  def leash(context: Context, args: Arguments): Array[AnyRef] = {
    if (leashedEntities.size >= MaxLeashedEntities) return result((), "too many leashed entities")
    val side = args.checkSideAny(0)
    val nearBounds = position.bounds
    val farBounds = nearBounds.move(side.getStepX * 2.0, side.getStepY * 2.0, side.getStepZ * 2.0)
    val bounds = nearBounds.minmax(farBounds)
    entitiesInBounds[Mob](classOf[Mob], bounds).find(_.canBeLeashed()) match {
      case Some(entity) =>
        if (shrinkLeash()) {
          entity.setLeashedTo(host, false)
          leashedEntities += entity.getUUID
          context.pause(0.1)
          result(true)
        }
        else {
          result(false, "no lead in inventory")
        }
      case _ => result(false, "no unleashed entity")
    }
  }

  @Callback(doc = """function() -- Unleashes all currently leashed entities.""")
  def unleash(context: Context, args: Arguments): Array[AnyRef] = {
    unleashAll()
    null
  }

  override def onDisconnect(node: Node): Unit = {
    super.onDisconnect(node)
    if (node == this.node) {
      unleashAll()
    }
  }

  private def unleashAll(): Unit = {
    entitiesInBounds(classOf[Mob], position.bounds.inflate(5, 5, 5)).foreach(entity => {
      if (leashedEntities.contains(entity.getUUID) && entity.getLeashHolder == host) {
        if (returnLeash()) {
          entity.dropLeash(true, false)
          leashedEntities -= entity.getUUID
        }
      }
    })
    leashedEntities.clear()
  }

  private def shrinkLeash(): Boolean = {
    val inventory = host.mainInventory()
    for (index <- 0 until inventory.getContainerSize) {
      val stack = inventory.getItem(index)
      if (!stack.isEmpty && stack.getItem == Items.LEAD) {
        stack.shrink(1)
        if (stack.isEmpty) {
          inventory.setItem(index, ItemStack.EMPTY)
        }
        return true
      }
    }
    false
  }

  private def returnLeash(): Boolean = {
    val inventory = host.mainInventory()

    for (index <- 0 until inventory.getContainerSize) {
      val stack = inventory.getItem(index)
      if (!stack.isEmpty && stack.getItem == Items.LEAD && stack.getCount < stack.getMaxStackSize) {
        stack.grow(1)
        return true
      }
    }

    for (index <- 0 until inventory.getContainerSize) {
      if (inventory.getItem(index).isEmpty) {
        inventory.setItem(index, new ItemStack(Items.LEAD))
        return true
      }
    }

    false
  }

  override def loadData(holder: DataComponentHolder): Unit = {
    super.loadData(holder)
    for(entities <- holder.getComponent(OCComponents.LEASHED_ENTITIES))
      leashedEntities ++= entities
    // Re-acquire leashed entities. Need to do this manually because leashed
    // entities only remember their leashee if it's an LivingEntity...
    EventHandler.scheduleServer(() => {
      val foundEntities = mutable.Set.empty[UUID]
      entitiesInBounds(classOf[Mob], position.bounds.inflate(5, 5, 5)).foreach(entity => {
        if (leashedEntities.contains(entity.getUUID)) {
          entity.setLeashedTo(host, false)
          foundEntities += entity.getUUID
        }
      })
      val missing = leashedEntities.diff(foundEntities)
      if (missing.nonEmpty) {
        OpenComputers.log.info(s"Could not find ${missing.size} leashed entities after loading!")
        leashedEntities --= missing
      }
    })
  }

  override def saveData(holder: MutableDataComponentHolder): Unit = {
    super.saveData(holder)
    holder.setComponent(OCComponents.LEASHED_ENTITIES, leashedEntities.toList)
  }
}
