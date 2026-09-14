package li.cil.oc.common.block

import li.cil.oc.Settings
import li.cil.oc.common.tileentity
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.{BlockBehaviour, BlockState}

/**
 * 无线接入点（原 1.7.10 `AccessPoint`，已废弃物品，等价于带无线收发能力的交换机）。
 *
 * 1.21.1 迁移要点：
 *  - 继承 [[Switch]]（即 [[RedstoneAware]] 的子类），并混入 [[traits.PowerAcceptor]]；
 *  - `energyThroughput` 仍取 `Settings.get.accessPointRate`；
 *  - `createTileEntity` → [[SimpleBlockHooks.createBlockEntity]]，构造为
 *    `new tileentity.AccessPoint(pos, state)`；
 *  - 整套图标系统删除（`customTextures` / `registerBlockIcons` / `getIcon`）。
 *
 * 纹理（原 `customTextures` 面序 DOWN, UP, NORTH, SOUTH, WEST, EAST）：
 * 下 = 未指定（沿用 `GenericTop`），上 = `AccessPointTop`，
 * 北 / 南 / 西 / 东 = `SwitchSide`。
 *
 * TODO(integration.util.NEI): 原构造里的 `NEI.hide(this)` 依赖 `integration.util.NEI`，
 * 该包未移植（1.21.1 也改用 JEI / 创造模式标签页控制可见性），整句删除。
 */
class AccessPoint(properties: BlockBehaviour.Properties = SimpleBlock.properties())
  extends Switch(properties) with traits.PowerAcceptor {

  override def energyThroughput = Settings.get.accessPointRate

  override def createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
    new tileentity.AccessPoint(pos, state)
}
