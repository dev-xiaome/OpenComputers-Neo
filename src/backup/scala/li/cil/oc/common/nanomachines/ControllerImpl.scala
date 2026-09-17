package li.cil.oc.common.nanomachines

import java.lang
import java.util.UUID

import com.google.common.base.Charsets
import com.google.common.base.Strings
import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.nanomachines.Behavior
import li.cil.oc.api.nanomachines.Controller
import li.cil.oc.api.nanomachines.DisableReason
import li.cil.oc.api.network.Packet
import li.cil.oc.api.network.WirelessEndpoint
import li.cil.oc.common.Tier
import li.cil.oc.common.item.data.NanomachineData
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedNBT._
import li.cil.oc.util.InventoryUtils
import li.cil.oc.util.PlayerUtils
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.{MobEffectInstance, MobEffects}
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level

import scala.jdk.CollectionConverters._
import scala.collection.mutable

/**
 * 纳米机器控制器实现（对应 1.7.10 的 `common.nanomachines.ControllerImpl`）。
 *
 * 1.21.1 迁移要点：
 *  - `EntityPlayer#getEntityWorld` → `Player#level()`；`World#isRemote` → `Level#isClientSide`
 *  - 维度不再有数字 id：`worldObj.provider.dimensionId` → `level.dimension()`（[[ResourceKey]]），
 *    因此无线网络的「离开旧维度」无法再用 [[li.cil.oc.api.Network#leaveWirelessNetwork(WirelessEndpoint, int)]]，
 *    改为 `updateWirelessNetwork` 让网络自行按当前维度重新登记（详见 `update()` 内注释）。
 *  - `player.inventory` → `player.getInventory()`；`getFoodStats` → `getFoodData`；
 *    `getAge` → `tickCount`；`capabilities.isCreativeMode` → `isCreative`；
 *    `attackEntityFrom` → `hurt`；`isDead` → `!isAlive()`；`getTotalWorldTime` → `getGameTime`。
 *  - `addPotionEffect(new PotionEffect(Potion.x.id, n))` → `addEffect(new MobEffectInstance(MobEffects.X, n))`
 *  - 伤害来源：见 [[DamageSourceWithRandomCause]]。
 *  - 配置同步改为 [[NanomachinePacketSender]]（`server.PacketSender` 尚未移植）。
 */
class ControllerImpl(val player: Player) extends Controller with WirelessEndpoint {
  if (isServer) api.Network.joinWirelessNetwork(this)

  var previousDimension: ResourceKey[Level] = player.level().dimension()

  lazy val CommandRange = Settings.get.nanomachinesCommandRange * Settings.get.nanomachinesCommandRange
  final val FullSyncInterval = 20 * 60

  final val OverloadDamage = DamageSourceWithRandomCause(player.level(), "oc.nanomachinesOverload", 3).
    setDamageBypassesArmor().
    setDamageIsAbsolute()

  var uuid = UUID.randomUUID.toString
  var responsePort = 0
  var commandDelay = 0
  var queuedCommand: Option[() => Unit] = None
  var storedEnergy = Settings.get.bufferNanomachines * 0.25
  var hadPower = true
  val configuration = new NeuralNetwork(this)
  val activeBehaviors = mutable.Set.empty[Behavior]
  var activeBehaviorsDirty = true
  var hasSentConfiguration = false

  override def world: Level = player.level()

  override def x: Int = BlockPosition(player).x

  override def y: Int = BlockPosition(player).y

  override def z: Int = BlockPosition(player).z

