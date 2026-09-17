package li.cil.oc.common.block

import net.minecraft.world.level.block.state.BlockBehaviour

/**
 * 伪装成末地石的方块（原 1.7.10 `FakeEndstone`）。
 *
 * 纹理：六面都用原版 `minecraft:block/end_stone`
 * （见 `models/block/endstone.json`，由 `tools/gen-block-assets.ps1` 生成）。
 *
 * 1.21.1 迁移要点：
 *  - `Material.rock` + `setHardness(3)` / `setResistance(15)` → 构造属性
 *    `SimpleBlock.properties().strength(3f, 15f)`；
 *  - 没有方块实体（原 `hasTileEntity` 返回 false）。
 */
class FakeEndstone(properties: BlockBehaviour.Properties = SimpleBlock.properties().strength(3f, 15f))
  extends SimpleBlock(properties) {

  override def hasBlockEntity: Boolean = false
}
