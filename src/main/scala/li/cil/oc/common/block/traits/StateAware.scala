package li.cil.oc.common.block.traits

import li.cil.oc.api
import li.cil.oc.common.block.SimpleBlockHooks
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState

/**
 * 比较器输出：工作中输出 15，可工作输出 10，其余 0（对应 1.7.10 的 `block.traits.StateAware`）。
 *
 * 1.7.10 里通过 `hasComparatorInputOverride` / `getComparatorInputOverride` 实现；
 * 1.21.1 对应 `hasAnalogOutputSignal` / `getAnalogOutputSignal`。
 */
trait StateAware extends SimpleBlockHooks {
  // 注意：Scala 的自类型不会被继承，[[SimpleBlockHooks]] 的子 trait 必须重新声明 `self: Block`。
  self: net.minecraft.world.level.block.Block =>

  override def providesAnalogOutput: Boolean = true

  override def analogOutputSignal(state: BlockState, level: Level, pos: BlockPos): Int =
    level.getBlockEntity(pos) match {
      case stateful: api.util.StateAware =>
        if (stateful.getCurrentState.contains(api.util.StateAware.State.IsWorking)) 15
        else if (stateful.getCurrentState.contains(api.util.StateAware.State.CanWork)) 10
        else 0
      case _ => 0
    }
}
