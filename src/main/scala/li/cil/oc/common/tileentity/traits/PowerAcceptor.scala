package li.cil.oc.common.tileentity.traits

/**
 * 接受外部能量输入的方块实体（对应 1.7.10 的 `common.tileentity.traits.PowerAcceptor`）。
 *
 * 1.7.10 通过混入 `power.*` 系列 trait 支持各模组的能量系统
 * （AE2 / Factorization / Galacticraft / IC2 / Mekanism / RF / RotaryCraft）。
 * 1.21.1 只保留仍然可用的通用部分与 NeoForge 能量（RF）通道：
 *  - `power.Common` 提供 `energyThroughput` / `tryChangeBuffer` / `canConnectPower` 等，
 *    只依赖已移植的 `api.network.Connector` 与 `Settings`；
 *  - 其余第三方模组的 trait 见 `traits.power` 包（并行移植）。
 *
 * 成员名与 1.7.10 保持一致（`energyThroughput`、`bufferSize`、`canConnectNode` 等），
 * 以保证 Lua 侧与文档不失效。
 */
trait PowerAcceptor
  extends power.Common
    with power.AppliedEnergistics2
    with power.Factorization
    with power.Galacticraft
    with power.IndustrialCraft2Experimental
    with power.IndustrialCraft2Classic
    with power.Mekanism
    with power.RedstoneFlux
    with power.RotaryCraft {
  // 注意：Scala 的自类型不会被继承，每个子 trait 都必须重新声明。
  self: net.minecraft.world.level.block.entity.BlockEntity =>
}
