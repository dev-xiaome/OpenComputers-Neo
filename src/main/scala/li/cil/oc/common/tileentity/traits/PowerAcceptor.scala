package li.cil.oc.common.tileentity.traits

/**
 * 接受外部能量输入的方块实体（对应 1.7.10 的 `common.tileentity.traits.PowerAcceptor`）。
 *
 * 1.7.10 通过混入 `power.*` 系列 trait 支持各模组的能量系统
 * （AE2 / Factorization / Galacticraft / IC2 / Mekanism / RF / RotaryCraft）。
 * 本移植版**只保留通用部分**（[[power.Common]]，提供 `energyThroughput` /
 * `tryChangeBuffer` / `canConnectPower` 等），第三方能量系统的 trait 已随
 * `li.cil.oc.integration` 一并移除；如果将来要重新接某个模组，按 1.21.1 的能力
 * （`Capabilities.EnergyStorage.BLOCK`）新增一个 trait 再混入即可。
 *
 * 成员名与 1.7.10 保持一致（`energyThroughput`、`bufferSize`、`canConnectNode` 等），
 * 以保证 Lua 侧与文档不失效。
 */
trait PowerAcceptor extends power.Common {
  // 注意：Scala 的自类型不会被继承，每个子 trait 都必须重新声明。
  self: net.minecraft.world.level.block.entity.BlockEntity =>
}
