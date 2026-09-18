package li.cil.oc.integration.projectred

import li.cil.oc.common.blockentity.traits.BundledRedstoneAware
import mrtjp.projectred.api.IBundledTileInteraction
import mrtjp.projectred.api.ProjectRedAPI
import net.minecraft.core.Direction
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level

object BundledProviderProjectRed extends IBundledTileInteraction {
  def install(): Unit = ProjectRedAPI.transmissionAPI.registerBundledTileInteraction(this)

  override def isValidInteractionFor(level: Level, pos: BlockPos, side: Direction) =
    level.getBlockEntity(pos).isInstanceOf[BundledRedstoneAware]

  override def canConnectBundled(level: Level, pos: BlockPos, side: Direction): Boolean =
    level.getBlockEntity(pos).asInstanceOf[BundledRedstoneAware].isOutputEnabled

  override def getBundledSignal(level: Level, pos: BlockPos, side: Direction): Array[Byte] = {
    val tileEntity = level.getBlockEntity(pos).asInstanceOf[BundledRedstoneAware]
    tileEntity.getBundledOutput(side).map(value => math.min(math.max(value, 0), 255).toByte)
  }
}
