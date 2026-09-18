package li.cil.oc.common.item

import li.cil.oc.{api, Constants, Localization, Settings}
import li.cil.oc.api.machine.Machine
import li.cil.oc.api.network._
import li.cil.oc.common.blockentity
import li.cil.oc.server.PacketSender
import li.cil.oc.util.ItemUtils
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.{InteractionHand, InteractionResult, InteractionResultHolder}
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.{Item, ItemStack}
import net.minecraft.world.item.Item.Properties
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.extensions.IItemExtension
import net.neoforged.neoforge.common.util.FakePlayer
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent

object Analyzer {
  private lazy val analyzer = api.Items.get(Constants.ItemName.Analyzer)

  @SubscribeEvent
  def onInteract(e: PlayerInteractEvent.EntityInteract): Unit = {
    val player = e.getEntity
    val held = player.getItemInHand(e.getHand)
    if (api.Items.get(held) == analyzer) {
      if (analyze(e.getTarget, player, Direction.DOWN, 0, 0, 0)) {
        player.swing(e.getHand)
        e.setCanceled(true)
      }
    }
  }

  def analyze(thing: AnyRef, player: Player, side: Direction, hitX: Float, hitY: Float, hitZ: Float): Boolean = {
    val world = player.level
    thing match {
      case analyzable: Analyzable =>
        if (!world.isClientSide) {
          analyzeNodes(analyzable.onAnalyze(player, side, hitX, hitY, hitZ), player)
        }
        true
      case host: SidedEnvironment =>
        if (!world.isClientSide) {
          analyzeNodes(Array(host.sidedNode(side)), player)
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

  private def analyzeNodes(nodes: Array[Node], player: Player) = if (nodes != null) for (node <- nodes if node != null) {
    player match {
      case _: FakePlayer => // Nope
      case playerMP: ServerPlayer =>
        if (node != null) node.host match {
          case machine: Machine =>
            if (machine != null) {
              if (machine.lastError != null) {
                playerMP.sendSystemMessage(Localization.Analyzer.LastError(machine.lastError))
              }
              playerMP.sendSystemMessage(Localization.Analyzer.Components(machine.componentCount, machine.maxComponents))
              val list = machine.users
              if (list.nonEmpty) {
                playerMP.sendSystemMessage(Localization.Analyzer.Users(list))
              }
            }
          case _ =>
        }
        node match {
          case connector: Connector =>
            if (connector.localBufferSize > 0) {
              playerMP.sendSystemMessage(Localization.Analyzer.StoredEnergy(f"${connector.localBuffer}%.2f/${connector.localBufferSize}%.2f"))
            }
            playerMP.sendSystemMessage(Localization.Analyzer.TotalEnergy(f"${connector.globalBuffer}%.2f/${connector.globalBufferSize}%.2f"))
          case _ =>
        }
        node match {
          case component: Component =>
            playerMP.sendSystemMessage(Localization.Analyzer.ComponentName(component.name))
          case _ =>
        }
        val address = node.address()
        if (address != null && address.nonEmpty) {
          playerMP.sendSystemMessage(Localization.Analyzer.Address(address))
          PacketSender.sendAnalyze(address, playerMP)
        }
      case _ =>
    }
  }
}

class Analyzer(props: Properties) extends Item(props) with traits.SimpleItem with IItemExtension {
  override def use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder[ItemStack] = {
    val stack = player.getItemInHand(hand)
    if (player.isCrouching) {
      CustomData.update(DataComponents.CUSTOM_DATA, stack, data => {
        data.remove(Settings.namespace + "clipboard")
      })

      return InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
    }

    super.use(level, player, hand)
  }

  override def useOn(ctx: UseOnContext): InteractionResult = {
    val world = ctx.getLevel
    val tag = ItemUtils.getTag(ctx.getItemInHand)
    val hitX = (ctx.getClickLocation.x - ctx.getClickedPos.getX).toFloat
    val hitY = (ctx.getClickLocation.y - ctx.getClickedPos.getY).toFloat
    val hitZ = (ctx.getClickLocation.z - ctx.getClickedPos.getZ).toFloat

    val result = world.getBlockEntity(ctx.getClickedPos) match {
      case screen: blockentity.Screen if ctx.getClickedFace == screen.facing =>
        if (ctx.isSecondaryUseActive) {
          screen.copyToAnalyzer(hitX, hitY, hitZ)
        }
        else if (tag != null && tag.contains(Settings.namespace + "clipboard")) {
          if (!world.isClientSide && ctx.getPlayer != null) {
            screen.origin.buffer.clipboard(tag.getString(Settings.namespace + "clipboard"), ctx.getPlayer)
          }
          true
        }
        else false
      case be if ctx.getPlayer != null => Analyzer.analyze(be, ctx.getPlayer, ctx.getClickedFace, hitX, hitY, hitZ)
      case _ => false
    }

    if (result) InteractionResult.sidedSuccess(world.isClientSide) else InteractionResult.PASS
  }
}
