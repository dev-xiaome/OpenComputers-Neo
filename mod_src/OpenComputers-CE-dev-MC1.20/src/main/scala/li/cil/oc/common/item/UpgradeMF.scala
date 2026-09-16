package li.cil.oc.common.item

import java.util

import li.cil.oc.Localization
import li.cil.oc.Settings
import li.cil.oc.util.Tooltip
import net.minecraft.world.item.Item
import net.minecraft.world.item.Item.Properties
import net.minecraft.world.item.ItemStack
import net.minecraftforge.common.extensions.IForgeItem
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.network.chat.Component
import net.minecraft.nbt.CompoundTag

class UpgradeMF(props: Properties) extends Item(props) with IForgeItem with traits.SimpleItem with traits.ItemTier {
  override def onItemUseFirst(stack: ItemStack, player: Player, level: Level, pos: BlockPos, side: Direction, hitX: Float, hitY: Float, hitZ: Float, hand: InteractionHand): InteractionResult = {
    if (!player.level.isClientSide && player.isCrouching) {
      val data = stack.getOrCreateTag
      data.putString(Settings.namespace + "dimension", level.dimension.location.toString)
      data.putIntArray(Settings.namespace + "coord", Array(pos.getX, pos.getY, pos.getZ, side.ordinal()))
      return InteractionResult.sidedSuccess(player.level.isClientSide)
    }
    super.onItemUseFirst(stack, player, level, pos, side, hitX, hitY, hitZ, hand)
  }

  override protected def tooltipExtended(stack: ItemStack, tooltip: util.List[Component]): Unit = {
    tooltip.add(Component.literal(Localization.Tooltip.MFULinked(stack.getTag match {
      case data: CompoundTag => data.contains(Settings.namespace + "coord")
      case _ => false
    })).setStyle(Tooltip.DefaultStyle))
  }
}
