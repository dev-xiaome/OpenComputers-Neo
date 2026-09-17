package li.cil.oc.common.block

import li.cil.oc.common.tileentity
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.world.level.{BlockGetter, Level}
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.{BlockBehaviour, BlockState}

/**
 * 会读取 / 输出红石的方块基类（原 1.7.10 `RedstoneAware`）。
 *
 * 1.21.1 迁移要点（钩子名全部来自 [[SimpleBlockHooks]]，不要直接覆写 NeoForge 同名方法）：
 *  - `canProvidePower` → [[providesRedstoneSignal]]，这里恒为 `true`；
 *  - `canConnectRedstone` → [[canConnectRedstoneTo]]，语义是「方块实体当前是否使能了输出」；
 *  - `isProvidingWeakPower` / `isProvidingStrongPower` → 同名钩子，
 *    弱信号取方块实体 `getOutput(side)`（强信号沿用基类默认值 = 与弱信号一致）；
 *  - `onNeighborBlockChange` → 同名钩子：先判断对方块实体是「可更新（由 tick 驱动）」还是
 *    「不可更新（自身不 tick）」，前者只标记需要在下一个 tick 重新采样输入，
 *    后者立刻逐面采样（与原实现一致）。
 *
 * ==降级说明==
 * 1.7.10 里本类同时实现 MineFactoryReloaded 的 `IRedNetOmniNode`
 * （`getConnectionType` / `getOutputValue(s)` / `onInputChanged(s)`），并在邻居变化时
 * 读取相邻的 `IRedNetNetworkContainer` 来清空红网输入。
 * TODO(integration): MFR / RedNet 集成不再移植（`powercrystals.*` 在 1.21.1 不存在），
 * 相关方法与 `@Optional.Interface` 注解整块删除；如需恢复，请在独立驱动里实现 NeoForge
 * 能力接口。方块实体侧的捆绑红石入口
 * （`tileentity.traits.BundledRedstoneAware` 的 `setRednetInput`）仍然保留，等
 * `integration.util.BundledRedstone` 移植后由它驱动。
 */
abstract class RedstoneAware(properties: BlockBehaviour.Properties = SimpleBlock.properties())
  extends SimpleBlock(properties) {

  /** 原 `hasTileEntity(metadata)`。 */
  override def hasBlockEntity: Boolean = true

  // ----------------------------------------------------------------------- //
  // 红石输出
  // ----------------------------------------------------------------------- //

  override def providesRedstoneSignal: Boolean = true

  override def canConnectRedstoneTo(state: BlockState, level: BlockGetter, pos: BlockPos, side: Direction): Boolean =
    level.getBlockEntity(pos) match {
      case redstone: tileentity.traits.RedstoneAware => redstone.isOutputEnabled
      case _ => false
    }

  override def isProvidingWeakPower(state: BlockState, level: BlockGetter, pos: BlockPos, side: Direction): Int =
    level.getBlockEntity(pos) match {
      case redstone: tileentity.traits.RedstoneAware => redstone.getOutput(side) max 0
      case _ => 0
    }

  // ----------------------------------------------------------------------- //
  // 红石输入
  // ----------------------------------------------------------------------- //

  override def onNeighborBlockChange(state: BlockState, level: Level, pos: BlockPos, neighborBlock: Block): Unit = {
    level.getBlockEntity(pos) match {
      case redstone: tileentity.traits.RedstoneAware =>
        if (redstone.canUpdate)
          redstone.checkRedstoneInputChanged()
        else
          Direction.values().foreach(redstone.updateRedstoneInput)
      case _ => // Ignore.
    }
  }
}
