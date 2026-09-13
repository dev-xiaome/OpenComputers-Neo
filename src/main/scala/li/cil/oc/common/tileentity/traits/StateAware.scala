package li.cil.oc.common.tileentity.traits

import li.cil.oc.api

/**
 * 有「工作状态」的方块实体（对应 1.7.10 的 `common.tileentity.traits.StateAware`）。
 *
 * 1.7.10 通过 `@Injectable.Interface` + `@Optional.Method` 把它暴露成 BuildCraft 的
 * `buildcraft.api.tiles.IHasWork`；1.21.1 已移除 ASM / 接口注入，也没有 BuildCraft 集成，
 * 因此 [[hasWork]] 只保留为普通方法（名字保持不变，比较器输出见
 * `common.block.traits.StateAware`）。
 */
trait StateAware extends api.util.StateAware {
  // 注意：Scala 的自类型不会被继承，每个子 trait 都必须重新声明。
  self: net.minecraft.world.level.block.entity.BlockEntity =>

  // TODO(integration): BuildCraft 的 `IHasWork` 接口注入不再移植，`hasWork` 仅作为兼容 API 保留。
  def hasWork: Boolean = getCurrentState.contains(api.util.StateAware.State.CanWork) || getCurrentState.contains(api.util.StateAware.State.IsWorking)
}
