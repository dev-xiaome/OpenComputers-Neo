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
          // 1.21.1：`Level#playAuxSFX(id, x, y, z, data)` 已移除，且物品拾取的
          // level event（1.7.10 的 2003）在新版里已被删除（2003 现在是末影之眼死亡粒子）。
          // 因此这里改为直接播放与拾取时刻相同的音效（原效果也只是给附近玩家播放拾取音 + 粒子）。
          world.playSound(null: net.minecraft.world.entity.player.Player,
            item.getX, item.getY, item.getZ,
            net.minecraft.sounds.SoundEvents.ITEM_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS,
            0.2f, (world.random.nextFloat() - world.random.nextFloat()) * 0.7f + 1.0f)
          return result(true)
        }
      }
      result(false)
    }
  }

  /**
   * 「有玩家」宿主（机器人 / 平板）的吸取实现。
   *
   * 注意：本类是 [[UpgradeTractorBeam]] 的内嵌类，名字 `Player` 会遮蔽
   * `net.minecraft.world.entity.player.Player`，因此这里的函数类型必须写全限定名，
   * 否则会被解析成 `UpgradeTractorBeam.Player` 自己。
   */
  class Player(val owner: EnvironmentHost, val player: () => net.minecraft.world.entity.player.Player) extends Common {
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
