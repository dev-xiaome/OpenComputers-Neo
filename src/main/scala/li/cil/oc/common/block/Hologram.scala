package li.cil.oc.common.block

import java.util

import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import li.cil.oc.Settings
import li.cil.oc.common.tileentity
import li.cil.oc.integration.coloredlights.ModColoredLights
import li.cil.oc.util.Rarity
import li.cil.oc.util.Tooltip
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.core.Direction

class Hologram(val tier: Int) extends SimpleBlock with traits.SpecialBlock {
  if (Settings.get.hologramLight) {
    ModColoredLights.setLightLevel(this, 15, 15, 15)
  }
  setBlockBounds(0, 0, 0, 1, 0.5f, 1)

  // ----------------------------------------------------------------------- //

  override protected def customTextures = Array(
    None,
    Some("HologramTop" + tier),
    Some("HologramSide"),
    Some("HologramSide"),
    Some("HologramSide"),
    Some("HologramSide")
  )

  override def isBlockSolid(world: IBlockAccess, x: Int, y: Int, z: Int, side: Direction) = side == Direction.DOWN

  @SideOnly(Dist.CLIENT)
  override def shouldSideBeRendered(world: IBlockAccess, x: Int, y: Int, z: Int, side: Direction) = {
    super.shouldSideBeRendered(world, x, y, z, side) || side == Direction.UP
  }

  override def isSideSolid(world: IBlockAccess, x: Int, y: Int, z: Int, side: Direction) = side == Direction.DOWN

  // ----------------------------------------------------------------------- //

  override def rarity(stack: ItemStack) = Rarity.byTier(tier)

  override protected def tooltipBody(metadata: Int, stack: ItemStack, player: Player, tooltip: util.List[String], advanced: Boolean): Unit = {
    tooltip.addAll(Tooltip.get(getClass.getSimpleName + tier))
  }

  // ----------------------------------------------------------------------- //

  override def hasTileEntity(metadata: Int) = true

  override def createTileEntity(world: Level, metadata: Int) = new tileentity.Hologram(tier)
}
