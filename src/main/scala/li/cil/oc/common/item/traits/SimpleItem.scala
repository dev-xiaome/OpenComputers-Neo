package li.cil.oc.common.item.traits

import li.cil.oc.util.ItemStackNBTExtensions._

import java.util
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.event.RobotRenderEvent.MountPoint
import li.cil.oc.api.internal.Robot
import li.cil.oc.client.renderer.item.ItemUpgradeRenderer
import li.cil.oc.common.blockentity
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.Tooltip
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.LevelReader
import net.minecraft.core.Direction
import net.minecraft.world.{InteractionHand, InteractionResult}
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level
import net.minecraft.world.InteractionResultHolder
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.network.chat.Component
import net.minecraft.world.item.TooltipFlag
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn

import scala.collection.convert.ImplicitConversionsToScala._
import com.mojang.blaze3d.vertex.PoseStack

trait SimpleItem extends Item with api.driver.item.UpgradeRenderer {
  def createItemStack(amount: Int = 1) = new ItemStack(this, amount)

  @Deprecated
  protected var unlocalizedName = getClass.getSimpleName.toLowerCase

  @Deprecated
  override def getDescriptionId = "item.oc." + unlocalizedName

  override def doesSneakBypassUse(stack: ItemStack, level: LevelReader, pos: BlockPos, player: Player): Boolean = {
    level.getBlockEntity(pos) match {
      case drive: blockentity.DiskDrive => true
      // 1.21.1：不再走 `super.doesSneakBypassUse` —— 该默认实现来自 NeoForge 的 `IItemExtension` 接口，
      // trait 里对接口方法做 `super` 调用会要求每个实现类都直接混入该接口（Scala 的 super accessor 限制）。
      // 默认值就是 false，这里直接内联。
      case _ => false
    }
  }

  @Deprecated
  override def onItemUseFirst(stack: ItemStack, ctx: UseOnContext): InteractionResult = {
    val pos = ctx.getClickedPos
    val hitPos = ctx.getClickLocation
    onItemUseFirst(stack, ctx.getPlayer, ctx.getPlayer.level, pos, ctx.getClickedFace,
      (hitPos.x - pos.getX).toFloat, (hitPos.y - pos.getY).toFloat, (hitPos.z - pos.getZ).toFloat, ctx.getHand)
  }

  @Deprecated
  def onItemUseFirst(stack: ItemStack, player: Player, level: Level, pos: BlockPos, side: Direction, hitX: Float, hitY: Float, hitZ: Float, hand: InteractionHand): InteractionResult = InteractionResult.PASS

  @Deprecated
  override def useOn(ctx: UseOnContext): InteractionResult =
    ctx.getItemInHand match {
      case stack: ItemStack => {
        val world = ctx.getLevel
        val pos = BlockPosition(ctx.getClickedPos, world)
        val hitPos = ctx.getClickLocation
        val success = onItemUse(stack, ctx.getPlayer, pos, ctx.getClickedFace,
          (hitPos.x - pos.x).toFloat, (hitPos.y - pos.y).toFloat, (hitPos.z - pos.z).toFloat)
        if (success) InteractionResult.sidedSuccess(world.isClientSide) else InteractionResult.PASS
      }
      // 1.21.1：同上，`Item#useOn` 的默认返回就是 PASS，直接内联。
      case _ => InteractionResult.PASS
    }

  @Deprecated
  def onItemUse(stack: ItemStack, player: Player, position: BlockPosition, side: Direction, hitX: Float, hitY: Float, hitZ: Float): Boolean = false

  @Deprecated
  override def use(world: Level, player: Player, hand: InteractionHand): InteractionResultHolder[ItemStack] =
    player.getItemInHand(hand) match {
      case stack: ItemStack => use(stack, world, player)
      // 1.21.1：同上，`Item#use` 的默认返回就是 PASS + 手上的堆叠，直接内联。
      case _ => new InteractionResultHolder(InteractionResult.PASS, player.getItemInHand(hand))
    }

  @Deprecated
  def use(stack: ItemStack, level: Level, player: Player): InteractionResultHolder[ItemStack] = new InteractionResultHolder(InteractionResult.PASS, stack)

  protected def tierFromDriver(stack: ItemStack): Int =
    api.Driver.driverFor(stack) match {
      case driver: api.driver.DriverItem => driver.tier(stack)
      case _ => 0
    }

  protected def tooltipName = Option(unlocalizedName)

  protected def tooltipData = Seq.empty[Any]

  @OnlyIn(Dist.CLIENT)
  // 1.21.1：Item#appendHoverText 的第二个参数从 `Level` 换成了 `Item.TooltipContext`。
  override def appendHoverText(stack: ItemStack, context: net.minecraft.world.item.Item.TooltipContext, tooltip: util.List[Component], flag: TooltipFlag): Unit = {
    if (tooltipName.isDefined) {
      for (curr <- Tooltip.get(tooltipName.get, tooltipData: _*)) {
        tooltip.add(Component.literal(curr).setStyle(Tooltip.DefaultStyle))
      }
      tooltipExtended(stack, tooltip)
    }
    else {
      for (curr <- Tooltip.get(getClass.getSimpleName.toLowerCase)) {
        tooltip.add(Component.literal(curr).setStyle(Tooltip.DefaultStyle))
      }
    }
    tooltipCosts(stack, tooltip)
  }

  // For stuff that goes to the normal 'extended' tooltip, before the costs.
  protected def tooltipExtended(stack: ItemStack, tooltip: java.util.List[Component]): Unit = {}

  protected def tooltipCosts(stack: ItemStack, tooltip: java.util.List[Component]): Unit = {
    if (stack.hasTag && stack.getTag.contains(Settings.namespace + "data")) {
      val data = stack.getTag.getCompound(Settings.namespace + "data")
      if (data.contains("node") && data.getCompound("node").contains("address")) {
        tooltip.add(Component.literal("§8" + data.getCompound("node").getString("address").substring(0, 13) + "...§7"))
      }
    }
  }

  // ----------------------------------------------------------------------- //

  override def computePreferredMountPoint(stack: ItemStack, robot: Robot, availableMountPoints: util.Set[String]): String = ItemUpgradeRenderer.preferredMountPoint(stack, availableMountPoints)

  @OnlyIn(Dist.CLIENT)
  override def render(matrix: PoseStack, buffer: MultiBufferSource, light: Int, stack: ItemStack, mountPoint: MountPoint, robot: Robot, pt: Float): Unit = ItemUpgradeRenderer.render(matrix, buffer, light, stack, mountPoint)
}
