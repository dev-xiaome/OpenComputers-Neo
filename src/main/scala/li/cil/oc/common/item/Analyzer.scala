package li.cil.oc.common.item

import li.cil.oc.util.ItemStackNBTExtensions._

import li.cil.oc.Constants
import li.cil.oc.Localization
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.machine.Machine
import li.cil.oc.api.network._
import li.cil.oc.common.PacketType
import li.cil.oc.common.SimplePacketBuilder
import li.cil.oc.util.BlockPosition
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/**
 * 「分析仪」（原 `li.cil.oc.common.item.Analyzer`）。
 *
 * 1.21.1 迁移要点：
 *  - `EntityInteractEvent`（`net.minecraftforge.event.entity.player`）在 NeoForge 已删除；
 *    实体交互改由 `PlayerInteractEvent.EntityInteract` 触发，且事件的注册需要
 *    `NeoForge.EVENT_BUS`。为避免在物品层引入未移植的事件基础设施，
 *    这里只保留 [[Analyzer.analyze]] 静态入口，由事件层（`common/event`）在移植后接线。
 *  - `world.getTileEntity(pos)` → `world.getBlockEntity(pos)`
 *  - `player.addChatMessage(...)` → `ServerPlayer#displayClientMessage(Component, actionBar)`
 *  - `FakePlayer`（Forge）→ NeoForge 的 `FakePlayer` 仍在 `net.neoforged.neoforge.common.util`，
 *    但为避免引入未移植依赖，这里改为**不区分假玩家**，只在 `ServerPlayer` 上输出。
 *  - `PacketSender.sendAnalyze`（`li.cil.oc.server`，未移植）→ 直接用已移植的
 *    [[li.cil.oc.common.SimplePacketBuilder]] 发 `PacketType.Analyze`。
 */
object Analyzer {
  private lazy val analyzer = api.Items.get(Constants.ItemName.Analyzer)

  /** 玩家手持分析仪右键实体时的入口（由事件层调用）。 */
  def onInteract(player: Player, target: net.minecraft.world.entity.Entity): Boolean = {
    val held = player.getItemInHand(InteractionHand.MAIN_HAND)
    if (api.Items.get(held) == analyzer) {
      if (analyze(target, player, 0, 0, 0, 0)) {
        player.swing(InteractionHand.MAIN_HAND)
        return true
      }
    }
    false
  }

  def analyze(thing: AnyRef, player: Player, side: Int, hitX: Float, hitY: Float, hitZ: Float): Boolean = {
    val world = player.level()
    thing match {
      case analyzable: Analyzable =>
        if (!world.isClientSide) {
          analyzeNodes(analyzable.onAnalyze(player, side, hitX, hitY, hitZ), player)
        }
        true
      case host: SidedEnvironment =>
        if (!world.isClientSide) {
          analyzeNodes(Array(host.sidedNode(Direction.from3DDataValue(side))), player)
        }
        true
      case host: Environment =>
        if (!world.isClientSide) {
          analyzeNodes(Array(host.node), player)
        }
        true
      case _ =>
        false
    }
  }

  private def analyzeNodes(nodes: Array[Node], player: Player): Unit = if (nodes != null) for (node <- nodes if node != null) {
    player match {
      case playerMP: ServerPlayer =>
        if (node != null) node.host match {
          case machine: Machine =>
            if (machine != null) {
              if (machine.lastError != null) {
                playerMP.displayClientMessage(Localization.Analyzer.LastError(machine.lastError), false)
              }
              playerMP.displayClientMessage(
                Localization.Analyzer.Components(machine.componentCount, machine.maxComponents), false)
              val list = machine.users
              if (list != null && list.nonEmpty) {
                playerMP.displayClientMessage(Localization.Analyzer.Users(list.toSeq), false)
              }
            }
          case _ =>
        }
        node match {
          case connector: Connector =>
            if (connector.localBufferSize > 0) {
              playerMP.displayClientMessage(Localization.Analyzer.StoredEnergy(
                f"${connector.localBuffer}%.2f/${connector.localBufferSize}%.2f"), false)
            }
            playerMP.displayClientMessage(Localization.Analyzer.TotalEnergy(
              f"${connector.globalBuffer}%.2f/${connector.globalBufferSize}%.2f"), false)
          case _ =>
        }
        node match {
          case component: Component =>
            playerMP.displayClientMessage(Localization.Analyzer.ComponentName(component.name), false)
          case _ =>
        }
        val address = node.address()
        if (address != null && address.nonEmpty) {
          playerMP.displayClientMessage(Localization.Analyzer.Address(address), false)
          sendAnalyze(address, playerMP)
        }
      case _ =>
    }
  }

  /**
   * 原 `li.cil.oc.server.PacketSender.sendAnalyze(address, player)`。
   *
   * 该包尚未移植，这里用已移植的网络层直接构造并发送 `PacketType.Analyze` 负载
   * （数据格式与原版一致：`writeUTF(address)`）；`PacketHandler` 侧的
   * `PacketType.Analyze` 处理器移植后即可直接消费。
   */
  private def sendAnalyze(address: String, player: ServerPlayer): Unit = {
    val packet = new SimplePacketBuilder(PacketType.Analyze)
    packet.writeUTF(address)
    packet.sendToPlayer(player)
  }
}

class Analyzer(props: Item.Properties) extends Item(props) with traits.Delegate {

  override def onItemRightClick(stack: ItemStack, world: Level, player: Player): ItemStack = {
    if (player.isShiftKeyDown && stack.hasTag()) {
      stack.getTag().remove(Settings.namespace + "clipboard")
      if (stack.getTag().isEmpty) {
        stack.setTag(null)
      }
    }
    super.onItemRightClick(stack, world, player)
  }

  override def onItemUse(stack: ItemStack, player: Player, position: BlockPosition,
                         side: Int, hitX: Float, hitY: Float, hitZ: Float): Boolean = {
    position.world match {
      case Some(world) =>
        // TODO(方块实体): 原版对屏幕方块（`tileentity.Screen`）有特殊处理
        // （复制/粘贴剪贴板、`copyToAnalyzer`），需要 `common/tileentity` 移植后恢复。
        Analyzer.analyze(world.getBlockEntity(position.toChunkCoordinates), player, side, hitX, hitY, hitZ)
      case _ => super.onItemUse(stack, player, position, side, hitX, hitY, hitZ)
    }
  }
}
