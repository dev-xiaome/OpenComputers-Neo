package li.cil.oc.common.tileentity.traits.power

/**
 * IC2（工业时代 2）两种能量接口的公共状态位
 * （对应 1.7.10 的 `traits.power.IndustrialCraft2Common`）。
 *
 * 本文件**不依赖任何外部模组 API**（只是两个 `var`），因此原样保留：
 * `IndustrialCraft2Classic` / `IndustrialCraft2Experimental` 用它记录
 * "是否已经加入 IC2 能量网"，`common.EventHandler` 在延迟加入能量网时也会读写它。
 *
 * 注意：IC2 集成本身未移植（见同目录下两个 IC2 trait 的降级说明），
 * 因此本状态位目前恒为初始值 `false`。
 */
trait IndustrialCraft2Common {
  var addedToIC2PowerGrid = false
}
