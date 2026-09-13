package li.cil.oc.server.component

import java.util

import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.Settings
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.internal
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.InventoryUtils
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player

import scala.jdk.CollectionConverters._
import scala.jdk.CollectionConverters._

object UpgradeTractorBeam {

  abstract class Common extends prefab.ManagedEnvironment with DeviceInfo {
    override val node = Network.newNode(this, Visibility.Network).
      withComponent("tractor_beam").
      create()

    private val pickupRadius = 3

    private final lazy val deviceInfo = Map(
      DeviceAttribute.Class -> DeviceClass.Generic,
      DeviceAttribute.Description -> "Tractor beam",
      DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
      DeviceAttribute.Product -> "T313-K1N.3515"
    )

    override def getDeviceInfo: util.Map[String, String] = deviceInfo

    protected def position: BlockPosition

    protected def collectItem(item: ItemEntity): Unit

    private def world = position.world.get

    @Callback(doc = """function():boolean -- Tries to pick up a random item in the robots' vicinity.""")
    def suck(context: Context, args: Arguments): Array[AnyRef] = {
      val items = world.getEntitiesWithinAABB(classOf[ItemEntity], position.bounds.expand(pickupRadius, pickupRadius, pickupRadius))
        .map(_.asInstanceOf[ItemEntity])
        .filter(item => item.isEntityAlive && item.delayBeforeCanPickup <= 0)
      if (items.nonEmpty) {
        val item = items(world.rand.nextInt(items.size))
        val stack = item.getEntityItem
        val size = stack.stackSize
        collectItem(item)
        if (stack.stackSize < size || item.isDead) {
          context.pause(Settings.get.suckDelay)
          world.playAuxSFX(2003, math.floor(item.posX).toInt, math.floor(item.posY).toInt, math.floor(item.posZ).toInt, 0)
          return result(true)
        }
      }
      result(false)
    }
  }

  class Player(val owner: EnvironmentHost, val player: () => Player) extends Common {
    override protected def position = BlockPosition(owner)

    override protected def collectItem(item: ItemEntity) = item.onCollideWithPlayer(player())
  }

  class Drone(val owner: internal.Agent) extends Common {
    override protected def position = BlockPosition(owner)

    override protected def collectItem(item: ItemEntity) = {
      InventoryUtils.insertIntoInventory(item.getEntityItem, owner.mainInventory, None, 64, simulate = false, Some(insertionSlots))
    }

    private def insertionSlots = (owner.selectedSlot until owner.mainInventory.getSizeInventory) ++ (0 until owner.selectedSlot)
  }

}
