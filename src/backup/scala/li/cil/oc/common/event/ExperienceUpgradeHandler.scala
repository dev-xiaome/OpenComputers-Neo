package li.cil.oc.common.event

import li.cil.oc.Localization
import li.cil.oc.Settings
import li.cil.oc.api.event._
import li.cil.oc.api.internal.Agent
import li.cil.oc.api.internal.Robot
import li.cil.oc.api.network.Node
import li.cil.oc.server.component
import net.neoforged.neoforge.common.NeoForge
import org.lwjgl.opengl.GL11

import scala.jdk.CollectionConverters._

/**
 * 经验升级：机器人分析、挖矿、放置、移动、疲劳时累积经验，并按等级提供加成。
 *
 * 1.21.1 迁移要点：
 *  - `@SubscribeEvent` → 显式 `addListener`（见 [[initialize]]）。
 *  - `EntityPlayer#addChatMessage` → `ServerPlayer#sendSystemMessage`。
 *  - `robot.equipmentInventory` 现在是 `IItemHandler`：`getStackInSlot` 不再返回 `null`，
 *    改用 `isEmpty`；`robot.getSizeInventory` → `robot.getSlots`。
 *  - `Entity#isDead` → `!Entity#isAlive`。
 *  - `Node#reachableNodes` 是 Java `Iterable`，需要 `.asScala`。
 *  - `org.lwjgl.opengl.GL11#glColor3f` 在 LWJGL 3 里仍然存在，但 1.21.1 的渲染管线使用
 *    顶点颜色，固定管线的当前颜色不再影响已烘焙模型。这里保留调用并留下 TODO：
 *    机器人渲染器应改为把等级对应的颜色写进 `VertexConsumer#setColor`。
 */
object ExperienceUpgradeHandler {
  /** 注册监听器；由主类（或 [[EventHandlers]]）调用一次。 */
  def initialize(): Unit = {
    NeoForge.EVENT_BUS.addListener((e: RobotAnalyzeEvent) => onRobotAnalyze(e))
    NeoForge.EVENT_BUS.addListener((e: RobotUsedToolEvent.ComputeDamageRate) => onRobotComputeDamageRate(e))
    NeoForge.EVENT_BUS.addListener((e: RobotBreakBlockEvent.Pre) => onRobotBreakBlockPre(e))
    NeoForge.EVENT_BUS.addListener((e: RobotAttackEntityEvent.Post) => onRobotAttackEntityPost(e))
    NeoForge.EVENT_BUS.addListener((e: RobotBreakBlockEvent.Post) => onRobotBreakBlockPost(e))
    NeoForge.EVENT_BUS.addListener((e: RobotPlaceBlockEvent.Post) => onRobotPlaceBlockPost(e))
    NeoForge.EVENT_BUS.addListener((e: RobotMoveEvent.Post) => onRobotMovePost(e))
    NeoForge.EVENT_BUS.addListener((e: RobotExhaustionEvent) => onRobotExhaustion(e))
    NeoForge.EVENT_BUS.addListener((e: RobotRenderEvent) => onRobotRender(e))
  }

  def onRobotAnalyze(e: RobotAnalyzeEvent): Unit = {
    val (level, experience) = getLevelAndExperience(e.agent)
    // 这基本就是「有没有经验升级」的判定。
    if (experience != 0.0) {
      e.player.sendSystemMessage(Localization.Analyzer.RobotXp(experience, level))
    }
  }

  def onRobotComputeDamageRate(e: RobotUsedToolEvent.ComputeDamageRate): Unit = {
    e.setDamageRate(e.getDamageRate * math.max(0, 1 - getLevel(e.agent) * Settings.get.toolEfficiencyPerLevel))
  }

  def onRobotBreakBlockPre(e: RobotBreakBlockEvent.Pre): Unit = {
    val boost = math.max(0, 1 - getLevel(e.agent) * Settings.get.harvestSpeedBoostPerLevel)
    e.setBreakTime(e.getBreakTime * boost)
  }

  def onRobotAttackEntityPost(e: RobotAttackEntityEvent.Post): Unit = {
    e.agent match {
      case robot: Robot =>
        val equipment = robot.equipmentInventory
        if (equipment != null && !equipment.getStackInSlot(0).isEmpty && !e.target.isAlive) {
          addExperience(robot, Settings.get.robotActionXp)
        }
      case _ =>
    }
  }

  def onRobotBreakBlockPost(e: RobotBreakBlockEvent.Post): Unit = {
    addExperience(e.agent, e.experience * Settings.get.robotOreXpRate + Settings.get.robotActionXp)
  }

  def onRobotPlaceBlockPost(e: RobotPlaceBlockEvent.Post): Unit = {
    addExperience(e.agent, Settings.get.robotActionXp)
  }

  def onRobotMovePost(e: RobotMoveEvent.Post): Unit = {
    addExperience(e.agent, Settings.get.robotExhaustionXpRate * 0.01)
  }

  def onRobotExhaustion(e: RobotExhaustionEvent): Unit = {
    addExperience(e.agent, Settings.get.robotExhaustionXpRate * e.exhaustion)
  }

  def onRobotRender(e: RobotRenderEvent): Unit = {
    val level = e.agent match {
      case robot: Robot =>
        var acc = 0
        for (index <- 0 until robot.getSlots) {
          robot.getComponentInSlot(index) match {
            case upgrade: component.UpgradeExperience =>
              acc += upgrade.level
            case _ =>
          }
        }
        acc
      case _ => 0
    }
    // TODO(渲染): 1.21.1 用顶点颜色，机器人渲染器需要读取等级并把颜色写进
    // `VertexConsumer#setColor`；下面的固定管线调用不再生效，仅为保留原有语义。
    if (level > 19) {
      GL11.glColor3f(0.4f, 1, 1)
    }
    else if (level > 9) {
      GL11.glColor3f(1, 1, 0.4f)
    }
    else {
      GL11.glColor3f(0.5f, 0.5f, 0.5f)
    }
  }

  private def getLevel(agent: Agent) = {
    var level = 0
    // TODO(server.machine): 机器层移植前 `machine` 可能为 null，这里做空值保护。
    if (agent.machine != null) {
      foreachUpgrade(agent.machine.node, upgrade => level += upgrade.level)
    }
    level
  }

  private def getLevelAndExperience(agent: Agent) = {
    var level = 0
    var experience = 0.0
    if (agent.machine != null) {
      foreachUpgrade(agent.machine.node, upgrade => {
        level += upgrade.level
        experience += upgrade.experience
      })
    }
    (level, experience)
  }

  private def addExperience(agent: Agent, amount: Double): Unit = {
    if (agent.machine != null) {
      foreachUpgrade(agent.machine.node, upgrade => upgrade.addExperience(amount))
    }
  }

  private def foreachUpgrade(node: Node, f: (component.UpgradeExperience) => Unit): Unit = {
    node.reachableNodes.asScala.foreach(_.host match {
      case upgrade: component.UpgradeExperience => f(upgrade)
      case _ =>
    })
  }
}