  override def receivePacket(packet: Packet, sender: WirelessEndpoint): Unit = {
    if (getLocalBuffer > 0 && commandDelay < 1 && player.isAlive) {
      val (dx, dy, dz) = ((sender.x + 0.5) - player.getX, (sender.y + 0.5) - player.getY, (sender.z + 0.5) - player.getZ)
      val dSquared = Math.sqrt(dx * dx + dy * dy + dz * dz)
      if (dSquared <= CommandRange) packet.data.headOption match {
        case Some(header: Array[Byte]) if new String(header, Charsets.UTF_8) == "nanomachines" =>
          val command = packet.data.drop(1).map {
            case value: Array[Byte] => new String(value, Charsets.UTF_8)
            case value => value
          }
          command match {
            case Array("setResponsePort", port: java.lang.Number) =>
              responsePort = port.intValue max 0 min 0xFFFF
              respond(sender, "port", responsePort)
            case Array("getPowerState") =>
              respond(sender, "power", getLocalBuffer, getLocalBufferSize)
            case Array("saveConfiguration") =>
              val nanomachines = api.Items.get(Constants.ItemName.Nanomachines)
              try {
                val inventory = player.getInventory
                val index = inventory.items.asScala.indexWhere(stack =>
                  stack != null && !stack.isEmpty && api.Items.get(stack) == nanomachines &&
                    new NanomachineData(stack).configuration.isEmpty)
                if (index >= 0) {
                  // 1.21.1：`decrStackSize` → `removeItem`；`addItemStackToInventory` → `add`。
                  val stack = inventory.removeItem(index, 1)
                  // 把控制器当前的神经连接图写入这张纳米机器物品，之后它就可以被「刷写」到其它玩家身上。
                  // 旧版用 `new NanomachineData(this)` 辅助构造器；这里直接组装数据，语义完全一致
                  // （`NanomachineData` 的字段写入格式不变，因此存档 / 物品数据保持兼容）。
                  val data = new NanomachineData()
                  data.uuid = uuid
                  val configurationNbt = new CompoundTag()
                  configuration.save(configurationNbt, forItem = true)
                  data.configuration = Option(configurationNbt)
                  data.save(stack)
                  if (!inventory.add(stack)) {
                    InventoryUtils.spawnStackInWorld(BlockPosition(player), stack)
                  }
                  respond(sender, "saved", true)
                }
                else respond(sender, "saved", false, "no nanomachines")
              }
              catch {
                case _: Throwable =>
                  respond(sender, "saved", false, "error")
              }
            case Array("getHealth") =>
              respond(sender, "health", player.getHealth, player.getMaxHealth)
            case Array("getHunger") =>
              respond(sender, "hunger", player.getFoodData.getFoodLevel, player.getFoodData.getSaturationLevel)
            case Array("getAge") =>
              respond(sender, "age", (player.tickCount / 20f).toInt)
            case Array("getName") =>
              respond(sender, "name", player.getName.getString)
            case Array("getExperience") =>
              respond(sender, "experience", player.experienceLevel)

            case Array("getTotalInputCount") =>
              respond(sender, "totalInputCount", getTotalInputCount)
            case Array("getSafeActiveInputs") =>
              respond(sender, "safeActiveInputs", getSafeActiveInputs)
            case Array("getMaxActiveInputs") =>
              respond(sender, "maxActiveInputs", getMaxActiveInputs)
            case Array("getInput", index: java.lang.Number) =>
              try {
                val trigger = getInput(index.intValue - 1)
                respond(sender, "input", index.intValue, trigger)
              }
              catch {
                case _: Throwable =>
                  respond(sender, "input", "error")
              }
            case Array("setInput", index: java.lang.Number, value: java.lang.Boolean) =>
              try {
                if (setInput(index.intValue - 1, value.booleanValue)) {
                  respond(sender, "input", index.intValue, getInput(index.intValue - 1))
                }
                else {
                  respond(sender, "input", "too many active inputs")
                }
              }
              catch {
                case _: Throwable =>
                  respond(sender, "input", "error")
              }
            case Array("getActiveEffects") =>
              configuration.synchronized {
                val names = getActiveBehaviors.asScala.map(_.getNameHint).filterNot(Strings.isNullOrEmpty)
                val joined = "{" + names.map(_.replace(',', '_').replace('"', '_')).mkString(",") + "}"
                respond(sender, "effects", joined)
              }
            case _ => // 其它指令忽略。
          }
        case _ => // 不是发给我们的。
      }
    }
  }

