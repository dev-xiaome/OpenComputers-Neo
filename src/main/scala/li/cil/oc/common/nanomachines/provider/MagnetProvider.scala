package li.cil.oc.common.nanomachines.provider

import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.prefab.AbstractBehavior
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.phys.Vec3

import scala.jdk.CollectionConverters._

object MagnetProvider extends ScalaProvider("9324d5ec-71f1-41c2-b51c-406e527668fc") {
  override def createScalaBehaviors(player: Player) = Iterable(new MagnetBehavior(player))

  override def readBehaviorFromNBT(player: Player, nbt: CompoundTag) = new MagnetBehavior(player)

  class MagnetBehavior(player: Player) extends AbstractBehavior(player) {
    override def getNameHint = "magnet"

    override def update(): Unit = {
      val world = player.getEntityWorld
      if (!world.isRemote) {
        val actualRange = Settings.get.nanomachineMagnetRange * api.Nanomachines.getController(player).getInputCount(this)
        val items = world.getEntitiesWithinAABB(classOf[ItemEntity], player.boundingBox.expand(actualRange, actualRange, actualRange))
        items.collect {
          case item: ItemEntity if item.delayBeforeCanPickup < 1 && item.getEntityItem != null && player.inventory.mainInventory.exists(stack => stack == null || stack.stackSize < stack.getMaxStackSize && stack.isItemEqual(item.getEntityItem)) =>
            val dx = player.posX - item.posX
            val dy = player.posY - item.posY
            val dz = player.posZ - item.posZ
            val delta = Vec3.createVectorHelper(dx, dy, dz).normalize()
            item.addVelocity(delta.xCoord * 0.1, delta.yCoord * 0.1, delta.zCoord * 0.1)
        }
      }
    }
  }

}
