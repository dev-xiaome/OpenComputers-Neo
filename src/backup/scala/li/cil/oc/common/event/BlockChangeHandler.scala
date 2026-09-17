package li.cil.oc.common.event

import li.cil.oc.common.EventHandler
import li.cil.oc.util.BlockPosition
import net.minecraft.world.level.Level
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.level.BlockEvent

import scala.collection.mutable

/**
 * 监听「某个坐标的方块发生变化」，供绑定了具体方块的升级（如 MF 升级）在方块被改动后
 * 重新建立驱动状态。
 *
 * 1.21.1 迁移要点：
 *  - 1.7.10 通过 `Level#addWorldAccess(IWorldAccess)` 拿到 `markBlockForUpdate` 回调，
 *    1.21.1 已没有 `IWorldAccess`；改为监听 `BlockEvent.NeighborNotifyEvent`
 *    （方块变化后通知邻居时触发），在服务端判定。
 *  - 因此 `Listener` 内部类、`WorldEvent.Load` 注册与 `IWorldAccess` 的一堆空实现全部删除。
 *
 * @author Vexatos
 */
object BlockChangeHandler {

  def addListener(listener: ChangeListener, coord: BlockPosition): Unit = {
    EventHandler.scheduleServer(() => changeListeners.put(listener, coord))
  }

  def removeListener(listener: ChangeListener): Unit = {
    EventHandler.scheduleServer(() => changeListeners.remove(listener))
  }

  private val changeListeners = mutable.WeakHashMap.empty[ChangeListener, BlockPosition]

  /** 注册监听器；由主类（或 [[EventHandlers]]）调用一次。 */
  def initialize(): Unit = {
    NeoForge.EVENT_BUS.addListener((e: BlockEvent.NeighborNotifyEvent) => onNeighborNotify(e))
  }

  def onNeighborNotify(e: BlockEvent.NeighborNotifyEvent): Unit = e.getLevel match {
    case world: Level =>
      val current = BlockPosition(e.getPos.getX, e.getPos.getY, e.getPos.getZ, world)
      // 先取快照：回调里可能（通过 scheduleServer 间接）改动监听器表。
      for ((listener, coord) <- changeListeners.toArray) if (coord == current) {
        listener.onBlockChanged()
      }
    case _ => // 不是普通世界（理论上不会发生），忽略。
  }

  trait ChangeListener {
    def onBlockChanged(): Unit
  }
}