  def respond(endpoint: WirelessEndpoint, data: Any*): Unit = {
    queuedCommand = Option(() => {
      if (responsePort > 0) {
        val cost = Settings.get.wirelessCostPerRange(Tier.Two) * CommandRange
        val epsilon = 0.1
        if (changeBuffer(-cost) > -epsilon) {
          val packet = api.Network.newPacket(uuid, null, responsePort, (Iterable("nanomachines") ++ data.map(_.asInstanceOf[AnyRef])).toArray)
          api.Network.sendWirelessPacket(this, CommandRange, packet)
        }
      }
    })
    commandDelay = (Settings.get.nanomachinesCommandDelay * 20).toInt
  }

  // ----------------------------------------------------------------------- //

  override def reconfigure() = {
    if (isServer) configuration.synchronized {
      configuration.reconfigure()
      activeBehaviorsDirty = true

      player match {
        case playerMP: ServerPlayer if playerMP.connection != null =>
          // 1.7.10：失明 100 tick、中毒 150 tick、缓慢 200 tick。
          player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100))
          player.addEffect(new MobEffectInstance(MobEffects.POISON, 150))
          player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200))
          changeBuffer(-Settings.get.nanomachineReconfigureCost)

          hasSentConfiguration = false
        case _ => // 仍在初始化 / 读取存档。
      }
    }
    this
  }

  override def getTotalInputCount: Int = configuration.synchronized(configuration.triggers.length)

  override def getSafeActiveInputs: Int = Settings.get.nanomachinesSafeInputsActive

  override def getMaxActiveInputs: Int = Settings.get.nanomachinesMaxInputsActive

  override def getInput(index: Int): Boolean = configuration.synchronized(configuration.triggers(index).isActive)

  override def setInput(index: Int, value: Boolean): Boolean = {
    isServer && configuration.synchronized {
      (!value || configuration.triggers.count(_.isActive) < Settings.get.nanomachinesMaxInputsActive) && {
        configuration.triggers(index).isActive = value
        activeBehaviorsDirty = true
        true
      }
    }
  }

  override def getActiveBehaviors: lang.Iterable[Behavior] = configuration.synchronized {
    cleanActiveBehaviors(DisableReason.InputChanged)
    activeBehaviors.asJava
  }

  override def getInputCount(behavior: Behavior): Int = configuration.synchronized(configuration.inputs(behavior))

  // ----------------------------------------------------------------------- //

  override def getLocalBuffer: Double = storedEnergy

  override def getLocalBufferSize: Double = Settings.get.bufferNanomachines

  override def changeBuffer(delta: Double): Double = {
    if (isClient) delta
    else if (delta < 0 && (Settings.get.ignorePower || player.isCreative)) 0.0
    else {
      val newValue = storedEnergy + delta
      storedEnergy = math.min(math.max(newValue, 0), getLocalBufferSize)
      newValue - storedEnergy
    }
  }

  // ----------------------------------------------------------------------- //

  def update(): Unit = {
    if (!player.isAlive) {
      return
    }

    if (isServer) {
      if (commandDelay > 0) {
        commandDelay -= 1
        if (commandDelay == 0) {
          queuedCommand.foreach(_ ())
          queuedCommand = None
        }
      }

      // 处理维度切换。旧版这里用数字维度 id 做「先离开旧维度、再进入新维度」，
      // 1.21.1 的 `leaveWirelessNetwork(endpoint, dimension)` 仍要求数字 id，
      // 而数字维度 id 已被移除，因此改为直接 update：无线网络会按当前维度重新登记。
      if (player.level().dimension() != previousDimension) {
        api.Network.updateWirelessNetwork(this)
        previousDimension = player.level().dimension()
      }
      else {
        api.Network.updateWirelessNetwork(this)
      }
    }

    var hasPower = getLocalBuffer > 0 || Settings.get.ignorePower
    lazy val active = getActiveBehaviors.asScala // 只包装一次。
    lazy val activeInputs = configuration.triggers.count(_.isActive)

    if (hasPower != hadPower) {
      if (!hasPower) {
        active.foreach(_.onDisable(DisableReason.OutOfEnergy)) // 这一步可能改变能量缓冲。
        hasPower = getLocalBuffer > 0 || Settings.get.ignorePower
      }
      else active.foreach(_.onEnable())
    }

    if (hasPower) {
      active.foreach(_.update())

      if (isServer) {
        if (player.level().getGameTime % Settings.get.tickFrequency == 0) {
          changeBuffer(-Settings.get.nanomachineCost * Settings.get.tickFrequency * (activeInputs + 0.5))
          NanomachinePacketSender.sendNanomachinePower(player)
        }

        val overload = activeInputs - getSafeActiveInputs
        if (!player.isCreative && overload > 0 && player.level().getGameTime % 20 == 0) {
          player.hurt(OverloadDamage, overload.toFloat)
        }
      }

      if (isClient && Settings.get.enableNanomachinePfx) {
        val energyRatio = getLocalBuffer / (getLocalBufferSize + 1)
        val triggerRatio = activeInputs / (configuration.triggers.length + 1)
        val intensity = (energyRatio + triggerRatio) * 0.25
        PlayerUtils.spawnParticleAround(player, "portal", intensity)
      }
    }

    if (isServer) {
      // 供电状态变化时通知客户端。
      if (hadPower != hasPower) {
        NanomachinePacketSender.sendNanomachinePower(player)
      }

      // 定期做一次全量同步，例如刚靠近、没能收到初始信息的玩家。
      if (!hasSentConfiguration || player.level().getGameTime % FullSyncInterval == 0) {
        hasSentConfiguration = true
        NanomachinePacketSender.sendNanomachineConfiguration(player)
      }
    }

    hadPower = hasPower
  }

  def reset(): Unit = {
    configuration.synchronized {
      for (index <- 0 until getTotalInputCount) {
        configuration.triggers(index).isActive = false
        activeBehaviorsDirty = true
      }
      cleanActiveBehaviors(DisableReason.Default)
    }
  }

  def dispose(): Unit = {
    reset()
    if (isServer) {
      api.Network.leaveWirelessNetwork(this)
    }
  }

  def debug(): Unit = {
    if (isServer) {
      configuration.debug()
      activeBehaviorsDirty = true
    }
  }

  def print(): Unit = {
    if (isServer) {
      configuration.print(player)
    }
  }

  // ----------------------------------------------------------------------- //

  def save(nbt: CompoundTag): Unit = configuration.synchronized {
    nbt.putString("uuid", uuid)
    nbt.putInt("port", responsePort)
    nbt.putDouble("energy", storedEnergy)
    nbt.setNewCompoundTag("configuration", configuration.save)
  }

  def load(nbt: CompoundTag): Unit = configuration.synchronized {
    uuid = nbt.getString("uuid")
    responsePort = nbt.getInt("port")
    storedEnergy = nbt.getDouble("energy")
    configuration.load(nbt.getCompound("configuration"))
    activeBehaviorsDirty = true
  }

  // ----------------------------------------------------------------------- //

  private def isClient = world.isClientSide

  private def isServer = !isClient

  private def cleanActiveBehaviors(reason: DisableReason): Unit = {
    if (activeBehaviorsDirty) {
      configuration.synchronized(if (activeBehaviorsDirty) {
        val newBehaviors = configuration.behaviors.filter(_.isActive).map(_.behavior).toSet
        val addedBehaviors = newBehaviors -- activeBehaviors
        val removedBehaviors = activeBehaviors -- newBehaviors
        activeBehaviors.clear()
        activeBehaviors ++= newBehaviors
        activeBehaviorsDirty = false
        addedBehaviors.foreach(_.onEnable())
        removedBehaviors.foreach(_.onDisable(reason))

        if (isServer) {
          NanomachinePacketSender.sendNanomachineInputs(player)
        }
      })
    }
  }
}
