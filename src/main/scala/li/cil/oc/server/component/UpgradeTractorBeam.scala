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
import li.cil.oc.util.ExtendedWorld._
import li.cil.oc.util.InventoryUtils
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.LevelEvent

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

    // 1.21.1：Scala `Map` → `java.util.Map` 需要显式 `asJava`。
    override def getDeviceInfo: util.Map[String, String] = deviceInfo.asJava

    protected def position: BlockPosition

    protected def collectItem(item: ItemEntity): Unit

    private def world = position.world.get

    @Callback(doc = """function():boolean -- Tries to pick up a random item in the robots' vicinity.""")
    def suck(context: Context, args: Arguments): Array[AnyRef] = {
      // 1.21.1：`Level#getEntitiesWithinAABB` → `Level#getEntitiesOfClass`（返回 `java.util.List`，需 `asScala`）；
      // `AABB#expand` → `AABB#inflate`；
      // `Entity#isEntityAlive` → `isAlive`，`Entity#delayBeforeCanPickup` → `ItemEntity#hasPickUpDelay`；
      // `Level#rand` → `Level#random`。
      val items = world.getEntitiesOfClass(classOf[ItemEntity],
        position.bounds.inflate(pickupRadius, pickupRadius, pickupRadius)).asScala
        .filter(item => item != null && item.isAlive && !item.hasPickUpDelay)
      if (items.nonEmpty) {
        val item = items(world.random.nextInt(items.size))
        // 1.21.1：`ItemEntity#getEntityItem` → `getItem`，`ItemStack#stackSize` → `getCount`，
        // `Entity#isDead` → `isRemoved`。
        val stack = item.getItem
        val size = if (stack == null) 0 else stack.getCount
        collectItem(item)
        val remaining = if (stack == null) 0 else stack.getCount
        if (remaining < size || item.isRemoved) {
          context.pause(Settings.get.suckDelay)
          // 1.21.1：`Level#playAuxSFX(id, x, y, z, data)` 已移除，
          // 2003（拾取物品粒子 + 音效）对应 `LevelEvent.SOUND_ITEM_PICKUP` 与 `Level#levelEvent`。
          world.levelEvent(LevelEvent.SOUND_ITEM_PICKUP,
            math.floor(item.getX).toInt, math.floor(item.getY).toInt, math.floor(item.getZ).toInt, 0)
          return result(true)
        }
      }
      result(false)
    }
  }

  class Player(val owner: EnvironmentHost, val player: () => Player) extends Common {
    override protected def position = BlockPosition(owner)

    // 1.21.1：`Entity#onCollideWithPlayer(player)` 重命名为 `playerTouch(player)`。
    override protected def collectItem(item: ItemEntity) = item.playerTouch(player())
  }

  class Drone(val owner: internal.Agent) extends Common {
    override protected def position = BlockPosition(owner)

    override protected def collectItem(item: ItemEntity) = {
      // 1.21.1：`ItemEntity#getEntityItem` → `getItem`。
      InventoryUtils.insertIntoInventory(item.getItem, owner.mainInventory, None, 64, simulate = false, Some(insertionSlots))
    }

    // 1.21.1：`IInventory#getSizeInventory` → `IItemHandler#getSlots`。
    private def insertionSlots = (owner.selectedSlot until owner.mainInventory.getSlots) ++ (0 until owner.selectedSlot)
  }

}
