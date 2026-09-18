package li.cil.oc.common.entity

import net.minecraft.network.chat.Component
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.{Entity, EntityType, EquipmentSlot, Mob, MoverType}
import net.minecraft.world.item.{ArmorItem, ItemStack}
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn

object TrainRobot {
  private val RobotFlagTag = "RobotFlag"

  /** Any vanilla or modded head armor can be used as the physical hat. */
  def isHat(stack: ItemStack): Boolean = stack.getItem match {
    case armor: ArmorItem => armor.getEquipmentSlot == EquipmentSlot.HEAD
    case _ => false
  }

  def replaceRobot(level: Level, pos: BlockPos, yaw: Float, proxy: li.cil.oc.common.blockentity.RobotProxy): Boolean = {
    val entity = EntityTypes.TRAIN_ROBOT.get().create(level)
    if (entity == null) return false

    // The assembled robot is intentionally consumed; the train robot has no
    // computer or inventory to migrate.
    proxy.robot.node.remove()
    proxy.robot.saveComponents()
    if (!level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3)) return false

    entity.setPos(pos.getX + 0.5, pos.getY, pos.getZ + 0.5)
    entity.setYRot(yaw)
    entity.setYHeadRot(yaw)
    entity.setRobotFlag(proxy.robot.info.flag)
    entity.getPersistentData.putBoolean("TrainHat", true)
    level.addFreshEntity(entity)
    true
  }
}

/**
 * A deliberately inert robot-shaped mob for Create train conductor seats.
 *
 * It is a Mob so vanilla lead interaction can target it. Create only needs a
 * non-player passenger in a conductor seat, so there is no computer, AI, or
 * other robot functionality attached to this entity.
 */
class TrainRobot(selfType: EntityType[TrainRobot], level: Level) extends Mob(selfType, level) with IEntityWithComplexSpawn {
  setNoAi(true)

  private var robotFlag: Option[ResourceLocation] = None

  def flag: Option[ResourceLocation] = robotFlag

  def setRobotFlag(value: Option[ResourceLocation]): Unit = {
    robotFlag = value
  }

  override def writeSpawnData(buffer: RegistryFriendlyByteBuf): Unit = {
    buffer.writeUtf(flag.map(_.toString).getOrElse(""))
  }

  override def readSpawnData(buffer: RegistryFriendlyByteBuf): Unit = {
    val value = buffer.readUtf()
    setRobotFlag(Option(ResourceLocation.tryParse(value)).filter(_ => value.nonEmpty))
  }

  override def readAdditionalSaveData(nbt: CompoundTag): Unit = {
    if (nbt.contains(TrainRobot.RobotFlagTag)) {
      setRobotFlag(Option(ResourceLocation.tryParse(nbt.getString(TrainRobot.RobotFlagTag))))
    }
    else {
      setRobotFlag(None)
    }
  }

  override def addAdditionalSaveData(nbt: CompoundTag): Unit = {
    flag.foreach(value => nbt.putString(TrainRobot.RobotFlagTag, value.toString))
  }

  override protected def registerGoals(): Unit = ()

  override def tick(): Unit = {
    super.tick()
    if (isPassenger) faceMotion()
  }

  private def faceMotion(): Unit = {
    val vehicle = getVehicle
    if (vehicle == null) return

    val delta = vehicle.position().subtract(vehicle.xo, vehicle.yo, vehicle.zo)
    val horizontal = new Vec3(delta.x, 0, delta.z)
    if (horizontal.lengthSqr() > 0.0001) {
      val yaw = (-math.toDegrees(math.atan2(horizontal.x, horizontal.z))).toFloat
      setYRot(yaw)
      setYHeadRot(yaw)
    }
  }

  // No-AI mobs do not pathfind toward a leash holder. Keep the leash attached
  // and use the vanilla movement tick to pull this inert conductor instead.
  override def closeRangeLeashBehaviour(holder: Entity): Unit = moveToward(holder, 0.12)

  override def elasticRangeLeashBehaviour(holder: Entity, distance: Float): Unit = {
    moveToward(holder, math.min(0.45, 0.12 + (distance - 6.0) * 0.05))
  }

  override def leashTooFarBehaviour(): Unit = {
    val holder = getLeashHolder
    if (holder != null) elasticRangeLeashBehaviour(holder, distanceTo(holder))
  }

  private def moveToward(holder: Entity, speed: Double): Unit = {
    val delta = holder.position().subtract(position())
    val horizontal = new Vec3(delta.x, 0, delta.z)
    if (horizontal.lengthSqr() > 0.01) {
      val direction = horizontal.normalize()
      val current = getDeltaMovement
      val pull = direction.scale(math.min(speed, horizontal.length() * 0.5))
      move(MoverType.SELF, pull)
      setDeltaMovement(0, current.y, 0)
      if (!isPassenger) {
        setYRot((math.toDegrees(math.atan2(direction.z, direction.x)) - 90).toFloat)
        setYHeadRot(getYRot)
      }
    } else {
      val current = getDeltaMovement
      setDeltaMovement(0, current.y, 0)
    }
  }

  override def mobInteract(player: net.minecraft.world.entity.player.Player,
                           hand: net.minecraft.world.InteractionHand) =
    net.minecraft.world.InteractionResult.PASS

  override def getName: Component = Component.translatable("entity.oc.TrainRobot")
}
