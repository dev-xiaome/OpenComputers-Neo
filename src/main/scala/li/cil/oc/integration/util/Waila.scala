package li.cil.oc.integration.util

import li.cil.oc.integration.Mods

/**
 * 工具提示模组（WAILA）集成的遗留辅助。
 *
 * 1.7.10 的 WAILA 会直接调用方块实体的 `writeToNBT`，于是 OC 需要一个「现在是不是
 * 正在为工具提示写存档」的判据，来避免把计算机的完整内核状态写进 NBT。
 *
 * 1.21.1 迁移说明：
 *  - WAILA（`mcp.mobius.waila`）在 1.21.1 没有对应版本（生态上换成了 Jade / HWYLA），
 *    因此 [[Mods.Waila]] 的 `isAvailable` 恒为 `false`，本方法也**恒返回 `false`**
 *    （等价于「总是写完整数据」，与 `common/tileentity` 下各处的 TODO 说明一致）。
 *  - 现在没有任何地方引用本对象；保留它是为了留住这个判据的语义与将来的接线点
 *    （若之后要支持 Jade，把类名判定换成 `snownee.jade` 即可）。
 */
object Waila {
  // This is used to check if certain data actually has to be saved in
  // writeToNBT calls. For some stuff we write lots of data (e.g. computer
  // state), and we want to avoid that when Waila is calling us.
  def isSavingForTooltip = Mods.Waila.isAvailable && new Exception().getStackTrace.exists(_.getClassName.startsWith("mcp.mobius.waila"))
}
