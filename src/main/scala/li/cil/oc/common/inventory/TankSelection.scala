package li.cil.oc.common.inventory

/**
 * 「当前选中的储罐」状态（对应 1.7.10 的 `common.inventory.TankSelection`）。
 *
 * 1.21.1 迁移：无变化；流体侧改用 `api.internal.MultiTank` / `IFluidHandler`，
 * 这个 trait 仍然只表示选中索引。
 */
trait TankSelection {
  def selectedTank: Int

  def selectedTank_=(value: Int): Unit
}
