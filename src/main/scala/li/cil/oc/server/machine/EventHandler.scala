package li.cil.oc.server.machine

import li.cil.oc.api.machine.MachineHost
import li.cil.oc.server.machine.MachineLog

import scala.collection.mutable

/**
 * 延迟关闭机器的登记表（`common/EventHandler.scheduleClose` 的等价物）。
 *
 * 上游 `common/EventHandler` 会把待关闭的机器放进一个集合，然后在服务端 tick
 * 时统一调用 `tryClose()`。该文件当前还是 1.7.10 的原始拷贝（引用了
 * `cpw.mods.fml` 等已不存在的类型），无法进入编译集，所以这里做一个最小实现：
 * 提供一个与上游语义一致的登记表，由宿主（计算机方块实体）在 tick 中调用
 * [[EventHandler.process]] 处理。
 *
 * TODO(common.EventHandler): `common/EventHandler.scala` 移植完成后删除本文件，
 * 并把 `Machine.scala` 里的调用改回 `li.cil.oc.common.EventHandler`。
 */
private[machine] object EventHandler {

  private val machines = mutable.Set.empty[Machine]

  /** 登记一台需要关闭的机器（幂等）。 */
  def scheduleClose(machine: Machine): Unit = machines.synchronized {
    machines += machine
  }

  /** 取消登记（例如机器重启了）。 */
  def unscheduleClose(machine: Machine): Unit = machines.synchronized {
    machines -= machine
  }

  /**
   * 执行所有已登记的关闭操作。
   *
   * 上游是在 `ServerTickEvent` 的 START 阶段调用；`tryClose()` 内部会自己
   * 判断执行线程是否还在跑，所以这里可以直接调用。
   */
  def process(): Unit = {
    val pending = machines.synchronized {
      val copy = machines.toArray
      machines.clear()
      copy
    }
    for (machine <- pending) {
      try machine.tryClose()
      catch {
        case t: Throwable =>
          MachineLog.log.warn("Failed closing machine scheduled for shutdown.", t)
      }
    }
  }

  /** 世界卸载时清空所有待关闭登记（对应上游 `EventHandler.onWorldUnload`）。 */
  def clear(): Unit = machines.synchronized(machines.clear())
}
