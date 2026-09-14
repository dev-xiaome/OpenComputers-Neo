package li.cil.oc.common.nanomachines.provider

import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.prefab.AbstractBehavior
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3

import scala.jdk.CollectionConverters._

/**
 * 「磁铁」行为：把附近的掉落物吸向玩家。
 *
 * 1.21.1 迁移要点：
 *  - `player.getEntityWorld` → `player.level()`；`World#isRemote` → `Level#isClientSide`
 *  - `world.getEntitiesWithinAABB(clazz, aabb)` → `world.getEntitiesOfClass(clazz, aabb)`
 *    （返回 Java `List`，需 `asScala`）
 *  - `entity.boundingBox` → `getBoundingBox`；`AABB.expand` → `inflate`
 *  - `item.delayBeforeCanPickup < 1` → `!item.hasPickUpDelay`
 *  - `item.getEntityItem` → `item.getItem()`
 *  - `player.inventory.mainInventory` → `player.getInventory().items`（`NonNullList[ItemStack]`）
 *  - `stack.stackSize` → `stack.getCount`；`stack.isItemEqual(other)` →
 *    `ItemStack.isSameItemSameComponents(stack, other)`
 *  - `Vec3.createVectorHelper(dx, dy, dz)` → `new Vec3(dx, dy, dz)`；`xCoord/yCoord/zCoord` → `x/y/z`
 */
object MagnetProvider extends ScalaProvider("9324d5ec-71f1-41c2-b51c-406e527668fc") {
  override def createScalaBehaviors(player: Player) = Iterable(new MagnetBehavior(player))

  override def readBehaviorFromNBT(player: Player, nbt: CompoundTag) = new MagnetBehavior(player)

  class MagnetBehavior(player: Player) extends AbstractBehavior(player) {
    override def getNameHint = "magnet"

    override def update(): Unit = {
      val world = player.level()
      if (!world.isClientSide) {
        val actualRange = Settings.get.nanomachineMagnetRange * api.Nanomachines.getController(player).getInputCount(this)
        val bounds = player.getBoundingBox.inflate(actualRange, actualRange, actualRange)
        val items = world.getEntitiesOfClass(classOf[ItemEntity], bounds).asScala
        items.foreach {
          case item: ItemEntity if isMagnetizable(item) =>
            val dx = player.getX - item.getX
            val dy = player.getY - item.getY
            val dz = player.getZ - item.getZ
            val delta = new Vec3(dx, dy, dz).normalize()
            item.addDeltaMovement(new Vec3(delta.x * 0.1, delta.y * 0.1, delta.z * 0.1))
          case _ =>
        }
      }
    }

    /**
     * 该掉落物是否值得吸：没有拾取延迟、确实装着物品，
     * 且玩家背包里存在「空的或可继续合并的同类堆叠」（与旧版判定一致）。
     */
    private def isMagnetizable(item: ItemEntity): Boolean = {
      val stack = item.getItem
      if (item.hasPickUpDelay || stack == null || stack.isEmpty) return false
      player.getInventory.items.asScala.exists { inSlot =>
        inSlot == null || inSlot.isEmpty ||
          (inSlot.getCount < inSlot.getMaxStackSize && ItemStack.isSameItemSameComponents(inSlot, stack))
      }
    }
  }
}
