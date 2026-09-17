package li.cil.oc.common.nanomachines

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.Persistable
import li.cil.oc.api.nanomachines.Behavior
import li.cil.oc.api.nanomachines.BehaviorProvider
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.world.entity.player.Player
import net.minecraft.server.level.ServerPlayer
import net.minecraft.nbt.{CompoundTag, Tag}
import net.minecraft.network.chat.Component
import net.minecraft.ChatFormatting

import scala.jdk.CollectionConverters._
import scala.collection.mutable
import scala.util.Random

/**
 * 纳米机器的「神经网络」：输入（trigger）→ 中间层（connector）→ 行为（behavior）。
 *
 * 1.21.1 迁移要点：
 *  - `api.Nanomachines.getProviders` 返回 Java `Iterable`，Scala 2.13 不再有隐式转换，
 *    统一显式 `asScala`，并把 `Seq` 结果显式 `toSeq` 以免 `ArrayBuffer ++=` 推断成 `IterableOnce`。
 *  - `Level#rand` → `Player#getRandom`；`Player#addChatMessage(IChatComponent)` →
 *    `Player#displayClientMessage(Component, actionBar = false)`
 *  - `net.minecraftforge.common.util.Constants.NBT` → `net.minecraft.nbt.Tag`
 *  - `ListTag#foreach` → 先 `asScala`
 *  - 配置同步改为 [[NanomachinePacketSender]]（`server.PacketSender` 尚未移植）。
 */
class NeuralNetwork(controller: ControllerImpl) extends Persistable {
  val triggers = mutable.ArrayBuffer.empty[TriggerNeuron]
  val connectors = mutable.ArrayBuffer.empty[ConnectorNeuron]
  val behaviors = mutable.ArrayBuffer.empty[BehaviorNeuron]

  val behaviorMap = mutable.Map.empty[Behavior, BehaviorNeuron]

  def inputs(behavior: Behavior) = behaviorMap.get(behavior) match {
    case Some(node) => node.inputs.count(_.isActive)
    case _ => 0
  }

  /** 从全部已注册 provider 收集有效行为（过滤 `null`）。 */
  private def collectBehaviors(): Seq[BehaviorNeuron] = {
    api.Nanomachines.getProviders.asScala.toSeq.
      map(p => (p, Option(p.createBehaviors(controller.player)).map(_.asScala.filter(_ != null).toSeq).orNull)).
      filter(_._2 != null).
      flatMap(pb => pb._2.map(b => new BehaviorNeuron(pb._1, b)))
  }

  def reconfigure(): Unit = {
    // 重建有效行为列表。
    behaviors.clear()
    behaviors ++= collectBehaviors()

    // 调整 trigger 数量并复位。
    while (triggers.length > behaviors.length * Settings.get.nanomachineTriggerQuota) {
      triggers.remove(triggers.length - 1)
    }
    triggers.foreach(_.isActive = false)
    while (triggers.length < behaviors.length * Settings.get.nanomachineTriggerQuota) {
      triggers += new TriggerNeuron()
    }

    // 调整 connector 数量并复位。
    while (connectors.length > behaviors.length * Settings.get.nanomachineConnectorQuota) {
      connectors.remove(connectors.length - 1)
    }
    connectors.foreach(_.inputs.clear())
    while (connectors.length < behaviors.length * Settings.get.nanomachineConnectorQuota) {
      connectors += new ConnectorNeuron()
    }

    // 建立连接。
    val rng = new Random(controller.player.getRandom.nextInt())

    def connect[Sink <: ConnectorNeuron, Source <: Neuron](sinks: Iterable[Sink], sources: mutable.ArrayBuffer[Source]): Unit = {
      // 打乱 sink 顺序，保证每个条目机会均等。
      val sinkPool = rng.shuffle(sinks.toBuffer)
      for (sink <- sinkPool if sources.nonEmpty) {
        // 避免同一个 sink 重复连到同一个 source。
        val blacklist = mutable.Set.empty[Source]
        for (n <- 0 to rng.nextInt(Settings.get.nanomachineMaxInputs) if sources.nonEmpty) {
          val baseIndex = rng.nextInt(sources.length)
          val sourceIndex = (sources.drop(baseIndex) ++ sources.take(baseIndex)).indexWhere(s => !blacklist.contains(s))
          if (sourceIndex >= 0) {
            val source = sources.remove((sourceIndex + baseIndex) % sources.length)
            blacklist += source
            sink.inputs += source
          }
        }
      }
    }

    // 先把 connector 连到 trigger，再把 behavior 连到 connector 和/或剩余的 trigger。
    val sourcePool = mutable.ArrayBuffer.fill(Settings.get.nanomachineMaxOutputs)(triggers.map(_.asInstanceOf[Neuron])).flatten
    connect(connectors, sourcePool)
    sourcePool ++= mutable.ArrayBuffer.fill(Settings.get.nanomachineMaxOutputs)(connectors.map(_.asInstanceOf[Neuron])).flatten
    connect(behaviors, sourcePool)

    // 清理没有连接的节点。
    val deadConnectors = connectors.filter(_.inputs.isEmpty).toSeq
    connectors --= deadConnectors
    behaviors.foreach(_.inputs --= deadConnectors)

    val deadBehaviors = behaviors.filter(_.inputs.isEmpty).toSeq
    behaviors --= deadBehaviors

    behaviorMap.clear()
    behaviorMap ++= behaviors.map(n => n.behavior -> n)
  }

