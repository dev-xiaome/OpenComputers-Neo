package li.cil.oc.common.block

import li.cil.oc.util.Color
import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.level.block.state.{BlockBehaviour, BlockState, StateDefinition}

/**
 * 可被染色的「变色石」方块（原 1.7.10 `ChameliumBlock`）。
 *
 * 1.21.1 迁移要点：
 *  - 1.7.10 用 metadata 存染料颜色下标（0..15），1.21.1 改为 `BlockState` 的
 *    [[ChameliumBlock.Color]] 整数属性；
 *  - `getRenderColor(meta)` → [[SimpleBlockHooks.tintColor]]，真正生效还需要客户端注册
 *    `BlockColor`（`li.cil.oc.client` 未移植，见 `tintColor` 上的 TODO）；
 *  - `damageDropped(meta)`：1.21.1 的掉落走战利品表 + 数据组件，
 *    TODO(common.item): 需要物品侧把颜色写进掉落物的数据组件。
 *
 * 纹理：六面 `White`（见 `models/block/chameliumblock.json`，由 `tools/gen-block-assets.ps1` 生成）。
 */
class ChameliumBlock(properties: BlockBehaviour.Properties = SimpleBlock.properties())
  extends SimpleBlock(properties) {

  override def hasBlockEntity: Boolean = false

  override protected def createBlockStateDefinition(builder: StateDefinition.Builder[Block, BlockState]): Unit = {
    super.createBlockStateDefinition(builder)
    builder.add(ChameliumBlock.Color)
  }

  override def tintColor(state: BlockState, level: BlockGetter, pos: BlockPos, tintIndex: Int): Int = {
    val index = state.getValue(ChameliumBlock.Color)
    Color.byOreName(Color.dyes(index max 0 min (Color.dyes.length - 1)))
  }
}

object ChameliumBlock {
  /** 染料颜色下标（原 metadata，0..15）。 */
  val Color: IntegerProperty = IntegerProperty.create("color", 0, 15)
}
