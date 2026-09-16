package li.cil.oc.common.blockentity.traits

import net.minecraft.world.level.block.state.BlockState

trait Tickable extends BaseBlockEntity {
  def tick(): Unit = updateEntity()
}