  // 调试配置：一个输入对应一个行为，并把映射打印到控制台。
  def debug(): Unit = {
    val log: String => Unit = controller.player match {
      case playerMP: ServerPlayer => (s: String) => NanomachinePacketSender.sendClientLog(s, playerMP)
      case _ => (s: String) => OpenComputers.log.info(s)
    }
    log(s"Creating debug configuration for nanomachines in player ${controller.player.getName.getString}.")

    behaviors.clear()
    behaviors ++= collectBehaviors()

    connectors.clear()

    triggers.clear()
    for (i <- behaviors.indices) {
      val behavior = behaviors(i)
      val trigger = new TriggerNeuron()
      triggers += trigger
      behavior.inputs += trigger

      log(s"$i -> ${behavior.behavior.getNameHint} (${behavior.behavior.getClass.toString})")
    }
  }

  def print(player: Player): Unit = {
    val sb = StringBuilder.newBuilder
    def colored(value: Any, enabled: Boolean) = {
      if (enabled) sb.append(ChatFormatting.GREEN)
      else sb.append(ChatFormatting.RED)
      sb.append(value)
      sb.append(ChatFormatting.RESET)
    }
    for (behavior <- behaviors) {
      val name = Option(behavior.behavior.getNameHint).getOrElse(behavior.behavior.getClass.getSimpleName)
      colored(name, behavior.isActive)
      sb.append(" <- (")
      var first = true
      for (input <- behavior.inputs) {
        if (first) first = false else sb.append(", ")
        input match {
          case neuron: TriggerNeuron =>
            colored(triggers.indexOf(neuron) + 1, neuron.isActive)
          case neuron: ConnectorNeuron =>
            sb.append("(")
            first = true
            for (trigger <- neuron.inputs) {
              if (first) first = false else sb.append(", ")
              colored(triggers.indexOf(trigger) + 1, trigger.isActive)
            }
            first = false
            sb.append(")")
        }
      }
      sb.append(")")
      // 1.21.1：`addChatMessage` → `displayClientMessage`（`actionBar = false`）。
      player.displayClientMessage(Component.literal(sb.toString()), false)
      sb.clear()
    }
  }

  override def save(nbt: CompoundTag): Unit = {
    save(nbt, forItem = false)
  }

  def save(nbt: CompoundTag, forItem: Boolean): Unit = {
    nbt.setNewTagList("triggers", triggers.map(t => {
      val nbt = new CompoundTag()
      nbt.putBoolean("isActive", t.isActive && !forItem)
      nbt
    }).toIndexedSeq)

    nbt.setNewTagList("connectors", connectors.map(c => {
      val nbt = new CompoundTag()
      nbt.putIntArray("triggerInputs", c.inputs.map(triggers.indexOf(_)).filter(_ >= 0).toArray)
      nbt
    }).toIndexedSeq)

    nbt.setNewTagList("behaviors", behaviors.map(b => {
      val nbt = new CompoundTag()
      nbt.putIntArray("triggerInputs", b.inputs.map(triggers.indexOf(_)).filter(_ >= 0).toArray)
      nbt.putIntArray("connectorInputs", b.inputs.map(connectors.indexOf(_)).filter(_ >= 0).toArray)
      nbt.put("behavior", b.provider.writeToNBT(b.behavior))
      nbt
    }).toIndexedSeq)
  }

  override def load(nbt: CompoundTag): Unit = {
    triggers.clear()
    nbt.getList("triggers", Tag.TAG_COMPOUND).asScala.foreach {
      case t: CompoundTag =>
        val neuron = new TriggerNeuron()
        neuron.isActive = t.getBoolean("isActive")
        triggers += neuron
      case _ =>
    }

    connectors.clear()
    nbt.getList("connectors", Tag.TAG_COMPOUND).asScala.foreach {
      case t: CompoundTag =>
        val neuron = new ConnectorNeuron()
        neuron.inputs ++= t.getIntArray("triggerInputs").map(triggers.apply)
        connectors += neuron
      case _ =>
    }

    behaviors.clear()
    nbt.getList("behaviors", Tag.TAG_COMPOUND).asScala.foreach {
      case t: CompoundTag =>
        api.Nanomachines.getProviders.asScala.find(p => p.readFromNBT(controller.player, t.getCompound("behavior")) match {
          case b: Behavior =>
            val neuron = new BehaviorNeuron(p, b)
            neuron.inputs ++= t.getIntArray("triggerInputs").map(triggers.apply)
            neuron.inputs ++= t.getIntArray("connectorInputs").map(connectors.apply)
            behaviors += neuron
            true // 命中，停止查找。
          case _ =>
            false // 继续找下一个 provider。
        })
      case _ =>
    }

    behaviorMap.clear()
    behaviorMap ++= behaviors.map(n => n.behavior -> n)
  }

  trait Neuron {
    def isActive: Boolean
  }

  class TriggerNeuron extends Neuron {
    var isActive = false
  }

  class ConnectorNeuron extends Neuron {
    val inputs = mutable.ArrayBuffer.empty[Neuron]

    override def isActive = inputs.forall(_.isActive)
  }

  class BehaviorNeuron(val provider: BehaviorProvider, val behavior: Behavior) extends ConnectorNeuron

}
